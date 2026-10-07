// Response cache in front of the rate-limit queue (spec NFR-3). Only these endpoints are cached.
const MIN = 60_000;

export const TTL_MS = {
  '/catalogue/SearchTitles': 10 * MIN,
  '/catalogue/GetAvailabilityInfo': 5 * MIN,
  '/eresource/GetAvailabilityInfo': 5 * MIN,
  '/library/GetBranches': 7 * 24 * 60 * MIN,
};

export const ttlFor = (pathname) => (Object.hasOwn(TTL_MS, pathname) ? TTL_MS[pathname] : undefined);

/** Path plus every query parameter in a stable order. Never the host, headers or device. */
export function cacheKey(url) {
  const params = [...url.searchParams].sort(([a, av], [b, bv]) => (a < b ? -1 : a > b ? 1 : av < bv ? -1 : av > bv ? 1 : 0));
  const query = params.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
  return query ? `${url.pathname}?${query}` : url.pathname;
}

export class TtlCache {
  constructor({ now = Date.now, maxEntries = 300 } = {}) {
    this.now = now;
    this.maxEntries = maxEntries;
    this.entries = new Map(); // key -> { value, expires }; Map order = oldest first
  }

  get(key) {
    const hit = this.entries.get(key);
    if (!hit) return undefined;
    if (this.now() >= hit.expires) {
      this.entries.delete(key);
      return undefined;
    }
    return hit.value;
  }

  set(key, value, ttlMs) {
    this.entries.delete(key);
    this.entries.set(key, { value, expires: this.now() + ttlMs });
    while (this.entries.size > this.maxEntries) this.entries.delete(this.entries.keys().next().value);
  }
}
