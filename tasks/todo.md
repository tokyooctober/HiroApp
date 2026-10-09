# Hiro-Kids task list

Order and rationale: `tasks/plan.md`. Requirements: `docs/hiro-kids-spec.md`. Every task starts by writing its tests (spec section 8), cites its FR/NFR ids in the PR, and follows the boundaries in spec section 10. Commands below are the proposed ones from section 10 and are confirmed in Tasks 2 and 4.

Scope key: S = 1-2 files, M = 3-5, L = 5-8. A task that grows beyond L is split.

---

## Phase 0: Unblock and de-risk

### Task 1: Record fixtures and settle NLB API facts
**Spec task:** T0 · **Covers:** spec section 2, section 9 open items
**Description:** With the issued key, call all 8 endpoints and record the JSON responses as fixtures, including `SearchTitles` with `Locations` for both audiences. Use them to answer the facts that decide the design, and correct spec section 2 where the live API differs. A human also sends two questions to nlblabs@nlb.gov.sg.
**Acceptance criteria:**
- [ ] `fixtures/` holds one real response per endpoint, plus `SearchTitles` for `juvenile` and `adult` with and without `Locations`/`Availability`; no key or personal data in any file
- [ ] Written answers in spec section 9 for: `Availability=true` + `Locations` semantics; whether location facets count on-shelf books per branch; facet id vs `GetBranches` code casing; exact `IntendedAudiences` ids from `facets[]`; which `audience`/`audienceImda` values appear on children's titles
- [ ] Children's eResource subject list found, or recorded as missing (children's eResources stay hidden)
- [ ] Decision recorded on the role of `data/nlb_libraries.json` (seed or not, `branchCode` source, radius, `upcoming` libraries)
- [ ] Email sent to NLB asking for a higher rate limit and an ISBN/BRN-to-MID mapping (human action)
**Verification:**
- [ ] `cd proxy && npm run record-fixtures` re-creates the fixtures from a clean checkout when the key is in the environment
- [ ] `grep -r "X-Api-Key" fixtures/` finds no key value
- [ ] Manual: human reads the updated section 2 and 9
**Dependencies:** none (needs the API key)
**Files likely touched:** `proxy/scripts/record-fixtures.*`, `fixtures/**`, `docs/hiro-kids-spec.md`
**Estimated scope:** M

### Task 2: Proxy with key injection and a rate-limit queue
**Spec task:** T1 · **Covers:** NFR-3, NFR-4 (proxy side), FR-12 (429)
**Description:** A thin proxy that forwards the app's requests to the 4 NLB APIs, adds `X-Api-Key` and `X-App-Code` from the secret manager, and queues every call so the key stays within 1 call/s and 15 calls/min. If a call would wait longer than 10 s, it returns 429 with `Retry-After`. Decision first (ask the human): Cloud Run with `max-instances=1`, or a Cloudflare Worker with a Durable Object. Deployed to a dev environment.
**Acceptance criteria:**
- [ ] The app-facing path allows only the 8 endpoints of section 2; everything else is 404
- [ ] Headers are injected server-side; the response never contains them
- [ ] A burst of 20 concurrent requests reaches NLB at ≤ 1 call/s and ≤ 15 in any 60 s
- [ ] A queued call that cannot run within 10 s gets 429 + `Retry-After`; an NLB 429/5xx is passed through unchanged
- [ ] No coordinates or other user data are logged (NFR-5)
**Verification:**
- [ ] `cd proxy && npm test` (header injection, allow-list, queue timing with a fake clock, 429 pass-through)
- [ ] Load script against a fake NLB confirms the rate; one live smoke call through the dev proxy returns a real `GetBranches`
- [ ] Secret manager holds the key; `git grep` finds none
**Dependencies:** Task 1
**Files likely touched:** `proxy/src/index.*`, `proxy/src/queue.*`, `proxy/src/allowlist.*`, `proxy/test/*`, deploy config
**Estimated scope:** M

