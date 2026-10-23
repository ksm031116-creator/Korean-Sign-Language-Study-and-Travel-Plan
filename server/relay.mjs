// Sign Korean 중계 서버 (PRD §10.3). 공급자 비밀키는 이 서버에만 두고 앱에는 넣지 않는다(설계서 원칙 13).
// 카카오맵 도보·대중교통 경로(POST /v1/routes)를 묻는다. 리뷰·평점은 공급자가 없어 UNSUPPORTED 다.
// 대신 "카카오맵에서 리뷰 보기"용으로 이름이 같은 카카오 장소 페이지를 찾는다(POST /v1/places/kakao-page).
// 실행: node server/relay.mjs  (키는 저장소 루트 .env 또는 server/.env 의 KAKAO_REST_API_KEY — git 에 올리지 않는다)
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const KAKAO_WALK = 'https://dapi.kakao.com/v2/routing/walk';
const KAKAO_TRANSIT = 'https://dapi.kakao.com/v2/routing/publictraffic';
const KAKAO_KEYWORD = 'https://dapi.kakao.com/v2/local/search/keyword.json';
// 앱의 장소 좌표는 근사치라 조금 넓게 찾는다. 같은 이름이 멀리 있으면 다른 장소다.
const PAGE_RADIUS_M = 1000;
const MAX_NAME = 60;
const NO_ROUTE = new Set([
  // 도보
  'ROUTE_RESULT_NOT_FOUND', 'START_LINK_NOT_FOUND', 'END_LINK_NOT_FOUND', 'TOO_FAR_AWAY', 'SAME_POINT', 'TOO_MANY_SEARCH_LINK',
  // 대중교통
  'NO_RESULTS', 'STARTNODES_NULL', 'ENDNODES_NULL', 'EQUAL_POINTS',
]);
// 앱이 그릴 줄 아는 탈것. 그 밖의 것(기차·고속버스 등)이 섞인 경로는 건너뛴다 — 걷기로 바꿔 보이지 않게.
const STEP_KIND = { WALKING: 'walk', BUS: 'bus', SUBWAY: 'subway' };
const MAX_BODY = 16 * 1024;
// 지금은 USB(adb reverse)로 붙는 루프백 전용이다. https 로 따로 배포하면 그 도메인을 여기에 더해야 한다.
const ALLOWED_HOST = /^(127\.0\.0\.1|localhost)(:\d+)?$/;

const json = (status, body) => ({ status, body: JSON.stringify(body) });
const error = (status, code, retryable) => json(status, { error: { code, retryable } });
const isCoord = (p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng) && Math.abs(p.lat) <= 90 && Math.abs(p.lng) <= 180;
const list = (v) => (Array.isArray(v) ? v : []);

class Upstream extends Error {}

