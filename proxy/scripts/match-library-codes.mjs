// One-time update of data/nlb_libraries.json: fill in each library's NLB branchCode.
// Matches by coordinates (names differ between the file and NLB, e.g. "Ang Mo Kio Public Library" vs "Ang Mo Kio Library")
// against the recorded GetBranches fixture (fixtures/ is not in git: run `npm run record-fixtures` first), so it needs no network once recorded.
//
//   node scripts/match-library-codes.mjs            dry run: prints the table, changes nothing
//   node scripts/match-library-codes.mjs --write    writes branchCode into the file (a reviewer checks the table first)
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { nearestBranch } from './geo.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const file = join(root, 'data', 'nlb_libraries.json');
const fixture = join(root, 'fixtures', 'library-get-branches.json');
const write = process.argv.includes('--write');

const data = JSON.parse(readFileSync(file, 'utf8'));
const publicLibraries = JSON.parse(readFileSync(fixture, 'utf8'))
  .branches.filter((b) => ['PL', 'RL'].includes(b.branchType?.code)) // public and regional libraries
  .map((b) => ({ code: b.branchCode, name: b.branchName, lat: Number(b.coordinates.geoLatitude), lng: Number(b.coordinates.geoLongitude) }));

const WARN_M = 200; // a clear match is much closer than this; anything beyond is shown to the reviewer
const used = new Map();
const rows = data.libraries.map((lib) => {
  const { best, second } = nearestBranch(lib, publicLibraries);
  const flags = [];
  if (best.metres > WARN_M) flags.push(`far (${Math.round(best.metres)} m)`);
  if (second.metres - best.metres < 100) flags.push(`close runner-up ${second.branch.code} (${Math.round(second.metres)} m)`);
  used.set(best.branch.code, [...(used.get(best.branch.code) ?? []), lib.id]);
  return { lib, best, flags };
});
for (const [code, ids] of used) {
  if (ids.length > 1) rows.filter((r) => ids.includes(r.lib.id)).forEach((r) => r.flags.push(`code ${code} also matched by ${ids.filter((i) => i !== r.lib.id).join(', ')}`));
}

const pad = (s, n) => String(s).padEnd(n);
console.log(`${pad('file entry', 40)} ${pad('status', 9)} ${pad('NLB code', 9)} ${pad('NLB name', 28)} metres  flags`);
for (const { lib, best, flags } of rows) {
  console.log(`${pad(lib.name, 40)} ${pad(lib.status, 9)} ${pad(best.branch.code, 9)} ${pad(best.branch.name, 28)} ${pad(Math.round(best.metres), 7)} ${flags.join('; ')}`);
}
const unmatched = publicLibraries.filter((b) => !used.has(b.code));
console.log(`\nNLB public libraries with no entry in the file (${unmatched.length}): ${unmatched.map((b) => `${b.code} ${b.name}`).join(', ') || 'none'}`);
console.log(`Flagged for review: ${rows.filter((r) => r.flags.length).length} of ${rows.length}`);

if (write) {
  for (const { lib, best } of rows) lib.branchCode = best.branch.code;
  writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
  console.log(`\nWrote branchCode for ${rows.length} libraries to ${file}`);
} else {
  console.log('\nDry run: nothing written. Re-run with --write after checking the table.');
}
