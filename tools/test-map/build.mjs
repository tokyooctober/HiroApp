#!/usr/bin/env node
// Builds docs/test-map.html: how many tests cover each spec, by test category and by tier.
//
//   node tools/test-map/build.mjs           write docs/test-map.html and print a summary
//   node tools/test-map/build.mjs --check   validate the tags only (exit 1 on any problem)
//
// Every test carries one comment line directly above it (above @Test in Kotlin):
//
//   // spec: FR-3; cat: logic
//   // spec: NFR-3, FR-12; cat: perf; tier: tooling
//
//   spec  one or more ids from docs/hiro-kids-spec.md (FR-n, NFR-n) or a spec task (T0, T1, T2)
//   cat   ui | input | logic | perf | data | integ | sec | err
//   tier  ui | domain | data | proxy | tooling; optional when the path decides it (see tierFromPath)
//
// No dependencies. Needs Node 22+.

import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const OUT = join(ROOT, 'docs', 'test-map.html');
const SPEC = join(ROOT, 'docs', 'hiro-kids-spec.md');

export const CATEGORIES = [
  { k: 'ui', name: 'UI / Visual', what: 'Colours, shapes, layout, navigation, accessibility, screen sizes' },
  { k: 'input', name: 'Input validation', what: 'What the user types: valid, invalid and boundary values' },
  { k: 'logic', name: 'Functional / Logic', what: 'Rules and behaviour are correct' },
  { k: 'perf', name: 'Performance', what: 'Rate limit, cache, response time, large lists' },
  { k: 'data', name: 'Data / Persistence', what: 'Saved, read back, survives restart' },
  { k: 'integ', name: 'Integration / Contract', what: 'Components and NLB responses working together' },
  { k: 'sec', name: 'Security', what: 'Secrets, content filtering, privacy, what is reachable' },
  { k: 'err', name: 'Error handling', what: 'Offline, 429 and 5xx, empty states, retry' },
];
export const TIERS = [
  { k: 'ui', name: 'UI', what: 'Compose screens, ViewModels' },
  { k: 'domain', name: 'Domain', what: 'Rules and policies' },
  { k: 'data', name: 'Data', what: 'Repository, store, location' },
  { k: 'proxy', name: 'Proxy', what: 'Key, cache, rate limit' },
  { k: 'tooling', name: 'Tooling', what: 'One-off scripts, fixture recorder' },
];
// Spec tasks that tests may cite when no FR/NFR covers them.
const TASK_SPECS = {
  T0: 'Fixture recorder and scripts',
  T1: 'Proxy plumbing',
  T2: 'App skeleton demo screen',
};

