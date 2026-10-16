// Pure summarising helpers for probe-voice-search.mjs. No network, so they are unit-tested against fixtures/.

const TALL_CM = 28; // MARC 300$c: 28 cm or more is a "large" book on the shelf

/** One flat list of records from a SearchTitles body, each carrying its group's series title. */
export function flattenRecords(body) {
  return (body?.titles ?? []).flatMap((group) =>
    (group.records ?? []).map((record) => ({ ...record, seriesTitle: group.seriesTitle ?? [], groupTitle: group.title })),
  );
}

/** Height in cm from the physical description ("12 unnumbered pages : colour illustrations ; 29 cm"), or null. */
export function heightCm(record) {
  const match = (record.physicalDescription ?? []).join(' ').match(/(\d{1,2}) cm/);
  return match ? Number(match[1]) : null;
}

function searchableText(record) {
  return [record.groupTitle, record.title, ...(record.subjects ?? []), ...(record.summary ?? [])].join(' | ');
}

const count = (records, test) => records.filter(test).length;

/**
 * What one SearchTitles response says about a query. `topic` is a regex for the topic word (or null for a format-only probe),
 * so "on topic" means the topic appears in the title, subjects or summary of the record.
 */
export function summarize(body, topic = null) {
  const records = flattenRecords(body);
  const audienceFacet = (body?.facets ?? []).find((f) => f.id === 'intendedAudience')?.values ?? [];
  const facetCount = (id) => audienceFacet.find((v) => v.id === id)?.count ?? 0;
  const juvenile = facetCount('juvenile');
  const banded = ['preschool', 'primary', 'pre-adolescent', 'adolescent'].reduce((sum, id) => sum + facetCount(id), 0);
  return {
    totalRecords: body?.totalRecords ?? 0,
    records: records.length,
    onTopic: topic ? count(records, (r) => topic.test(searchableText(r))) : null,
    boardOrPictureSubject: count(records, (r) => (r.subjects ?? []).some((s) => /board books|picture books?/i.test(s))),
    juvenileSubject: count(records, (r) => (r.subjects ?? []).some((s) => /juvenile (fiction|literature|nonfiction)/i.test(s))),
    tall: count(records, (r) => (heightCm(r) ?? 0) >= TALL_CM),
    withHeight: count(records, (r) => heightCm(r) !== null),
    withSeries: count(records, (r) => (r.seriesTitle ?? []).length > 0),
    withAudienceField: count(records, (r) => (r.audience ?? []).length > 0),
    bandShare: juvenile > 0 ? banded / juvenile : null,
  };
}

const cell = (value) =>
  value === null || value === undefined
    ? '-'
    : typeof value === 'number' && !Number.isInteger(value)
      ? value.toFixed(2)
      : String(value).replaceAll('|', '/'); // a pipe in a name would split the markdown cell

/** A GitHub-markdown table, one row per probe. */
export function toMarkdownTable(rows) {
  const columns = ['name', 'status', 'totalRecords', 'records', 'onTopic', 'boardOrPictureSubject', 'juvenileSubject', 'tall', 'withHeight', 'withSeries', 'withAudienceField', 'bandShare'];
  const lines = [`| ${columns.join(' | ')} |`, `| ${columns.map(() => '---').join(' | ')} |`];
  for (const row of rows) lines.push(`| ${columns.map((c) => cell(row[c])).join(' | ')} |`);
  return lines.join('\n') + '\n';
}
