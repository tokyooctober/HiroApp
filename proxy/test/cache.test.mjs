import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TtlCache, cacheKey, ttlFor, TTL_MS } from '../src/cache.mjs';

const MIN = 60_000;

test('TTLs follow NFR-3: search 10 min, availability 5 min, branches 7 days', () => {
  assert.equal(ttlFor('/catalogue/SearchTitles'), 10 * MIN);
  assert.equal(ttlFor('/catalogue/GetAvailabilityInfo'), 5 * MIN);
  assert.equal(ttlFor('/eresource/GetAvailabilityInfo'), 5 * MIN);
  assert.equal(ttlFor('/library/GetBranches'), 7 * 24 * 60 * MIN);
});

test('endpoints without a TTL in NFR-3 are not cached', () => {
  for (const p of ['/catalogue/GetTitles', '/catalogue/GetTitleDetails', '/eresource/SearchResources', '/recommendation/GetRecommendationsForTitles']) {
    assert.equal(ttlFor(p), undefined, p);
  }
  assert.equal(Object.keys(TTL_MS).length, 4);
});

test('cache key holds the path and every query parameter, in a stable order', () => {
  const a = cacheKey(new URL('http://p/catalogue/SearchTitles?Keywords=dino&Limit=20&Offset=20'));
  const b = cacheKey(new URL('http://p/catalogue/SearchTitles?Offset=20&Limit=20&Keywords=dino'));
  assert.equal(a, b);
  for (const q of ['Keywords=dino', 'Limit=20', 'Offset=20']) assert.ok(a.includes(q), q);
});

test('a different value for any parameter is a different key', () => {
  const base = 'http://p/catalogue/SearchTitles?Keywords=dino&Limit=20&Locations=trl';
  const keys = new Set([
    cacheKey(new URL(base)),
    cacheKey(new URL(base.replace('trl', 'prl'))),
    cacheKey(new URL(base.replace('Limit=20', 'Limit=10'))),
    cacheKey(new URL(base + '&Availability=true')),
    cacheKey(new URL('http://p/catalogue/GetAvailabilityInfo?Keywords=dino&Limit=20&Locations=trl')),
  ]);
  assert.equal(keys.size, 5);
});

test('the key never contains a host, header or device value', () => {
  const key = cacheKey(new URL('http://some-device.example:8080/library/GetBranches?ListType=active'));
  assert.equal(key, '/library/GetBranches?ListType=active');
});

test('get returns the value inside its TTL and nothing after it', () => {
  let t = 0;
  const c = new TtlCache({ now: () => t });
  c.set('k', { body: 'x' }, 10 * MIN);
  t = 10 * MIN - 1;
  assert.deepEqual(c.get('k'), { body: 'x' });
  t = 10 * MIN;
  assert.equal(c.get('k'), undefined);
});

test('the oldest entries are dropped when the cache is full', () => {
  const c = new TtlCache({ now: () => 0, maxEntries: 2 });
  c.set('a', 1, MIN); c.set('b', 2, MIN); c.set('c', 3, MIN);
  assert.equal(c.get('a'), undefined);
  assert.equal(c.get('b'), 2);
  assert.equal(c.get('c'), 3);
});
