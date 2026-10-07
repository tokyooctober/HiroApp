// One queue for the whole NLB key: <= 1 call/s and <= 15 calls in any 60 s (spec section 2, NFR-3).
// Slots are reserved when a call arrives, so the wait is known up front and a call that
// would wait longer than maxWaitMs is refused without using a slot.

export class QueueBusyError extends Error {
  constructor(retryAfterSeconds) {
    super('NLB call queue is busy');
    this.name = 'QueueBusyError';
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export class RateLimitQueue {
  constructor({
    minGapMs = 1000,
    maxPerWindow = 15,
    windowMs = 60_000,
    maxWaitMs = 10_000,
    now = Date.now,
    sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  } = {}) {
    Object.assign(this, { minGapMs, maxPerWindow, windowMs, maxWaitMs, now, sleep });
    this.slots = []; // reserved start times (ms), ascending
  }

  /** Reserves the next slot and returns how long to wait (ms) before calling NLB. Throws QueueBusyError. */
  reserve() {
    const now = this.now();
    this.slots = this.slots.filter((s) => s > now - this.windowMs); // drop what no longer counts
    let start = now;
    const last = this.slots[this.slots.length - 1];
    if (last !== undefined) start = Math.max(start, last + this.minGapMs);
    for (;;) {
      const inWindow = this.slots.filter((s) => s > start - this.windowMs);
      if (inWindow.length < this.maxPerWindow) break;
      start = inWindow[inWindow.length - this.maxPerWindow] + this.windowMs;
    }
    const wait = start - now;
    if (wait > this.maxWaitMs) throw new QueueBusyError(Math.max(1, Math.ceil((wait - this.maxWaitMs) / 1000)));
    this.slots.push(start);
    return wait;
  }

  /** Waits for a slot, then runs fn. */
  async run(fn) {
    const wait = this.reserve();
    if (wait > 0) await this.sleep(wait);
    return fn();
  }
}