/** 요청 하나를 처리한다. HTTP 서버와 떼어 두어 테스트가 가짜 fetch 로 부를 수 있게 한다. 어떤 응답이 와도 던지지 않는다. */
export function createHandler({ kakaoKey, fetch = globalThis.fetch, timeoutMs = 8_000 }) {
  // 외부 호출은 헤더부터 본문 끝까지 8초에서 끊는다(PRD §10.3). 늦은 fetch 도 abort 로 멈춘다.
  async function askKakao(url) {
    const ac = new AbortController();
    let timer;
    const deadline = new Promise((_, reject) => {
      timer = setTimeout(() => {
        ac.abort();
        reject(new Error('timeout'));
      }, timeoutMs);
    });
    try {
      return await Promise.race([
        (async () => {
          const res = await fetch(url, { headers: { Authorization: `KakaoAK ${kakaoKey}` }, signal: ac.signal });
          // 원시 오류 본문은 앱에 넘기지 않는다(키가 들어 있을 수 있다). 읽지 않는 본문은 닫아 연결을 돌려준다.
          const failWith = (fail) => {
            res.body?.cancel?.().catch(() => {});
            return { fail };
          };
          if (res.status === 429) return failWith(error(503, 'RATE_LIMITED', true));
          if (res.status === 401 || res.status === 403) return failWith(error(502, 'UNSUPPORTED', false));
          // 400 은 좌표가 지원 범위 밖인 경우 등 — 다시 해도 같다.
          if (res.status === 400) return failWith(error(422, 'NO_DATA', false));
          if (!res.ok) return failWith(error(502, 'UPSTREAM_ERROR', res.status >= 500));
          return { body: await res.json() };
        })(),
        deadline,
      ]);
    } catch (e) {
      return { fail: e?.message === 'timeout' ? error(504, 'UPSTREAM_TIMEOUT', true) : error(502, 'UPSTREAM_ERROR', true) };
    } finally {
      clearTimeout(timer);
    }
  }

  async function routes(rawBody) {
    let req;
    try {
      req = JSON.parse(rawBody ?? '');
    } catch {
      return error(400, 'INVALID_INPUT', false);
    }
    if (!isCoord(req?.origin) || !isCoord(req?.destination) || (req.mode !== 'walk' && req.mode !== 'transit')) return error(400, 'INVALID_INPUT', false);
    if (!kakaoKey) return error(501, 'UNSUPPORTED', false);

    const transit = req.mode === 'transit';
    const url = new URL(transit ? KAKAO_TRANSIT : KAKAO_WALK);
    url.searchParams.set('start_x', String(req.origin.lng));
    url.searchParams.set('start_y', String(req.origin.lat));
    url.searchParams.set('end_x', String(req.destination.lng));
    url.searchParams.set('end_y', String(req.destination.lat));

    const { body, fail } = await askKakao(url);
    if (fail) return fail;
    if (body?.status !== 'OK') return NO_ROUTE.has(body?.status) ? error(422, 'NO_DATA', false) : error(502, 'UPSTREAM_ERROR', true);
    try {
      return transit ? transitRoute(body) : walkRoute(body);
    } catch (e) {
      if (e instanceof Upstream || e instanceof TypeError) return error(502, 'UPSTREAM_ERROR', true);
      throw e;
    }
  }

  // 이름이 정확히 같은(공백 무시) 장소만. 비슷한 이름("Apple 가로수길")으로 대신하면 엉뚱한 리뷰를 보게 된다.
  async function kakaoPage(rawBody) {
    let req;
    try {
      req = JSON.parse(rawBody ?? '');
    } catch {
      return error(400, 'INVALID_INPUT', false);
    }
    const name = typeof req?.name === 'string' ? req.name.trim() : '';
    if (!name || name.length > MAX_NAME || !isCoord(req)) return error(400, 'INVALID_INPUT', false);
    if (!kakaoKey) return error(501, 'UNSUPPORTED', false);

    const url = new URL(KAKAO_KEYWORD);
    url.searchParams.set('query', name);
    url.searchParams.set('x', String(req.lng));
    url.searchParams.set('y', String(req.lat));
    url.searchParams.set('radius', String(PAGE_RADIUS_M));
    url.searchParams.set('sort', 'accuracy');
    url.searchParams.set('size', '15');
    const { body, fail } = await askKakao(url);
    if (fail) return fail;
    if (!Array.isArray(body?.documents)) return error(502, 'UPSTREAM_ERROR', true);
    // 공백·대소문자·유니코드 정규형(NFC/NFD)만 무시한다.
    const key = (s) => s.normalize('NFC').replace(/\s+/g, '').toLowerCase();
    const want = key(name);
    // id 가 숫자인 것만 — 받은 place_url 을 그대로 쓰지 않고 https 주소를 직접 만든다.
    // 같은 이름이 여럿이면(체인점 등) 앱 좌표에서 가장 가까운 곳.
    const dist = (d) => (Number.isFinite(Number(d.distance)) && d.distance !== '' ? Number(d.distance) : Infinity);
    const hit = body.documents
      .filter((d) => typeof d?.place_name === 'string' && key(d.place_name) === want && /^\d+$/.test(String(d.id)))
      .reduce((best, d) => (best === null || dist(d) < dist(best) ? d : best), null);
    if (!hit) return error(404, 'NOT_FOUND', false);
    return json(200, { url: `https://place.map.kakao.com/${hit.id}` });
  }

  // 카카오의 [x(경도), y(위도)] 를 앱의 [위도, 경도] 로. 단계가 이어지는 같은 점은 한 번만 넣는다.
  // 점이 없으면 빈 경로선이다 — 출발·도착을 직선으로 잇지 않는다(FR15).
  function toPath(steps) {
    const path = [];
    for (const step of list(steps)) {
      for (const point of list(step?.path?.points)) {
        if (!Array.isArray(point)) throw new Upstream('point');
        const [x, y] = point;
        if (!Number.isFinite(x) || !Number.isFinite(y)) throw new Upstream('point');
        const last = path[path.length - 1];
        if (!last || last[0] !== y || last[1] !== x) path.push([y, x]);
      }
    }
    return path.length >= 2 ? path : [];
  }

  function walkRoute(body) {
    const props = body.route?.properties;
    if (!Number.isFinite(props?.totalDistance) || !Number.isFinite(props?.totalTime)) throw new Upstream('walk');
    const steps = list(body.route.legs).flatMap((leg) => list(leg?.steps));
    return json(200, { distance_m: props.totalDistance, duration_s: props.totalTime, path: toPath(steps), attribution: 'Kakao Map' });
  }

  // 대중교통은 그릴 수 있는 경로 중 가장 빨리 도착하는 것을 고른다. 단계마다 탈것과 노선 이름, 공급자 초를 그대로 넘긴다.
  function transitRoute(body) {
    if (!Array.isArray(body.routes)) throw new Upstream('routes');
    const usable = body.routes
      .filter((r) => Number.isFinite(r?.properties?.totalTime) && Number.isFinite(r?.properties?.totalDistance))
      .filter((r) => list(r.steps).length > 0 && list(r.steps).every((s) => STEP_KIND[s?.properties?.type]));
    if (usable.length === 0) return error(422, 'NO_DATA', false);
    const best = usable.reduce((a, b) => (b.properties.totalTime < a.properties.totalTime ? b : a));
    const steps = best.steps.map((step) => ({
      kind: STEP_KIND[step.properties.type],
      // 같은 노선의 열차가 여러 대 오므로 이름은 한 번씩만(순서 유지).
      lines: [...new Set(list(step.properties.vehicles).map((v) => v?.name).filter((n) => typeof n === 'string' && n))],
      time_s: Number.isFinite(step.properties.time) ? step.properties.time : null,
      distance_m: Number.isFinite(step.properties.distance) ? step.properties.distance : null,
    }));
    const p = best.properties;
    return json(200, {
      distance_m: p.totalDistance,
      duration_s: p.totalTime,
      path: toPath(best.steps),
      attribution: 'Kakao Map',
      transit: { transfers: Number.isFinite(p.transfers) ? p.transfers : 0, fare_won: Number.isFinite(p.fare?.value) ? p.fare.value : null, steps },
    });
  }

  return async function handle({ method, path, body, host }) {
    // 같은 PC 의 브라우저 페이지가 DNS 리바인딩으로 쿼터를 쓰지 못하게 Host 를 본다(서버는 루프백에서만 듣는다).
    if (host !== undefined && !ALLOWED_HOST.test(host)) return error(403, 'FORBIDDEN', false);
    if (body !== undefined && Buffer.byteLength(body) > MAX_BODY) return error(413, 'INVALID_INPUT', false);
    const pathname = path.split('?')[0];
    if (method === 'POST' && pathname === '/v1/routes') return routes(body);
    if (method === 'POST' && pathname === '/v1/places/kakao-page') return kakaoPage(body);
    // 리뷰·평점 공급자는 아직 연결하지 않았다. 앱은 "연결되지 않음"으로 보인다.
    if (method === 'GET' && (/^\/v1\/places\/[^/]+\/reviews$/.test(pathname) || /^\/v1\/cards\/[^/]+\/places$/.test(pathname))) {
      return error(501, 'UNSUPPORTED', false);
    }
    return error(404, 'NOT_FOUND', false);
  };
}

