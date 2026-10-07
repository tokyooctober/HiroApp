// Host-neutral request handler: takes {method, url}, returns {status, headers, body}.
// Cloud Run (node:http) and a Worker can both wrap it.
import { resolveUpstream } from './allowlist.mjs';
import { TtlCache, cacheKey, ttlFor } from './cache.mjs';
import { QueueBusyError } from './queue.mjs';

const json = (status, body, headers = {}) => ({
  status,
  headers: { 'content-type': 'application/json', ...headers },
  body: JSON.stringify(body),
});

export function createHandler({ fetch: fetchFn = fetch, queue, cache = new TtlCache(), apiKey, appCode, log = () => {} }) {
  const secrets = [apiKey, appCode].filter(Boolean);
  const inFlight = new Map(); // cache key -> promise, so identical concurrent requests share one NLB call

  // Calls NLB through the queue. Returns the response to send and whether it may be cached.
  async function forward(url, upstream) {
    let res;
    try {
      res = await queue.run(() =>
        fetchFn(upstream, { method: 'GET', headers: { 'X-Api-Key': apiKey, 'X-App-Code': appCode, Accept: 'application/json' } }),
      );
    } catch (e) {
      if (e instanceof QueueBusyError) {
        log({ path: url.pathname, status: 429, reason: 'queue-busy' });
        return { logged: true, out: json(429, { statusCode: 429, error: 'Too Many Requests', message: 'Busy, try again shortly.' }, { 'retry-after': String(e.retryAfterSeconds) }) };
      }
      log({ path: url.pathname, status: 502, reason: 'upstream-unreachable' });
      return { logged: true, out: json(502, { statusCode: 502, error: 'Bad Gateway' }) };
    }

    const body = await res.text();
    // Never let a credential leave through the proxy, whatever NLB sends back (NFR-4).
    if (secrets.some((s) => body.includes(s))) {
      log({ path: url.pathname, status: 502, reason: 'credential-in-response' });
      return { logged: true, out: json(502, { statusCode: 502, error: 'Bad Gateway' }) };
    }

    const headers = { 'content-type': res.headers.get('content-type') ?? 'application/json' };
    const retryAfter = res.headers.get('retry-after');
    if (retryAfter) headers['retry-after'] = retryAfter;
    return { out: { status: res.status, headers, body }, cacheable: res.status >= 200 && res.status < 300 };
  }

  return async function handle(req) {
    const url = new URL(req.url);
    const upstream = resolveUpstream(req.method, url);
    if (!upstream) {
      log({ path: url.pathname, status: 404 });
      return json(404, { statusCode: 404, error: 'Not Found' });
    }

    const ttl = ttlFor(url.pathname);
    const key = ttl ? cacheKey(url) : null;
    if (key) {
      const hit = cache.get(key);
      if (hit) {
        log({ path: url.pathname, status: hit.status, cache: 'hit' });
        return hit;
      }
      const pending = inFlight.get(key);
      if (pending) {
        const out = await pending;
        log({ path: url.pathname, status: out.status, cache: 'shared' });
        return out;
      }
    }

    const work = (async () => {
      const { out, cacheable, logged } = await forward(url, upstream);
      if (key && cacheable) cache.set(key, out, ttl);
      if (!logged) log({ path: url.pathname, status: out.status, ...(key ? { cache: 'miss' } : {}) });
      return out;
    })();
    if (key) inFlight.set(key, work);
    try {
      return await work;
    } finally {
      if (key) inFlight.delete(key);
    }
  };
}
