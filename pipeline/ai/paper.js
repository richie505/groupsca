'use strict';
// Today's newspaper (The Hindu, Eenadu e-paper PDFs uploaded from the app) read by the AI into exam items.
// The PDF is split into parts of a few pages, each read with no web search; the items, written in the
// model's own words (never the paper's text), join that day's briefing marked with the paper and page.
// The PDF itself is never saved in this repository (it is public); it stays in the private inbox.

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const P = require('./prompt');
const AI = require('./openai');
const V = require('./validate');
const N = require('./notes');

const PAPERS = {
  hindu: { name: 'The Hindu', url: 'https://epaper.thehindu.com/' },
  eenadu: { name: 'Eenadu', url: 'https://epaper.eenadu.net/' },
};
const KEYS = ['international', 'national', 'andhraPradesh'];
const PAGES_PER_PART = 6;
const MAX_PART_BYTES = 30 * 1024 * 1024;

/** Which paper a file is, from its name ("2026-10-16__eenadu.pdf", "TheHindu_AP.pdf"). */
function paperOf(name) {
  const n = String(name).toLowerCase();
  if (/eenadu|ఈనాడు/.test(n)) return 'eenadu';
  if (/hindu/.test(n)) return 'hindu';
  return '';
}

function paperTask(date, paper, from, to) {
  return `These are pages ${from}-${to} of ${paper.name} (${date}).${paper.name === 'Eenadu' ? ' The paper is in Telugu: write everything in English.' : ''}

Pick out the APPSC Group 1 and Group 2 current-affairs developments on these pages, as the system prompt defines them:
international, national and Andhra Pradesh (state and district) developments that the exam asks about — appointments, awards,
sports results, schemes, Cabinet and government decisions, Bills and judgments, reports and indices, summits and agreements,
science, space, defence, environment, economy data, AP projects, investments and district developments.
Leave out party politics, statements, protests, crime, accidents, markets, advertisements, cinema, astrology and opinion pieces;
weather forecasts; local civic works and tenders; temple visits and ceremonies; private companies' surveys; domestic league
and state-level sport (Ranji, national ranking events) — keep international events and India's national teams; foreign
local events with no India angle. One item per story: if a story runs on two pages, give it once with all its facts.

Write every item in your own words (never copy the paper's sentences); keep all names, numbers, dates and places exact.
For each item give its section ("international", "national" or "andhraPradesh"), the page number it is on, and the full item fields.
For "sources" give one entry: title = the paper's headline translated to English if needed, publisher = "${paper.name}",
url = "${paper.url}", publishedDate = "${date}", isPrimary = false. Only what these pages report: add nothing from elsewhere.
Also give "paperDate": the edition date printed on these pages (YYYY-MM-DD), or "" if no date is printed.
Return {"paperDate": "...", "items": [{"section", "page", "item"}]} — an empty list if these pages have nothing for the exam.`;
}

const PAPER_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['paperDate', 'items'],
  properties: {
    paperDate: { type: 'string' },
    items: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['section', 'page', 'item'],
        properties: {
          section: { type: 'string', enum: KEYS },
          page: { type: 'integer' },
          item: P.SECTION_SCHEMA.properties.items.items,
        },
      },
    },
  },
};

/** Splits a PDF into parts of PAGES_PER_PART pages (qpdf), shrinking a part that is still too big (ghostscript). */
function splitPdf(file, dir) {
  const pages = Number(execFileSync('qpdf', ['--show-npages', file]).toString().trim());
  const parts = [];
  for (let from = 1; from <= pages; from += PAGES_PER_PART) {
    const to = Math.min(pages, from + PAGES_PER_PART - 1);
    let part = path.join(dir, `part-${from}.pdf`);
    execFileSync('qpdf', [file, '--pages', '.', `${from}-${to}`, '--', part]);
    if (fs.statSync(part).size > MAX_PART_BYTES) {
      const small = path.join(dir, `part-${from}-small.pdf`);
      execFileSync('gs', ['-sDEVICE=pdfwrite', '-dPDFSETTINGS=/ebook', '-dNOPAUSE', '-dBATCH', '-dQUIET', `-sOutputFile=${small}`, part]);
      part = small;
    }
    parts.push({ file: part, from, to });
  }
  return parts;
}

/** Reads one part: returns [{ section, item }] with the page on the item. */
async function readPart(date, paper, part, respond) {
  const data = fs.readFileSync(part.file).toString('base64');
  const input = [{
    role: 'user',
    content: [
      { type: 'input_file', filename: path.basename(part.file), file_data: `data:application/pdf;base64,${data}` },
      { type: 'input_text', text: paperTask(date, paper, part.from, part.to) },
    ],
  }];
  const r = await respond({ instructions: P.systemPrompt(date), input, schema: PAPER_SCHEMA, name: 'appsc_paper', search: false });
  readPart.paperDate = readPart.paperDate || (/^\d{4}-\d{2}-\d{2}$/.test(r.data.paperDate || '') ? r.data.paperDate : '');
  return (r.data.items || []).filter((x) => KEYS.includes(x.section) && x.item).map((x) => ({
    section: x.section,
    item: { ...x.item, section: P.SECTIONS[x.section].label, paper: `${paper.name} · p.${x.page || part.from}` },
  }));
}

