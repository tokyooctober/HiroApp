import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from '../src/handler.mjs';
import { RateLimitQueue } from '../src/queue.mjs';

const KEY = 'test-key-123';
const APP = 'TEST-APP';

function setup({ upstream, queueOpts = {} } = {}) {
  const calls = [];
  const clock = { t: 1_000_000 };
  const fetchFn = async (url, init) => {
    calls.push({ url: String(url), headers: init.headers });
    return upstream ? upstream(url, init) : new Response('{"ok":true}', { status: 200, headers: { 'content-type': 'application/json' } });
  };
  const logs = [];
  const queue = new RateLimitQueue({ now: () => clock.t, sleep: async () => {}, ...queueOpts });
  const handle = createHandler({ fetch: fetchFn, queue, apiKey: KEY, appCode: APP, log: (e) => logs.push(e) });
  return { handle, calls, logs, clock };
}

const get = (handle, path) => handle({ method: 'GET', url: `http://proxy.test${path}` });

// spec: NFR-4; cat: integ
test('forwards an allowed call and injects both headers server-side', async () => {
  const { handle, calls } = setup();
  const res = await get(handle, '/library/GetBranches?ListType=active');
  assert.equal(res.status, 200);
  assert.equal(calls[0].url, 'https://openweb.nlb.gov.sg/api/v1/Library/GetBranches?ListType=active');
  assert.equal(calls[0].headers['X-Api-Key'], KEY);
  assert.equal(calls[0].headers['X-App-Code'], APP);
});

// spec: NFR-4; cat: sec
test('the response never contains the credentials, even if upstream echoes them', async () => {
  const { handle } = setup({
    upstream: async () => new Response(`{"echo":"${KEY}"}`, { status: 200, headers: { 'x-api-key': KEY, 'content-type': 'application/json' } }),
  });
  const res = await get(handle, '/library/GetBranches');
  assert.equal(res.status, 502);
  assert.ok(!JSON.stringify(res).includes(KEY));
  assert.ok(!Object.keys(res.headers).some((h) => h.toLowerCase().startsWith('x-api')));
});

// spec: NFR-4; cat: sec
test('does not forward x-api-key or x-app-code from the app, and sends no cookies', async () => {
  const { handle, calls } = setup();
  await handle({ method: 'GET', url: 'http://p/library/GetBranches', headers: { 'x-api-key': 'attacker', cookie: 'a=b' } });
  assert.equal(calls[0].headers['X-Api-Key'], KEY);
  assert.equal(calls[0].headers.cookie, undefined);
});

// spec: NFR-4; cat: sec
test('unknown paths and non-GET methods are 404 and never reach NLB', async () => {
  const { handle, calls } = setup();
  assert.equal((await get(handle, '/admin')).status, 404);
  assert.equal((await handle({ method: 'POST', url: 'http://p/catalogue/SearchTitles' })).status, 404);
  assert.equal(calls.length, 0);
});

// spec: FR-12; cat: err
test('an NLB 429 and 5xx are passed through with their status and body', async () => {
  for (const status of [429, 500, 503]) {
    const { handle } = setup({ upstream: async () => new Response(`{"statusCode":${status}}`, { status, headers: { 'retry-after': '7', 'content-type': 'application/json' } }) });
    const res = await get(handle, '/catalogue/SearchTitles?Keywords=x');
    assert.equal(res.status, status);
    assert.equal(res.body, `{"statusCode":${status}}`);
    if (status === 429) assert.equal(res.headers['retry-after'], '7');
  }
});

// spec: NFR-3, FR-12; cat: perf
test('a call that cannot run within 10 s gets 429 with Retry-After, and NLB is not called', async () => {
  const { handle, calls } = setup();
  const results = [];
  // 20 different, uncached calls (identical GetBranches calls would now be served from the cache)
  for (let i = 0; i < 20; i++) results.push(await get(handle, `/catalogue/GetTitleDetails?BRN=${i}`));
  const busy = results.filter((r) => r.status === 429);
  assert.ok(busy.length > 0);
  assert.ok(busy.every((r) => Number(r.headers['retry-after']) >= 1));
  assert.equal(calls.length, results.length - busy.length);
});

// spec: FR-12; cat: err
test('a network failure to NLB is a 502', async () => {
  const { handle } = setup({ upstream: async () => { throw new Error('boom'); } });
  assert.equal((await get(handle, '/library/GetBranches')).status, 502);
});

// spec: NFR-5; cat: sec
test('logs hold path and status only: no query string, no coordinates', async () => {
  const { handle, logs } = setup();
  await get(handle, '/catalogue/SearchTitles?Keywords=dinosaur&lat=1.35&lon=103.94');
  const text = JSON.stringify(logs);
  assert.ok(text.includes('/catalogue/SearchTitles'));
  for (const bad of ['dinosaur', 'lat', '1.35', '103.94', KEY]) assert.ok(!text.includes(bad), bad);
});
