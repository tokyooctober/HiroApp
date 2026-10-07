import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertNoSecrets, buildUrl, makePacer, slug } from '../scripts/lib.mjs';

test('assertNoSecrets throws when a secret appears in the body', () => {
  assert.throws(() => assertNoSecrets('{"echo":"abc123"}', ['abc123']), /credential/);
});

test('assertNoSecrets ignores empty secrets and clean bodies', () => {
  assert.doesNotThrow(() => assertNoSecrets('{"ok":true}', ['abc123', '', undefined]));
});

test('buildUrl skips empty params and encodes values', () => {
  const url = buildUrl('https://x.test/api', '/Search', { Keywords: 'a b', Limit: 20, Empty: '', Nope: undefined });
  assert.equal(url.search, '?Keywords=a+b&Limit=20');
});

test('slug makes a file-safe name', () => {
  assert.equal(slug('Search: junior / dinosaur'), 'search-junior-dinosaur');
});

test('pacer spaces calls at least gapMs apart', async () => {
  let t = 1000;
  const slept = [];
  const pace = makePacer(4000, () => t, async (ms) => { slept.push(ms); t += ms; });
  await pace(); // first call: last=0, no wait
  t += 1000;
  await pace(); // 1000 ms since last, must wait 3000
  assert.deepEqual(slept, [3000]);
});
