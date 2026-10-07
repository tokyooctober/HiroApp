// The only app-facing paths (spec section 2). Everything else is 404.
const NLB = 'https://openweb.nlb.gov.sg/api';

const ROUTES = {
  '/catalogue/SearchTitles': `${NLB}/v2/Catalogue/SearchTitles`,
  '/catalogue/GetTitles': `${NLB}/v2/Catalogue/GetTitles`,
  '/catalogue/GetTitleDetails': `${NLB}/v2/Catalogue/GetTitleDetails`,
  '/catalogue/GetAvailabilityInfo': `${NLB}/v2/Catalogue/GetAvailabilityInfo`,
  '/library/GetBranches': `${NLB}/v1/Library/GetBranches`,
  '/eresource/SearchResources': `${NLB}/v1/EResource/SearchResources`,
  '/eresource/GetAvailabilityInfo': `${NLB}/v1/EResource/GetAvailabilityInfo`,
  '/recommendation/GetRecommendationsForTitles': `${NLB}/v1/Recommendation/GetRecommendationsForTitles`,
};

/** Returns the NLB URL for an allowed call (query string kept as sent), or null. */
export function resolveUpstream(method, url) {
  if (method !== 'GET') return null;
  const target = Object.hasOwn(ROUTES, url.pathname) ? ROUTES[url.pathname] : null;
  return target ? new URL(`${target}${url.search}`) : null;
}
