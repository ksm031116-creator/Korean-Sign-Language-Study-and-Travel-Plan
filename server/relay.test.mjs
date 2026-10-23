// 중계 서버 테스트 (PRD §10.3). 카카오 API 는 가짜 fetch 로 흉내 낸다 — 키도 네트워크도 쓰지 않는다.
// 실행: node --test server/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler, createRelayServer } from './relay.mjs';

const KAKAO_OK = {
  status: 'OK',
  route: {
    properties: { totalDistance: 1234, totalTime: 905, landingUrl: 'https://map.kakao.com/link/by/walk/...' },
    legs: [{
      properties: { distance: 1234, time: 905 },
      steps: [
        { properties: { distance: 600 }, path: { points: [[126.99, 37.57], [126.991, 37.571]] } },
        { properties: { distance: 634 }, path: { points: [[126.991, 37.571], [126.985, 37.575]] } },
      ],
    }],
  },
};

function fakeFetch(respond) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url: String(url), init });
    const { status = 200, body } = await respond(String(url), init);
    return { status, ok: status >= 200 && status < 300, json: async () => body };
  };
  fn.calls = calls;
  return fn;
}

async function call(handler, method, path, body) {
  const res = await handler({ method, path, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, body: JSON.parse(res.body) };
}

const walk = { origin: { lat: 37.57, lng: 126.99 }, destination: { lat: 37.575, lng: 126.985 }, mode: 'walk' };

test('경로 - 카카오 도보 경로를 앱 계약(distance_m, duration_s, [lat,lng] 경로, 출처)으로 바꾼다', async () => {
  const fetch = fakeFetch(async () => ({ body: KAKAO_OK }));
  const res = await call(createHandler({ kakaoKey: 'test-key', fetch }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 200);
  assert.equal(res.body.distance_m, 1234);
  assert.equal(res.body.duration_s, 905);
  // 카카오는 [x(경도), y(위도)], 앱은 [위도, 경도]. 단계 사이의 같은 점은 한 번만.
  assert.deepEqual(res.body.path, [[37.57, 126.99], [37.571, 126.991], [37.575, 126.985]]);
  assert.equal(res.body.attribution, 'Kakao Map');
  const url = new URL(fetch.calls[0].url);
  assert.equal(url.origin + url.pathname, 'https://dapi.kakao.com/v2/routing/walk');
  assert.equal(url.searchParams.get('start_x'), '126.99');
  assert.equal(url.searchParams.get('start_y'), '37.57');
  assert.equal(url.searchParams.get('end_x'), '126.985');
  assert.equal(fetch.calls[0].init.headers.Authorization, 'KakaoAK test-key');
});

test('경로를 못 찾으면 NO_DATA(다시 해도 같다)', async () => {
  for (const status of ['ROUTE_RESULT_NOT_FOUND', 'START_LINK_NOT_FOUND', 'END_LINK_NOT_FOUND', 'TOO_FAR_AWAY', 'SAME_POINT']) {
    const fetch = fakeFetch(async () => ({ body: { status } }));
    const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/routes', walk);
    assert.equal(res.status, 422, status);
    assert.deepEqual(res.body.error, { code: 'NO_DATA', retryable: false }, status);
  }
});

test('카카오가 너무 많은 요청이라고 하면 RATE_LIMITED(다시 해 볼 수 있다)', async () => {
  const fetch = fakeFetch(async () => ({ status: 429, body: { msg: 'quota' } }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 503);
  assert.deepEqual(res.body.error, { code: 'RATE_LIMITED', retryable: true });
});

test('키가 틀렸거나 권한이 없으면 UNSUPPORTED — 원시 오류와 키는 앱에 보내지 않는다', async () => {
  const fetch = fakeFetch(async () => ({ status: 401, body: { msg: 'wrong appKey test-key' } }));
  const res = await call(createHandler({ kakaoKey: 'test-key', fetch }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 502);
  assert.deepEqual(res.body.error, { code: 'UNSUPPORTED', retryable: false });
  assert.ok(!JSON.stringify(res.body).includes('test-key'));
});

test('8초 안에 답이 없으면 UPSTREAM_TIMEOUT', async () => {
  const fetch = fakeFetch(() => new Promise(() => {}));
  const res = await call(createHandler({ kakaoKey: 'k', fetch, timeoutMs: 50 }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 504);
  assert.deepEqual(res.body.error, { code: 'UPSTREAM_TIMEOUT', retryable: true });
});

test('잘못된 요청 - 좌표가 없거나 도보가 아니면 INVALID_INPUT, 카카오에 묻지 않는다', async () => {
  const fetch = fakeFetch(async () => ({ body: KAKAO_OK }));
  const handler = createHandler({ kakaoKey: 'k', fetch });
  for (const body of [{}, { ...walk, mode: 'car' }, { ...walk, origin: { lat: 'x', lng: 1 } }]) {
    const res = await call(handler, 'POST', '/v1/routes', body);
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'INVALID_INPUT');
  }
  const broken = await handler({ method: 'POST', path: '/v1/routes', body: '{not json' });
  assert.equal(broken.status, 400);
  assert.equal(fetch.calls.length, 0);
});

test('키가 설정되지 않은 서버는 UNSUPPORTED', async () => {
  const res = await call(createHandler({ kakaoKey: '', fetch: fakeFetch(async () => ({ body: KAKAO_OK })) }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 501);
  assert.equal(res.body.error.code, 'UNSUPPORTED');
});

test('리뷰·평점은 아직 공급자가 없어 UNSUPPORTED (앱은 "연결되지 않음"으로 보인다)', async () => {
  const handler = createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body: {} })) });
  for (const path of ['/v1/places/gyeongbokgung/reviews', '/v1/cards/t-market-1/places']) {
    const res = await call(handler, 'GET', path);
    assert.equal(res.status, 501, path);
    assert.deepEqual(res.body.error, { code: 'UNSUPPORTED', retryable: false }, path);
  }
});

