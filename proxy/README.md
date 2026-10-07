# Hiro-Kids API proxy

A thin proxy between the apps and the NLB Open Web Services. The apps never hold the NLB key (spec NFR-4); the proxy
adds it, queues calls to stay inside NLB's limit (1 call/s, 15 calls/min for the whole key), and caches repeats.

- Only the 8 GET endpoints in spec section 2 are reachable (`/catalogue/...`, `/library/GetBranches`, `/eresource/...`,
  `/recommendation/...`). Everything else is 404.
- A call that would wait more than 10 s in the queue gets `429` with `Retry-After`. NLB's own 429 and 5xx pass through.
- Cached: `SearchTitles` 10 min, `GetAvailabilityInfo` (catalogue and eResource) 5 min, `GetBranches` 7 days.
- Logs hold the path and status only: no query string, no coordinates (NFR-5).
- **Run exactly one instance.** The queue and the cache are in memory, so two instances would each spend the key's
  limit and NLB would start returning 429.

## Run it

Credentials come from the environment only. Never write them into a file in this repo.

```
cd proxy
export NLB_API_KEY=...  NLB_APP_CODE=...
npm run dev           # http://localhost:8080
npm test
```

### In a container

```
export NLB_API_KEY=...  NLB_APP_CODE=...
docker compose up --build          # from the repo root: http://localhost:8080
```

or plain Docker:

```
docker build -t hiro-kids-proxy ./proxy
docker run --rm -p 8080:8080 -e NLB_API_KEY -e NLB_APP_CODE hiro-kids-proxy
```

Smoke test: `curl "http://localhost:8080/library/GetBranches?ListType=active&LibraryTypes=PL"` returns the library
list, and `curl -i http://localhost:8080/admin` returns 404.

### Reaching it from a phone (Cloudflare Tunnel)

A tunnel publishes your local proxy on a public HTTPS address without opening a port on your router.

1. In the Cloudflare Zero Trust dashboard create a tunnel and copy its token.
2. Add a public hostname for it that points to the service `http://proxy:8080`.
3. `export TUNNEL_TOKEN=...` and run `docker compose --profile tunnel up --build`.
4. Build the apps with that address: Android `-PproxyBaseUrl=https://your-hostname`, iOS `PROXY_BASE_URL`.

Until app attestation exists (Task 18), anyone who learns the address can spend your NLB quota. Keep it private.

## Hosting options (decision pending)

The same image runs anywhere that runs a container with one instance.

| Host | Notes |
|---|---|
| Your own machine + Cloudflare Tunnel | Free. The machine must stay on. |
| Google Cloud Run | `gcloud run deploy hiro-kids-proxy --source proxy --region asia-southeast1 --max-instances 1 --min-instances 0 --set-secrets NLB_API_KEY=nlb-api-key:latest,NLB_APP_CODE=nlb-app-code:latest`. Needs a project with billing. |
| A small VM (for example Oracle Always Free) | `docker run -d --restart unless-stopped -p 8080:8080 -e NLB_API_KEY -e NLB_APP_CODE hiro-kids-proxy`, with a tunnel or reverse proxy in front. |

Rollback on any host: redeploy the previous image tag.
