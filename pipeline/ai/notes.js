'use strict';
// Structured notes for each briefing item, in the Rocket notes form (the Rocket Prep app's topic pages):
// numbered "# N. Heading" blocks, "- **Label:** value" keyword lines with the exam words in bold,
// "  - sub-point" lines, comparison tables, "- ⚠ **Trap:** ..." lines and one "> Exam angle: ..." line.
// Each item is also tagged with the APPSC Current Affairs blueprint angles it answers (the recurring
// question angles of the blueprint analysis). The notes stage adds no web search: it rewrites the
// item's own verified facts, with only well-established static background marked [GK].

const BLUEPRINT = [
  'FIRST', 'Largest / Smallest / Highest', 'Committee', 'Commission', 'Report', 'Index / Ranking', 'Launched', 'Appointed',
  'Inaugurated', 'Celebrated / Important day', 'Developed by', 'Elected', 'Visited', 'Declared', 'Ministry', 'Minister',
  'Project', 'Satellite', 'Rocket', 'Mission', 'Missile', 'Defence', 'Defence exercise', 'DRDO', 'ISRO', 'GI tag', 'App / Portal / Platform',
  'Scheme / Yojana', 'Objective (scheme)', 'Programme / Initiative / Campaign', 'Policy', 'Energy', 'Book / Author', 'Festival',
  'Vaccine / Disease / Virus', 'UN / International organisation', 'Headquarters', 'Bank / RBI / NBFC', 'Summit / Conference',
  'Agreement / Deal / MoU', 'Amendment / Bill / Act', 'Judgement (Supreme Court / High Court)', 'Award / Prize', 'Sports',
  'Trophy / Championship', 'Anniversary', 'Date and year', 'Places in news', 'Persons in news (designation)', 'New species / discovery',
  'Tiger reserve / National park / Protected area', 'Biosphere reserve / Ramsar site', 'Projects and dams', 'New terms / Abbreviations',
  'Institution / University / IIT / AIIMS', 'UNESCO', 'WHO', 'Projections on growth rate', 'Cyclones / Floods in news', 'Tribes in news',
  'Temples in news', 'Operation', 'Slogan / Theme', 'Chief guest', 'Renaming', 'Elections (constitutional)',
];

const FORMAT = `ROCKET NOTES FORM (write "notes" exactly in this small markdown form; nothing else):
# 1. <short heading>
- **<Label>:** <value, exam words in **bold**>
  - <sub-point when needed>
| <Basis> | <Column> | <Column> |
|---|---|---|
| <row> | <value> | <value> |
- ⚠ **Trap:** <the confusion an MCQ would exploit, e.g. edition vs first, MoU vs completed project, proposed vs existing>
> Exam angle: <one line: how APPSC asks this, naming the blueprint angle>

RULES:
- 2 to 4 numbered headings, short (e.g. "# 1. What happened", "# 2. Key facts", "# 3. Background", "# 4. Andhra Pradesh link").
- Keyword style, not sentences: every line starts with a bold "**Label:**" (Who, What, Where, When, Organisation, Headquarters, Edition, Theme, Objective, Ministry, Amount, Status, Winner, Rank...).
- Put every name, number, date, place, organisation, edition, rank and amount of the facts in **bold**. Keep ALL of the item's facts and figures; drop none.
- Use a table when the facts compare or list 3+ parallel things (participants, winners, rankings, provisions, old vs new).
- Add a "⚠ **Trap:**" line only when there is a real exam confusion; at most 2.
- Background: you MAY add 1-3 well-established static facts (an organisation's headquarters or founding year, a constitutional article, the previous edition) that are certainly true; end each such line with " [GK]". Never add new current events, figures or claims.
- Status words exactly as the item states them: an MoU stays an MoU, proposed jobs stay proposed.
- End with exactly one "> Exam angle:" line.

ANSWER FIELDS APPSC ASKS (from the APPSC current-affairs PYQs; include each that applies, as a bold Label line):
- International organisation / summit: **Headquarters**, **Head / Chair**, **Members**, **Founded** (static ones may be [GK]); summit: **Edition**, **Host city**, **Theme**, **Next host**.
- Report / index / ranking: **Released by** (organisation), **Edition**, **India's rank** (and previous rank if in the facts), **Top country**, **AP's rank** if given.
- Appointment / oath: **Post**, **Appointed by** (President, Governor, Collegium...), **Constitutional / legal basis** (Article [GK]), **Predecessor** if in the facts, **Tenure**.
- Award / prize: **Award**, **Field**, **Conferred by**, **Winner(s)**, **Edition / year**, **First given** [GK] when certain.
- Defence exercise: **Type** (bilateral / multilateral; Army / Navy / Air Force), **Countries**, **Edition**, **Venue**.
- Scheme / mission: **Ministry**, **Launched**, **Objective**, **Beneficiaries**, **Funding / amount**.
- Important day: **Date**, **Theme** of the year, **Observed by / declared by**, **First observed** [GK] when certain.
- Sports: **Event**, **Edition**, **Host / venue**, **Winner**, **Runner-up**, **India's medals**.
- Places / species / protected area: **State / district**, **Status**, **River / range** nearby.
Never guess a field: when a field is not in the facts and is not a certain static fact, leave it out.`;