/** .env 의 KEY=VALUE 줄을 읽는다(외부 패키지 없이). */
function loadEnv(file) {
  if (!existsSync(file)) return {};
  const env = {};
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (m && !line.trimStart().startsWith('#')) env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
  return env;
}

/**
 * HTTP 서버. 본문이 [MAX_BODY] 를 넘으면 그 자리에서 413 으로 끝내고 더 읽지 않는다 — 넘친 조각을 버리고
 * 앞부분만 처리하지 않는다(리뷰 후속 M1). 어떤 요청도 서버를 죽이지 않는다.
 */
export function createRelayServer(handle) {
  return createServer((req, res) => {
    const chunks = [];
    let size = 0;
    let done = false;
    const reply = (out) => {
      if (done) return;
      done = true;
      // 413 은 본문을 다 받기 전에 보내므로 연결을 닫는다(남은 업로드를 끝까지 받지 않게).
      const headers = { 'Content-Type': 'application/json; charset=utf-8' };
      if (out.status === 413) headers.Connection = 'close';
      res.writeHead(out.status, headers);
      res.end(out.body);
      // 좌표·키는 로그에 남기지 않는다(NFR05). 경로와 상태만.
      console.log(`${req.method} ${(req.url ?? '').split('?')[0]} -> ${out.status}`);
    };
    // 업로드 중 끊긴 연결 등. 처리하지 않으면 프로세스가 죽는다.
    req.on('error', () => {
      done = true;
    });
    req.on('data', (c) => {
      if (done) return;
      size += c.length;
      if (size > MAX_BODY) {
        reply(error(413, 'INVALID_INPUT', false));
        // 남은 업로드는 읽어 버리기만 한다(쌓지 않는다).
        return;
      }
      chunks.push(c);
    });
    req.on('end', async () => {
      if (done) return;
      let out;
      try {
        out = await handle({ method: req.method, path: req.url, body: Buffer.concat(chunks).toString('utf8'), host: req.headers.host ?? '' });
      } catch {
        out = error(500, 'UPSTREAM_ERROR', true);
      }
      reply(out);
    });
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  // 키는 저장소 루트의 .env(TourAPI·Gemini 키와 같은 곳) 또는 server/.env 에 둔다. 빈 값은 무시한다. 둘 다 git 에 올리지 않는다.
  const here = dirname(fileURLToPath(import.meta.url));
  const env = {};
  for (const source of [loadEnv(join(here, '..', '.env')), loadEnv(join(here, '.env')), process.env]) {
    for (const [k, v] of Object.entries(source)) if (v) env[k] = v;
  }
  const port = Number(env.PORT || 8787);
  const handle = createHandler({ kakaoKey: env.KAKAO_REST_API_KEY ?? '' });
  createRelayServer(handle).listen(port, '127.0.0.1', () => {
    console.log(`Sign Korean relay on http://127.0.0.1:${port} (Kakao key ${env.KAKAO_REST_API_KEY ? 'set' : 'MISSING'})`);
  });
}
