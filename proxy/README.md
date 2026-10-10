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

## Run it on your own machine (container)

This is the first step before any hosting decision. The container is the same image a host would run later.

**Once:** install Docker Desktop (Windows: the WSL 2 backend it offers) and check `docker version` shows a Server section.

**Start it.** Credentials come from the environment of the terminal you start it in; nothing is written to a file.

PowerShell (Windows):

```
$env:NLB_API_KEY = Read-Host "NLB API key"
$env:NLB_APP_CODE = Read-Host "NLB app code"
docker compose up --build -d
```

bash (Mac, Linux, Git Bash):

```
export NLB_API_KEY=...  NLB_APP_CODE=...
docker compose up --build -d
```

Run it from the repository root. The proxy is then on `http://localhost:8080`, **on this machine only** (see
`PROXY_BIND` below). The variables last as long as that terminal window: set them again in a new window.

**Check it works** (use `curl.exe` in PowerShell, because plain `curl` there is something else):

```
curl.exe "http://localhost:8080/library/GetBranches?ListType=active&LibraryTypes=PL"   the library list (JSON)
curl.exe -i http://localhost:8080/admin                                                 404
docker compose ps                                                                       one proxy, "healthy"
docker compose logs -f proxy                                                            path and status per request
```

Stop it with `docker compose down`. Plain Docker, without compose:

```
docker build -t hiro-kids-proxy ./proxy
docker run --rm -p 127.0.0.1:8080:8080 -e NLB_API_KEY -e NLB_APP_CODE hiro-kids-proxy
```

Without a container: `cd proxy && npm run dev` (Node 22) and `npm test`.

### Pointing the apps at it

| App | How |
|---|---|
| Android emulator | Nothing to do: debug builds use `http://10.0.2.2:8080`, which is this machine as the emulator sees it. |
| Android phone on USB | `adb reverse tcp:8080 tcp:8080`, then build with `-PproxyBaseUrl=http://localhost:8080`. |
| iOS simulator (Mac only) | Nothing to do: the default is `http://localhost:8080`. |
| A phone on Wi-Fi, no cable | Not supported while it is a debug build: plain HTTP is allowed only to `localhost` and `10.0.2.2`. Use a tunnel (below) when you are ready. |

Do not set `PROXY_BIND=0.0.0.0` to reach it over Wi-Fi unless the network is yours: until app attestation exists
(Task 18) anyone who can reach the port can spend the NLB quota.

### Updating and rolling back

```
git pull
docker compose up --build -d      # rebuilds the image and replaces the running container
```

Check with `docker compose ps` that exactly one proxy is running. The queue and the cache are in memory, so an update
empties the cache and restarts the rate-limit window: the first requests after it are slower, and the next
`GetBranches` call is made again.

To go back, check out the previous commit (`git checkout <commit>`, or `git switch -` after a `git pull`) and run the
same `docker compose up --build -d`. The rebuilt image behaves like the old one, but Docker gives it a new image ID.

## Later: reaching it from outside this machine (Cloudflare Tunnel)

Not needed for the steps above. A tunnel publishes your local proxy on a public HTTPS address without opening a port
on your router.

1. In the Cloudflare Zero Trust dashboard create a tunnel and copy its token.
2. Add a public hostname for it that points to the service `http://proxy:8080`.
3. Set `TUNNEL_TOKEN` as above and run `docker compose --profile tunnel up --build -d`.
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
