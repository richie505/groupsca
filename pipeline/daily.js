#!/usr/bin/env node
'use strict';

// One day's current affairs, from the internet to feed/days/<date>.json.
//
//   node pipeline/daily.js                      # today (IST), live sources
//   node pipeline/daily.js --date 2026-10-02    # a given day
//   node pipeline/daily.js --dry-run            # print, write nothing
//   node pipeline/daily.js --fixtures pipeline/test/fixtures --out /tmp/feed   # offline
//
// Stages, none of which uses a model:
//   1. Fetch    PIB's release listing + the RSS feeds in sources.js
//   2. Clean    headline noise filter, date window, one copy per story,
//               nothing already published on an earlier run
//   3. Read     the full text of PIB releases (official, so worth quoting)
//   4. Score    syllabus units, blueprint angles, AP, importance, both exams
//   5. Keep     score >= 40, or >= 30 for Andhra Pradesh, with a daily AP floor
//   6. Write    key facts + summary from the article's own sentences, then
//               the day file and feed/index.json, which the app downloads
//
// Runs twice a day from .github/workflows/daily.yml. Each run adds to the day
// files rather than replacing them, so the evening run picks up what the
// morning one could not yet see.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const { SOURCES, isNoise } = require('./sources');
const F = require('./lib/fetch');
const S = require('./lib/score');
const { keyFacts, summarise } = require('./lib/facts');

const MIN_SCORE = 40;
// APPSC sets this exam: an Andhra Pradesh story already earns 20 for being
// AP, so it needs less on top — but it must be ABOUT something examinable
// (see examinable()), or every municipal notice from the AP desks gets in.
const AP_MIN_SCORE = 35;
// On a heavy national news day the AP desk must not be crowded out. If fewer
// than this many AP items reach AP_MIN_SCORE, the best AP items that still
// carry SOME syllabus, blueprint or official-act signal are added up to it.
const AP_FLOOR = 15;
// Per day and per side (AP / everything else), so the day stays readable.
const MAX_PER_SIDE = 60;
const PIB_BODY_LIMIT = 120;
const BODY_CONCURRENCY = 6;

function parseArgs(argv) {
  const a = { date: null, dryRun: false, fixtures: null, out: path.join(__dirname, '..', 'feed') };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--date') a.date = argv[++i];
    else if (argv[i] === '--dry-run') a.dryRun = true;
    else if (argv[i] === '--fixtures') a.fixtures = argv[++i];
    else if (argv[i] === '--out') a.out = argv[++i];
  }
  return a;
}

// India's calendar day, whatever the runner's clock says.
function todayIst(now = Date.now()) {
  return new Date(now + 5.5 * 3600 * 1000).toISOString().slice(0, 10);
}

