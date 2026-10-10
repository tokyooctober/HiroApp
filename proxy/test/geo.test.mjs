import { test } from 'node:test';
import assert from 'node:assert/strict';
import { distanceM, nameScore, nearestBranch } from '../scripts/geo.mjs';

// spec: FR-3; cat: logic; tier: tooling
test('distance is 0 for the same point', () => {
  assert.equal(distanceM({ lat: 1.35, lng: 103.94 }, { lat: 1.35, lng: 103.94 }), 0);
});

// spec: FR-3; cat: logic; tier: tooling
test('one degree of latitude is about 111.2 km', () => {
  const d = distanceM({ lat: 1, lng: 103 }, { lat: 2, lng: 103 });
  assert.ok(Math.abs(d - 111_195) < 50, String(d));
});

// spec: FR-3; cat: logic; tier: tooling
test('distance is symmetric', () => {
  const a = { lat: 1.352391, lng: 103.940821 };
  const b = { lat: 1.4054, lng: 103.9021 };
  assert.equal(distanceM(a, b), distanceM(b, a));
});

// spec: FR-3; cat: logic; tier: tooling
test('299 m and 301 m are told apart (the Task 5 radius edge)', () => {
  const origin = { lat: 1.3, lng: 103.8 };
  const metresPerDegLat = 111_195;
  const at = (m) => ({ lat: origin.lat + m / metresPerDegLat, lng: origin.lng });
  assert.ok(distanceM(origin, at(299)) < 300);
  assert.ok(distanceM(origin, at(301)) > 300);
});

// spec: FR-3; cat: logic; tier: tooling
test('nearestBranch picks the closest and reports the runner-up', () => {
  const lib = { lat: 1.3524, lng: 103.9408 };
  const branches = [
    { code: 'FAR', name: 'Far', lat: 1.45, lng: 103.8 },
    { code: 'NEAR', name: 'Near', lat: 1.3525, lng: 103.9409 },
    { code: 'MID', name: 'Mid', lat: 1.36, lng: 103.95 },
  ];
  const { best, second } = nearestBranch(lib, branches);
  assert.equal(best.branch.code, 'NEAR');
  assert.equal(second.branch.code, 'MID');
  assert.ok(best.metres < second.metres);
});

// spec: FR-3; cat: logic; tier: tooling
test('name score ignores generic words and compares the identifying ones', () => {
  assert.equal(nameScore('Central Public Library', 'Central Library'), 1);
  assert.equal(nameScore('Central Public Library', 'Central Arts Library'), 0.5);
  assert.equal(nameScore('Tampines Regional Library', 'Tampines Library'), 1);
  assert.equal(nameScore('library@chinatown', 'Chinatown Library'), 1);
  assert.equal(nameScore('Orchard Library (library@orchard)', 'Orchard Library'), 1);
  assert.equal(nameScore('Bedok Public Library', 'Bishan Library'), 0);
});

// spec: FR-3; cat: logic; tier: tooling
test('libraries sharing a building: a near-tie is decided by name, not by a few metres', () => {
  const lib = { name: 'Central Public Library', lat: 1.2998, lng: 103.8546 };
  const branches = [
    { code: 'CAL', name: 'Central Arts Library', lat: 1.29981, lng: 103.8546 }, // slightly nearer
    { code: 'CLL', name: 'Central Library', lat: 1.29985, lng: 103.8546 },
  ];
  assert.equal(nearestBranch(lib, branches).best.branch.code, 'CLL');
});

// spec: FR-3; cat: logic; tier: tooling
test('a tie never reaches a library that is far away, whatever its name', () => {
  const lib = { name: 'Jurong Regional Library', lat: 1.3331, lng: 103.7424 };
  const branches = [
    { code: 'NEAR', name: 'Somewhere Else', lat: 1.3332, lng: 103.7425 },
    { code: 'NAMED', name: 'Jurong Library', lat: 1.4, lng: 103.9 }, // right name, 20 km away
  ];
  assert.equal(nearestBranch(lib, branches).best.branch.code, 'NEAR');
});
