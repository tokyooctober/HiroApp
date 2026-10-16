# Voice search for a library app: what the data said (notes for a LinkedIn write-up)

Working notes, Oct 2026. Everything below comes from the files in [`experiments/`](../experiments/README.md) and the recorded
responses in [`fixtures/`](../fixtures/README.md); the numbers can be re-checked there. This is a hobby/non-commercial project
using the National Library Board (NLB) Singapore's open web services. It is not an NLB product and NLB has not endorsed it.

## The one-line story
I wanted parents to *say* "colourful, large children's books for a young reader" and get library results. Before writing any
speech code I spent a day testing whether the library's catalogue could answer such requests at all. It can answer some of them
very well and others not at all, and two of my own early conclusions turned out to be wrong.

## The question
A parent does not search like a librarian. They say things like:
- "large colourful books for a young reader"
- "something about penguins / tigers / rockets / scientists to grow my child's interest"
- "popular ones, maybe a series"

Does the catalogue API have a field, filter or word for each of those? And would asking Amazon first, then mapping its results
back to the library, work better?

## Method (small on purpose)
1. Read the 107 recorded books already in the repo (all "dinosaur") for any format, size, series or popularity signal.
2. Run 25 live `SearchTitles` calls: 4 topics x {plain, "picture book", "easy reader", "series", `Fiction=true`} plus 5 format-only
   phrases ("board book", "big book", "oversized", "lift the flap", "large format"). 4 of the 25 failed (see below).
3. Run 14 more: topic + format words, "juvenile fiction / literature", `Fiction` variants, filler words ("colourful", "popular").
   2 of the 14 failed.
4. Read the first results of 5 parent-style searches on amazon.sg once by hand, then look up each ISBN in NLB (40 exact lookups).
5. Decide in advance what would count as "works" (a pass table was written before the first run).

Total: 39 catalogue searches + 40 ISBN lookups, paced inside the library's limit of 15 calls a minute.

## Findings you can quote

**1. Topics work, almost perfectly.** Plain topic searches returned results that were on topic 97 of 98 times
(penguin 23/23, tiger 21/22, rocket 25/25, scientist 28/28).

**2. "Board book" and "picture book" are real catalogue words and they steer results.** Next to a topic, "board book" gave
57-59% board/picture-book subject headings (tiger 16/28, penguin 20/34) while staying on topic (26/28, 31/34).
Adding "picture book" lifted the picture-book share, e.g. penguin 17% -> 59%.

**3. There is no "large" or "colourful" field.** Size exists only as free text in the physical description ("29 cm"); I could read
a height on 85 of 107 books and 28 of them were 28 cm or taller. "Colour illustrations" appears on 60 of 107, so it separates
nothing. Size can be checked on the results after they come back, but cannot be asked of the API.

**4. My first conclusion about "big book" was wrong.** On its own, "big book" returned mostly large books (15 of 22 with a known
height were 28 cm+, versus about 23% for ordinary topic searches), so I marked it a pass. The next probe showed why: it matches
the *words in titles* ("The Usborne big book of..."). Next to a topic it stopped working: "tiger big book" found "Big tiger" and
"Little tiger, big tiger"; only 2 of 21 penguin and 7 of 23 tiger results were actually large.

**5. The documented `Fiction` filter does not exist.** The API's own definition lists it, but `Fiction=true` and `=false` return
HTTP 501 Not Implemented and `Fiction=Y` returns 400. The workaround is in the data: appending "juvenile fiction" to a tiger search
returned fiction on 29 of 29 results, "juvenile literature" returned non-fiction on 25 of 25 (the plain search was a mix).

**6. "Popular" has no support at all.** No loan counts; the reservations field was 0 on all 107 books; no sort option was
confirmed. A search for "popular penguin books" fell apart: 9 of 144 results were about penguins. (I did not isolate whether
"popular" or "books" did the damage, so the parser will drop both.)

