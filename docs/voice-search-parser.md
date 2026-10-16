# Rule-based voice query parser (design, not built yet)

Why this shape: the library (NLB) has no language-understanding service, so the understanding lives in the app. The experiments in
[`voice-search-experiments.md`](voice-search-experiments.md) showed which spoken ideas the catalogue can answer and which it cannot,
so the parser's job is to sort every spoken word into one of those buckets, send what works, and say plainly what it left out.

## Pipeline
```
speech -> text (Android SpeechRecognizer) -> VoiceQueryParser -> ParsedQuery -> "How I understood you" chips -> SearchQuery -> existing search
```
The parser is pure common Kotlin (like `AudiencePolicy`): no microphone, no network, unit-tested table-driven. It works on typed text
too, so we can build and test everything before touching the microphone.

## What it outputs
`ParsedQuery(keywords, formatWords, fictionHint, minHeightCm, language, availableOnly, ignored, unsupported)`

| Field | Becomes | Evidence |
| --- | --- | --- |
| topic words | `Keywords` | Strong: 97 of 98 topic results on topic |
| format words | appended to `Keywords` | "board book", "picture book" worked next to a topic; "lift the flap" did not (penguin: 4 of 32 on topic) |
| fiction hint | " juvenile fiction" / " juvenile literature" appended | Strong for tiger: 29 of 29 and 25 of 25 |
| size words | on-page check `height >= 28 cm` (not sent to NLB) | No API field; heights readable on ~80% of records |
| language | `Languages` (existing chips) | Already built |
| "available / at my library" | `Availability=true` + current branch (existing) | Already built |
| ignored | dropped silently | filler and generic words |
| unsupported | shown greyed, with a reason | popular, series, colourful, new |

Always sent: `IntendedAudiences=juvenile` in Children mode, `MaterialTypes=bks`.

## The word buckets (starting vocabulary, grows from real use)
- **Filler (ignore):** uh, um, I'm looking for, I want, I'd like to know, show me, find me, recommend (me) (some), can you, please, some, good, nice, books/book when alone.
- **Format (append):** board book(s), picture book(s). "Big book" only when said literally as "big book".
- **Size (page check, not sent):** big, large, huge, giant, oversized, jumbo. Colourful words are not size words.
- **Fiction:** story, stories, storybook, tale, adventure -> "juvenile fiction". **Non-fiction:** facts, true, real, learn about, how things work, information -> "juvenile literature".
- **Age phrases:** toddler / baby / 0-3 -> add "board book"; preschool / 4-6 / "4 year old" -> add "picture book"; young reader / early reader / 6-8 / "7 year old" -> add "easy reader" (see risk below); 9-12 / chapter book -> no word (untested).
- **Language:** English, Chinese, Malay, Tamil -> `Languages` (existing).
- **Unsupported (tell the user):** popular, bestselling, famous, most borrowed, trending, new/latest, series ("series are shown on cards, we can't search by them"), colourful/colorful/bright ("almost all picture books are illustrated").
- **Everything else:** topic. Adjacent topic words stay together ("space rockets").

## Worked examples
Chips shown to the user in brackets; [~] is a greyed "not supported" chip.