### Task 3: Proxy response caching
**Spec task:** T1 · **Covers:** NFR-3
**Description:** Add the caches of NFR-3 in front of the queue: `SearchTitles` 10 min, availability 5 min, branches 7 days. Cache hits do not use queue budget.
**Acceptance criteria:**
- [x] Repeat request within its TTL makes 0 NLB calls and returns the same body _(`handler-cache.test.mjs`; identical requests arriving together also share one NLB call)_
- [x] After the TTL, the next request calls NLB once _(each endpoint on its own TTL: search 10 min, availability 5 min, branches 7 days)_
- [x] Cache key includes every query parameter, never the device or the key _(`cache.test.mjs`: path plus sorted parameters, no host or headers)_
- [x] Error responses are not cached _(429, 500, 404, a network failure, and a response echoing a credential)_
**Verification:**
- [x] `cd proxy && npm test` (TTL per endpoint, key construction, no caching of 429/5xx) _(50 tests pass, 9 Oct 2026; cache hits are also tested to use no queue slot, and logs say hit or miss with no query string)_
- [ ] Manual: two identical dev-proxy searches, second visible as a hit in proxy logs
**Dependencies:** Task 2
**Files touched:** `proxy/src/cache.mjs`, `proxy/src/handler.mjs`, `proxy/test/cache.test.mjs`, `proxy/test/handler-cache.test.mjs` (built with the proxy in the first commit; this entry was never ticked)
**Estimated scope:** S

### Task 3b: Package the proxy as a container, then deploy it
**Spec task:** T1 (deploy) · **Covers:** NFR-3, NFR-4 (secrets), NFR-5 (no logging of queries)
**Description:** Part A (no decision needed): package the proxy as a container that runs the same on any host. Part B (host decision deferred by the owner on 7 Oct 2026): deploy it. Options: the owner's own machine behind a Cloudflare Tunnel (the owner has a Cloudflare account; free), Google Cloud Run (`max-instances=1`, Singapore, needs billing), a small VM such as Oracle Always Free, or Cloudflare Workers (needs a rewrite: the queue becomes a Durable Object). Whatever the host, there must be exactly **one** instance, because the rate-limit queue and the cache live in memory. The NLB key and app code reach the container only as environment variables or the host's secret store; never in the image, the repo or the logs.
**Local first (owner, 9 Oct 2026):** run the container on the owner's own machine and prove it there, updates included, before deciding where to host it outside that machine. Part A is all that needs; Part B waits.
**Part A: container (done; one manual step on the owner's machine)**
- [x] `proxy/Dockerfile` (Node 22, non-root user, no credentials, `PORT` from the environment, TCP health check), `proxy/.dockerignore`, `docker-compose.yml` (credentials copied from the shell, read-only filesystem, optional `tunnel` profile for Cloudflare Tunnel), `proxy/README.md` (run, container, tunnel, hosting options, rollback)
- [x] CI job `proxy-image` builds the image and smoke-tests it with fake credentials: unknown path and POST give 404, `GetBranches` passes NLB's 401 through, the container stays up _(green, 7 Oct 2026)_
- [x] The `docker compose` path was run in a container runtime on 9 Oct 2026 with fake credentials: the image builds, one container runs as a non-root user with a read-only filesystem and all capabilities dropped, the healthcheck turns healthy, an unknown path and a POST give 404, and the logs hold the path and status only. An update (rebuild and replace) and a rollback (rebuild the previous commit) both work. Not checked there: the real `GetBranches` list, because the cloud sandbox cannot reach NLB
- [x] Compose publishes on this machine only (`127.0.0.1:8080`); `PROXY_BIND=0.0.0.0` is the opt-in for a network you trust. CI now smoke-tests the compose path as well (read-only, user, loopback)
- [ ] Manual, on the owner's machine: install Docker Desktop, follow "Run it on your own machine" in `proxy/README.md` (PowerShell steps) with the real key; `GetBranches` returns the library list and `docker compose ps` shows one healthy proxy
**Part B: deploy outside the owner's machine (deferred: first prove Part A on the owner's machine, then choose a host)**
- [ ] The chosen host runs one instance and serves `/library/GetBranches` with the real branch list; an unknown path returns 404; a burst of 20 requests is spaced at ≤ 1 call/s and any wait over 10 s gets 429 with `Retry-After`
- [ ] Secrets come from the host's secret store or environment; the image history and service description show no secret value; `git grep` finds none
- [ ] Logs on the host contain the path and status only: no query string, no coordinates (NFR-5). Checked by searching them for a distinctive search term after a test search
- [ ] The URL reaches the apps through `-PproxyBaseUrl=` (Android) and `PROXY_BASE_URL` (iOS); nothing about the URL or key is committed
- [ ] Manual: the Android emulator app shows "26 libraries" using the deployed URL
**Human first for Part B:** pick the host. For Cloud Run: a Google Cloud project with billing, `gcloud` installed and signed in, and the owner's go-ahead on cost. For the tunnel: a Cloudflare tunnel token and a hostname.
**Dependencies:** Tasks 2 and 3 (done). Until Task 18 adds app attestation, the URL is open to anyone who finds it: treat it as dev only and keep it private.
**Files likely touched:** `proxy/Dockerfile`, `.dockerignore`, `docker-compose.yml`, `proxy/README.md`, `.github/workflows/ci.yml`, host config
**Estimated scope:** M

