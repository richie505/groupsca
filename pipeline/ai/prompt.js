'use strict';
// The APPSC daily briefing: prompts and the JSON schema the model must return.
// Research runs in stages (International, National, Andhra Pradesh, each with live
// web search), then one editorial stage (coverage audit, MCQs, Mains questions)
// over the verified items. No aptitude, reasoning or Data Interpretation, ever.

const STATUSES = [
  'ANNOUNCED', 'PROPOSED', 'APPROVED', 'MOU_SIGNED', 'FOUNDATION_LAID', 'UNDER_CONSTRUCTION',
  'LAUNCHED', 'OPERATIONAL', 'COMPLETED', 'FORECAST', 'ONGOING', 'FINAL_RESULT',
];

const SUBJECTS = [
  'Indian History & Culture', 'AP History & Culture', 'Indian Geography', 'AP Geography', 'Constitution', 'Polity',
  'Indian Society', 'Social Justice', 'Indian Economy', 'AP Economy', 'Science & Technology', 'Health',
  'Environment & Ecology', 'International Relations', 'Governance', 'Public Administration', 'Ethics',
  'Disaster Management', 'Essay (contemporary issues)',
];

const CATEGORIES = [
  'Awards & Prizes', 'Sports & Games', 'Appointments', 'Persons in News', 'Apps / Portals / Platforms',
  'Schemes / Missions / Campaigns', 'Reports / Indices / Rankings', 'Summits / Organisations / Agreements', 'Defence',
  'Space', 'Science & Technology', 'Health', 'Environment', 'Places', 'Culture', 'Tribes', 'GI Tags', 'UNESCO', 'Polity',
  'Law', 'Court Judgments', 'Economy', 'Banking', 'Committees', 'Commissions', 'Books & Authors', 'Important Days',
  'FIRSTs', 'Largest / Highest / Longest', 'New Terms / Abbreviations', 'AP Projects', 'AP Investments',
];

const PRIMARY_DOMAINS = [
  'pib.gov.in', 'ap.gov.in', 'apegazette.cgg.gov.in', 'rbi.org.in', 'isro.gov.in', 'imd.gov.in', 'mea.gov.in',
  'commerce.gov.in', 'mha.gov.in', 'mospi.gov.in', 'sci.gov.in', 'aphc.gov.in', 'eci.gov.in', 'niti.gov.in',
  'who.int', 'un.org', 'worldbank.org', 'imf.org', 'unesco.org', 'olympics.com', 'gov.in', 'nic.in',
];

const SECTIONS = {
  international: {
    label: 'INTERNATIONAL',
    focus: 'international developments relevant to India and to APPSC Group 1 and Group 2: international relations, ' +
      'organisations, summits, agreements, reports and indices, science, space, environment, health, awards, sports, ' +
      'persons in news, important days. Every item needs an AP link or "No direct AP-specific link established."',
    min: 4,
    max: 10,
  },
  national: {
    label: 'NATIONAL',
    focus: 'national Indian developments: Union government schemes, Cabinet decisions, Bills and Acts, Supreme Court ' +
      'judgments, RBI and economy data, reports, defence, space (ISRO), science, health, environment (tiger reserves, ' +
      'Ramsar, species), culture, GI tags, appointments, awards, sports, important days. Every item needs an AP link ' +
      'or "No direct AP-specific link established."',
    min: 6,
    max: 14,
  },
  andhraPradesh: {
    label: 'ANDHRA PRADESH',
    focus: 'Andhra Pradesh state AND district developments: state government orders and policies, Cabinet decisions, ' +
      'schemes, projects (Polavaram, Amaravati, ports, irrigation), investments and MoUs (with district, company, ' +
      'amount, jobs and stage), High Court of AP judgments, agriculture, infrastructure, culture and heritage, ' +
      'environment, governance, tribes, GI tags, AP persons in news and AP sports. Give Andhra Pradesh extra emphasis.',
    min: 6,
    max: 14,
  },
};

// What the APPSC current-affairs PYQs actually ask (2,945 APPSC CA questions, 2017-2025): appointments
// 6%, awards 8%, sports 5%, schemes 18%, organisations and summits 17%, reports and indices 11%,
// agreements 13%, economy data 14%, AP-specific 21%; formats 84% direct recall, 8% multi-statement,
// 5% negative ("NOT correct"), 1% match the following, 1% assertion-reason.
const PYQ_WEIGHT = `APPSC PYQ WEIGHT (what the commission actually asks in current affairs): appointments to constitutional,
statutory and top posts (CJI and judges, Governors, Election Commissioners, heads of RBI, ISRO, DRDO, UN bodies, AP posts),
awards and prizes (national and international, AP awardees), sports results, schemes and their objectives,
international organisations and summits (host, theme, headquarters), reports and indices (publisher, India's and AP's rank),
agreements and MoUs, AP state developments. Prefer these. Leave out routine administrative notices (stock limits,
adalats, grievance camps, review meetings), foreign diplomacy with no India angle, and repeated monthly data unless a
figure is new and exam-worthy. At most 2 defence items per section.`;