const TEST_DIRS = ['proxy/test', 'shared/src'];
const isTestFile = (f) => /\.test\.mjs$/.test(f) || (/Test\.kt$/.test(f) && /\/(commonTest|jvmTest|androidUnitTest|androidHostTest|iosTest)\//.test(f));

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir).sort()) {
    if (name === 'node_modules' || name === 'build' || name.startsWith('.')) continue;
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

/** Tier from the file path; null when the path does not decide it. */
function tierFromPath(rel) {
  if (rel.startsWith('proxy/test/')) return 'proxy';
  if (/\/ui\//.test(rel)) return 'ui';
  if (/\/domain\//.test(rel)) return 'domain';
  if (/\/(data|db)\//.test(rel)) return 'data';
  return null;
}

function humanise(camel) {
  const words = camel.replace(/([A-Z])/g, ' $1').toLowerCase().trim();
  return words;
}

/** Reads every test and the tag line above it. Returns { tests, problems }. */
export function scanTests() {
  const tests = [];
  const problems = [];
  const files = TEST_DIRS.flatMap((d) => walk(join(ROOT, d))).filter((f) => isTestFile(relative(ROOT, f).replaceAll('\\', '/')));
  for (const file of files) {
    const rel = relative(ROOT, file).replaceAll('\\', '/');
    const lines = readFileSync(file, 'utf8').split('\n');
    const kotlin = rel.endsWith('.kt');
    lines.forEach((line, i) => {
      let name = null;
      if (kotlin) {
        if (!/^\s*@Test\s*$/.test(line)) return;
        const fn = lines.slice(i + 1, i + 4).map((l) => l.match(/fun\s+`?([A-Za-z0-9_ ]+)`?\s*\(/)).find(Boolean);
        name = fn ? humanise(fn[1]) : '(unnamed)';
      } else {
        const m = line.match(/^test\((['"`])((?:\\.|(?!\1).)*)\1/);
        if (!m) return;
        name = m[2];
      }
      const where = `${rel}:${i + 1}`;
      const prev = lines[i - 1] ?? '';
      const tag = prev.match(/^\s*\/\/\s*spec:\s*(.*)$/);
      if (!tag) { problems.push(`${where}  no "// spec: ...; cat: ..." line above "${name}"`); return; }
      const fields = Object.fromEntries(
        `spec: ${tag[1]}`.split(';').map((p) => { const j = p.indexOf(':'); return [p.slice(0, j).trim(), p.slice(j + 1).trim()]; }),
      );
      const specs = (fields.spec ?? '').split(',').map((s) => s.trim()).filter(Boolean);
      const cat = fields.cat;
      const tier = fields.tier ?? tierFromPath(rel);
      if (!specs.length) problems.push(`${where}  empty spec`);
      if (!CATEGORIES.some((c) => c.k === cat)) problems.push(`${where}  unknown cat "${cat}" (use ${CATEGORIES.map((c) => c.k).join(', ')})`);
      if (!tier) problems.push(`${where}  the path does not decide the tier; add "tier: ..." (${TIERS.map((t) => t.k).join(', ')})`);
      else if (!TIERS.some((t) => t.k === tier)) problems.push(`${where}  unknown tier "${tier}"`);
      tests.push({ name, file: rel, line: i + 1, specs, cat, tier });
    });
  }
  return { tests, problems };
}

/** Spec ids and names from the requirement tables of docs/hiro-kids-spec.md. */
export function readSpecs() {
  const md = readFileSync(SPEC, 'utf8');
  const specs = [];
  for (const line of md.split('\n')) {
    const m = line.match(/^\|\s*((N?FR)-(\d+))\s+([^|]+?)\s*\|/);
    if (m && !specs.some((s) => s.id === m[1])) specs.push({ id: m[1], name: m[4], group: m[2] === 'FR' ? 'Functional requirements' : 'Non-functional requirements', n: Number(m[3]) });
  }
  const rank = (s) => (s.group.startsWith('Functional') ? 0 : 1) * 1000 + s.n;
  return specs.sort((a, b) => rank(a) - rank(b));
}

function buildModel() {
  const { tests, problems } = scanTests();
  const specs = readSpecs();
  const known = new Set([...specs.map((s) => s.id), ...Object.keys(TASK_SPECS)]);
  for (const t of tests) for (const s of t.specs) if (!known.has(s)) problems.push(`${t.file}:${t.line}  "${s}" is not a spec id in docs/hiro-kids-spec.md or a spec task (${Object.keys(TASK_SPECS).join(', ')})`);
  const rows = specs.map((s) => ({ id: s.id, name: s.name, group: s.group, tests: [] }));
  const used = new Set(tests.flatMap((t) => t.specs));
  for (const [id, name] of Object.entries(TASK_SPECS)) if (used.has(id)) rows.push({ id, name, group: 'Spec tasks with no requirement of their own', tests: [] });
  for (const t of tests) for (const s of t.specs) rows.find((r) => r.id === s)?.tests.push({ name: t.name, file: t.file, line: t.line, tier: t.tier, cat: t.cat });
  return { model: { categories: CATEGORIES, tiers: TIERS, rows, uniqueTests: tests.length }, problems };
}

function summary(model) {
  const lines = [];
  const withTests = model.rows.filter((r) => r.tests.length);
  lines.push(`${model.uniqueTests} tests, every one tagged`);
  const reqs = model.rows.filter((r) => /^N?FR-/.test(r.id));
  const covered = reqs.filter((r) => r.tests.length);
  lines.push(`${covered.length} of ${reqs.length} requirements have at least one test; ${reqs.length - covered.length} have none`);
  lines.push('');
  const pad = (s, n) => String(s).padEnd(n);
  lines.push(pad('', 28) + CATEGORIES.map((c) => pad(c.k, 6)).join('') + 'total');
  for (const r of withTests) {
    const by = (k) => r.tests.filter((t) => t.cat === k).length;
    lines.push(pad(`${r.id} ${r.name}`.slice(0, 27), 28) + CATEGORIES.map((c) => pad(by(c.k) || '.', 6)).join('') + r.tests.length);
  }
  return lines.join('\n');
}

const TEMPLATE = readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'template.html'), 'utf8');

function render(model) {
  const json = JSON.stringify(model).replaceAll('<', '\\u003c');
  return TEMPLATE.replace('/*__DATA__*/null', json);
}

const { model, problems } = buildModel();
if (problems.length) {
  console.error(`${problems.length} problem(s):\n` + problems.map((p) => '  ' + p).join('\n'));
  process.exit(1);
}
console.log(summary(model));
if (!process.argv.includes('--check')) {
  writeFileSync(OUT, render(model));
  console.log(`\nwrote ${relative(ROOT, OUT)}`);
}
