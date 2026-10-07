import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolveUpstream } from '../src/allowlist.mjs';

test('the 8 app-facing endpoints map to NLB URLs', () => {
  const cases = {
    '/catalogue/SearchTitles': 'https://openweb.nlb.gov.sg/api/v2/Catalogue/SearchTitles',
    '/catalogue/GetTitles': 'https://openweb.nlb.gov.sg/api/v2/Catalogue/GetTitles',
    '/catalogue/GetTitleDetails': 'https://openweb.nlb.gov.sg/api/v2/Catalogue/GetTitleDetails',
    '/catalogue/GetAvailabilityInfo': 'https://openweb.nlb.gov.sg/api/v2/Catalogue/GetAvailabilityInfo',
    '/library/GetBranches': 'https://openweb.nlb.gov.sg/api/v1/Library/GetBranches',
    '/eresource/SearchResources': 'https://openweb.nlb.gov.sg/api/v1/EResource/SearchResources',
    '/eresource/GetAvailabilityInfo': 'https://openweb.nlb.gov.sg/api/v1/EResource/GetAvailabilityInfo',
    '/recommendation/GetRecommendationsForTitles': 'https://openweb.nlb.gov.sg/api/v1/Recommendation/GetRecommendationsForTitles',
  };
  for (const [path, upstream] of Object.entries(cases)) {
    assert.equal(resolveUpstream('GET', new URL(`http://p${path}?Keywords=x&Limit=20`))?.origin + resolveUpstream('GET', new URL(`http://p${path}`))?.pathname, upstream);
  }
});

test('the query string is forwarded unchanged', () => {
  const u = resolveUpstream('GET', new URL('http://p/catalogue/SearchTitles?Keywords=a+b&Limit=20'));
  assert.equal(u.search, '?Keywords=a+b&Limit=20');
});

test('anything else is not allowed', () => {
  for (const path of ['/', '/catalogue', '/catalogue/Other', '/catalogue/SearchTitles/', '/catalogue/searchtitles', '/../api/v2/Catalogue/SearchTitles', '/library/GetBranches/..', '/admin', '/%2e%2e/x']) {
    assert.equal(resolveUpstream('GET', new URL(`http://p${path}`)), null, path);
  }
});

test('only GET is allowed', () => {
  for (const method of ['POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS']) {
    assert.equal(resolveUpstream(method, new URL('http://p/catalogue/SearchTitles?Keywords=x')), null, method);
  }
});
