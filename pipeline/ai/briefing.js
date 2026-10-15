'use strict';
// One day's APPSC briefing: three research stages with live web search (International, National,
// Andhra Pradesh), an editorial stage (coverage audit, MCQs, Mains questions), validation, then
// feed/briefings/<date>.json and feed/briefings/index.json.

const fs = require('fs');
const path = require('path');
const P = require('./prompt');
const AI = require('./openai');
const V = require('./validate');
const N = require('./notes');

const dir = (out) => path.join(out, 'briefings');
const file = (out, date) => path.join(dir(out), `${date}.json`);

function read(out, date) {
  try {
    return JSON.parse(fs.readFileSync(file(out, date), 'utf8'));
  } catch {
    return null;
  }
}

/** Canonical topics of the 3 days before `date`, for "no repeat without a new development". */
function recentTopics(out, date) {
  const topics = [];
  for (let k = 1; k <= 3; k++) {
    const d = new Date(`${date}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() - k);
    const b = read(out, d.toISOString().slice(0, 10));
    if (!b) continue;
    for (const key of ['international', 'national', 'andhraPradesh']) for (const it of b.sections[key] || []) if (it.canonicalTopic) topics.push(it.canonicalTopic);
  }
  return [...new Set(topics)];
}

/** Generates, validates and saves the briefing for `date`. Returns the saved briefing. */
async function generate(date, { out, respond = AI.respond, log = console.log } = {}) {
  const t0 = Date.now();
  const instructions = P.systemPrompt(date);
  const recent = recentTopics(out, date);
  const sections = {};
  const citations = [];
  let model = '';
  for (const key of ['andhraPradesh', 'national', 'international']) {
    log(`${date}: researching ${P.SECTIONS[key].label}…`);
    const r = await respond({ instructions, input: P.sectionTask(date, key, recent), schema: P.SECTION_SCHEMA, name: `appsc_${key}`, search: true });
    sections[key] = (r.data.items || []).map((it) => ({ ...it, section: P.SECTIONS[key].label }));
    citations.push(...r.citations);
    model = r.model;
    log(`${date}: ${P.SECTIONS[key].label}: ${sections[key].length} items, ${r.searches.length} searches`);
  }
  // the newspaper's items read earlier today stay (after the web's, so a story in both keeps its web sources)
  const prev = read(out, date);
  for (const key of ['andhraPradesh', 'national', 'international']) sections[key].push(...((prev && prev.sections[key]) || []).filter((i) => i.paper));
  log(`${date}: editorial stage (coverage audit, MCQs, Mains)…`);
  const ed = await editorial(date, sections, respond);

  const b = {
    version: 1,
    date,
    title: `APPSC Group 1 & Group 2 Daily Current Affairs — ${date}`,
    model,
    generatedAt: new Date().toISOString(),
    sections,
    coverageAudit: ed.coverageAudit || [],
    mcqs: ed.mcqs || [],
    mainsQuestions: ed.mainsQuestions || [],
    citations: [...new Map(citations.map((c) => [c.url, c])).values()],
    ...(prev && prev.papers ? { papers: prev.papers } : {}),
  };
  const v = V.validate(b, recent);
  b.status = v.status;
  b.problems = v.problems;
  b.fixes = v.fixes;
  log(`${date}: Rocket-style notes…`);
  await N.addNotes(b, respond, log);
  b.durationMs = Date.now() - t0;
  b.sourceCount = new Set(['international', 'national', 'andhraPradesh'].flatMap((k) => b.sections[k].flatMap((i) => i.sources.map((s) => s.url)))).size;
  fs.mkdirSync(dir(out), { recursive: true });
  fs.writeFileSync(file(out, date), JSON.stringify(b, null, 1) + '\n');
  writeIndex(out);
  log(`${date}: ${b.status} · ${['international', 'national', 'andhraPradesh'].map((k) => `${k} ${b.sections[k].length}`).join(' · ')} · ${b.mcqs.length} MCQs · ${b.sourceCount} sources` +
    (v.problems.length ? ` · problems: ${v.problems.join('; ')}` : ''));
  return b;
}

/** feed/briefings/index.json: every briefing, newest first, with its status and counts. */
/** The editorial stage over a day's items: coverage audit, MCQs, Mains questions (no web search). */
async function editorial(date, sections, respond) {
  const all = [...sections.international, ...sections.national, ...sections.andhraPradesh];
  const r = await respond({ instructions: P.systemPrompt(date), input: P.editorialTask(date, all), schema: P.EDITORIAL_SCHEMA, name: 'appsc_editorial', search: false });
  return r.data;
}

const KEYS = ['international', 'national', 'andhraPradesh'];

/** One web-search pass over a saved day: adds the PYQ-heavy items it missed and the exam fields its items lack. */
async function topup(date, { out, respond = AI.respond, log = console.log } = {}) {
  const b = read(out, date);
  if (!b) return false;
  const items = KEYS.flatMap((k) => b.sections[k]);
  const r = await respond({ instructions: P.systemPrompt(date), input: P.topupTask(date, items), schema: P.TOPUP_SCHEMA, name: 'appsc_topup', search: true });
  let added = 0;
  let filled = 0;
  for (const a of r.data.additions || []) {
    const it = items[a.item - 1];
    const facts = (a.facts || []).map((f) => String(f).trim()).filter((f) => f && !(it && it.facts.includes(f)));
    const sources = (a.sources || []).filter((x) => /^https?:\/\//.test(x.url || ''));
    if (!it || !facts.length || !sources.length) continue;
    it.facts.push(...facts);
    for (const x of sources) if (!it.sources.some((y) => y.url === x.url)) it.sources.push(x);
    filled++;
  }
  for (const n of r.data.newItems || []) {
    if (!KEYS.includes(n.section) || !n.item) continue;
    b.sections[n.section].push({ ...n.item, section: P.SECTIONS[n.section].label, topup: true });
    added++;
  }
  // the validator drops new items without a source and any that duplicate an existing story
  const before = KEYS.reduce((s, k) => s + b.sections[k].length, 0);
  V.validate(b, []);
  const kept = added - (before - KEYS.reduce((s, k) => s + b.sections[k].length, 0));
  b.citations = [...new Map([...(b.citations || []), ...r.citations].map((c) => [c.url, c])).values()];
  b.sourceCount = new Set(KEYS.flatMap((k) => b.sections[k].flatMap((i) => i.sources.map((x) => x.url)))).size;
  b.toppedUp = new Date().toISOString();
  fs.writeFileSync(file(out, date), JSON.stringify(b, null, 1) + '\n');
  log(`${date}: top-up · ${kept} new items · ${filled} items given missing fields · ${r.searches.length} searches`);
  return true;
}

/** Redoes a saved day's MCQs, Mains questions, audit and notes with the current prompts (no new research). */
async function refresh(date, { out, respond = AI.respond, log = console.log } = {}) {
  const b = read(out, date);
  if (!b) return false;
  const ed = await editorial(date, b.sections, respond);
  b.coverageAudit = ed.coverageAudit || [];
  b.mcqs = ed.mcqs || [];
  b.mainsQuestions = ed.mainsQuestions || [];
  const v = V.validate(b, []);
  b.status = v.status;
  b.problems = v.problems;
  await N.addNotes(b, respond, log);
  b.refreshedAt = new Date().toISOString();
  fs.writeFileSync(file(out, date), JSON.stringify(b, null, 1) + '\n');
  log(`${date}: refreshed · ${b.status} · ${b.mcqs.length} MCQs`);
  return true;
}

/** Writes notes (Rocket form + blueprint angles) into a saved briefing. Returns false when skipped. */
async function addNotes(date, { out, respond = AI.respond, log = console.log, force = false } = {}) {
  const b = read(out, date);
  if (!b || (b.notesVersion && !force)) return false;
  await N.addNotes(b, respond, log);
  fs.writeFileSync(file(out, date), JSON.stringify(b, null, 1) + '\n');
  return true;
}

function writeIndex(out) {
  const d = dir(out);
  if (!fs.existsSync(d)) return [];
  const list = fs.readdirSync(d).filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f)).sort().reverse().map((f) => {
    const b = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8'));
    return {
      date: b.date,
      status: b.status,
      generatedAt: b.generatedAt,
      international: b.sections.international.length,
      national: b.sections.national.length,
      andhraPradesh: b.sections.andhraPradesh.length,
      mcqs: b.mcqs.length,
      sources: b.sourceCount || 0,
    };
  });
  fs.writeFileSync(path.join(d, 'index.json'), JSON.stringify({ updated: new Date().toISOString(), briefings: list }, null, 1) + '\n');
  return list;
}

module.exports = { generate, addNotes, refresh, editorial, topup, writeIndex, recentTopics, read };
