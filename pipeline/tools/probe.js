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
    if (!rows.length) {
      const links = [...body.matchAll(/href="([^"]*(?:rss|feed)[^"]*)"/gi)].map((m) => m[1]).slice(0, 8);
      if (links.length) console.log(`     feed links on page: ${[...new Set(links)].join(' ')}`);
    }
  } catch (e) {
    console.log(`FAIL ${url} — ${e.message}`);
  }
}

async function pibRegions() {
  for (let reg = 1; reg <= 40; reg++) {
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

const args = process.argv.slice(2).concat((process.env.PROBE_URLS || '').split(/\s+/).filter(Boolean));
(async () => {
  if (args.includes('--pib-regions')) await pibRegions();
  for (const u of args.filter((a) => !a.startsWith('--'))) await probe(u);
})();