| # | Spoken | Sent to NLB | Page check | Chips | Evidence |
| --- | --- | --- | --- | --- | --- |
| 1 | "Uh, I'm looking for children's books about penguins" | `penguins` | none | [Topic: penguins] | strong |
| 2 | "Board books about tigers for my toddler" | `tiger board book` | none | [tigers] [board books] [toddler] | 26 of 28 on topic; 16 of 28 board/picture subject |
| 3 | "Stories about tigers" | `tiger juvenile fiction` | none | [tigers] [stories] | 29 of 29 fiction |
| 4 | "Facts about tigers" | `tiger juvenile literature` | none | [tigers] [facts] | 25 of 25 non-fiction |
| 5 | "Large colourful picture books about rockets" | `rocket picture book` | height >= 28 cm | [rockets] [picture books] [large >= 28 cm] [~ colourful] | topic 33 of 33; format share only 21% |
| 6 | "Easy readers about scientists for a 7 year old" | `scientist easy reader` | none | [scientists] [easy readers] [age 6-8] | 31 of 35 on topic |
| 7 | "Big book about space" | `space big book` | height >= 28 cm | [space] [big book] [large >= 28 cm] | about half were 28 cm+ |
| 8 | "Chinese picture books about dragons available at Tampines" | `dragon picture book`, `Languages=chi`, `Availability=true`, `Locations=trl` | none | [dragons] [picture books] [Chinese] [available at Tampines] | language and library parts already built |
| 9 | "Popular series about penguins" | `penguin` | none | [penguins] [~ popular] [~ series] | "series" word barely changes results; popularity absent |
| 10 | "Lift the flap books about penguins" | `penguin` (topic first) | prefer flap titles later | [penguins] [~ lift-the-flap not searchable with a topic] | the phrase swamped the topic |
| 11 | "Something to make my kid love reading" | nothing sent | none | no topic found: "Tell me a subject, like animals or space" | avoids searching "something" |
| 12 | "Books like the Gruffalo" | `gruffalo` (title search) | none | [gruffalo] [~ "like this" not supported yet] | the recommendation endpoint cannot take free text |
| 13 | "Recommend me some books" | nothing sent | none | no topic found | same as 11 |
| 14 | "Colourful big book tiger" | `tiger big book` | height >= 28 cm | [tigers] [big book] [large] [~ colourful] | 21 of 26 on topic with the stray word |

## Rules that came straight from the failures
1. **Strip generic words before sending.** "popular penguin books" returned 9 of 144 on topic; we don't know whether "popular" or "books" did it, so both are dropped.
2. **Never send a format phrase without a topic unless the user said only the format.** "Board book" alone returned 11,115 matches; with a topic it narrows.
3. **Size is checked, not asked.** "Big/large" never becomes a keyword (except the literal phrase "big book"); results with a known height under 28 cm are hidden, results with no height stay and are marked "size unknown".
4. **Size filtering needs more pages.** One page is 20 books, so the repository keeps paging (up to 3) until enough pass.
5. **Publisher names are topic noise.** "penguin" also matches Penguin Young Readers. Show the matched subject on each card so a parent can see why it appeared; no fix beyond that yet.
6. **Always show the sentence it heard.** Editable text plus removable chips, so a speech-recognition slip ("pen wins") is one tap to fix.

## Known risks
- **"Easy reader" can pull in a publisher series** instead of the topic (penguin: 16 of 76 on topic; tiger, rocket, scientist 74-89%). Test per topic before relying on age phrases.
- **Age mapping is a guess.** Only "board book" and "picture book" are backed by the data; "easy reader" is mixed; 9-12 has no word. The finer age bands in the data (`preschool`, `primary`) cover only ~18-71% of books, depending on the topic.
- **Accents and mixed languages.** Singapore English, Mandarin and Malay speech are not covered in v1; language words choose the *book* language, not the speech language.
- **Privacy.** Android's recogniser may send audio to a server. Prefer the on-device recogniser where the phone has one, and say so in settings (to be checked per device).
- **Vocabulary is finite.** Unknown words become topic words, which is the safe default (a topic search is what the catalogue is best at).

## Build order (each step shippable and testable)
1. `VoiceQueryParser` + golden-file unit tests (the 14 rows above become the first tests). No UI.
2. Extend `SearchQuery.Keywords` with the format words and size check; repository post-filter and paging. Tests with `fixtures/`.
3. A text box that accepts a spoken-style sentence and shows the chips (works on desktop tests and Compose UI tests, no microphone).
4. Microphone button: `VoiceInput` interface in `domain/` (like `LocationProvider`), `SpeechRecognizer` on Android, stub on iOS, `RECORD_AUDIO` permission.
5. Re-run `probe-voice-search.mjs` with the parser's output for a fresh set of topics, to measure on-topic rate before release.

## Not in scope here
Popularity (needs a curated list or another source), "more like this" (the recommendation endpoint cannot take free text), adult mode
(fiction words untested), and any live Amazon lookup (poor topic match, one NLB call per ISBN, and against Amazon's terms).