**7. Filler words and publisher names add noise.** "Penguin" also matched the publisher Penguin Young Readers (21 of 60
off-topic results in "penguin easy reader"). Keyword search reads publisher and series text, not just subjects.

**8. Asking Amazon first does not help.** I read the top results of 5 parent-style searches on amazon.sg.
62 results; 40 had an ISBN (the rest were Kindle or self-published colouring books); NLB held 22 of those 40, so
22 of 62 results (35%) led to a book the library has. Amazon understood *format* well ("board book" gave only board
books; "big oversized" gave real big books) but it ranks bestsellers over topic: 1 of 14 "board books penguins" results was about
penguins. Two of the five searches yielded nothing checkable. The route also costs one NLB call per ISBN (the rate limit is
about 15 a minute), and scraping Amazon is against its terms; its official product API needs an approved Associates account. The
only thing Amazon has that the library lacks is popularity, and a small hand-curated list could supply that without live scraping.

## What I got wrong along the way (good for an honest post)
- Marked "big book" a pass after one probe; a second probe overturned it.
- Said "20 of 25 searches returned data and 5 were `Fiction=true`". The truth: 21 returned data, 4 were `Fiction=true`.
- The first results table had shifted column headings because a `|` in a query name split the markdown cell; found and fixed with a
  test before drawing conclusions from it.
- Assumed the spec was right: the project's own requirements document planned fiction/non-fiction filters on a parameter that
  does not work. It is now corrected with the evidence.

## Lessons
- Probe the data before building the feature. A day of read-only experiments replaced weeks of guessing about what voice could do.
- Write the pass/fail criteria before looking at results; it kept me from talking myself into "big book".
- A second, cheaper experiment is often the one that corrects the first.
- "Natural language" in the product sense (what the parent says) and "NLP API" in the technical sense (a service that understands
  it) are different things: the library has no language-understanding service. The understanding has to live in our own parser.
- Guardrails belong in code: the scripts refuse to write a response that contains a credential, and read keys only from the
  environment.

## Honest limits
- One shelf (children's books, `IntendedAudiences=juvenile`), 4 topics, English keywords, one day. Live data changes.
- "On topic" was judged by a keyword in title, subjects or summary, not by reading the books.
- The Amazon check used the first ~12 non-sponsored results of 5 searches on one storefront (amazon.sg), and matched by exact
  edition (ISBN), so "NLB holds" is a lower bound.
- Adult-shelf searches, toddler (0-3) and independent-reader (9-12) personas were not tested in depth.

## Draft post (about 200 words; edit freely)
> I wanted parents to *say* "big, colourful books about penguins for my 5-year-old" and get library results. Before writing any
> speech code, I tested whether the catalogue could answer that at all.
>
> 39 searches and 40 lookups later:
> - Topics work: 97 of 98 results on topic.
> - "Board book" and "picture book" are real catalogue words and steer results.
> - There is no "large" or "colourful" field. Size is only a number in a free-text line, so it can be checked after the search, not asked of it.
> - The filter the docs promised (`Fiction`) returns HTTP 501. The data had a workaround hiding in the subject headings: 29 of 29 fiction.
> - "Popular" isn't in the data at all.
> - I also tried asking Amazon first and mapping back. Only 35% of its top results exist in the library, and it ranks bestsellers over topic.
>
> I also got two things wrong on the way, and a second cheaper experiment caught both.
>
> Takeaway: probe the data before you build the feature, and write down what "works" means before you look.
>
> #productmanagement #voiceui #opendata #libraries #buildinpublic

Hook alternatives:
- "The API documented a filter that returns HTTP 501."
- "I was 68% sure 'big book' worked. Then I added a topic."
- "I asked Amazon to help search a library. Here is why it didn't."

## Before posting
- Say it is a personal, non-commercial project using NLB's open data; credit NLB; do not imply endorsement.
- Do not publish API keys, app codes or screenshots showing them.
- Check NLB's current terms for sharing numbers derived from the data, and keep the Amazon section factual and brief.