function notesTask(date, items) {
  const list = items.map((it, n) => `${n + 1}. ${it.headline}
   category: ${it.category} | status: ${it.status} | date: ${it.date}
   facts: ${(it.facts || []).join(' / ')}
   summary: ${it.summary}
   prelims: ${it.prelimsTakeaway}
   AP link: ${it.apLink}
   static link: ${it.staticLink}`).join('\n');
  return `Write APPSC exam notes for these ${items.length} verified current-affairs items of ${date}.

${FORMAT}

BLUEPRINT ANGLES (APPSC Current Affairs blueprint analysis — recurring question angles). For each item list the 1-3 MOST SPECIFIC angles it answers (the angle a PYQ would use; 'Date and year' or 'Places in news' only when the date or place itself is the point), using these names exactly: ${BLUEPRINT.join('; ')}.

ITEMS:
${list}

Return {"notes": [{"item": <number>, "blueprint": [...], "notes": "<markdown>"}, ...]} with one entry per item, in order.`;
}

const NOTES_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['notes'],
  properties: {
    notes: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['item', 'blueprint', 'notes'],
        properties: { item: { type: 'integer' }, blueprint: { type: 'array', items: { type: 'string' } }, notes: { type: 'string' } },
      },
    },
  },
};

const NUM = /\d[\d,.]*\d|\d/g;
const norm = (s) => String(s).replace(/,/g, '');

/** The numbers of the facts that the notes leave out (Rocket's topic_check: every figure must be on the page). */
function missingFigures(item, notes) {
  const have = new Set((norm(notes).match(NUM) || []));
  const out = [];
  for (const f of item.facts || []) {
    const miss = (norm(f).match(NUM) || []).filter((n) => !have.has(n));
    if (miss.length) out.push(f);
  }
  return out;
}

/** Cleans the model's notes; a fact whose figures are missing is appended so no fact is ever lost. */
function cleanNotes(item, raw) {
  let md = String(raw || '').replace(/\r/g, '').replace(/^```(?:markdown|md)?\s*|```\s*$/g, '').trim();
  const lines = md.split('\n').map((l) => l.replace(/\s+$/, ''));
  md = lines.filter((l, i) => l.trim() || (i > 0 && lines[i - 1].trim())).join('\n').trim();
  if (!/^# \d+\./m.test(md)) return '';
  const miss = missingFigures(item, md);
  if (miss.length) {
    const n = (md.match(/^# \d+\./gm) || []).length + 1;
    const extra = `# ${n}. Also note\n` + miss.map((f) => `- ${f}`).join('\n');
    const at = md.search(/^> /m);
    md = at >= 0 ? `${md.slice(0, at).trimEnd()}\n${extra}\n${md.slice(at)}` : `${md}\n${extra}`;
  }
  return md;
}

/** Adds notes + blueprint to every item of every section, a section per call. Returns the number of items noted. */
async function addNotes(b, respond, log = () => {}) {
  let done = 0;
  for (const key of ['andhraPradesh', 'national', 'international']) {
    const items = b.sections[key] || [];
    if (!items.length) continue;
    let r;
    try {
      r = await respond({ instructions: 'You are an APPSC Group 1 and Group 2 notes editor. Return valid JSON only.', input: notesTask(b.date, items), schema: NOTES_SCHEMA, name: `appsc_notes_${key}`, search: false });
    } catch (e) {
      log(`${b.date}: notes for ${key} failed: ${e.message}`);
      continue;
    }
    for (const n of r.data.notes || []) {
      const it = items[n.item - 1];
      if (!it) continue;
      const md = cleanNotes(it, n.notes);
      if (!md) continue;
      it.notes = md;
      it.blueprint = [...new Set((n.blueprint || []).map(String).filter((x) => BLUEPRINT.includes(x)))].slice(0, 4);
      done++;
    }
    log(`${b.date}: notes ${key}: ${items.filter((i) => i.notes).length}/${items.length}`);
  }
  b.notesVersion = 1;
  return done;
}

module.exports = { BLUEPRINT, FORMAT, notesTask, NOTES_SCHEMA, missingFigures, cleanNotes, addNotes };