test('모르는 주소는 NOT_FOUND', async () => {
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body: {} })) }), 'GET', '/v1/unknown');
  assert.equal(res.status, 404);
  assert.equal(res.body.error.code, 'NOT_FOUND');
});

// ---------- 대중교통 (TRN) ----------

const KAKAO_TRANSIT = {
  status: 'OK',
  properties: { total: 2 },
  routes: [
    {
      properties: { type: 'BUS', totalDistance: 6000, totalTime: 2700, transfers: 0, fare: { value: 1500 } },
      steps: [{ properties: { type: 'BUS', distance: 6000, time: 2700, vehicles: [{ name: '7212', type: '간선' }] }, path: { points: [[126.99, 37.57], [126.985, 37.575]] } }],
    },
    {
      properties: { type: 'BUS_AND_SUBWAY', totalDistance: 5013, totalTime: 2115, transfers: 1, fare: { value: 1550 } },
      steps: [
        { properties: { type: 'WALKING', distance: 300, time: 240 }, path: { points: [[126.99, 37.57], [126.991, 37.571]] } },
        { properties: { type: 'SUBWAY', distance: 4000, time: 1200, vehicles: [{ name: '3호선', type: '일반' }] }, path: { points: [[126.991, 37.571], [126.988, 37.574]] } },
        { properties: { type: 'BUS', distance: 713, time: 675, vehicles: [{ name: '7212' }, { name: '1020' }] }, path: { points: [[126.988, 37.574], [126.985, 37.575]] } },
      ],
    },
  ],
};

const transitReq = { ...walk, mode: 'transit' };

