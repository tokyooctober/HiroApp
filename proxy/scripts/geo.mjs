// Distance and nearest-branch matching used by the one-time library code update.

const R_KM = 6371;
const rad = (d) => (d * Math.PI) / 180;

/** Great-circle distance in metres (haversine, Earth radius 6371 km: spec section 5). */
export function distanceM(a, b) {
  const dLat = rad(b.lat - a.lat);
  const dLon = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * R_KM * 1000 * Math.asin(Math.sqrt(h));
}

const NOISE = new Set(['public', 'regional', 'library', 'the', 'at']);

/** Words that identify a library once generic ones are dropped: "Tampines Regional Library" -> {tampines}. */
export function nameTokens(name = '') {
  return new Set(
    name
      .toLowerCase()
      .replace(/\(.*?\)/g, ' ')
      .split(/[^a-z0-9]+/)
      .filter((t) => t && !NOISE.has(t)),
  );
}

/** Overlap of the identifying words, 0..1. "Central Public Library" vs "Central Library" is 1; vs "Central Arts Library" is 0.5. */
export function nameScore(a, b) {
  const x = nameTokens(a);
  const y = nameTokens(b);
  if (x.size === 0 || y.size === 0) return 0;
  const shared = [...x].filter((t) => y.has(t)).length;
  return shared / new Set([...x, ...y]).size;
}

/**
 * Nearest branch to a library. Branches within `tieMetres` of the nearest are treated as a tie
 * (several libraries can share a building) and the closest name wins. Returns the runner-up too,
 * so a reviewer can see whether the match was clear. `branches` are { code, name, lat, lng }.
 */
export function nearestBranch(library, branches, { tieMetres = 100 } = {}) {
  const ranked = branches
    .map((branch) => ({ branch, metres: distanceM(library, branch), score: nameScore(library.name, branch.name) }))
    .sort((x, y) => x.metres - y.metres);
  const tied = ranked.filter((r) => r.metres <= ranked[0].metres + tieMetres);
  const best = [...tied].sort((x, y) => y.score - x.score || x.metres - y.metres)[0];
  const second = ranked.find((r) => r !== best);
  return { best, second };
}
