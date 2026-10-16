// Read-only experiment: of the books Amazon ranks for a parent's query, how many does NLB hold? (exact GetTitles?ISBN= lookups)
// Input: ../experiments/out/amazon/amazon-top.json. Reads credentials from the environment only: NLB_API_KEY, NLB_APP_CODE.
// Calls are paced to stay inside 1 call/s and 15 calls/min for the whole key.
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MIN_GAP_MS, assertNoSecrets, buildUrl, makePacer } from './lib.mjs';

const key = process.env.NLB_API_KEY;
const appCode = process.env.NLB_APP_CODE;
if (!key || !appCode) {
  console.error('Set NLB_API_KEY and NLB_APP_CODE in the environment (never in a file in the repo).');
  process.exit(1);
}

const dir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'experiments', 'out', 'amazon');
const input = JSON.parse(await readFile(join(dir, 'amazon-top.json'), 'utf8'));
const pace = makePacer(MIN_GAP_MS);

async function lookup(isbn) {
  const url = buildUrl('https://openweb.nlb.gov.sg/api/v2/Catalogue/GetTitles', '', { ISBN: isbn });
  let res;
  let text;
  for (let attempt = 1; attempt <= 3; attempt++) {
    await pace();
    res = await fetch(url, { headers: { 'X-Api-Key': key, 'X-App-Code': appCode, Accept: 'application/json' } });
    text = await res.text();
    if (res.status !== 429) break;
    await new Promise((r) => setTimeout(r, 20000));
  }
  assertNoSecrets(text, [key, appCode]);
  let body;
  try { body = JSON.parse(text); } catch { body = {}; }
  const record = body.titles?.[0];
  return {
    isbn,
    status: res.status,
    held: (body.totalRecords ?? 0) > 0,
    title: record?.title?.split(' / ')[0] ?? null,
    juvenile: (record?.subjects ?? []).some((s) => /juvenile|board books|picture books/i.test(s)),
    onShelfSomewhere: record?.availability ?? null,
  };
}

const results = {};
for (const [query, { isbnShaped, resultsSeen }] of Object.entries(input.queries)) {
  const rows = [];
  for (const isbn of isbnShaped) {
    const row = await lookup(isbn);
    rows.push(row);
    console.log(`${row.status}  ${row.held ? 'HELD   ' : 'missing'}  ${isbn}  ${row.title ?? ''}`);
  }
  results[query] = { resultsSeen, withIsbn: isbnShaped.length, held: rows.filter((r) => r.held).length, rows };
}
await writeFile(join(dir, 'nlb-holdings.json'), JSON.stringify(results, null, 2) + '\n');
console.log('\nquery | results seen | with ISBN | NLB holds');
for (const [q, r] of Object.entries(results)) console.log(`${q} | ${r.resultsSeen} | ${r.withIsbn} | ${r.held}`);
