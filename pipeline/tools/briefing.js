#!/usr/bin/env node
'use strict';
// Generates APPSC daily briefings (pipeline/ai). Server side only: needs OPENAI_API_KEY.
//   node pipeline/tools/briefing.js                      today (IST); skipped if already published
//   node pipeline/tools/briefing.js --date 2026-10-11    one day
//   node pipeline/tools/briefing.js --from 2026-09-01 --to 2026-10-10   a range (published days skipped)
//   add --force to regenerate published days
const path = require('path');
const B = require('../ai/briefing');
const { todayIst, addDays } = require('../daily');

const arg = (name) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : '';
};
const force = process.argv.includes('--force');
const out = arg('out') || path.join(__dirname, '..', '..', 'feed');

async function main() {
  const from = arg('from') || arg('date') || todayIst();
  const to = arg('to') || arg('date') || from;
  let failed = 0;
  for (let d = from; d <= to; d = addDays(d, 1)) {
    const have = B.read(out, d);
    if (have && have.status === 'published' && !force) {
      console.log(`${d}: already published, skipped`);
      continue;
    }
    try {
      await B.generate(d, { out });
    } catch (e) {
      failed++;
      console.error(`${d}: FAILED ${e.message}`);
    }
  }
  B.writeIndex(out);
  // a failure makes the run fail, so the scheduled retries try again
  if (failed) process.exit(1);
}

main();