test('TRN 대중교통 - 카카오 대중교통 경로 중 가장 빠른 것을 고르고 환승·요금·단계를 넘긴다', async () => {
  const fetch = fakeFetch(async () => ({ body: KAKAO_TRANSIT }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/routes', transitReq);
  assertEqual200(res);
  const url = new URL(fetch.calls[0].url);
  assert.equal(url.origin + url.pathname, 'https://dapi.kakao.com/v2/routing/publictraffic');
  assert.equal(res.body.duration_s, 2115);
  assert.equal(res.body.distance_m, 5013);
  assert.deepEqual(res.body.path, [[37.57, 126.99], [37.571, 126.991], [37.574, 126.988], [37.575, 126.985]]);
  assert.deepEqual(res.body.transit, {
    transfers: 1,
    fare_won: 1550,
    steps: [
      { kind: 'walk', lines: [], time_s: 240, distance_m: 300 },
      { kind: 'subway', lines: ['3호선'], time_s: 1200, distance_m: 4000 },
      { kind: 'bus', lines: ['7212', '1020'], time_s: 675, distance_m: 713 },
    ],
  });
});

test('TRN 대중교통 경로가 없으면 NO_DATA', async () => {
  for (const status of ['NO_RESULTS', 'STARTNODES_NULL', 'ENDNODES_NULL', 'EQUAL_POINTS']) {
    const fetch = fakeFetch(async () => ({ body: { status } }));
    const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/routes', transitReq);
    assert.equal(res.status, 422, status);
    assert.deepEqual(res.body.error, { code: 'NO_DATA', retryable: false }, status);
  }
});

function assertEqual200(res) {
  assert.equal(res.status, 200, JSON.stringify(res.body));
}

test('TRN 같은 노선의 열차가 여러 번 와도 노선 이름은 한 번만', async () => {
  const body = structuredClone(KAKAO_TRANSIT);
  body.routes[1].steps[1].properties.vehicles = [{ name: '1호선' }, { name: '1호선' }, { name: '4호선' }, { name: '1호선' }];
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body })) }), 'POST', '/v1/routes', transitReq);
  assert.deepEqual(res.body.transit.steps[1].lines, ['1호선', '4호선']);
});

// ---------- 리뷰 수정 (RLY) ----------

test('RLY-M1 카카오 응답 모양이 이상해도 서버는 죽지 않고 UPSTREAM_ERROR 로 답한다', async () => {
  const shapes = [
    { status: 'OK', routes: {} },
    { status: 'OK', routes: [{ properties: { totalTime: 10, totalDistance: 10, transfers: 0 }, steps: [null] }] },
    { status: 'OK', routes: [{ properties: { totalTime: 10, totalDistance: 10, transfers: 0 }, steps: [{ properties: { type: 'BUS', time: 60, vehicles: [null] }, path: { points: [[126.99, 37.57], [126.98, 37.58]] } }] }] },
  ];
  for (const body of shapes) {
    const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body })) }), 'POST', '/v1/routes', transitReq);
    assert.ok([200, 422, 502].includes(res.status), JSON.stringify(body));
  }
  const walkShape = { status: 'OK', route: { properties: { totalDistance: 5, totalTime: 5 }, legs: [{ steps: [{ path: { points: [{ x: 1, y: 2 }] } }] }] } };
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body: walkShape })) }), 'POST', '/v1/routes', walk);
  assert.ok([200, 502].includes(res.status));
});

test('RLY-M2 모르는 탈것(기차 등)이 섞인 경로는 건너뛰고 다음으로 빠른 경로를 쓴다, 다 그러면 NO_DATA', async () => {
  const body = structuredClone(KAKAO_TRANSIT);
  body.routes[1].steps[1].properties.type = 'TRAIN';
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body })) }), 'POST', '/v1/routes', transitReq);
  assert.equal(res.status, 200);
  assert.equal(res.body.duration_s, 2700);
  body.routes[0].steps[0].properties.type = 'EXPRESS_BUS';
  const none = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body })) }), 'POST', '/v1/routes', transitReq);
  assert.equal(none.status, 422);
  assert.deepEqual(none.body.error, { code: 'NO_DATA', retryable: false });
});

test('RLY-M3 본문을 늦게 보내도 시간 제한 안에서 끊는다', async () => {
  const fetch = async () => ({ status: 200, ok: true, json: () => new Promise(() => {}) });
  const started = Date.now();
  const res = await call(createHandler({ kakaoKey: 'k', fetch, timeoutMs: 50 }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 504);
  assert.ok(Date.now() - started < 1000);
});

test('RLY-L4 경로 점이 없으면 직선을 그리지 않고 빈 경로선', async () => {
  const body = structuredClone(KAKAO_OK);
  body.route.legs = [];
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body })) }), 'POST', '/v1/routes', walk);
  assert.equal(res.status, 200);
  assert.deepEqual(res.body.path, []);
});

