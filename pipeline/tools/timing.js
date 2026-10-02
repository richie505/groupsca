#!/usr/bin/env node
'use strict';

// When does each source publish? Reads every source in sources.js and reports,
// per source, the IST publish times of what its feed currently holds (usually
// the last one to three days), so the collection times and the "today" rule
// can be set from evidence rather than guesswork.
//
//   node pipeline/tools/timing.js            # print the report
//   node pipeline/tools/timing.js --write    # also write ops/publish-times.md and .json

const fs = require('fs');
const path = require('path');
const F = require('../lib/fetch');
const { SOURCES } = require('../sources');
const D = require('../daily');

const BANDS = [
  ['00-06', 0, 6],
  ['06-12', 6, 12],
  ['12-18', 12, 18],
  ['18-24', 18, 24],
];

function summarise(rows) {
  const timed = rows.filter((r) => r.time);
  const byDate = new Map();
  for (const r of timed) byDate.set(r.date, [...(byDate.get(r.date) || []), r.time]);
  const hours = new Array(24).fill(0);
  for (const r of timed) hours[Number(r.time.slice(0, 2))]++;
  const bands = BANDS.map(([label, a, b]) => [label, hours.slice(a, b).reduce((x, y) => x + y, 0)]);
  const days = [...byDate.entries()]
    .sort((a, b) => (a[0] < b[0] ? 1 : -1))
    .map(([date, times]) => ({ date, count: times.length, first: times.sort()[0], last: times.sort()[times.length - 1] }));
  const peak = hours
    .map((n, h) => [h, n])
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .filter(([, n]) => n > 0)
    .map(([h]) => `${String(h).padStart(2, '0')}:00`);
  return { items: rows.length, timed: timed.length, days, bands, peak, hours };
}

(async () => {
  const now = new Date();
  const today = D.todayIst(now.getTime());
  const load = D.loader(null);
  const { items, status } = await D.fetchAll(load, today);

  // GKToday's sitemap has no times: read each post's page for its publish time.
  const gk = items.filter((i) => SOURCES.find((s) => s.id === i.sourceId && s.fetchMeta));
  await D.pool(gk, 4, async (it) => {
    try {
      const m = F.pageMeta(await load(it.url));
      if (m.published) Object.assign(it, { date: m.published.date, time: m.published.time });
    } catch {
      /* left untimed */
    }
  });

  const report = {};
  for (const s of status) {
    report[s.id] = { name: s.name, error: s.error, ...summarise(items.filter((i) => i.sourceId === s.id)) };
  }

  const lines = [
    `# When each source publishes (IST)`,
    '',
    `Measured ${now.toISOString().replace('T', ' ').slice(0, 16)} UTC by pipeline/tools/timing.js from what each`,
    `feed held at that moment. "Last" is the latest publish time seen for that day.`,
    '',
    '| Source | Items | Days in feed (date: count, first–last) | 00–06 | 06–12 | 12–18 | 18–24 | Peak hours |',
    '|---|---|---|---|---|---|---|---|',
  ];
  for (const [id, r] of Object.entries(report)) {
    if (r.error) {
      lines.push(`| ${r.name} | – | failed: ${r.error} | | | | | |`);
      continue;
    }
    const days = r.days.slice(0, 4).map((d) => `${d.date.slice(5)}: ${d.count}, ${d.first}–${d.last}`).join('<br>') || (r.items ? 'no times given' : '');
    lines.push(`| ${r.name} | ${r.items} | ${days} | ${r.bands.map(([, n]) => n).join(' | ')} | ${r.peak.join(', ')} |`);
  }
  const md = lines.join('\n') + '\n';
  console.log(md);
  if (process.argv.includes('--write')) {
    const dir = path.join(__dirname, '..', '..', 'ops');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'publish-times.md'), md);
    fs.writeFileSync(path.join(dir, 'publish-times.json'), JSON.stringify({ measured: now.toISOString(), today, report }, null, 1) + '\n');
  }
})();