function addDays(iso, n) {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

const idOf = (url) => crypto.createHash('sha1').update(url).digest('hex').slice(0, 12);

async function pool(items, n, fn) {
  const out = new Array(items.length);
  let next = 0;
  await Promise.all(
    Array.from({ length: Math.min(n, items.length) }, async () => {
      while (next < items.length) {
        const i = next++;
        out[i] = await fn(items[i], i);
      }
    })
  );
  return out;
}

// ---------------------------------------------------------------------------
// 1. fetch
// ---------------------------------------------------------------------------

function loader(fixtures) {
  if (!fixtures) return (url) => F.fetchText(url);
  // Offline: <fixtures>/<source id>.xml|.html, and pib-<PRID>.html for releases.
  return async (url, src) => {
    const prid = (url.match(/PRID=(\d+)/) || [])[1];
    const names = src ? [`${src.id}.xml`, `${src.id}.html`] : prid ? [`pib-${prid}.html`] : [];
    for (const n of names) {
      const p = path.join(fixtures, n);
      if (fs.existsSync(p)) return fs.readFileSync(p, 'utf8');
    }
    throw new Error('no fixture');
  };
}

async function fetchAll(load) {
  const results = await Promise.all(
    SOURCES.map(async (src) => {
      try {
        const body = await load(src.url, src);
        const rows = src.kind === 'pib-index' ? F.parsePibIndex(body) : F.parseRss(body);
        return { src, rows, error: rows.length ? null : 'no items parsed' };
      } catch (e) {
        return { src, rows: [], error: e.message };
      }
    })
  );
  const status = results.map(({ src, rows, error }) => ({ id: src.id, name: src.name, found: rows.length, error }));
  const items = results.flatMap(({ src, rows }) =>
    rows.map((r) => ({
      ...r,
      sourceId: src.id,
      sourceName: src.name,
      primary: !!src.primary,
      apSource: !!src.ap,
      opinion: !!src.opinion,
    }))
  );
  return { items, status };
}

// ---------------------------------------------------------------------------
// files
// ---------------------------------------------------------------------------

function dayPath(out, date) {
  return path.join(out, 'days', `${date}.json`);
}

function readDay(out, date) {
  try {
    return JSON.parse(fs.readFileSync(dayPath(out, date), 'utf8'));
  } catch {
    return null;
  }
}

function writeIndex(out) {
  const dir = path.join(out, 'days');
  const days = fs
    .readdirSync(dir)
    .filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f))
    .sort()
    .reverse()
    .map((f) => {
      const d = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
      const count = (b) => d.items.filter(b).length;
      return {
        date: d.date,
        updated: d.updated,
        count: d.items.length,
        ap: count((i) => i.ap),
        critical: count((i) => i.band === 'critical'),
        high: count((i) => i.band === 'high'),
      };
    });
  const index = { version: 1, updated: new Date().toISOString(), days };
  fs.writeFileSync(path.join(out, 'index.json'), JSON.stringify(index, null, 1) + '\n');
  return index;
}

// ---------------------------------------------------------------------------
// selection
// ---------------------------------------------------------------------------

// A syllabus unit in the headline, a blueprint angle, or an official act.
function examinable(r) {
  return r.anchored || r.angles.length > 0 || r.why.importance > 0;
}

function select(scored) {
  const live = scored.filter((x) => !x.result.vetoed);
  const keep = live.filter((x) =>
    x.result.ap ? x.result.score >= AP_MIN_SCORE && examinable(x.result) : x.result.score >= MIN_SCORE
  );
  const apKept = keep.filter((x) => x.result.ap).length;
  if (apKept < AP_FLOOR) {
    const extra = live
      .filter((x) => x.result.ap && !keep.includes(x))
      .filter((x) => examinable(x.result))
      .sort((a, b) => b.result.score - a.result.score)
      .slice(0, AP_FLOOR - apKept);
    for (const x of extra) x.floor = true;
    keep.push(...extra);
  }
  return keep;
}

function toItem({ article, result, floor }) {
  const text = `${article.summary || ''}\n${article.body || ''}`;
  return {
    id: idOf(article.url),
    date: article.date,
    title: article.headline,
    summary: summarise(article.primary && article.body ? article.body : article.summary || article.body),
    facts: keyFacts(text.trim() ? text : article.headline),
    source: article.sourceName,
    sourceId: article.sourceId,
    official: article.primary,
    ministry: article.primary ? article.category || '' : '',
    alsoIn: article.alsoIn || [],
    url: article.url,
    bucket: result.bucket,
    scope: result.scope,
    ap: result.ap,
    exams: result.exams,
    subjects: result.subjects,
    units: result.units,
    topics: result.topics,
    angles: result.angles,
    score: result.score,
    band: result.band,
    why: result.why,
    ...(floor ? { apFloor: true } : {}),
  };
}

function capDay(items) {
  const order = (a, b) => b.score - a.score || a.title.localeCompare(b.title);
  const ap = items.filter((i) => i.ap).sort(order).slice(0, MAX_PER_SIDE);
  const rest = items.filter((i) => !i.ap).sort(order).slice(0, MAX_PER_SIDE);
  return [...ap, ...rest].sort(order);
}

// ---------------------------------------------------------------------------
// run
// ---------------------------------------------------------------------------

