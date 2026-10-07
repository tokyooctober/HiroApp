# Implementation Plan: Hiro-Kids (Android + iOS)

Source of truth: `docs/hiro-kids-spec.md` (requirements FR-1..18, NFR-1..12, build conventions in section 10). Task details, acceptance criteria and status are in `tasks/todo.md`; this file holds the order, decisions, risks and checkpoints. Tasks are tracked in `tasks/todo.md` (no external tracker).

## Overview

A Kotlin Multiplatform app that shows what is on the shelf in the NLB library the user is standing in, in Children or Adult mode, backed by a small API proxy that holds the NLB key. Nothing exists yet except the spec, design sources and `data/nlb_libraries.json`. The plan builds a thin end-to-end path first (proxy → shared core → one screen on both platforms), then adds one user-visible capability per task, with the safety-critical audience filter and the 1 call/s rate limit tackled early because they are the two biggest risks.

The spec's T0–T10 are layer-oriented. This plan keeps their requirement coverage but re-slices them vertically (each task ends with something a user, or a test, can see working). The spec task each one absorbs is named in `todo.md` as "Spec task".

## Dependency graph

```
Fixtures + API facts (T0, needs API key)
   │
   ├── Proxy: key, queue, cache ──────────────────────────────┐
   │                                                          │
KMP skeleton + CI (both apps) ──┬──────────────────────────────┤
                                │                              │
                     Branch directory + current library (FR-3) │
                                │                              │
          AudiencePolicy (children) + fixture regression       │
                                │                              │
              Search → "On the shelf here" (FR-1,2,4,5,14) ◄───┘
                     │
        ┌────────────┼────────────────┬──────────────┐
  Paging/errors   Other libraries    Book detail    Adult mode
  (FR-11,12)      + View (FR-15,16)  (FR-6,14)      (FR-2)
                     │                  │               │
                     │         ┌────────┼─────────┐     │
                     │     Favourites  Directions  Read online
                     │     + Recent    (maps)      (FR-8)
                     │     (FR-10,18)
                     └──────── Filters + Teen (FR-7,13) ── Settings (FR-17)
                                                              │
                          Hardening: attestation, regression, a11y, perf, release
```

Implementation order follows this graph bottom-up. Parallel lanes once the skeleton exists: proxy work (Tasks 2, 3) vs. app work (Tasks 4+); fixture-based domain work (AudiencePolicy, ResultGrouper) vs. UI.

## Architecture decisions

- **Everything through the proxy** (NFR-3, NFR-4). The app never holds the NLB key. Development builds point at a dev proxy.
- **One rate-limit queue, one coordinator.** The 1 call/s, 15 calls/min limit is per key, so the proxy needs a single place that queues calls. Cloud Run needs `max-instances=1` (in-memory queue); Cloudflare Workers need a Durable Object. This is the deciding factor between the two hosts (decision in Task 2, "ask first").
- **Domain in pure common Kotlin.** `AudiencePolicy` and `ResultGrouper` take plain models and have no platform or network dependencies, so they are table-tested on the JVM against recorded fixtures.
- **Fixtures are the contract.** Task 1 records real responses; DTOs, mappers and policy tests are written against them (tests first). Section 2 of the spec is corrected wherever fixtures disagree.
- **Hide when ambiguous.** Anything `AudiencePolicy` cannot confirm for the current mode is not shown (section 4). Children's eResources stay hidden until the children's subject list exists.
- **Render from local first.** Cards come from the SQLDelight book store (FR-14); only shelf availability goes to the network.
- **Direct-vs-proxy attestation last.** Play Integrity / App Attest is added in the hardening phase; dev builds use a debug token that the proxy accepts only in a dev environment.

## Task list (order; details in todo.md)

Phase 0, unblock and de-risk
1. Record fixtures and settle NLB API facts (spec T0)
2. Proxy: key injection, rate-limit queue, 429 pass-through (spec T1)
3. Proxy: response caching with the TTLs of NFR-3 (spec T1)
3b. Deploy the proxy to Google Cloud Run, dev (spec T1; needs a Google Cloud project, added 7 Oct 2026)

Phase 1, walking skeleton
4. KMP skeleton: both apps build and call the proxy, CI green (spec T2)
5. Current library: branch directory, detection, picker, "You're at" card (spec T5, FR-3)
6. `AudiencePolicy` for Children mode with fixture regression set (spec T4, part of T9)