const FACT_RULES = `FACT EXTRACTION (put these in "facts", each a short exam-ready line):
- Awards: award, field, winner, awarding body, edition/year, first woman/Indian/Telugu only if verified, AP connection.
- Sports: event, edition, host, winner, runner-up, India's medals, records, next host, AP athlete if any.
- Appointments: person, new position, organisation, predecessor, any verified first-ever claim.
- Schemes: scheme, ministry, launch date, objective, beneficiaries, amount, coverage, AP counterpart, status.
- Reports: report, publisher, edition, India's rank and score, top country, AP rank where available.
- International organisations: organisation, HQ, members, chair, summit edition, host, theme, India's role, outcomes.
- Defence: exercise/system, type, range if relevant, countries, venue, developer, indigenous status, verified FIRSTs.
- Space: mission, agency, launch vehicle, orbit, objective, status, verified FIRST.
- Health: disease, pathogen, programme, WHO status, ministry, coverage.
- Environment: location, status, species, protected-area status, national count, cyclone naming body.
- Polity/Law: Bill/Act, constitutional article, major provisions, legislative status, court, bench, judgment, principle.
- Economy: data, date, releasing authority, previous figure, latest figure, AP figure if available.
- AP investment: project, district, company, investment, capacity, jobs, partner, current stage.`;

function systemPrompt(date) {
  return `You are an APPSC Group 1 and Group 2 current-affairs research editor.

Your task is to produce a daily, source-verified, exam-oriented current-affairs briefing for Andhra Pradesh Public Service Commission examinations.

DATE: ${date}
TIMEZONE: Asia/Kolkata

MANDATORY COVERAGE LEVELS: 1. International 2. National India 3. Andhra Pradesh state and district developments.
Andhra Pradesh must receive additional emphasis without reducing important national or international coverage.

SUBJECTS TO CHECK: ${SUBJECTS.join('; ')}.

EXCLUDE COMPLETELY: General Mental Ability, aptitude, quantitative aptitude, mathematics, numerical reasoning, Data Interpretation.
Also leave out party politics (who said what about whom), protests, crime, stock-market movements, festivals and entertainment.

CATEGORIES TO CHECK: ${CATEGORIES.join('; ')}.

SOURCE RULE: Search the live web. Prefer primary sources: Government of India, Government of Andhra Pradesh, PIB, RBI, IMD, ISRO, ministries, courts, the United Nations, WHO, World Bank, IMF, UNESCO, official sporting bodies. Use trustworthy secondary reporting where necessary, especially for local Andhra Pradesh developments. Do not invent information. Every fresh factual claim keeps its supporting source (title, publisher, URL, published date).

FIRST RULE: Only set verifiedFirst (⭐ FIRST / largest / highest / longest) when an authoritative primary source independently supports the claim. If uncertain, set it false and do not state the claim.

AP LINK RULE: Every non-AP item contains either a genuine factual AP connection or exactly "No direct AP-specific link established." Never manufacture an AP link.

STATUS RULE: classify each item's status as one of ${STATUSES.join(', ')}. Never convert an MoU into a completed investment, proposed jobs into existing jobs, a foundation stone into a completed project, a forecast into an observed event, or a scheduled event into a completed one.

DATE RULE: Prioritise developments occurring on ${date}. Include important late-breaking developments from the previous day only when still relevant. Do not use old filler to cover a category.

GROUP 2 MAPPING: Mains Paper I = AP History & Culture, Indian Constitution, Governance. Paper II = Indian Economy, AP Economy, Science & Technology.
Group 1 Mains: Paper I Essay; Paper II History, Culture & Geography; Paper III Polity, Governance, Ethics; Paper IV Economy & Development; Paper V Science, Technology & Environment.

${PYQ_WEIGHT}

${FACT_RULES}

OUTPUT: Return valid JSON only, exactly in the requested schema. No text outside the JSON.`;
}

