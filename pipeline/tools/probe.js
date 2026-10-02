#!/usr/bin/env node
'use strict';

// Tries candidate feed URLs from where the daily job runs (GitHub Actions) and
// reports what each returns, so a new source is added only once it is known
// to work there.
//
//   node pipeline/tools/probe.js <url> [<url> ...]
//   node pipeline/tools/probe.js --pib-regions      # which PIB reg= ids exist, by name

const F = require('../lib/fetch');

async function probe(url) {
  try {
    const body = await F.fetchText(url, { timeoutMs: 20000 });
    const rss = F.parseRss(body);
    const pib = F.parsePibIndex(body);
    const title = (body.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1] || '';
    const rows = rss.length ? rss : pib;
    console.log(`OK   ${url}\n     ${body.length} bytes · rss ${rss.length} · pib ${pib.length} · title "${F.decode(title).slice(0, 80)}"`);
    for (const r of rows.slice(0, 3)) console.log(`     - ${r.date} ${r.headline.slice(0, 100)}`);
    if (args.includes('--links')) {
      // Article-looking links and the markup around the first few, to write a parser.
      const links = [...body.matchAll(/<a[^>]+href="([^"]+)"[^>]*>([^<]{30,200})<\/a>/gi)].slice(0, 25);
      for (const l of links) console.log(`     link ${l[1]} | ${F.decode(l[2])}`);
      const i = links.length ? body.indexOf(links[0][0]) : -1;
      if (i > 0) console.log('     context: ' + body.slice(Math.max(0, i - 600), i + 1500).replace(/\s+/g, ' '));
    }
    if (process.env.PROBE_RAW || args.includes('--raw')) console.log(body.slice(0, 1500).replace(/\s+/g, ' '));
    if (!rows.length) {
      const links = [...body.matchAll(/href="([^"]*(?:rss|feed)[^"]*)"/gi)].map((m) => m[1]).slice(0, 8);
      if (links.length) console.log(`     feed links on page: ${[...new Set(links)].join(' ')}`);
    }
  } catch (e) {
    console.log(`FAIL ${url} — ${e.message}`);
  }
}

async function pibRegions(from = 1, to = 40) {
  for (let reg = from; reg <= to; reg++) {
    await new Promise((r) => setTimeout(r, 300));
    const url = `https://www.pib.gov.in/allrelease.aspx?reg=${reg}&lang=1`;
    try {
      const body = await F.fetchText(url, { timeoutMs: 20000 });
      const rows = F.parsePibIndex(body);
      const region = (body.match(/(PIB\s+[A-Z][A-Za-z]+(?:\s[A-Z][a-z]+)?)/g) || []).slice(0, 6);
      const sel = (body.match(/<option[^>]*selected[^>]*>([^<]*)</i) || [])[1] || '';
      console.log(`reg=${reg}: ${rows.length} releases · selected "${sel.trim()}" · ${[...new Set(region)].join(', ')}`);
      for (const r of rows.slice(0, 2)) console.log(`     - ${r.category.slice(0, 40)} | ${r.headline.slice(0, 90)}`);
    } catch (e) {
      console.log(`reg=${reg}: FAIL ${e.message}`);
    }
  }
}

// --explain=<source id>[,<id>]: every item the source returns today, with its
// score and why, to tune the vocabulary against real headlines.
async function explain(ids) {
  const { SOURCES, isNoise } = require('../sources');
  const S = require('../lib/score');
  const vocab = S.loadVocab();
  for (const src of SOURCES.filter((x) => ids.includes(x.id))) {
    let rows = [];
    try {
      const body = await F.fetchText(src.url);
      rows = src.kind === 'pib-index' ? F.parsePibIndex(body)
        : src.kind === 'sitemap' ? F.parseSitemap(body, { pathIncludes: src.pathIncludes })
        : F.parseRss(body);
      if (!rows.length) console.log(body.slice(0, 800).replace(/\s+/g, ' '));
    } catch (e) {
      console.log(`${src.id}: FAIL ${e.message}`);
      continue;
    }
    console.log(`== ${src.id}: ${rows.length} items`);
    for (const r of rows.slice(0, 120)) {
      if (isNoise(r.headline)) { console.log(`  noise  ${r.date} ${r.headline.slice(0, 100)}`); continue; }
      const x = S.score({ ...r, apSource: !!src.ap, primary: !!src.primary }, vocab);
      const tag = x.vetoed ? `veto ${x.vetoed}` : `${x.score} ${x.anchored ? 'A' : '-'} [${x.angles.join(',')}] {${x.units.map((u) => u.code).join(',')}}`;
      console.log(`  ${tag}  ${r.date} ${r.headline.slice(0, 100)}`);
    }
  }
}

const args = process.argv.slice(2).concat((process.env.PROBE_URLS || '').split(/\s+/).filter(Boolean));
(async () => {
  const ex = args.find((a) => a.startsWith('--explain='));
  if (ex) await explain(ex.slice('--explain='.length).split(','));
  if (args.includes('--pib-regions')) await pibRegions();
  if (args.includes('--pib-regions-high')) await pibRegions(41, 80);
  for (const u of args.filter((a) => !a.startsWith('--'))) await probe(u);
})();
