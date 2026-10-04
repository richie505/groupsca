'use strict';
// The day's study layer, made by fixed rules (no AI):
//  - top ("New today"): the 15 new topics most worth reading, Andhra Pradesh kept in;
//    a topic that started on an earlier day is an update (threads.js), read as one line
//  - oneLiner: appointments, awards, sports, days and the like, as one line each
//  - quiz: fill-in-the-blank questions from the stories' own figures and years

const TOP_N = 15;
const TOP_AP_MIN = 5;
const QUIZ_N = 15;

/** Book 6 stories (appointments, awards, sports, days, persons) read best as one line. */
const ONE_LINE_ANGLES = /^(Appointed|Award|Sports|Day|Person|Persons in news|Places in news)/i;
const ONE_LINE_TITLE =
  /\b(appointed|appoints|takes charge|assumes charge|sworn in|elected (?:as )?(?:president|chair)|award(?:s|ed)?|wins?|won|gold|silver|bronze|medal|champion(?:ship)?|trophy|jayanti|diwas|day\b|week\b|anniversary|birth anniversary|death anniversary|passes away|dies)\b/i;

function isOneLiner(it) {
  if (it.digest) return false;
  if (it.subject === 'Current Affairs') return true;
  // a short news fact with no static notes behind it
  const noNotes = !((it.brief && it.brief.sections) || []).length;
  return noNotes && (ONE_LINE_TITLE.test(it.title) || (it.angles || []).some((a) => ONE_LINE_ANGLES.test(a)));
}

/** The one line: the first key fact, else the summary's first sentence, else the headline. */
function lineOf(it) {
  const first = (s) => String(s || '').split(/(?<=[.!?])\s+/)[0].trim();
  const f = (it.facts || [])[0];
  const text = (f && f.text) || first(it.summary) || it.title;
  return text.length > 220 ? `${text.slice(0, 219).replace(/\s+\S*$/, '')}…` : text;
}

const NOT_TOP =
  /\b(protests?|protested|stage[sd]? (?:a )?(?:protest|dharna)|dharna|attacks?|slams?|flays?|alleges?|criticis\w*|blames?|urges?|demands?|warns?|SIT|police|arrest\w*|murder\w*|killed|accident|hearing|gimmick|conspiracy|kutra|కుట్ర)\b/i;

/** Marks `top`, `oneLiner` and `line` on the day's items. */
function markDay(items) {
  for (const it of items) {
    delete it.top;
    delete it.oneLiner;
    delete it.line;
  }
  const leads = items.filter((i) => !i.topicOf && !i.digest);
  for (const it of leads) {
    if (isOneLiner(it)) {
      it.oneLiner = true;
      it.line = lineOf(it);
    } else if (it.update) {
      // a follow-up on a topic read before: its headline says what is new
      it.line = it.title;
    }
  }
  // politics and incidents (protests, attacks, cases) are news, rarely exam topics
  const rankScore = (i) => i.score - (NOT_TOP.test(i.title) ? 20 : 0) - (i.lang === 'te' ? 5 : 0);
  const order = (a, b) => rankScore(b) - rankScore(a) || a.title.localeCompare(b.title);
  const pool = leads.filter((i) => !i.oneLiner && !i.update && rankScore(i) >= 45).sort(order);
  const ap = pool.filter((i) => i.ap).slice(0, TOP_AP_MIN);
  const chosen = new Set(ap);
  for (const it of pool) {
    if (chosen.size >= TOP_N) break;
    chosen.add(it);
  }
  for (const it of chosen) it.top = true;
  return {
    top: chosen.size,
    oneLiners: leads.filter((i) => i.oneLiner).length,
    updates: leads.filter((i) => i.update && !i.oneLiner).length,
  };
}

// ---------------------------------------------------------------------------
// quiz
// ---------------------------------------------------------------------------

// a figure with what it counts: ₹1.75 lakh crore, $600 million, 14.7%, 46 days, 2,000 km
const FIGURE =
  /(?:₹|Rs\.?\s?|\$|US\$)\s?\d[\d,]*(?:\.\d+)?(?:\s?(?:lakh crore|crore|lakh|billion|million|trillion|bn|mn|cr))?|\b\d[\d,]*(?:\.\d+)?\s?(?:%|per cent|percent|lakh crore|crore|lakh|billion|million|trillion|km|MW|GW|tonnes|MT|hectares|ha|acres|kg|days|years|villages|districts|states|countries|members)\b/gi;
const YEAR = /\b(19[5-9]\d|20[0-4]\d)\b/g;

function hash(s) {
  let h = 2166136261;
  for (const c of String(s)) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return h >>> 0;
}

/** The number inside a figure, its decimals and whether it had thousands commas. */
function numberIn(fig) {
  const m = fig.match(/\d[\d,]*(?:\.\d+)?/);
  if (!m) return null;
  const raw = m[0];
  const value = Number(raw.replace(/,/g, ''));
  if (!Number.isFinite(value) || value === 0) return null;
  const decimals = (raw.split('.')[1] || '').length;
  return { raw, value, decimals, commas: raw.includes(',') };
}

