'use strict';
// Weekly (Monday-Sunday) and monthly digests, written as small files
// (feed/digests/week-2026-09-28.json, month-2026-10.json) so a period can be
// revised long after its day files have left the phone: every topic once, by
// book, with its stories in the period and its static notes, then the
// period's one-liners and quiz.

const fs = require('fs');
const path = require('path');

const BOOKS = ['History & Culture', 'Polity, Society & IR', 'Economy', 'Geography', 'Science, Tech & Environment', 'Current Affairs'];

const iso = (d) => d.toISOString().slice(0, 10);
function weekStart(date) {
  const d = new Date(`${date}T00:00:00Z`);
  const back = (d.getUTCDay() + 6) % 7; // Monday = 0
  d.setUTCDate(d.getUTCDate() - back);
  return iso(d);
}
const monthStart = (date) => `${date.slice(0, 7)}-01`;

const LEAD_KEYS = ['id', 'date', 'title', 'summary', 'source', 'url', 'ap', 'scope', 'exams', 'subject', 'units', 'band', 'score', 'top', 'thread', 'lang'];
const pick = (o, keys) => Object.fromEntries(keys.filter((k) => o[k] !== undefined).map((k) => [k, o[k]]));

/** One period's digest from its days (and every day, to find where each topic started). */
function digestOf(kind, start, days, allLeads) {
  const items = days.flatMap((d) => d.items).filter((i) => !i.topicOf && !i.digest);
  const byThread = new Map();
  for (const it of items.filter((i) => !i.oneLiner)) {
    const t = it.thread || it.id;
    if (!byThread.has(t)) byThread.set(t, []);
    byThread.get(t).push(it);
  }
  const topics = [...byThread.entries()].map(([t, list]) => {
    const all = allLeads.filter((i) => (i.thread || i.id) === t).sort((a, b) => a.date.localeCompare(b.date));
    const lead = all[0] || list[0];
    const withNotes = [...all, ...list].find((i) => i.brief && (i.brief.sections || []).length);
    list.sort((a, b) => a.date.localeCompare(b.date));
    return {
      thread: t,
      main: list.some((i) => i.top),
      lead: pick(lead, LEAD_KEYS),
      stories: list.map((i) => pick({ ...i, line: i.line || i.title }, ['id', 'date', 'title', 'line', 'url', 'top', 'source'])),
      sections: withNotes ? withNotes.brief.sections : [],
      score: Math.max(...list.map((i) => i.score || 0)),
    };
  });
  topics.sort((a, b) => (b.main ? 1 : 0) - (a.main ? 1 : 0) || b.score - a.score);
  return {
    kind,
    start,
    days: days.map((d) => d.date),
    books: BOOKS,
    topics,
    oneLiners: items.filter((i) => i.oneLiner).map((i) => pick(i, ['id', 'date', 'title', 'line', 'source', 'url'])),
    quiz: days.flatMap((d) => d.quiz || []),
  };
}

/** Writes every week's and month's digest; returns [{ kind, start, topics, main }] for the index. */
function writeDigests(out) {
  const dir = path.join(out, 'days');
  const days = fs
    .readdirSync(dir)
    .filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f))
    .sort()
    .map((f) => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')));
  const allLeads = days.flatMap((d) => d.items).filter((i) => !i.topicOf && !i.digest);
  const periods = new Map();
  for (const d of days) {
    for (const [kind, start] of [['week', weekStart(d.date)], ['month', monthStart(d.date)]]) {
      const key = `${kind}-${kind === 'month' ? start.slice(0, 7) : start}`;
      if (!periods.has(key)) periods.set(key, { kind, start, days: [] });
      periods.get(key).days.push(d);
    }
  }
  const outDir = path.join(out, 'digests');
  fs.mkdirSync(outDir, { recursive: true });
  const list = [];
  for (const [key, p] of periods) {
    const dg = digestOf(p.kind, p.start, p.days, allLeads);
    const file = path.join(outDir, `${key}.json`);
    const text = JSON.stringify(dg) + '\n';
    if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8') !== text) fs.writeFileSync(file, text);
    list.push({ kind: p.kind, start: p.start, file: `digests/${key}.json`, topics: dg.topics.length, main: dg.topics.filter((t) => t.main).length });
  }
  return list.sort((a, b) => b.start.localeCompare(a.start) || a.kind.localeCompare(b.kind));
}

module.exports = { writeDigests, digestOf, weekStart, monthStart };
