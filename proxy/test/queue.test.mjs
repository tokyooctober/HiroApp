import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RateLimitQueue, QueueBusyError } from '../src/queue.mjs';

const fresh = (opts = {}) => {
  const clock = { t: 1_000_000 };
  const q = new RateLimitQueue({ now: () => clock.t, ...opts });
  return { q, clock };
};

test('first call starts immediately', () => {
  const { q } = fresh();
  assert.equal(q.reserve(), 0);
});

test('calls are spaced at least 1 s apart', () => {
  const { q } = fresh();
  assert.deepEqual([q.reserve(), q.reserve(), q.reserve()], [0, 1000, 2000]);
});

test('a call after a quiet period starts immediately again', () => {
  const { q, clock } = fresh();
  q.reserve();
  clock.t += 5000;
  assert.equal(q.reserve(), 0);
});

test('a burst of 20 reaches NLB at <= 1 call/s and <= 15 in any 60 s (calls that would wait > 10 s are refused)', () => {
  const { q } = fresh();
  const starts = [];
  let busy = 0;
  for (let i = 0; i < 20; i++) {
    try { starts.push(q.reserve()); } catch (e) { assert.ok(e instanceof QueueBusyError); busy++; }
  }
  assert.equal(starts.length, 11); // t = 0..10 s
  assert.equal(busy, 9);
  for (let i = 1; i < starts.length; i++) assert.ok(starts[i] - starts[i - 1] >= 1000);
});

test('never more than 15 starts in a rolling 60 s window, even over a long run', () => {
  const { q, clock } = fresh({ maxWaitMs: 10 * 60_000 });
  const starts = [];
  for (let i = 0; i < 40; i++) starts.push(clock.t + q.reserve());
  for (let i = 0; i < starts.length; i++) {
    const inWindow = starts.filter((s) => s >= starts[i] && s < starts[i] + 60_000).length;
    assert.ok(inWindow <= 15, `window starting at call ${i} holds ${inWindow}`);
  }
  for (let i = 1; i < starts.length; i++) assert.ok(starts[i] - starts[i - 1] >= 1000);
});

test('a refused call carries Retry-After seconds and does not use a slot', () => {
  const { q } = fresh({ maxWaitMs: 2000 });
  q.reserve(); q.reserve(); q.reserve(); // 0, 1 s, 2 s
  assert.throws(() => q.reserve(), (e) => e instanceof QueueBusyError && e.retryAfterSeconds === 1);
  assert.throws(() => q.reserve(), (e) => e instanceof QueueBusyError); // still refused, nothing was reserved
});

test('run() waits for the reserved delay, then calls the function', async () => {
  const slept = [];
  const { q } = fresh({ sleep: async (ms) => { slept.push(ms); } });
  const out = [];
  await q.run(async () => out.push('a'));
  await q.run(async () => out.push('b'));
  assert.deepEqual(out, ['a', 'b']);
  assert.deepEqual(slept, [1000]); // first call had no wait
});