async function run(args) {
  const date = args.date || todayIst();
  const window = new Set([addDays(date, -1), date]);
  const load = loader(args.fixtures);
  const vocab = S.loadVocab();

  const { items: fetched, status } = await fetchAll(load);
  let items = fetched.filter((i) => window.has(i.date));
  const inWindow = items.length;
  items = items.filter((i) => !isNoise(i.headline));
  const afterNoise = items.length;
  items = F.dedupe(items);
  const afterDedupe = items.length;

  // Already published on an earlier run (this day or the two before it).
  const earlier = [];
  for (let k = -3; k <= 0; k++) {
    const d = readDay(args.out, addDays(date, k));
    if (d) earlier.push(...d.items);
  }
  const seenUrl = new Set(earlier.map((i) => i.url));
  const seenTitle = earlier.map((i) => i.title);
  items = items.filter(
    (i) => !seenUrl.has(i.url) && !seenTitle.some((t) => F.overlap(t, i.headline) >= 0.75)
  );
  const fresh = items.length;

  // 3. PIB release text. Official, so it is both quotable and the best
  //    evidence for scoring; newspapers are scored on headline + feed summary.
  const pib = items.filter((i) => i.primary).slice(0, PIB_BODY_LIMIT);
  let bodies = 0;
  await pool(pib, args.fixtures ? 1 : BODY_CONCURRENCY, async (it) => {
    try {
      it.body = F.releaseBody(await load(it.url), it.headline);
      if (it.body) bodies++;
    } catch {
      it.body = '';
    }
  });

  // 4-5. score and keep
  const scored = items.map((article) => ({ article, result: S.score(article, vocab) }));
  const kept = select(scored).map(toItem);
  const vetoed = scored.filter((x) => x.result.vetoed).length;

  const report = {
    date, fetched: fetched.length, inWindow, afterNoise, afterDedupe, fresh, pibBodies: bodies,
    vetoed, kept: kept.length, keptAp: kept.filter((i) => i.ap).length,
    failedSources: status.filter((s) => s.error).map((s) => `${s.id}: ${s.error}`),
  };

  if (args.dryRun) return { report, kept, scored };

  // 6. merge into the day files (items are filed under their own date)
  const now = new Date().toISOString();
  const byDate = new Map();
  for (const it of kept) byDate.set(it.date, [...(byDate.get(it.date) || []), it]);
  // Today's file exists even on a quiet run, so the app always has a "today".
  if (!byDate.has(date)) byDate.set(date, []);
  fs.mkdirSync(path.join(args.out, 'days'), { recursive: true });
  for (const [d, add] of byDate) {
    const prev = readDay(args.out, d);
    const have = new Set((prev ? prev.items : []).map((i) => i.id));
    const merged = capDay([...(prev ? prev.items : []), ...add.filter((i) => !have.has(i.id))]);
    const day = { version: 1, date: d, updated: now, sources: status, items: merged };
    fs.writeFileSync(dayPath(args.out, d), JSON.stringify(day, null, 1) + '\n');
  }
  writeIndex(args.out);
  return { report, kept, scored };
}

function printReport({ report, kept }, dryRun) {
  const lines = [
    `## Current affairs ${report.date}${dryRun ? ' (dry run)' : ''}`,
    '',
    `fetched ${report.fetched} · in window ${report.inWindow} · after noise ${report.afterNoise} · ` +
      `one per story ${report.afterDedupe} · new ${report.fresh} · PIB texts ${report.pibBodies}`,
    `vetoed ${report.vetoed} · **kept ${report.kept}** (Andhra Pradesh ${report.keptAp})`,
  ];
  if (report.failedSources.length) lines.push('', `Sources that failed: ${report.failedSources.join('; ')}`);
  lines.push('', '| score | bucket | title | units |', '|---|---|---|---|');
  for (const i of [...kept].sort((a, b) => b.score - a.score).slice(0, 40)) {
    lines.push(`| ${i.score} | ${i.bucket} | ${i.title.replace(/\|/g, '/')} | ${i.units.map((u) => u.code).join(', ')} |`);
  }
  const text = lines.join('\n');
  console.log(text);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, text + '\n');
}

if (require.main === module) {
  const args = parseArgs(process.argv);
  run(args)
    .then((r) => printReport(r, args.dryRun))
    .catch((e) => {
      console.error(e);
      process.exit(1);
    });
}

module.exports = { run, select, todayIst, addDays, MIN_SCORE, AP_MIN_SCORE, AP_FLOOR };
