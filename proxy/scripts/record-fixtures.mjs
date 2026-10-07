// Records real NLB responses to ../fixtures (spec T0). Reads credentials from the environment only:
//   NLB_API_KEY, NLB_APP_CODE
// Calls are paced to stay inside 1 call/s and 15 calls/min for the whole key.
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MIN_GAP_MS, assertNoSecrets, buildUrl, makePacer, slug } from './lib.mjs';

const key = process.env.NLB_API_KEY;
const appCode = process.env.NLB_APP_CODE;
if (!key || !appCode) {
  console.error('Set NLB_API_KEY and NLB_APP_CODE in the environment (never in a file in the repo).');
  process.exit(1);
}

const ROOT = 'https://openweb.nlb.gov.sg/api';
const CATALOGUE = `${ROOT}/v2/Catalogue`;
const LIBRARY = `${ROOT}/v1/Library`;
const ERESOURCE = `${ROOT}/v1/EResource`;
const RECOMMENDATION = `${ROOT}/v1/Recommendation`;

const outDir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'fixtures');
const pace = makePacer(MIN_GAP_MS);
const manifest = [];
const seen = {}; // parsed bodies by fixture name, so later calls can use real ids

async function call(name, base, path, params) {
  const url = buildUrl(base, path, params);
  let res;
  let text;
  for (let attempt = 1; attempt <= 3; attempt++) {
    await pace();
    res = await fetch(url, { headers: { 'X-Api-Key': key, 'X-App-Code': appCode, Accept: 'application/json' } });
    text = await res.text();
    if (res.status !== 429) break;
    console.log(`  429 on ${name}, waiting 20 s (try ${attempt}/3)`);
    await new Promise((r) => setTimeout(r, 20000));
  }
  assertNoSecrets(text, [key, appCode]);
  let body;
  try { body = JSON.parse(text); } catch { body = undefined; }
  await mkdir(outDir, { recursive: true });
  const file = `${slug(name)}.json`;
  await writeFile(join(outDir, file), body === undefined ? text : JSON.stringify(body, null, 2) + '\n');
  manifest.push({ name, file, status: res.status, endpoint: `${path}`, params: Object.fromEntries(url.searchParams) });
  seen[name] = body;
  console.log(`${res.status}  ${name}`);
  return body;
}

// --- Library: branches ---
const branches = await call('library-get-branches', LIBRARY, '/GetBranches', { ListType: 'active' });
const tampines = branches?.branches?.find((b) => /^tampines library$/i.test(b.branchName));
// GetBranches codes are upper case (TRL) but SearchTitles location facet ids are lower case (trl).
const branchCode = tampines?.branchCode?.toLowerCase();
console.log(`Current library for location searches: ${tampines?.branchName} (${branchCode})`);

// --- Catalogue: SearchTitles, both audiences, with and without Locations/Availability ---
const q = 'dinosaur';
// Facet ids (from a plain search): audience `juvenile` / `adult`, material type `bks`. The spec's `junior` / `BK` return 400.
const search = (name, extra) => call(name, CATALOGUE, '/SearchTitles', { Keywords: q, Limit: 20, MaterialTypes: 'bks', ...extra });
const juniorPlain = await search('search-titles-junior-plain', { IntendedAudiences: 'juvenile' });
await search('search-titles-adult-plain', { IntendedAudiences: 'adult' });
await search('search-titles-junior-here-available', { IntendedAudiences: 'juvenile', Locations: branchCode, Availability: true });
await search('search-titles-adult-here-available', { IntendedAudiences: 'adult', Locations: branchCode, Availability: true });
await search('search-titles-junior-here-any', { IntendedAudiences: 'juvenile', Locations: branchCode });
await search('search-titles-junior-everywhere-available', { IntendedAudiences: 'juvenile', Availability: true });
await search('search-titles-no-filters', {}); // no audience / material type: facet reference

// --- Catalogue: per-title endpoints, using a real title from the junior search ---
const first = juniorPlain?.titles?.[0];
const record = first?.records?.[0];
const brn = record?.brn ?? first?.brn;
const isbn = (record?.isbns ?? first?.isbns ?? [])[0] ?? record?.isbn ?? first?.isbn;
console.log(`Sample title: BRN=${brn} ISBN=${isbn}`);
if (isbn) await call('get-titles-isbn', CATALOGUE, '/GetTitles', { ISBN: isbn });
await call('get-titles-title', CATALOGUE, '/GetTitles', { Title: first?.title ?? q });
if (brn) {
  await call('get-title-details', CATALOGUE, '/GetTitleDetails', { BRN: brn });
  await call('get-availability-info', CATALOGUE, '/GetAvailabilityInfo', { BRN: brn });
}

// --- EResource ---
const ebooks = await call('eresource-search-ebooks', ERESOURCE, '/SearchResources', { ContentType: 'eBooks', Keywords: q, Limit: 10 });
await call('eresource-search-audio-books', ERESOURCE, '/SearchResources', { ContentType: 'Audio Books', Keywords: q, Limit: 10 });
const resource = ebooks?.results?.[0];
// OverDrive items only. Try ISBN first (the UUID `id` returned 404 as TitleId in the first run).
const resourceIsbn = resource?.isbns?.[0];
if (resourceIsbn) await call('eresource-get-availability-info-isbn', ERESOURCE, '/GetAvailabilityInfo', { IdType: 'ISBN', Id: resourceIsbn });
else if (resource?.id) await call('eresource-get-availability-info-id', ERESOURCE, '/GetAvailabilityInfo', { IdType: 'TitleId', Id: resource.id });

// --- Recommendation: needs a title MID, which no Catalogue endpoint returns (spec section 9).
// Try the eResource id once so the real error or shape is on record.
if (resource?.id) {
  await call('recommendation-for-titles', RECOMMENDATION, '/GetRecommendationsForTitles', {
    RecommendationType: 'book', IdType: 'title', Id: resource.id,
  });
}

await writeFile(join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(`Wrote ${manifest.length} fixtures to ${outDir}`);