test('RLY-L5 대중교통 단계 시간은 초 그대로(time_s), 없으면 null', async () => {
  const body = structuredClone(KAKAO_TRANSIT);
  delete body.routes[1].steps[2].properties.time;
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body })) }), 'POST', '/v1/routes', transitReq);
  assert.deepEqual(res.body.transit.steps.map((s) => s.time_s), [240, 1200, null]);
});

test('카카오 400 은 이 좌표로는 길이 없다는 뜻이라 NO_DATA', async () => {
  const res = await call(createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ status: 400, body: {} })) }), 'POST', '/v1/routes', walk);
  assert.deepEqual(res.body.error, { code: 'NO_DATA', retryable: false });
});

test('RLY-L3 본문이 16KB 를 넘으면 413, 다른 Host 로 온 요청은 403', async () => {
  const handler = createHandler({ kakaoKey: 'k', fetch: fakeFetch(async () => ({ body: KAKAO_OK })) });
  const big = await handler({ method: 'POST', path: '/v1/routes', body: 'x'.repeat(17 * 1024) });
  assert.equal(big.status, 413);
  const rebinding = await handler({ method: 'POST', path: '/v1/routes', body: JSON.stringify(walk), host: 'evil.example:8787' });
  assert.equal(rebinding.status, 403);
  const ok = await handler({ method: 'POST', path: '/v1/routes', body: JSON.stringify(walk), host: '127.0.0.1:8787' });
  assert.equal(ok.status, 200);
});

// 실제 HTTP 서버 경로(리뷰 후속 M1). handler 를 직접 부르면 data 이벤트의 크기 제한을 지나치지 않는다.
async function withServer(run) {
  const handle = createHandler({ kakaoKey: 'k', fetch: fakeFetch(() => ({ body: KAKAO_OK })) });
  const server = createRelayServer(handle);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  try {
    await run(`http://127.0.0.1:${server.address().port}`);
  } finally {
    await new Promise((r) => server.close(r));
  }
}

const VALID = JSON.stringify({ origin: { lat: 37.57, lng: 126.99 }, destination: { lat: 37.575, lng: 126.985 }, mode: 'walk' });

test('RLY-M1 서버: 16KB 를 넘는 본문은 413 이다(잘라서 처리하지 않는다)', async () => {
  await withServer(async (base) => {
    const big = await fetch(`${base}/v1/routes`, { method: 'POST', body: 'x'.repeat(64 * 1024) });
    assert.equal(big.status, 413);
    // 유효한 JSON 뒤에 공백을 붙여 상한을 넘겨도 처리하지 않는다.
    const padded = await fetch(`${base}/v1/routes`, { method: 'POST', body: VALID + ' '.repeat(20 * 1024) });
    assert.equal(padded.status, 413);
  });
});

test('RLY-M1 서버: 상한 안의 요청은 그대로 처리한다', async () => {
  await withServer(async (base) => {
    const ok = await fetch(`${base}/v1/routes`, { method: 'POST', body: VALID });
    assert.equal(ok.status, 200);
    assert.equal((await ok.json()).distance_m, 1234);
  });
});

// 카카오맵 장소 페이지 찾기 ("카카오맵에서 리뷰 보기"). 이름이 정확히 같은(공백 무시) 장소만 돌려준다.
const KEYWORD_OK = {
  documents: [
    { id: '17577343', place_name: '경복궁 주차장', place_url: 'http://place.map.kakao.com/17577343' },
    { id: '18619553', place_name: '경복궁', place_url: 'http://place.map.kakao.com/18619553' },
  ],
};
const PAGE = { name: '경복궁', lat: 37.5796, lng: 126.977 };

