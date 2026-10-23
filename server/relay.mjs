// Sign Korean 중계 서버 (PRD §10.3). 공급자 비밀키는 이 서버에만 두고 앱에는 넣지 않는다(설계서 원칙 13).
// 지금은 카카오맵 도보 경로(POST /v1/routes)만 실제로 묻는다. 리뷰·평점은 공급자가 없어 UNSUPPORTED 다.
// 실행: node server/relay.mjs  (키는 저장소 루트 .env 또는 server/.env 의 KAKAO_REST_API_KEY — git 에 올리지 않는다)
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const KAKAO_WALK = 'https://dapi.kakao.com/v2/routing/walk';
const KAKAO_TRANSIT = 'https://dapi.kakao.com/v2/routing/publictraffic';
const NO_ROUTE = new Set([
  // 도보
  'ROUTE_RESULT_NOT_FOUND', 'START_LINK_NOT_FOUND', 'END_LINK_NOT_FOUND', 'TOO_FAR_AWAY', 'SAME_POINT', 'TOO_MANY_SEARCH_LINK',
  // 대중교통
  'NO_RESULTS', 'STARTNODES_NULL', 'ENDNODES_NULL', 'EQUAL_POINTS',
]);
const STEP_KIND = { WALKING: 'walk', BUS: 'bus', SUBWAY: 'subway' };

const json = (status, body) => ({ status, body: JSON.stringify(body) });
const error = (status, code, retryable) => json(status, { error: { code, retryable } });
const isCoord = (p) => p && Number.isFinite(p.lat) && Number.isFinite(p.lng) && Math.abs(p.lat) <= 90 && Math.abs(p.lng) <= 180;

/** 요청 하나를 처리한다. HTTP 서버와 떼어 두어 테스트가 가짜 fetch 로 부를 수 있게 한다. */
export function createHandler({ kakaoKey, fetch = globalThis.fetch, timeoutMs = 8_000 }) {
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

    let res;
    let timer;
    try {
      // 외부 호출은 8초에서 끊는다(PRD §10.3). 늦은 응답은 버린다.
      res = await Promise.race([
        fetch(url, { headers: { Authorization: `KakaoAK ${kakaoKey}` } }),
        new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('timeout')), timeoutMs); }),
      ]);
    } catch (e) {
      return e?.message === 'timeout' ? error(504, 'UPSTREAM_TIMEOUT', true) : error(502, 'UPSTREAM_ERROR', true);
    } finally {
      clearTimeout(timer);
    }
    // 원시 오류 본문은 앱에 넘기지 않는다(키가 들어 있을 수 있다).
    if (res.status === 429) return error(503, 'RATE_LIMITED', true);
    if (res.status === 401 || res.status === 403) return error(502, 'UNSUPPORTED', false);
    if (!res.ok) return error(502, 'UPSTREAM_ERROR', res.status >= 500);

    let body;
    try {
      body = await res.json();
    } catch {
      return error(502, 'UPSTREAM_ERROR', true);
    }
    if (body?.status !== 'OK') return NO_ROUTE.has(body?.status) ? error(422, 'NO_DATA', false) : error(502, 'UPSTREAM_ERROR', true);

    return transit ? transitRoute(body, req) : walkRoute(body, req);
  }

  // 카카오의 [x(경도), y(위도)] 를 앱의 [위도, 경도] 로. 단계가 이어지는 같은 점은 한 번만 넣는다.
  function toPath(steps, req) {
    const path = [];
    for (const step of steps) {
      for (const [x, y] of step.path?.points ?? []) {
        const last = path[path.length - 1];
        if (!last || last[0] !== y || last[1] !== x) path.push([y, x]);
      }
    }
    if (path.length < 2) return [[req.origin.lat, req.origin.lng], [req.destination.lat, req.destination.lng]];
    return path;
  }

  function walkRoute(body, req) {
    const props = body.route?.properties;
    if (!Number.isFinite(props?.totalDistance) || !Number.isFinite(props?.totalTime)) return error(502, 'UPSTREAM_ERROR', true);
    const steps = (body.route.legs ?? []).flatMap((leg) => leg.steps ?? []);
    return json(200, { distance_m: props.totalDistance, duration_s: props.totalTime, path: toPath(steps, req), attribution: 'Kakao Map' });
  }

  // 대중교통은 카카오가 준 경로 중 가장 빨리 도착하는 것을 고른다. 단계마다 탈것(걷기·버스·지하철)과 노선 이름을 넘긴다.
  function transitRoute(body, req) {
    const candidates = (body.routes ?? []).filter((r) => Number.isFinite(r.properties?.totalTime) && Number.isFinite(r.properties?.totalDistance));
    if (candidates.length === 0) return error(422, 'NO_DATA', false);
    const best = candidates.reduce((a, b) => (b.properties.totalTime < a.properties.totalTime ? b : a));
    const steps = [];
    for (const step of best.steps ?? []) {
      const kind = STEP_KIND[step.properties?.type];
      if (!kind) return error(502, 'UPSTREAM_ERROR', true);
      steps.push({
        kind,
        // 같은 노선의 열차가 여러 대 오므로 이름은 한 번씩만(순서 유지).
        lines: [...new Set((step.properties.vehicles ?? []).map((v) => v.name).filter(Boolean))],
        minutes: Math.ceil((step.properties.time ?? 0) / 60),
        distance_m: step.properties.distance ?? 0,
      });
    }
    const p = best.properties;
    return json(200, {
      distance_m: p.totalDistance,
      duration_s: p.totalTime,
      path: toPath(best.steps ?? [], req),
      attribution: 'Kakao Map',
      transit: { transfers: p.transfers ?? 0, fare_won: p.fare?.value ?? null, steps },
    });
  }

  return async function handle({ method, path, body }) {
    const pathname = path.split('?')[0];
    if (method === 'POST' && pathname === '/v1/routes') return routes(body);
    // 리뷰·평점 공급자(Google Places 등)는 아직 연결하지 않았다. 앱은 "연결되지 않음"으로 보인다.
    if (method === 'GET' && (/^\/v1\/places\/[^/]+\/reviews$/.test(pathname) || /^\/v1\/cards\/[^/]+\/places$/.test(pathname))) {
      return error(501, 'UNSUPPORTED', false);
    }
    return error(404, 'NOT_FOUND', false);
  };
}

/** server/.env 의 KEY=VALUE 줄을 읽는다(외부 패키지 없이). */
function loadEnv(file) {
  if (!existsSync(file)) return {};
  const env = {};
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (m && !line.trimStart().startsWith('#')) env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
  return env;
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
  createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', async () => {
      const out = await handle({ method: req.method, path: req.url, body: Buffer.concat(chunks).toString('utf8') });
      res.writeHead(out.status, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(out.body);
      // 좌표·키는 로그에 남기지 않는다(NFR05). 경로와 상태만.
      console.log(`${req.method} ${req.url.split('?')[0]} -> ${out.status}`);
    });
  }).listen(port, '127.0.0.1', () => {
    console.log(`Sign Korean relay on http://127.0.0.1:${port} (Kakao key ${env.KAKAO_REST_API_KEY ? 'set' : 'MISSING'})`);
  });
}
