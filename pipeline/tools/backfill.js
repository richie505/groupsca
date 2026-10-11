#!/usr/bin/env node
'use strict';
// Collects past news days the live feeds no longer list, from sources that keep a dated archive:
//   - The Hindu and BusinessLine: their daily archive sitemaps (every article of a day, with its time)
//   - PIB: release pages by number (PRID), which run in order through the days
//   - AIR News: its WordPress post sitemaps, when it has them
// and runs each day through the same scoring, exam filter and static notes as the daily run.
//
//   node pipeline/tools/backfill.js --from 2026-09-21 --to 2026-09-30
const F = require('../lib/fetch');
const { run, addDays, pool } = require('../daily');

const arg = (name, dflt) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : dflt;
};
const FROM = arg('from', '2026-09-21');
const TO = arg('to', '2026-09-30');
const OUT = arg('out', require('path').join(__dirname, '..', '..', 'feed'));
const ymd = (d) => d.replace(/-/g, '');

// Archive sections and the live source each stands for (pipeline/sources.js).
const HINDU_SECTIONS = [
  [/\/news\/national\/andhra-pradesh\//, 'hindu-ap', 'The Hindu — Andhra Pradesh', true],
  [/\/news\/cities\/Visakhapatnam\//i, 'hindu-vizag', 'The Hindu — Visakhapatnam', true],
  [/\/news\/cities\/Vijayawada\//i, 'hindu-vijayawada', 'The Hindu — Vijayawada', true],
  [/\/news\/national\/telangana\//, 'hindu-telangana', 'The Hindu — Telangana', false],
  [/\/news\/national\/[^/]+\/article/, 'hindu-national', 'The Hindu — National', false],
  [/\/news\/international\//, 'hindu-international', 'The Hindu — International', false],
  [/\/business\/Economy\//i, 'hindu-economy', 'The Hindu — Economy', false],
  [/\/sci-tech\/energy-and-environment\//, 'hindu-environment', 'The Hindu — Environment', false],
  [/\/sci-tech\//, 'hindu-scitech', 'The Hindu — Science', false],
  [/\/opinion\/editorial\//, 'hindu-editorial', 'The Hindu — Editorial', false],
];
const BL_SECTIONS = [
  [/\/economy\//, 'bl-economy', 'BusinessLine — Economy', false],
  [/\/news\/national\//, 'bl-economy', 'BusinessLine — Economy', false],
];

async function sitemapDay(base, date, sections) {
  const rows = [];
  for (let n = 1; n <= 6; n++) {
    let xml;
    try {
      xml = await F.fetchText(`${base}/sitemap/archive/all/${ymd(date)}_${n}.xml`);
    } catch {
      break;
    }
    const got = F.parseSitemap(xml);
    if (!got.length) break;
    for (const r of got) {
      const s = sections.find(([re]) => re.test(r.url));
      if (!s) continue;
      rows.push({ ...r, sourceId: s[1], sourceName: s[2], apSource: s[3], primary: false, opinion: s[1] === 'hindu-editorial', lang: 'en' });
    }
  }
  return rows;
}

/** A page's title and description, for archive rows that have only a URL. */
async function withMeta(rows) {
  await pool(rows, 6, async (r) => {
    try {
      const m = F.pageMeta(await F.fetchText(r.url));
      if (m.title) r.headline = m.title.replace(/\s+-\s+The Hindu( BusinessLine)?\s*$/i, '').trim();
      if (m.description) r.summary = m.description.slice(0, 700);
    } catch {
      // the URL's own words stay as the headline
    }
  });
  return rows;
}

// ---------------------------------------------------------------------------
// AIR News, through its WordPress API
// ---------------------------------------------------------------------------

async function airDay(date) {
  const src = require('../sources').SOURCES.find((x) => x.id === 'air');
  const rows = [];
  for (let page = 1; page <= 8; page++) {
    const url = `${src.url}&after=${date}T00:00:00&before=${addDays(date, 1)}T06:00:00&page=${page}`;
    let got;
    try {
      got = F.parseWpPosts(await F.fetchText(url));
    } catch {
      break;
    }
    for (const r of got) {
      const cat = (r.categories || []).map((c) => src.categories[c]).find(Boolean);
      if (cat) rows.push({ ...r, sourceId: cat[0], sourceName: cat[1], primary: true, apSource: false, lang: 'en' });
    }
    if (got.length < 100) break;
  }
  return rows;
}

// ---------------------------------------------------------------------------
// PIB, by release number
// ---------------------------------------------------------------------------

const PIB_URL = (id) => `https://pib.gov.in/PressReleasePage.aspx?PRID=${id}`;
const PIB_REGIONS = { Delhi: ['pib', 'PIB', false], Vijayawada: ['pib-vijayawada', 'PIB Vijayawada', true], Hyderabad: ['pib-hyderabad', 'PIB Hyderabad', false] };

async function pibPage(id) {
  try {
    const html = await F.fetchText(PIB_URL(id));
    const posted = F.pibPosted(html);
    const title = F.decode(((html.match(/<h2[^>]*>([\s\S]*?)<\/h2>/i) || [])[1] || '').replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim();
    const region = (html.match(/Posted On:[^<]*?by\s+PIB\s+([A-Za-z]+)/i) || [])[1] || '';
    const ministry = F.decode(((html.match(/class="[^"]*MinistryNameSubhead[^"]*"[^>]*>([\s\S]*?)<\//i) || [])[1] || '').replace(/<[^>]+>/g, ' ')).trim();
    return { id, html, posted, title, region, ministry };
  } catch {
    return { id, posted: null };
  }
}

/** The first release number posted on or after `date` (binary search on the posted dates). */
async function firstPrid(date, lo, hi) {
  while (hi - lo > 20) {
    const mid = Math.floor((lo + hi) / 2);
    // a missing number: look at the next few
    let p = null;
    for (let k = 0; k < 8 && !p; k++) p = (await pibPage(mid + k)).posted;
    if (!p || p.date >= date) hi = mid;
    else lo = mid;
  }
  return lo;
}

async function pibDays(from, to, hint) {
  const start = await firstPrid(from, hint - 9000, hint);
  const rows = [];
  let id = start;
  let misses = 0;
  const stop = addDays(to, 1);
  for (;;) {
    const batch = Array.from({ length: 40 }, (_, k) => id + k);
    const pages = [];
    await pool(batch, 8, async (n) => pages.push(await pibPage(n)));
    id += batch.length;
    let past = 0;
    for (const p of pages) {
      if (!p.posted) {
        misses++;
        continue;
      }
      misses = 0;
      if (p.posted.date > stop) past++;
      const region = PIB_REGIONS[p.region];
      // English releases of Delhi, Vijayawada and Hyderabad only (no Hindi or other-language copies)
      if (!region || !p.title || /[ऀ-ॿ]/.test(p.title)) continue;
      rows.push({
        headline: p.title,
        url: PIB_URL(p.id),
        date: p.posted.date,
        time: p.posted.time,
        pubDate: p.posted.date,
        summary: '',
        body: F.releaseBody(p.html, p.title),
        category: p.ministry,
        sourceId: region[0],
        sourceName: region[1],
        apSource: region[2],
        primary: true,
        lang: 'en',
      });
    }
    if (past > 20 || misses > 300) break;
  }
  return rows;
}

async function main() {
  const hint = Number(arg('prid', '2317443'));
  console.log(`Backfill ${FROM} .. ${TO}`);
  const pib = await pibDays(FROM, TO, hint);
  console.log(`PIB: ${pib.length} English releases`);
  let date = FROM;
  while (date <= TO) {
    // a news day runs 06:00 to 05:59 IST: its articles are in its own and the next day's archive
    const papers = [];
    for (const d of [date, addDays(date, 1)]) {
      papers.push(...(await sitemapDay('https://www.thehindu.com', d, HINDU_SECTIONS)));
      papers.push(...(await sitemapDay('https://www.thehindubusinessline.com', d, BL_SECTIONS)));
    }
    const seen = new Set();
    const day = papers.filter((r) => !seen.has(r.url) && seen.add(r.url));
    // the day's own window only, before the slower page reads
    const inDay = day.filter((r) => F.newsDay({ date: r.date, time: r.time }) === date);
    await withMeta(inDay);
    const air = (await airDay(date)).filter((r) => F.newsDay({ date: r.date, time: r.time }) === date);
    const items = [...inDay, ...air, ...pib.filter((r) => F.newsDay({ date: r.date, time: r.time }) === date)].map((r) => ({
      ...r,
      pubDate: r.pubDate || r.date,
    }));
    const status = [
      { id: 'hindu-archive', name: 'The Hindu (archive)', found: inDay.filter((r) => r.sourceId.startsWith('hindu')).length, error: null },
      { id: 'bl-archive', name: 'BusinessLine (archive)', found: inDay.filter((r) => r.sourceId.startsWith('bl')).length, error: null },
      { id: 'air-archive', name: 'AIR News (archive)', found: air.length, error: null },
      { id: 'pib-archive', name: 'PIB (by release number)', found: items.filter((r) => r.sourceId.startsWith('pib')).length, error: null },
    ];
    const r = await run({ date, out: OUT, dryRun: false, prefetched: { items, status } });
    console.log(`${date}: ${items.length} articles -> kept ${r.kept.length}`);
    date = addDays(date, 1);
  }
}

if (require.main === module) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}

module.exports = { sitemapDay, pibPage, firstPrid };