/** One per story: the same topic slug or close headlines (a story on page 1 and again on page 11). */
function sameStory(a, b) {
  if (a.canonicalTopic && a.canonicalTopic === b.canonicalTopic) return true;
  return V.similar(a.headline, b.headline) >= 0.6;
}

/** The paper's own date when it is printed and near the upload date (an upload just after midnight keeps its day). */
function editionDate(uploaded, printed) {
  if (!printed) return uploaded;
  const gap = Math.abs(Date.parse(`${printed}T00:00:00Z`) - Date.parse(`${uploaded}T00:00:00Z`)) / 86400000;
  return gap <= 2 ? printed : uploaded;
}

/**
 * Reads a paper PDF into the day's briefing (feed/briefings/<date>.json): items marked `paper`, duplicates of
 * stories already there dropped, Rocket-form notes, MCQs redone. A day with no briefing yet gets one holding
 * the paper's items (`paperOnly`); the evening research keeps them. Returns the number of items added.
 */
async function readPaper(date, pdf, key, { out, respond = AI.respond, log = console.log, split = splitPdf } = {}) {
  const paper = PAPERS[key];
  if (!paper) throw new Error(`unknown paper "${key}" (${Object.keys(PAPERS).join(', ')})`);
  const B = require('./briefing');
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'paper-'));
  const found = [];
  readPart.paperDate = '';
  try {
    for (const part of split(pdf, tmp)) {
      try {
        const got = await readPart(date, paper, part, respond);
        log(`${date}: ${paper.name} pages ${part.from}-${part.to}: ${got.length} items`);
        found.push(...got);
      } catch (e) {
        log(`${date}: ${paper.name} pages ${part.from}-${part.to} FAILED: ${e.message}`);
      }
    }
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
  date = editionDate(date, readPart.paperDate);
  const b = B.read(out, date) || {
    version: 1, date, title: `APPSC Group 1 & Group 2 Daily Current Affairs — ${date}`, model: '', generatedAt: new Date().toISOString(),
    sections: { international: [], national: [], andhraPradesh: [] }, coverageAudit: [], mcqs: [], mainsQuestions: [], citations: [], paperOnly: true,
  };
  const before = KEYS.reduce((s, k) => s + b.sections[k].length, 0);
  const have = KEYS.flatMap((k) => b.sections[k]);
  for (const f of found) {
    const same = have.find((x) => sameStory(x, f.item));
    if (!same) {
      b.sections[f.section].push(f.item);
      have.push(f.item);
    } else if (same.paper && (f.item.facts || []).length > (same.facts || []).length) {
      // the fuller report of the same story wins
      Object.assign(same, { ...f.item, section: same.section });
    }
  }
  // drops the paper's items that repeat a story already in (the web briefing's, or the other paper's)
  V.validate(b, []);
  const added = KEYS.reduce((s, k) => s + b.sections[k].length, 0) - before;
  b.papers = [...new Set([...(b.papers || []), paper.name])];
  if (added) {
    const fresh = { date, sections: Object.fromEntries(KEYS.map((k) => [k, b.sections[k].filter((i) => i.paper && !i.notes)])) };
    await N.addNotes(fresh, respond, log);
    const ed = await B.editorial(date, b.sections, respond);
    b.coverageAudit = ed.coverageAudit || [];
    b.mcqs = ed.mcqs || [];
    b.mainsQuestions = ed.mainsQuestions || [];
  }
  const v = V.validate(b, []);
  // a morning paper-only day is shown as soon as it has items; the evening research completes it
  b.status = b.paperOnly ? (added || before ? 'published' : 'needs_review') : v.status;
  b.problems = v.problems;
  b.generatedAt = new Date().toISOString();
  b.sourceCount = new Set(KEYS.flatMap((k) => b.sections[k].flatMap((i) => i.sources.map((x) => x.url)))).size;
  fs.mkdirSync(path.join(out, 'briefings'), { recursive: true });
  fs.writeFileSync(path.join(out, 'briefings', `${date}.json`), JSON.stringify(b, null, 1) + '\n');
  B.writeIndex(out);
  log(`${date}: ${paper.name}: ${added} new items (${found.length - added} already covered)`);
  return added;
}

module.exports = { sameStory, editionDate, PAPERS, paperOf, paperTask, PAPER_SCHEMA, splitPdf, readPaper };
