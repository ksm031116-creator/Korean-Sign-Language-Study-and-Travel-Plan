// 중계 서버 테스트 (PRD §10.3). 카카오 API 는 가짜 fetch 로 흉내 낸다 — 키도 네트워크도 쓰지 않는다.
// 실행: node --test server/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from './relay.mjs';

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
      { kind: 'walk', lines: [], minutes: 4, distance_m: 300 },
      { kind: 'subway', lines: ['3호선'], minutes: 20, distance_m: 4000 },
      { kind: 'bus', lines: ['7212', '1020'], minutes: 12, distance_m: 713 },
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
