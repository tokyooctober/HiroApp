# Recorded NLB responses (test fixtures)

Real responses from the National Library Board's Open Web Services, recorded on 7 Oct 2026 for Hiro-Kids tests:
the shapes of the data, the audience filter, result grouping and the library picker are tested against them.

- **Source and credit:** the National Library Board, Singapore, via NLB Open Web Services
  (<https://www.nlb.gov.sg/main/partner-us/contribute-and-create-with-us/NLBLabs>). The data belongs to NLB.
  Hiro-Kids is not an official NLB app and is for non-commercial use, as NLB's terms require.
- **What they hold:** catalogue searches for "dinosaur" (children's and adult), title details and copy availability,
  eBook and audiobook searches, and the list of branches (which includes the libraries' public contact details).
- **What they do not hold:** no API key, no app code, no account, library card or personal data. `manifest.json` lists
  the request behind each file (parameters only, never headers).
- **Re-creating them:** `cd proxy && npm run record-fixtures`, with `NLB_API_KEY` and `NLB_APP_CODE` in the environment.
  Live data changes, so a fresh recording will differ from these files.
- **If NLB asks for them to be removed**, delete this folder and re-record privately; tests that need them are listed in
  `tasks/todo.md` (Tasks 6, 7, 9 and 19).