test('KRL 이름이 같은 장소의 https 페이지를 돌려준다(키워드 검색, 좌표 근처, 정확도순)', async () => {
  const fetch = fakeFetch(() => ({ body: KEYWORD_OK }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/places/kakao-page', PAGE);
  assert.equal(res.status, 200);
  assert.deepEqual(res.body, { url: 'https://place.map.kakao.com/18619553' });
  const url = new URL(fetch.calls[0].url);
  assert.equal(url.origin + url.pathname, 'https://dapi.kakao.com/v2/local/search/keyword.json');
  assert.equal(url.searchParams.get('query'), '경복궁');
  assert.equal(url.searchParams.get('x'), '126.977');
  assert.equal(url.searchParams.get('y'), '37.5796');
  assert.equal(url.searchParams.get('sort'), 'accuracy');
  assert.ok(Number(url.searchParams.get('radius')) <= 2000);
});

test('KRL 공백만 다른 이름도 같은 장소로 본다', async () => {
  const fetch = fakeFetch(() => ({ body: { documents: [{ id: '5', place_name: '을지로인쇄골목' }] } }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/places/kakao-page', { ...PAGE, name: '을지로 인쇄골목' });
  assert.deepEqual(res.body, { url: 'https://place.map.kakao.com/5' });
});

test('KRL 이름이 같은 장소가 없으면 404 NOT_FOUND (비슷한 장소로 대신하지 않는다)', async () => {
  const fetch = fakeFetch(() => ({ body: { documents: [{ id: '9', place_name: 'Apple 가로수길' }] } }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/places/kakao-page', { ...PAGE, name: '가로수길' });
  assert.equal(res.status, 404);
  assert.equal(res.body.error.code, 'NOT_FOUND');
});

test('KRL id 가 숫자가 아니면 그 장소는 건너뛴다(주소를 만들지 않는다)', async () => {
  const fetch = fakeFetch(() => ({ body: { documents: [{ id: '../evil', place_name: '경복궁' }] } }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/places/kakao-page', PAGE);
  assert.equal(res.status, 404);
});

test('KRL 잘못된 입력은 400, 키가 없으면 501, 한도 초과는 503', async () => {
  const handler = createHandler({ kakaoKey: 'k', fetch: fakeFetch(() => ({ status: 429, body: {} })) });
  for (const bad of [{ ...PAGE, name: '' }, { ...PAGE, name: 'x'.repeat(61) }, { ...PAGE, lat: 'a' }, { name: 1, lat: 1, lng: 1 }]) {
    assert.equal((await call(handler, 'POST', '/v1/places/kakao-page', bad)).status, 400);
  }
  assert.equal((await call(handler, 'POST', '/v1/places/kakao-page', PAGE)).status, 503);
  assert.equal((await call(createHandler({ kakaoKey: '' }), 'POST', '/v1/places/kakao-page', PAGE)).status, 501);
});

test('KRL 응답 모양이 깨지면 502', async () => {
  const handler = createHandler({ kakaoKey: 'k', fetch: fakeFetch(() => ({ body: { documents: 'x' } })) });
  assert.equal((await call(handler, 'POST', '/v1/places/kakao-page', PAGE)).status, 502);
});

test('KRL 같은 이름이 여럿이면 가장 가까운 장소, 대소문자·유니코드 정규형은 무시한다', async () => {
  const docs = [
    { id: '1', place_name: 'CGV 용산', distance: '900' },
    { id: '2', place_name: 'cgv 용산', distance: '120' },
  ];
  const fetch = fakeFetch(() => ({ body: { documents: docs } }));
  const res = await call(createHandler({ kakaoKey: 'k', fetch }), 'POST', '/v1/places/kakao-page', { ...PAGE, name: 'CGV용산' });
  assert.deepEqual(res.body, { url: 'https://place.map.kakao.com/2' });
  const nfd = fakeFetch(() => ({ body: { documents: [{ id: '3', place_name: '경복궁'.normalize('NFD') }] } }));
  assert.equal((await call(createHandler({ kakaoKey: 'k', fetch: nfd }), 'POST', '/v1/places/kakao-page', PAGE)).status, 200);
});
