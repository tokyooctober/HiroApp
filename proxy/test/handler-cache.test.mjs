import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from '../src/handler.mjs';
import { RateLimitQueue } from '../src/queue.mjs';
import { TtlCache } from '../src/cache.mjs';

const MIN = 60_000;

function setup({ upstream } = {}) {
  const clock = { t: 1_000_000 };
  const calls = [];
  const logs = [];
  const fetchFn = async (url) => {
    calls.push(String(url));
    if (upstream) return upstream(url, calls.length);
    return new Response(JSON.stringify({ n: calls.length }), { status: 200, headers: { 'content-type': 'application/json' } });
  };
  const queue = new RateLimitQueue({ now: () => clock.t, sleep: async () => {} });
  const cache = new TtlCache({ now: () => clock.t });
  const handle = createHandler({ fetch: fetchFn, queue, cache, apiKey: 'k-123', appCode: 'APP', log: (e) => logs.push(e) });
  const get = (path) => handle({ method: 'GET', url: `http://proxy.test${path}` });
  return { get, calls, logs, clock, queue };
}

const SEARCH = '/catalogue/SearchTitles?Keywords=dino&Limit=20';

test('a repeat within the TTL makes 0 NLB calls and returns the same body', async () => {
  const { get, calls, clock } = setup();
  const first = await get(SEARCH);
  clock.t += 9 * MIN;
  const second = await get(SEARCH);
  assert.equal(calls.length, 1);
  assert.equal(second.status, 200);
  assert.equal(second.body, first.body);
});

test('after the TTL the next request calls NLB once, then is cached again', async () => {
  const { get, calls, clock } = setup();
  await get(SEARCH);
  clock.t += 10 * MIN;
  const refreshed = await get(SEARCH);
  await get(SEARCH);
  assert.equal(calls.length, 2);
  assert.equal(JSON.parse(refreshed.body).n, 2);
});

test('each endpoint expires on its own TTL', async () => {
  const { get, calls, clock } = setup();
  await get('/catalogue/GetAvailabilityInfo?BRN=1');
  await get('/library/GetBranches?ListType=active');
  clock.t += 6 * MIN; // availability (5 min) expired, branches (7 days) not
  await get('/catalogue/GetAvailabilityInfo?BRN=1');
  await get('/library/GetBranches?ListType=active');
  assert.equal(calls.filter((u) => u.includes('GetAvailabilityInfo')).length, 2);
  assert.equal(calls.filter((u) => u.includes('GetBranches')).length, 1);
});

test('any different query parameter is a separate cache entry', async () => {
  const { get, calls } = setup();
  await get('/catalogue/SearchTitles?Keywords=dino&Limit=20');
  await get('/catalogue/SearchTitles?Keywords=dino&Limit=20&Offset=20');
  await get('/catalogue/SearchTitles?Keywords=dino&Limit=20&Locations=trl');
  assert.equal(calls.length, 3);
});

test('error responses are not cached: 429, 500 and 404 each call NLB again', async () => {
  for (const status of [429, 500, 404]) {
    const { get, calls } = setup({ upstream: async () => new Response('{"e":1}', { status }) });
    await get(SEARCH);
    await get(SEARCH);
    assert.equal(calls.length, 2, `status ${status}`);
  }
});

test('a network failure is not cached', async () => {
  let fail = true;
  const { get, calls } = setup({ upstream: async (u, n) => { if (fail) { fail = false; throw new Error('boom'); } return new Response('{"ok":1}', { status: 200 }); } });
  assert.equal((await get(SEARCH)).status, 502);
  assert.equal((await get(SEARCH)).status, 200);
  assert.equal(calls.length, 2);
});

test('a response that echoes a credential is never cached', async () => {
  const { get, calls } = setup({ upstream: async () => new Response('{"echo":"k-123"}', { status: 200 }) });
  assert.equal((await get(SEARCH)).status, 502);
  assert.equal((await get(SEARCH)).status, 502);
  assert.equal(calls.length, 2);
});

test('endpoints with no TTL in NFR-3 are never cached', async () => {
  const { get, calls } = setup();
  await get('/catalogue/GetTitleDetails?BRN=1');
  await get('/catalogue/GetTitleDetails?BRN=1');
  assert.equal(calls.length, 2);
});

test('cache hits do not use queue budget', async () => {
  const { get, queue } = setup();
  await get(SEARCH);
  const slotsAfterFirst = queue.slots.length;
  for (let i = 0; i < 30; i++) await get(SEARCH);
  assert.equal(queue.slots.length, slotsAfterFirst);
});

test('identical requests that arrive together share one NLB call', async () => {
  let release;
  const gate = new Promise((r) => { release = r; });
  const { get, calls } = setup({ upstream: async () => { await gate; return new Response('{"shared":true}', { status: 200 }); } });
  const all = [get(SEARCH), get(SEARCH), get(SEARCH)];
  release();
  const results = await Promise.all(all);
  assert.equal(calls.length, 1);
  assert.ok(results.every((r) => r.body === '{"shared":true}'));
});

test('logs say hit or miss and still hold no query string', async () => {
  const { get, logs } = setup();
  await get(SEARCH);
  await get(SEARCH);
  assert.deepEqual(logs.map((l) => l.cache), ['miss', 'hit']);
  assert.ok(!JSON.stringify(logs).includes('dino'));
});
