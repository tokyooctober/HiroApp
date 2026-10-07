// Helpers for the fixture recorder. No network, so they are unit-tested.

export const MIN_GAP_MS = 4200; // 15 calls/min is the tighter NLB limit (1 call per 4 s)

/** Throws if `text` contains any of the secret values. Fixtures must never hold them (NFR-4). */
export function assertNoSecrets(text, secrets) {
  for (const secret of secrets) {
    if (secret && text.includes(secret)) {
      throw new Error('Refusing to write: response contains a credential value');
    }
  }
}

/** Builds a URL, skipping undefined and empty params. */
export function buildUrl(base, path, params = {}) {
  const url = new URL(`${base}${path}`);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  }
  return url;
}

/** File-name safe slug. */
export function slug(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

/** Waits so consecutive calls start at least `gapMs` apart. */
export function makePacer(gapMs, now = Date.now, sleep = (ms) => new Promise((r) => setTimeout(r, ms))) {
  let last = -Infinity;
  return async function pace() {
    const wait = last + gapMs - now();
    if (wait > 0) await sleep(wait);
    last = now();
  };
}