### Checkpoint: Foundation (after Tasks 1-3)
- [ ] Fixtures committed and reproducible with `npm run record-fixtures`; spec section 2 matches the live API
- [ ] Dev proxy deployed; load test shows ≤ 1 call/s; no key in the repo
- [ ] Human reviews the answers to the open API questions before app work builds on them

---

## Phase 1: Walking skeleton

### Task 4: KMP skeleton, both apps call the proxy, CI green
**Spec task:** T2 · **Covers:** NFR-9 (build side), NFR-10 (strings in resources)
**Description:** Create the Gradle project (`shared`, `androidApp`, `iosApp`) with Compose Multiplatform, Koin, Ktor (OkHttp / Darwin engines), SQLDelight, multiplatform-settings, Coil, ktlint and detekt. One screen shows the branch count fetched through the dev proxy. CI builds and tests both platforms.
**Acceptance criteria:**
- [ ] Android debug app and iOS simulator app launch and show "N libraries" from the dev proxy _(Android done 7 Oct 2026: API 36 emulator, local proxy on :8080, screen shows "26 libraries"; iOS builds in CI for the simulator but has not been run, that needs a Mac)_
- [x] Base URL comes from build config; the apps contain no NLB key _(Android: APK scanned, no key, app code, header name or NLB host; iOS: reads `ProxyBaseURL` from Info.plist, not yet built)_
- [x] CI runs shared tests, Android build, iOS build and lint on every push _(green on GitHub Actions 7 Oct 2026, run 37602342375: proxy tests, container build, Android build + shared tests + lint + APK key scan, iOS simulator build)_
- [ ] Design tokens (colours, Fredoka/Nunito Sans, 16 px cards) exist in one theme file _(colours, shapes and Material theme done in `HiroTheme.kt`; the Fredoka and Nunito Sans font files are not added yet)_
**Verification:**
- [x] `./gradlew :shared:allTests :androidApp:assembleDebug ktlintCheck detekt` _(passes: 10 JVM tests, 9 Android-host tests, lint clean)_
- [ ] `xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 15'`
- [ ] Manual: run both apps against the dev proxy
**Dependencies:** Task 2 (for the live call; the skeleton itself needs none)
**Files likely touched:** `settings.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `iosApp/**`, `.github/workflows/ci.yml`; theme and DI files under `shared/`
**Estimated scope:** L (new project; further split into "Android + shared" and "iOS + CI" if it runs long)

### Task 5: Current library: directory, detection, picker, "You're at" card
**Spec task:** T5 · **Covers:** FR-3 · **Design:** `Location.dc.html`, top of `Main.dc.html`
**Description:** `LibraryDirectory` loads `GetBranches` through the proxy and stores branch code, name, coordinates and hours. `LocationProvider` (one precise fix while the app is in use; spec section 7) feeds nearest-branch detection by haversine over the libraries in `data/nlb_libraries.json` whose status is `open`, accepted within 300 m. Otherwise the searchable picker opens with the closest libraries first, which also happens when the user grants only approximate location. Accuracy is not critical: the user can tap Change at any time. The picker shows only `open` libraries (22 today). The choice persists.
**Acceptance criteria:**
- [x] Inside 300 m: nearest branch is set and shown as "You're at {library}" with Change _(emulator, 8 Oct 2026: mock location at Tampines, precise permission)_
- [x] Outside 300 m, or permission denied: the picker opens and a library can be chosen _(emulator: far location gives the notice and a closest-first list with distances; "Don't allow" gives the notice and an alphabetical list)_
- [x] Choice survives a restart; Change reopens the picker with the current one ticked _(unit tests and emulator)_
- [x] Coordinates never leave the device (only branch codes do) (NFR-5) _(test on the request URLs and headers; the proxy log shows only `GetBranches`)_
- [x] Permissions requested are exactly those in spec section 7 _(Android manifest: INTERNET, fine and coarse location; iOS: when-in-use usage description)_
- [x] Libraries with status `upcoming` or `closed` never appear in the picker, in detection, or in the other-libraries list; a library marked `open` that is missing from the live `GetBranches` list is hidden too _(the other-libraries list arrives in Task 9 and must use the same directory)_
- [x] With a location available the picker lists the closest libraries first; Change is always available _(with an approximate fix: ordered, but no distances shown)_
- [x] Approximate-only location permission opens the picker instead of auto-detecting _(emulator: phone on top of Pasir Ris with approximate-only: library unchanged, notice shown)_
**Verification:**
- [x] `./gradlew :shared:allTests` (haversine, 299 m / 301 m, denied, persisted, tie-breaking) _(56 JVM + 51 Android-host tests)_
- [ ] Android Compose test and XCUITest for the picker flow _(not written yet)_
- [ ] Manual: mock location near and far from a library on both platforms _(Android done on an API 36 emulator; iOS needs a Mac)_
**Dependencies:** Tasks 3, 4
**Files likely touched:** `shared/.../domain/LibraryDirectory.kt`, `shared/.../data/BranchRepository.kt`, `LocationProvider` expect/actual, `LocationScreen.kt`, tests
**Estimated scope:** L

### Task 6: `AudiencePolicy` for Children mode with fixture regression set
**Spec task:** T4 (Children half), seed of T9 · **Covers:** FR-2, FR-13 (off), section 4
**Status:** code, tests and CI done 9 Oct 2026; waiting on the human read of the hidden titles.
**Description:** The pure-Kotlin gate that decides whether a record may be shown in Children mode, built rule by rule from section 4 and the Task 1 fixtures, plus a regression set that runs every Children fixture through it. No UI. Built before any result screen so nothing can be rendered ungated.
**Acceptance criteria:**
- [x] Each rule of the Children column has its own test (restricted, `minAgeLimit` > 0, rated media, usage levels, juvenile marker, mixed junior and adult copies counts only junior) _(`AudiencePolicyTest`, 31 tests; three rules (the older-audience veto, the shelf list, the marker) were also broken on purpose and tests failed each time)_
- [x] Anything ambiguous returns not-allowed _(unknown usage level, unreadable audience text, no marker, no copies, Adult mode until Task 12)_
- [x] Regression set: zero adult-only or teen-only titles allowed from the Children fixtures _(`AudienceRegressionTest`: the 43 adult-search records that are not in any juvenile result are all hidden unless they carry a Juvenile subject; and every marked children's record is allowed, so "hide everything" cannot pass. Teen records are synthetic in `AudiencePolicyTest`: no `adolescent` fixture is recorded)_
- [x] The hide rate on the fixtures is printed so MARC 521 sparsity can be judged _(7 of 42 distinct records, 16.7%; printed and written to `shared/build/reports/audience/hide-rate.txt`; spec section 9)_
**Verification:**
- [x] `./gradlew :shared:allTests --tests "*AudiencePolicy*"` _(green on GitHub Actions 9 Oct 2026, run 37908876027: `:shared:allTests`, ktlint and detekt on Linux, and the Kotlin tests on the iOS simulator target. Earlier checked in a JVM-only scratch build because the cloud sandbox cannot reach dl.google.com)_
- [ ] Manual: human reads the printed list of hidden titles for a sample
**Dependencies:** Task 1
**Files touched:** `shared/.../domain/AudiencePolicy.kt`, `AudienceText.kt`, `Models.kt`, `shared/src/commonTest/.../AudiencePolicyTest.kt`, `AudienceRegressionTest.kt`
**Estimated scope:** M

### Checkpoint: Skeleton (after Tasks 4-6)
- [ ] CI green for both platforms
- [ ] Current library detection and persistence work on a device or simulator
- [ ] `AudiencePolicy` Children rules pass with zero leaks
- [ ] Review with the human before building result screens

---

## Phase 2: Children's search vertical

### Task 7: Search in Children mode shows "On the shelf here" cards
**Spec task:** T3, T6 (group 1), T7 (part) · **Covers:** FR-1, FR-2 (Children), FR-4 (group 1), FR-5, FR-14 · **Design:** `Main.dc.html`, `Results.dc.html`
**Status (9 Oct 2026): slices 7a and 7b written; waiting on CI and a run on a device.** 7a is the data layer (DTOs and mappers for `SearchTitles` and `GetTitles`, query parsing, the audience gate on every result, the SQLDelight book store with its migration, `NlbRepository.search`). 7b is `SearchViewModel` and the screens (search home, results, cards, covers, empty, error with Retry) wired into `LibraryFlow`. **Decision (owner, 9 Oct 2026):** the card says "On the shelf here" exactly as the search reports it, with no per-card availability check; the recorded data shows this can be wrong for some cards (spec section 9). **Known gaps, by design:** the Android system Back button is not wired (the on-screen arrow is); paging, busy back-off and "all copies out" are Task 8; library switching is Task 9. Covers load straight from NLB's public cover host, not through the proxy, so the NLB host sees the phone's address for each cover.
**Description:** The first full path. Search home with the Children heading and search box; submit a 2+ character query; `NlbRepository.search` calls `SearchTitles` (`juvenile`, `Locations=<current>`, `Availability=true`), maps to domain models, gates through `AudiencePolicy`, stores titles in SQLDelight, and renders group 1 cards (cover, title, author, type, "On the shelf here", count and call number when the local store has them). ISBN-shaped queries route to `GetTitles?ISBN=`.
**Acceptance criteria:**
- [ ] "dinosaur" in Children mode returns ≥ 1 card within 3 s on 4G against the dev proxy
- [ ] Only titles passing `AudiencePolicy` are shown; a seeded adult title in a fixture is never rendered _(data layer done: the repository returns and stores only allowed records, tested with a seeded adult title and with the recorded adult fixture; the screen is slice 7b)_
- [x] A 10- or 13-digit ISBN calls `GetTitles?ISBN=` _(`SearchQueryTest`, `NlbRepositoryTest`; hyphens and spaces ignored, a trailing X allowed)_
- [x] Titles are saved by BRN with no expiry; a repeated title renders from the store without a `GetTitleDetails` call _(`SqlBookStore`, `NlbRepository.stored`; the database moves to version 2 with a migration, tested from a version 1 database)_
- [ ] Missing cover shows a placeholder; text scales to 200% without clipping _(written: the title is drawn on a plain cover under the image; text is in sp and wraps. Not yet seen on a device)_
**Verification:**
- [ ] `./gradlew :shared:allTests` (mappers on fixtures, store-hit-no-network, ISBN routing, 2-character minimum) _(48 new tests, 79 with Task 6, pass in a JVM-only scratch build with ktlint and detekt clean; the Compose screens could not be compiled there because Google's Maven is blocked, so CI is the first compile of them)_
- [ ] Compose UI test + screenshot test (Children), XCUITest for the search flow
- [ ] Manual: search on both platforms in a real or mocked library
**Dependencies:** Tasks 3, 5, 6
**Files likely touched:** `NlbRepository.kt`, DTOs and mappers, `BookStore.sq`, `SearchViewModel.kt`, `SearchHomeScreen.kt`, `ResultsScreen.kt` (plus tests)
**Estimated scope:** L (split DTO/mapper/store from UI if it runs long)

### Task 8: Paging, "all copies out here" group, errors and retry
**Spec task:** T6 (group 2), T7 (part) · **Covers:** FR-4 (group 2), FR-5 (loan cards), FR-11, FR-12
**Description:** Add the second `SearchTitles` call (no `Availability`) to fill "In this library, all copies out" (fewest waiting first), page 20 at a time with `nextRecordsOffset`, and show retry plus stored results on network failure, with 1/2/4 s back-off on 429 (max 3 tries). Handle the proxy's "busy" 429 with the same retry action.
**Acceptance criteria:**
- [ ] Group 2 shows titles with copies here but none on the shelf, labelled "On loan here" with the number waiting; none repeat group 1
- [ ] Scrolling to the end of a group loads the next 20; no duplicate BRNs; "End of results" when `hasMoreRecords = false`
- [ ] 429 retries after 1, 2, 4 s, then shows the error with Retry; offline shows Retry and stored results
**Verification:**
- [ ] `./gradlew :shared:allTests` (paging with MockEngine, de-duplication, back-off timing with a test clock)
- [ ] UI tests for the error and end-of-results states
- [ ] Manual: airplane mode mid-search
**Dependencies:** Task 7
**Files likely touched:** `NlbRepository.kt`, `ResultGrouper.kt`, `RetryPolicy.kt`, `ResultsScreen.kt`, tests
**Estimated scope:** M

### Task 9: Other libraries with copies on the shelf; View switches library
**Spec task:** T6 (group 3), T7 (part) · **Covers:** FR-4, FR-15 (results), FR-16 · **Design:** `Results.dc.html` dashed box
**Description:** Third `SearchTitles` call without `Locations` (`Availability=true`); read location facets to list other libraries with counts, ordered most books then nearest. View sets that library as current, re-runs the search for the same query and mode, and Back returns to the previous library. `ResultGrouper` is tested against the golden scenario.
**Acceptance criteria:**
- [ ] Golden scenario: A in group 1, B in group 2, C not in 1-2; group 3 lists Pasir Ris then Punggol; View on Pasir Ris moves C to group 1
- [ ] The "You're at" card and the results chip show the new library; Back restores the previous results
- [ ] A search costs at most 3 NLB calls, 0 when repeated in the cache window
**Verification:**
- [ ] `./gradlew :shared:allTests --tests "*ResultGrouper*"` (table-driven golden scenario, ordering ties by distance)
- [ ] UI test for View and Back on both platforms
- [ ] Manual: count calls in the proxy log for one search
**Dependencies:** Task 8
**Files likely touched:** `ResultGrouper.kt`, `OtherLibrary` models, `LibraryBackStack.kt`, `ResultsScreen.kt`, tests
**Estimated scope:** M

### Checkpoint: Children's search works end to end (after Tasks 7-9)
- [ ] Golden scenario passes in `commonTest`, and the same flow works by hand on Android and iOS
- [ ] Proxy log shows ≤ 3 NLB calls per search and cache hits on repeat
- [ ] First look at a real device in a real library (or a mocked location) and compare with the design screens
- [ ] **Stop and review with the human before Phase 3**

---

## Phase 3: Detail and the rest of the product

### Task 10: Book detail with call number, copies and other libraries
**Spec task:** T8 (part) · **Covers:** FR-6, FR-14 (detail), FR-15 (detail) · **Design:** `Detail.dc.html`
**Description:** Tapping a result opens detail from the local store (≤ 200 ms) and fetches `GetAvailabilityInfo` once for "{available} of {total} copies", the large call number and shelf type, then summary, subjects, and each other library as "{available} of {total} on shelf" (shelf copies first, then nearest) with View.
**Acceptance criteria:**
- [ ] A previously seen title opens in ≤ 200 ms, also offline, with a loading state for availability only
- [ ] Only copies passing `AudiencePolicy` are counted and listed
- [ ] No copy on the shelf here: the panel says so and other libraries move up
- [ ] View switches library as in Task 9
**Verification:**
- [ ] `./gradlew :shared:allTests` (availability mapper, copy counting per mode, sort order)
- [ ] UI + screenshot tests; Macrobenchmark or timer test for the 200 ms target
- [ ] Manual: open titles with and without local copies
**Dependencies:** Task 9
**Files likely touched:** `DetailViewModel.kt`, `DetailScreen.kt`, `AvailabilityMapper.kt`, `NlbRepository.kt`, tests
**Estimated scope:** M

### Task 11: Directions to another library
**Covers:** NFR-7 (allow-list), design reference (Detail "directions")
**Description:** A `MapsLauncher` (Google Maps intent on Android, Apple Maps URL on iOS) opens directions to a branch from the other-libraries rows. No in-app routing.
**Acceptance criteria:**
- [ ] Directions opens the platform maps app at the branch coordinates
- [ ] Only the allowed URL schemes and hosts can be launched (unit-tested allow-list)
**Verification:**
- [ ] `./gradlew :shared:allTests --tests "*LinkAllowList*"`
- [ ] Manual: tap Directions on both platforms
**Dependencies:** Task 10
**Files likely touched:** `MapsLauncher` expect/actual (3), `LinkAllowList.kt`, tests
**Estimated scope:** S

### Task 12: Adult mode
**Spec task:** T4 (Adult half), T7 (part) · **Covers:** FR-2, section 4 · **Design:** mode colours in `Main.dc.html`
**Description:** The Children / Adult switch on search home, mode-specific heading, tag, chip and colour (amber vs blue), `IntendedAudiences=adult`, and the Adult column of `AudiencePolicy`. The mode persists as the starting mode when Settings exists. Add adult fixtures to the regression set.
**Acceptance criteria:**
- [ ] Adult mode shows only titles NLB classifies as adult; no list ever mixes modes
- [ ] A title with junior and adult copies appears in each mode with only that mode's copies counted
- [ ] Regression set: zero junior titles allowed in Adult mode, zero adult titles in Children mode
- [ ] Mode is visible in the heading, search-box tag, results chip and colour; status is never colour-only
**Verification:**
- [ ] `./gradlew :shared:allTests --tests "*AudiencePolicy*" --tests "*AudienceRegression*"`
- [ ] Screenshot tests for both modes; UI test for switching modes with the same query
**Dependencies:** Task 9
**Files likely touched:** `AudiencePolicy.kt`, `SearchViewModel.kt`, `SearchHomeScreen.kt`, `theme/`, tests
**Estimated scope:** M

### Task 13: Filters and the teen toggle
**Spec task:** T7 (part) · **Covers:** FR-7, FR-13 · **Design:** `Filters.dc.html`
**Description:** Filters screen: type of book per mode, language, "On the shelf now", plus "Include teen books" in Children mode (off by default, "Teen" badge when on, never adult/restricted/rated). Filters persist for the session and map to the parameters in section 4.
**Acceptance criteria:**
- [ ] Each filter sends its documented API parameters and applies its client rule
- [ ] "On the shelf now" hides titles with no `S` copy here
- [ ] Teen off: zero teen-only results; teen on: teen titles appear with a badge, still never adult, restricted or rated
- [ ] Toggle hidden in Adult mode
**Verification:**
- [ ] `./gradlew :shared:allTests` (filter-to-parameter table, teen on/off regression)
- [ ] UI tests for Filters; screenshot tests per mode
**Dependencies:** Task 12
**Files likely touched:** `FilterState.kt`, `FiltersScreen.kt`, `AudiencePolicy.kt`, `SearchViewModel.kt`, tests
**Estimated scope:** M

### Task 14: Favourites and recently viewed
**Spec task:** T8 (part) · **Covers:** FR-10, FR-18 · **Design:** `Favourites.dc.html`, Recently viewed row of `Main.dc.html`
**Description:** Save and remove titles from detail, Favourites screen split by mode with live shelf status for the current library ("{n} on shelf here · {call number}" or "All copies out here"), and up to 10 recently viewed titles per mode on search home. All stored in SQLDelight; no account.
**Acceptance criteria:**
- [ ] Favourites and recents survive restart; recents switch with the mode
- [ ] Each favourite row shows live status via `GetAvailabilityInfo` for the current library
- [ ] "Saved on this phone" note present; no personal data leaves the device (NFR-6)
**Verification:**
- [ ] `./gradlew :shared:allTests` (SQLDelight in-memory: add, remove, cap of 10, per-mode)
- [ ] UI tests for save/remove; screenshot test of Favourites
**Dependencies:** Task 10, Task 12
**Files likely touched:** `Favourites.sq`, `FavouritesRepository.kt`, `FavouritesScreen.kt`, `DetailScreen.kt`, `SearchHomeScreen.kt`
**Estimated scope:** M

### Task 15: Read online: eBooks and audiobooks
**Spec task:** T8 (part) · **Covers:** FR-8, section 4 (eResources), NFR-7
**Description:** After physical results, up to 10 eResources for the current mode from `SearchResources` (`eBooks`, `Audio Books`), with `GetAvailabilityInfo` for OverDrive items. Each opens its NLB `url` in a Custom Tab / SFSafariViewController through `BrowserLauncher`. Children mode shows only items whose subject is in the children's list (hidden entirely if the list does not exist, see Task 1).
**Acceptance criteria:**
- [ ] The section never appears above physical results; at most 10 items
- [ ] Children mode: no item outside the children's subject list; Adult mode: none inside it
- [ ] Links open only allow-listed hosts (NLB eResource URLs)
**Verification:**
- [ ] `./gradlew :shared:allTests` (subject gating, cap, ordering, allow-list)
- [ ] Manual: open an eBook link on both platforms
**Dependencies:** Task 12
**Files likely touched:** `EResourceRepository.kt`, `EResourceGate.kt`, `BrowserLauncher` expect/actual (3), `ResultsScreen.kt`
**Estimated scope:** M

### Task 16: Settings
**Spec task:** T8 (part) · **Covers:** FR-17, FR-13 · **Design:** `Settings.dc.html`
**Description:** Library (opens the picker), starting mode, teen toggle, "Clear stored book details", "Clear recently viewed and searches", and an About note crediting NLB and stating the app is not an official NLB app.
**Acceptance criteria:**
- [ ] Each clear action empties only its own store (book store vs recents), favourites untouched
- [ ] Starting mode applies on next launch
- [ ] About note credits NLB and says the app is not official (NFR-12)
**Verification:**
- [ ] `./gradlew :shared:allTests` (each clear affects only its store)
- [ ] UI test for the screen; screenshot test
**Dependencies:** Tasks 13, 14
**Files likely touched:** `SettingsScreen.kt`, `SettingsViewModel.kt`, `strings.xml`/resources, tests
**Estimated scope:** M

### Task 17: More like this (KIV)
**Covers:** FR-9
**Description:** Up to 10 recommended titles in the same mode that are on the shelf here, on the detail screen. Blocked until NLB supplies a way to get a title MID from a BRN or ISBN (Task 1 email). FR-9 stays hidden until then. **KIV:** restart when NLB confirms whether a third-party app may ask users for the MID, MyLibraryId and date of birth, and how a user finds their MID (spec section 9). What we know: a MID is an NLB-internal patron identifier, not a national ID (owner, 8 Oct 2026), and with `IdType=patron` it returns physical-book recommendations (owner's test, 8 Oct 2026). Without NLB's answer no screen asks for it.
**Acceptance criteria:**
- [ ] Recommendations outside the current mode or not on the shelf here are dropped
- [ ] Section is hidden when no MID is available
- [ ] _(owner intent, 8 Oct 2026)_ The feature is off until MyLibraryId, MID and year of birth are all filled in; with any missing, no recommendation call is made
- [ ] _(owner intent)_ The three values live only in the phone's secure storage (this-device-only: no backup, no sync) and are removable in one action that turns the feature off
- [ ] _(owner intent)_ The values never appear in a URL: they travel in headers or a body, and the proxy neither logs nor caches patron requests (proxy test)
**Verification:**
- [ ] `./gradlew :shared:allTests` (filtering, hidden state)
**Dependencies:** Task 10 and an answer from NLB
**Files likely touched:** `RecommendationRepository.kt`, `DetailScreen.kt`, tests
**Estimated scope:** M

### Checkpoint: Feature complete (after Tasks 10-16)
- [ ] Every FR except FR-9 meets its acceptance criteria
- [ ] All 7 screens reviewed against `docs/design/`
- [ ] CI green on both platforms; human demo on a real device

---

## Phase 4: Hardening and release

### Task 18: App attestation and binary key scan
**Covers:** NFR-4
**Description:** Play Integrity (Android) and App Attest (iOS) tokens sent to the proxy, which rejects calls without one outside dev. CI scans the APK and IPA for the NLB key value.
**Acceptance criteria:**
- [ ] Proxy rejects requests without a valid token in production; dev accepts the debug token only in dev
- [ ] CI fails if either binary contains the key or app code
**Verification:**
- [ ] `cd proxy && npm test` (token required, rejected, dev bypass only in dev)
- [ ] CI scan step demonstrably fails when a dummy key is planted on a branch
**Dependencies:** Task 7 (for a real client), human provides Apple/Google accounts
**Files likely touched:** `proxy/src/attest.*`, `AttestationProvider` expect/actual (3), `.github/workflows/ci.yml`
**Estimated scope:** M

### Task 19: Audience regression set at full size
**Spec task:** T9 · **Covers:** FR-2, NFR-7
**Description:** Grow the regression to 200 queries per mode, run nightly on recorded fixtures and weekly as a small live sample (or with a separate key), and report the hide rate.
**Acceptance criteria:**
- [ ] Children mode (teen off): zero adult or teen results; Adult mode: zero junior results
- [ ] A scheduled CI job runs it; a failure blocks release
- [ ] Live run stays within a stated call budget (section 9)
**Verification:**
- [ ] `./gradlew :shared:allTests --tests "*AudienceRegression*"`; scheduled workflow run shows green
**Dependencies:** Tasks 12, 13
**Files likely touched:** `shared/src/commonTest/.../AudienceRegressionTest.kt`, `fixtures/regression/**`, `.github/workflows/nightly.yml`
**Estimated scope:** M

### Task 20: Accessibility, performance, privacy and compatibility pass
**Spec task:** T10 · **Covers:** NFR-1, NFR-2, NFR-5, NFR-8, NFR-9
**Description:** Run Accessibility Scanner and Xcode Accessibility Inspector, add TalkBack/VoiceOver labels, check 48 dp / 44 pt targets, 4.5:1 contrast and 200% text; Macrobenchmark for first results; verify no coordinates in network traffic; run the CI matrix (Android API 26/30/34/36, iOS 16/17/18).
**Acceptance criteria:**
- [ ] First page of results ≤ 2 s p50 and ≤ 4 s p95 on 4G; all groups ≤ 6 s p95 with one active user
- [ ] No scanner failures on any screen; every control labelled
- [ ] A traffic capture shows no coordinates
- [ ] Matrix green
**Verification:**
- [ ] Macrobenchmark report; scanner exports; proxy log inspection; CI matrix run
**Dependencies:** Checkpoint: Feature complete
**Files likely touched:** many small fixes across UI files; `androidApp/benchmark/**`, `.github/workflows/ci.yml`
**Estimated scope:** L (expect to split by platform)

### Task 21: Release readiness
**Covers:** NFR-12, section 9 risks
**Description:** NLB rate-limit increase confirmed, key renewal date set before 6 Apr 2027, name decided, store listings, privacy statement, About/credits finalised.
**Acceptance criteria:**
- [ ] Written NLB reply on the rate limit for public release (or release limited to a pilot)
- [ ] Reminder set for key renewal before 6 Apr 2027
- [ ] Final name and bundle id chosen; store listings do not imply an official NLB app
**Verification:**
- [ ] Human sign-off against the definition of done in spec section 10
**Dependencies:** Tasks 18, 19, 20
**Files likely touched:** `docs/`, store assets, app config
**Estimated scope:** M

### Checkpoint: Release (after Tasks 18-21)
- [ ] Definition of done in spec section 10 holds
- [ ] Human approves release
