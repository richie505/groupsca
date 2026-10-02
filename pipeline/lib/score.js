'use strict';

// The APPSC relevance score, out of 100, with no model in the path.
//
//   A. Syllabus units          30   Group-I Prelims + Group-II units and master topics
//   B. Blueprint angles        20   the question angles APPSC keeps returning to
//   C. Andhra Pradesh          20
//   D. Importance              15   a findable official act (order, Bill, judgment, appointment…)
//   E. Both exams              15   pays in Group-I and Group-II, or in more than one paper
//
//   80-100 CRITICAL   60-79 HIGH   40-59 MEDIUM   below 40 LOW (not published)
//
// The weights and bands are the portal's (server/src/lib/relevance.js). The
// factors are simpler, because this runs on headline + feed summary (+ the
// release text for PIB) rather than on a full newspaper page, and has no PYQ
// database to count against. Every score keeps its breakdown, so "why is this
// here" always has an answer in the app.
//
// Same input, same output: no clock, no network, no randomness.

const fs = require('fs');
const path = require('path');
const R = require('./rules');

const WEIGHTS = { syllabus: 30, angles: 20, ap: 20, importance: 15, reuse: 15 };
const BANDS = [[80, 'critical'], [60, 'high'], [40, 'medium'], [0, 'low']];

function bandFor(score) {
  return BANDS.find(([min]) => score >= min)[1];
}

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function norm(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[–—]/g, '-')
    .replace(/\s+/g, ' ');
}

/**
 * A matcher for one alias. Strict aliases (short all-caps acronyms) are
 * case-sensitive against the raw text, so 'ASI' does not fire inside "Asian";
 * loose ones match the lowercased text and tolerate a plural.
 */
function matcher(alias, strict) {
  const nonAscii = /[^\x00-\x7F]/.test(alias);
  if (nonAscii) {
    const needle = norm(alias);
    return (raw, low) => (low.includes(needle) ? 1 : 0);
  }
  let body = escapeRe(strict ? alias : norm(alias));
  if (!strict && alias.length >= 4 && alias !== alias.toUpperCase()) body += '(?:e?s)?';
  const re = new RegExp(`(?<![A-Za-z0-9])${body}(?![A-Za-z0-9])`, strict ? 'g' : 'gi');
  return (raw, low) => {
    const m = (strict ? raw : low).match(re);
    return m ? m.length : 0;
  };
}

// A blueprint entry can bundle several angles: "Pollution/Air Quality/CPCB",
// "Rivers → State/District/Town [LOCATION]", "Known as – Other Name". Each part
// is its own term; parentheticals are examples, not terms.
function angleTerms(entry) {
  return String(entry)
    .replace(/\([^)]*\)/g, ' ')
    .replace(/\[[^\]]*\]/g, ' ')
    .split(/[/|→@]|\s[–-]\s|\+/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 2);
}

/** Loads pipeline/vocab/*.json and compiles every matcher once. */
function loadVocab(dir = path.join(__dirname, '..', 'vocab')) {
  const read = (f) => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
  const compile = (list) => list.map(({ a, strict }) => ({ alias: a, test: matcher(a, strict) }));

  const units = read('units.json').map((u) => ({ ...u, matchers: compile(u.aliases) }));
  const topics = read('topics.json').map((t) => ({ ...t, matchers: compile(t.aliases) }));

  const angles = [];
  const seen = new Set();
  for (const [subject, list] of Object.entries(read('keywords.json'))) {
    for (const entry of list) {
      for (const term of angleTerms(entry)) {
        const low = term.toLowerCase();
        if (R.KEYWORD_STOPLIST.has(low) || seen.has(low)) continue;
        // Short acronyms (RBI, GST, NGT) are exact; anything else is a phrase.
        const strict = /^[A-Z0-9&]{2,6}$/.test(term);
        if (!strict && term.length < 4) continue;
        seen.add(low);
        angles.push({ term, subject, test: matcher(term, strict) });
      }
    }
  }

  const apTerms = read('ap-terms.json').map((t) => ({ term: t, test: matcher(t, /^[A-Z0-9]{2,6}$/.test(t)) }));
  return { units, topics, angles, apTerms };
}

const countOf = (re, text) => (text.match(new RegExp(re.source, re.flags.includes('g') ? re.flags : re.flags + 'g')) || []).length;

function bucketOf(text, ap) {
  if (ap) return 'ap';
  if (R.INTERNATIONAL.test(text) && countOf(R.INTERNATIONAL, text) > countOf(R.NATIONAL, text)) return 'international';
  if (R.DYNAMIC.test(text) && !R.NATIONAL.test(text)) return 'dynamic';
  return 'national';
}

/**
 * Units (or topics) the text is about. One mention in the body is not enough
 * for a one-word alias — "Bill" or "Mission" turn up everywhere — so a unit
 * needs its alias in the headline, a multi-word alias, or two mentions.
 */
function evidence(list, head, text) {
  const hl = norm(head);
  const tl = norm(text);
  const out = [];
  for (const u of list) {
    let hits = 0;
    let headHit = false;
    let phrase = false;
    const matched = [];
    for (const m of u.matchers) {
      const n = m.test(text, tl);
      if (!n) continue;
      hits += n;
      matched.push(m.alias);
      if (m.test(head, hl)) headHit = true;
      if (/\s/.test(m.alias) || m.alias.length >= 9) phrase = true;
    }
    if (hits && (headHit || phrase || hits >= 2)) out.push({ item: u, hits, headHit, matched });
  }
  return out.sort((a, b) => b.hits - a.hits);
}