function format(n, like) {
  const fixed = like.decimals ? n.toFixed(like.decimals) : String(Math.round(n));
  if (!like.commas) return fixed;
  const [i, d] = fixed.split('.');
  return Number(i).toLocaleString('en-IN') + (d ? `.${d}` : '');
}

/** Three wrong figures near the right one, in the same style. */
function figureOptions(fig, seed) {
  const n = numberIn(fig);
  if (!n) return null;
  const pct = /%|per ?cent/i.test(fig);
  const factors = pct && n.value < 60 ? null : [0.5, 0.75, 1.5, 2, 1.25, 0.6];
  const out = new Set();
  if (factors) {
    const start = seed % factors.length;
    for (let k = 0; k < factors.length && out.size < 3; k++) {
      const v = n.value * factors[(start + k) % factors.length];
      const s = fig.replace(n.raw, format(v, n));
      if (s !== fig && Number(format(v, { ...n, commas: false })) !== n.value) out.add(s);
    }
  } else {
    // a percentage: a few points either side
    for (const d of [-3, 2, 5, -6, 4]) {
      if (out.size >= 3) break;
      const v = n.value + d * (n.value < 10 ? 0.5 : 1);
      if (v <= 0) continue;
      out.add(fig.replace(n.raw, format(v, { ...n, decimals: n.decimals || (n.value < 10 ? 1 : 0) })));
    }
  }
  return out.size === 3 ? [...out] : null;
}

function yearOptions(year, seed) {
  const y = Number(year);
  const deltas = [-2, -1, 1, 2, 3, -3];
  const start = seed % deltas.length;
  const out = [];
  for (let k = 0; out.length < 3; k++) out.push(String(y + deltas[(start + k) % deltas.length]));
  return out;
}

/** Puts the right answer among the wrong ones at a place fixed by the question (same every run). */
function shuffle(right, wrong, seed) {
  const opts = [...wrong];
  const at = seed % (wrong.length + 1);
  opts.splice(at, 0, right);
  return { options: opts, answer: at };
}

/** One question from a sentence: its first figure (else a year) blanked, with 4 choices. */
function questionFrom(text, seed, newsYear = 0) {
  // citations and asides out: "(APPSC-GS 2025)", "(LENS Jun 2026)", "(TH, 5 Jan 2026)"
  const sentence = String(text).replace(/\s*\([^)]*\)/g, '').replace(/\s+([.,;])/g, '$1').trim();
  if (sentence.length < 40 || sentence.length > 240 || !/^[\p{L}\p{N}"'‘“₹$]/u.test(sentence)) return null;
  // table rows read badly as questions ("Years / Chair 1970-76; chair ...")
  if (/ \/ |→/.test(sentence) || (sentence.match(/;/g) || []).length > 1) return null;
  const figs = [...sentence.matchAll(FIGURE)].map((m) => m[0].trim());
  for (const fig of figs) {
    const wrong = figureOptions(fig, seed);
    if (!wrong) continue;
    return { q: sentence.replace(fig, '____'), ...shuffle(fig, wrong, seed), kind: 'figure' };
  }
  // a year on its own, not part of a range ("2014-15") or a date the story is from
  const years = [...sentence.matchAll(YEAR)]
    .filter((m) => !/[-–]\d/.test(sentence.slice(m.index + 4, m.index + 6)) && !/[-–]$/.test(sentence.slice(0, m.index)))
    // this year or next is no question ("launched in 2026" in 2026 news)
    .filter((m) => !newsYear || Math.abs(Number(m[0]) - newsYear) > 1)
    .map((m) => m[0]);
  if (years.length) {
    const y = years[0];
    return { q: sentence.replace(y, '____'), ...shuffle(y, yearOptions(y, seed), seed), kind: 'year' };
  }
  return null;
}

/**
 * The day's quiz: up to 15 questions, one per story, top stories first, from the
 * story's own key facts and summary (the current matter) and then its static notes.
 */
function quizFor(items) {
  const quiz = [];
  const order = (a, b) => (b.top ? 1 : 0) - (a.top ? 1 : 0) || b.score - a.score;
  const pool = items.filter((i) => !i.topicOf && i.lang !== 'te' && !i.digest).sort(order);
  for (const it of pool) {
    if (quiz.length >= QUIZ_N) break;
    const sentences = [
      ...(it.facts || []).map((f) => f.text),
      ...String(it.summary || '').split(/(?<=[.!?])\s+/),
      ...((it.brief && it.brief.sections) || []).flatMap((s) => s.bullets),
    ];
    for (const s of sentences) {
      const q = questionFrom(s, hash(it.id + s), Number(String(it.date || '').slice(0, 4)) || 0);
      if (!q) continue;
      quiz.push({ id: `${it.id}:${hash(s).toString(36)}`, item: it.id, title: it.title, ...q });
      break;
    }
  }
  return quiz;
}

module.exports = { markDay, quizFor, questionFrom, isOneLiner, lineOf, TOP_N };