function sectionTask(date, key, recentTopics = []) {
  const s = SECTIONS[key];
  return `Research ${s.label} current affairs for ${date} (IST) using live web search: ${s.focus}

Return between ${s.min} and ${s.max} items, the most exam-relevant first (importance 1 = highest, 3 = lowest). Fewer only if fewer verified exam-relevant developments exist. Each item:
- canonicalTopic: a short stable slug for the story (e.g. "ap-drought-kharif-2026"), the same slug every day the story continues.
- isUpdate: true only if the story continues one of the topics listed below AND there is a genuine new development today; then say in "summary" what changed ("Previous: ... Today: ...").
- headline, category (one of the categories), subjects (from the subject list), group1 (e.g. ["Prelims", "Mains Paper IV"]), group2 (e.g. ["Prelims", "Mains Paper II"]), importance, date (YYYY-MM-DD the development happened), facts (3-8 lines), summary (2-4 sentences), staticLink (the static syllabus concept it connects to), prelimsTakeaway, mainsAngle, apLink, status, verifiedFirst, sources (1-4, each with title, publisher, url, publishedDate, isPrimary).
${recentTopics.length ? `\nTopics already covered on the previous days (do not repeat them without a new development):\n${recentTopics.map((t) => `- ${t}`).join('\n')}` : ''}
Return {"items": [...]}`;
}

function editorialTask(date, items) {
  const brief = items.map((i, n) => `${n + 1}. [${i.section}] ${i.headline} | subjects: ${(i.subjects || []).join(', ')} | facts: ${(i.facts || []).join(' / ')}`).join('\n');
  return `Here are today's verified APPSC current-affairs items for ${date}:
${brief}

Using ONLY these items (no new facts, no web search needed):
1. coverageAudit: one entry per subject in this list: ${SUBJECTS.join('; ')}. Each: {"subject", "covered": true/false, "items": [item numbers], "note"}; when not covered, note = "No significant verified current-affairs item today."
2. mcqs: 8 to 10 APPSC-style multiple-choice questions from these items' facts, in the APPSC paper's own mix: mostly direct one-fact questions (who was appointed, which organisation released the index, where is the headquarters, which state ranked first, theme of the day), plus 1 or 2 "Consider the following statements: 1. ... 2. ... Which of the above is/are correct?" questions (options like "1 only", "2 only", "Both 1 and 2", "Neither 1 nor 2"), 1 "Which of the following is NOT correct" question, and 1 "Match List I with List II" question when the facts allow. At least 2 questions on Andhra Pradesh items. Each: {"question", "options": [4 strings], "answer": "A"|"B"|"C"|"D", "explanation" (one or two lines), "item": item number}. No mathematics, aptitude, reasoning or Data Interpretation.
3. mainsQuestions: 2 or 3 APPSC Group 1 Mains questions. Each: {"question", "paper" (e.g. "Group 1 Mains Paper IV"), "subject", "dimensions": [3-6 short answer dimensions]}. Do not write model answers.
Return {"coverageAudit": [...], "mcqs": [...], "mainsQuestions": [...]}`;
}

// JSON schemas (strict structured outputs: every property required, no extras)
const str = { type: 'string' };
const strs = { type: 'array', items: str };
const SOURCE = {
  type: 'object',
  additionalProperties: false,
  required: ['title', 'publisher', 'url', 'publishedDate', 'isPrimary'],
  properties: { title: str, publisher: str, url: str, publishedDate: str, isPrimary: { type: 'boolean' } },
};
const ITEM = {
  type: 'object',
  additionalProperties: false,
  required: ['canonicalTopic', 'isUpdate', 'headline', 'category', 'subjects', 'group1', 'group2', 'importance', 'date', 'facts',
    'summary', 'staticLink', 'prelimsTakeaway', 'mainsAngle', 'apLink', 'status', 'verifiedFirst', 'sources'],
  properties: {
    canonicalTopic: str, isUpdate: { type: 'boolean' }, headline: str, category: str, subjects: strs, group1: strs, group2: strs,
    importance: { type: 'integer' }, date: str, facts: strs, summary: str, staticLink: str, prelimsTakeaway: str, mainsAngle: str,
    apLink: str, status: { type: 'string', enum: STATUSES }, verifiedFirst: { type: 'boolean' }, sources: { type: 'array', items: SOURCE },
  },
};
const SECTION_SCHEMA = { type: 'object', additionalProperties: false, required: ['items'], properties: { items: { type: 'array', items: ITEM } } };
const EDITORIAL_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['coverageAudit', 'mcqs', 'mainsQuestions'],
  properties: {
    coverageAudit: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['subject', 'covered', 'items', 'note'],
      properties: { subject: str, covered: { type: 'boolean' }, items: { type: 'array', items: { type: 'integer' } }, note: str } } },
    mcqs: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['question', 'options', 'answer', 'explanation', 'item'],
      properties: { question: str, options: strs, answer: { type: 'string', enum: ['A', 'B', 'C', 'D'] }, explanation: str, item: { type: 'integer' } } } },
    mainsQuestions: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['question', 'paper', 'subject', 'dimensions'],
      properties: { question: str, paper: str, subject: str, dimensions: strs } } },
  },
};

module.exports = { STATUSES, SUBJECTS, CATEGORIES, PRIMARY_DOMAINS, SECTIONS, systemPrompt, sectionTask, editorialTask, SECTION_SCHEMA, EDITORIAL_SCHEMA };
