import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { flattenRecords, heightCm, summarize, toMarkdownTable } from '../scripts/probe-analysis.mjs';

const fixture = (name) => JSON.parse(readFileSync(fileURLToPath(new URL(`../../fixtures/${name}.json`, import.meta.url)), 'utf8'));

test('heightCm reads the last size in a physical description', () => {
  assert.equal(heightCm({ physicalDescription: ['72 pages : colour illustrations ; 29 cm + 1 wall chart.'] }), 29);
  assert.equal(heightCm({ physicalDescription: ['1 online resource'] }), null);
  assert.equal(heightCm({}), null);
});

test('flattenRecords keeps the group series title on each record', () => {
  const body = { titles: [{ title: 'T', seriesTitle: ['S'], records: [{ brn: 1 }, { brn: 2 }] }, { title: 'U', records: [{ brn: 3 }] }] };
  const records = flattenRecords(body);
  assert.deepEqual(records.map((r) => [r.brn, r.seriesTitle.length]), [[1, 1], [2, 1], [3, 0]]);
});

test('summarize counts the recorded junior dinosaur search', () => {
  const s = summarize(fixture('search-titles-junior-plain'), /dinosaur/i);
  assert.equal(s.totalRecords, 2596);
  assert.equal(s.records, 25);
  assert.equal(s.onTopic, 25);
  assert.equal(s.boardOrPictureSubject, 6);
  assert.equal(s.tall, 6);
  assert.equal(s.withAudienceField, 4);
  assert.ok(Math.abs(s.bandShare - 0.18) < 0.01);
});

test('summarize copes with an error body and a format-only probe', () => {
  const s = summarize({ message: 'bad request' });
  assert.equal(s.records, 0);
  assert.equal(s.onTopic, null);
  assert.equal(s.bandShare, null);
});

test('toMarkdownTable writes one row per probe and dashes for missing values', () => {
  const table = toMarkdownTable([{ name: 'a', status: 200, totalRecords: 5, onTopic: null, bandShare: 0.5 }]);
  const rows = table.trim().split('\n');
  assert.equal(rows.length, 3);
  assert.match(rows[2], /^\| a \| 200 \| 5 \| - \|/);
  assert.match(rows[2], /0\.50 \|$/);
});

test('toMarkdownTable keeps a pipe in a name inside its cell', () => {
  const rows = toMarkdownTable([{ name: 'penguin | baseline', status: 200 }]).trim().split('\n');
  assert.equal(rows[2].split(' | ').length, rows[0].split(' | ').length);
});
