# Voice-search experiments (Oct 2026)

Raw output of three small experiments that asked: can what a parent says aloud become an NLB search? Write-up: [`docs/voice-search-experiments.md`](../docs/voice-search-experiments.md). Planned parser: [`docs/voice-search-parser.md`](../docs/voice-search-parser.md).

| Folder | Script | What it holds |
| --- | --- | --- |
| `out/` | `proxy/scripts/probe-voice-search.mjs` | 25 `SearchTitles` calls: 4 topics (penguin, tiger, rocket, scientist) with and without format words, plus 5 format-only probes. `summary.md` / `summary.json` are the tables. |
| `out/combos/` | `proxy/scripts/probe-voice-search.mjs combos` | 14 calls: topic + format words, "juvenile fiction / literature", `Fiction` parameter variants, filler words. |
| `out/amazon/` | `proxy/scripts/probe-amazon-isbns.mjs` | `amazon-top.json`: ISBN-shaped ASINs from the first results of five amazon.sg searches (read by hand in a browser, once). `nlb-holdings.json`: whether NLB holds each, by exact `GetTitles?ISBN=`. |

**Source and credit:** catalogue data is from the National Library Board, Singapore, via NLB Open Web Services
(<https://www.nlb.gov.sg/main/partner-us/contribute-and-create-with-us/NLBLabs>). The data belongs to NLB. Hiro-Kids is not an
official NLB app and is for non-commercial use, as NLB's terms require. If NLB asks for it to be removed, delete this folder; the
scripts re-create it.

**What it does not hold:** no API key, no app code, no account or personal data. The scripts read credentials from the environment
only and refuse to write a response that contains them (`assertNoSecrets`).

**Re-running:** `cd proxy`, set `NLB_API_KEY` and `NLB_APP_CODE` in the shell, then `node scripts/probe-voice-search.mjs [combos]`
or `node scripts/probe-amazon-isbns.mjs`. Live data changes, so a new run will differ. Calls are paced at one every 4.2 s.
The Amazon list is not re-created by a script; it was read by hand and is a snapshot.