Phase 2, Children's search vertical
7. Search in Children mode shows "On the shelf here" cards (spec T3, T6 group 1, T7 part)
8. Paging, "all copies out here" group, errors and retry (spec T6 group 2, T7 part)
9. Other libraries with copies on the shelf, View switches library (spec T6, T7 part)

Phase 3, detail and the rest of the product
10. Book detail with call number, copies and other libraries (spec T8 part)
11. Directions to another library (maps hand-off)
12. Adult mode (spec T4, T7 part)
13. Filters and the teen toggle (spec T7 part)
14. Favourites and recently viewed (spec T8 part, FR-18)
15. Read online: eBooks and audiobooks (spec T8 part, FR-8)
16. Settings (spec T8 part)
17. More like this (FR-9), KIV: blocked until a title MID source exists

Phase 4, hardening and release
18. App attestation and binary key scan (NFR-4)
19. Audience regression set at full size, nightly on fixtures, weekly live (spec T9)
20. Accessibility, performance, privacy and compatibility pass (spec T10)
21. Release readiness: NLB limit increase, key renewal, name, store listings

## Checkpoints

- **After 1–3, Foundation:** fixtures reproducible (not committed, public repo); proxy deployed to dev, key only in the secret manager; queue never exceeds 1 call/s in a load test.
- **After 4–6, Skeleton:** both apps build and run in CI; the current library persists across restart; `AudiencePolicy` (Children) passes on every Children fixture with zero leaks.
- **After 7–9, Children's search works end to end:** the golden scenario of section 8 passes (A in group 1, B in group 2, C only in group 3 with Pasir Ris before Punggol, View makes C appear in group 1) on both platforms. **Stop and review with the human.**
- **After 10–16, Feature complete:** every FR except FR-9 meets its acceptance criteria; screens reviewed against `docs/design/`.
- **After 18–21, Release:** definition of done in spec section 10 holds.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `Availability=true` + `Locations` does not mean "on the shelf at that branch", or facets don't count on-shelf books | High: breaks groups 1 and 3 | Task 1 verifies on live data before any app code; fallback in spec section 9 (`GetAvailabilityInfo` for the first 5 cards) |
| Children's content leaks (sparse MARC 521 audience data) | High: core safety promise | `AudiencePolicy` before any UI (Task 6); hide-when-ambiguous; regression set grows with each task; measure the hide rate |
| 15 calls/min shared by all users | High for release, low for pilot | Queue with bounded wait and visible busy state (Tasks 2, 8); cache (Task 3); ask NLB for a higher limit in Task 1 and again in Task 21 |
| Facet ids differ from `GetBranches` codes (`trl` vs `TRL`) | Medium: one-library search silently returns nothing | Check in Task 1; normalise in one mapper |
| Approximate location is often off by more than 300 m | Low: detection falls through to the picker | Decided 7 Oct 2026: keep approximate-only; picker lists the closest libraries first; Change is always available; measure in a field test (Task 5) |
| No MID source, so FR-9 cannot ship | Low: FR-9 is hidden by design | Ask NLB in Task 1; Task 17 parked |
| KMP + two toolchains (Gradle, Xcode) slow the first slices | Medium | Skeleton and CI first (Task 4); iOS built and tested on every slice, not at the end |
| API key expires 6 Apr 2027 | Medium | Renewal task in Task 21; calendar reminder |
| Slice sizes exceed the 5-file guideline because a UI slice touches repo + ViewModel + screen + tests on two platforms | Low | Shared Compose UI keeps most files in `shared/`; tasks flagged M/L are split further if they run long |

## Open questions needing a human

- ~~Proxy host~~ Decided 7 Oct 2026 (owner): Cloud Run with `max-instances=1` (the queue is in memory).
- ~~Role of `data/nlb_libraries.json`~~ Decided 7 Oct 2026: seeds the picker, codes filled by a one-off script, 300 m, only `open` libraries shown (Orchard and Marine Parade confirmed closed).
- Google Cloud project, billing, `gcloud` sign-in and a GitHub repository URL (needed for Task 3b and for CI).
- App name now that Adult mode exists (blocks store listings only).
- Bundle identifier / package name, Apple developer account and Play console access (blocks Task 18 and Task 21, not before).
- NLB contact: higher rate limit, and an ISBN/BRN-to-MID mapping (human sends the email in Task 1). Also ask: what MID is, how to get recommendations for non-ebooks, and whether an app may use patron-based suggestions (see spec section 9, KIV).
