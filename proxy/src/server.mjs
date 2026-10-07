// node:http entry point (Cloud Run or local dev). Credentials come from the environment only.
import { createServer } from 'node:http';
import { createHandler } from './handler.mjs';
import { RateLimitQueue } from './queue.mjs';

const apiKey = process.env.NLB_API_KEY;
const appCode = process.env.NLB_APP_CODE;
if (!apiKey || !appCode) {
  console.error('Set NLB_API_KEY and NLB_APP_CODE in the environment (secret manager in production).');
  process.exit(1);
}

const handle = createHandler({
  // NLB's limit is 1 call/s. Exactly 1000 ms between our sends can arrive < 1 s apart (jitter) and get a 429
  // (seen in the T1 smoke test), so keep a 100 ms margin.
  queue: new RateLimitQueue({ minGapMs: 1100 }),
  apiKey,
  appCode,
  log: (entry) => console.log(JSON.stringify({ t: new Date().toISOString(), ...entry })), // path + status only
});

createServer(async (req, res) => {
  const out = await handle({ method: req.method, url: `http://${req.headers.host ?? 'proxy'}${req.url}` });
  res.writeHead(out.status, out.headers);
  res.end(out.body);
}).listen(Number(process.env.PORT ?? 8080), () => console.log('proxy listening'));
