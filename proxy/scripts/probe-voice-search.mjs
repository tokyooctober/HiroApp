// Read-only experiment: how well does NLB SearchTitles answer what a parent might say aloud? (voice search, see tasks/)
// Reads credentials from the environment only: NLB_API_KEY, NLB_APP_CODE. Never writes them anywhere.
// Calls are paced to stay inside 1 call/s and 15 calls/min for the whole key. Output goes to ../experiments/out (git-ignored).
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MIN_GAP_MS, assertNoSecrets, buildUrl, makePacer, slug } from './lib.mjs';
import { summarize, toMarkdownTable } from './probe-analysis.mjs';

const key = process.env.NLB_API_KEY;
const appCode = process.env.NLB_APP_CODE;
if (!key || !appCode) {
  console.error('Set NLB_API_KEY and NLB_APP_CODE in the environment (never in a file in the repo).');
  process.exit(1);
}

const SEARCH = 'https://openweb.nlb.gov.sg/api/v2/Catalogue/SearchTitles';
// Probe set: `topics` (default, the first experiment) or `combos` (topic + format words, fiction filter variants).
// Each set writes to its own folder so one run never overwrites another.
const set = process.argv[2] ?? 'topics';
const outDir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'experiments', 'out', ...(set === 'topics' ? [] : [set]));
const pace = makePacer(MIN_GAP_MS);

const TOPICS = [
  { word: 'penguin', topic: /penguin/i },
  { word: 'tiger', topic: /tiger/i },
  { word: 'rocket', topic: /rocket/i },
  { word: 'scientist', topic: /scien(tist|ce)/i },
];
const FORMAT_PROBES = ['board book', 'big book', 'oversized', 'lift the flap', 'large format'];

const probes = [];
if (set === 'combos') {
  const on = Object.fromEntries(TOPICS.map((t) => [t.word, t.topic]));
  const add = (name, keywords, topic, extra) => probes.push({ name, keywords, topic, extra });
  // Does a format word still work next to a topic?
  for (const [word, phrase] of [['penguin', 'big book'], ['tiger', 'big book'], ['rocket', 'big book'], ['tiger', 'board book'], ['penguin', 'board book'], ['penguin', 'lift the flap']]) {
    add(`${word} ${phrase}`, `${word} ${phrase}`, on[word]);
  }
  add('space big book', 'space big book', /space|planet|rocket|star/i);
  // Can "Juvenile fiction / literature" (words in the subject headings) stand in for the unimplemented Fiction filter?
  add('tiger juvenile fiction', 'tiger juvenile fiction', on.tiger);
  add('tiger juvenile literature', 'tiger juvenile literature', on.tiger);
  add('penguin juvenile fiction', 'penguin juvenile fiction', on.penguin);
  add('tiger Fiction=false', 'tiger', on.tiger, { Fiction: false });
  add('tiger Fiction=Y', 'tiger', on.tiger, { Fiction: 'Y' });
  // Words a parent says that are not catalogue words: do they break or dilute the search?
  add('colourful big book tiger', 'colourful big book tiger', on.tiger);
  add('popular penguin books', 'popular penguin books', on.penguin);
} else {
  for (const { word, topic } of TOPICS) {
    probes.push({ name: `${word} | baseline`, keywords: word, topic });
    probes.push({ name: `${word} | picture book`, keywords: `${word} picture book`, topic });
    probes.push({ name: `${word} | easy reader`, keywords: `${word} easy reader`, topic });
    probes.push({ name: `${word} | series`, keywords: `${word} series`, topic });
    probes.push({ name: `${word} | Fiction=true`, keywords: word, topic, extra: { Fiction: true } });
  }
  for (const phrase of FORMAT_PROBES) probes.push({ name: `format | ${phrase}`, keywords: phrase, topic: null });
}

async function run(probe) {
  const url = buildUrl(SEARCH, '', {
    Keywords: probe.keywords,
    Limit: 20,
    MaterialTypes: 'bks',
    IntendedAudiences: 'juvenile',
    ...probe.extra,
  });
  let res;
  let text;
  for (let attempt = 1; attempt <= 3; attempt++) {
    await pace();
    res = await fetch(url, { headers: { 'X-Api-Key': key, 'X-App-Code': appCode, Accept: 'application/json' } });
    text = await res.text();
    if (res.status !== 429) break;
    console.log(`  429 on ${probe.name}, waiting 20 s (try ${attempt}/3)`);
    await new Promise((r) => setTimeout(r, 20000));
  }
  assertNoSecrets(text, [key, appCode]);
  let body;
  try { body = JSON.parse(text); } catch { body = undefined; }
  await writeFile(join(outDir, `${slug(probe.name)}.json`), body === undefined ? text : JSON.stringify(body, null, 2) + '\n');
  const row = { name: probe.name, status: res.status, ...summarize(body, probe.topic) };
  console.log(`${res.status}  ${probe.name}  total=${row.totalRecords} onTopic=${row.onTopic ?? '-'}`);
  return { ...row, params: Object.fromEntries(url.searchParams) };
}

await mkdir(outDir, { recursive: true });
const rows = [];
for (const probe of probes) rows.push(await run(probe));
await writeFile(join(outDir, 'summary.md'), toMarkdownTable(rows));
await writeFile(join(outDir, 'summary.json'), JSON.stringify(rows, null, 2) + '\n');
console.log(`\nWrote ${rows.length} probes to ${outDir}`);