/**
 * Scores one article.
 *
 * article: { headline, summary, body, primary, apSource, opinion, alsoIn }
 * Returns { vetoed, score, band, bucket, subjects, units, topics, angles,
 *           instruments, exams, ap, why }.
 */
function score(article, vocab) {
  const head = String(article.headline || '');
  const lead = `${head}. ${article.summary || ''}`;
  const text = `${lead} ${article.body || ''}`;
  const low = norm(text);

  // ---- veto: never examinable, whatever else the story carries ----
  // Tested on the headline and summary only: a PIB release body carries site
  // chrome, and one stray word there must not throw out a Cabinet decision.
  for (const v of R.VETO) {
    if (v.re.test(lead)) return { vetoed: v.label, score: 0, band: 'low' };
  }
  if (R.SPORT_MATCH_REPORT.test(lead) && !R.SPORT_EXEMPT.test(lead)) {
    return { vetoed: 'sport match report', score: 0, band: 'low' };
  }

  // ---- C. Andhra Pradesh ----
  // APPSC sets this exam, so an AP story is a first-class story: an AP place,
  // body, scheme or leader in the headline or the feed's summary makes it AP in
  // full. In a PIB release body it takes two mentions, since national releases
  // list every State.
  const apLead = vocab.apTerms.some((t) => t.test(lead, norm(lead)));
  const apHits = vocab.apTerms.reduce((n, t) => n + t.test(text, low), 0);
  const ap = !!article.apSource || apLead || apHits >= 2;
  const apScore = article.apSource || apLead ? WEIGHTS.ap : apHits >= 2 ? 12 : 0;

  // Another country's internal affairs with no Indian thread: only the
  // foreign-policy unit may be claimed (relevance.js, FOREIGN_NAME).
  const foreignOnly = R.FOREIGN_NAME.test(head) && !R.INDIA_TERM.test(text);

  // ---- A. syllabus ----
  let units = evidence(vocab.units, head, text);
  if (foreignOnly) units = units.filter((u) => u.item.code === 'G1P-B6');
  const topics = foreignOnly ? [] : evidence(vocab.topics, head, text);
  const subjects = R.SUBJECT_HINTS.filter(([, re]) => re.test(text)).map(([name]) => name);

  const nUnits = units.length;
  let syllabus = nUnits >= 3 ? 24 : nUnits === 2 ? 18 : nUnits === 1 ? 12 : 0;
  const bestTier = topics.reduce((t, x) => Math.min(t, x.item.tier || 3), 9);
  if (bestTier === 1) syllabus += 12;
  else if (bestTier === 2) syllabus += 8;
  else if (bestTier === 3) syllabus += 4;
  if (!nUnits && !topics.length && subjects.length) syllabus += 4;
  syllabus = Math.min(WEIGHTS.syllabus, syllabus);

  // ---- B. blueprint angles ----
  const angles = vocab.angles.filter((a) => a.test(text, low)).map((a) => a.term);
  const angleScore = Math.min(WEIGHTS.angles, angles.length * 5);

  // ---- D. importance ----
  const instruments = R.INSTRUMENT.filter((i) => i.re.test(text));
  let importance = Math.min(12, instruments.reduce((n, i) => n + i.w, 0) * 2);
  if (article.primary) importance += 3;
  if ((article.alsoIn || []).length >= 2) importance += 3;
  importance = Math.min(WEIGHTS.importance, importance);

  // ---- E. both exams / several papers ----
  const exams = new Set(units.map((u) => u.item.exam));
  if (topics.length) exams.add('G1');
  if (angles.length) exams.add('G2');
  const papers = new Set(units.map((u) => u.item.paper));
  const reuse = exams.size === 2 && units.length >= 2 ? WEIGHTS.reuse : papers.size >= 2 ? 10 : papers.size === 1 ? 5 : 0;

  const why = { syllabus, angles: angleScore, ap: apScore, importance, reuse };
  const total = Object.values(why).reduce((a, b) => a + b, 0);

  return {
    vetoed: null,
    score: total,
    band: bandFor(total),
    bucket: bucketOf(text, ap),
    // Where the story sits leaving AP aside, so an AP story about a Union
    // decision also shows under National (and an AP-India-Japan MoU under
    // International) — AP gets its own lane without leaving the others.
    scope: bucketOf(text, false),
    subjects,
    ap,
    // Named by the combined syllabus tracker (G1-A1, G2-M1A-U4 ...).
    units: units.slice(0, 4).map((u) => ({ code: u.item.tracker, exam: u.item.exam, label: u.item.label })),
    topics: topics.slice(0, 3).map((t) => t.item.name),
    angles: angles.slice(0, 6),
    instruments: instruments.map((i) => i.label),
    // An item that reaches 40 on AP or importance alone still belongs to both
    // exams' current-affairs papers.
    exams: exams.size ? [...exams].sort() : ['G1', 'G2'],
    why,
  };
}

module.exports = { loadVocab, score, bandFor, matcher, angleTerms, norm, WEIGHTS };
