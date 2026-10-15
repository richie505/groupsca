#!/usr/bin/env node
'use strict';
// Generates APPSC daily briefings (pipeline/ai). Server side only: needs OPENAI_API_KEY.
//   node pipeline/tools/briefing.js                      today (IST); skipped if already published
//   node pipeline/tools/briefing.js --date 2026-10-11    one day
//   node pipeline/tools/briefing.js --from 2026-09-01 --to 2026-10-10   a range (published days skipped)
//   add --force to regenerate published days
//   --refresh (with --date or --from/--to): redo MCQs, Mains questions and notes of saved days, 4 days at a time
//   --topup (with --refresh): first one web search per day for missed appointments, awards, sports and exam fields
//   --news-notes (with --date or --from/--to): Rocket-form notes for the News feed's New-today stories
//   --notes (with --date or --from/--to): only add the Rocket-style notes to saved briefings, 4 days at a time
const path = require('path');
const B = require('../ai/briefing');
const AI = require('../ai/openai');
const NN = require('../ai/newsnotes');
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
  const refresh = process.argv.includes('--refresh');
  const topupToo = process.argv.includes('--topup');
  if (process.argv.includes('--news-notes')) {
    for (let d = from; d <= to; d = addDays(d, 1)) {
      try {
        await NN.noteNewsDay(out, d, { respond: AI.respond, force });
      } catch (e) {
        failed++;
        console.error(`${d}: news notes FAILED ${e.message}`);
      }
    }
    if (failed) process.exit(1);
    return;
  }
  if (refresh || process.argv.includes('--notes')) {
    const days = [];
    for (let d = from; d <= to; d = addDays(d, 1)) days.push(d);
    const work = async () => {
      for (let d = days.shift(); d; d = days.shift()) {
        try {
          if (refresh && topupToo && !B.read(out, d)?.toppedUp) await B.topup(d, { out });
          if (!(await (refresh ? B.refresh(d, { out }) : B.addNotes(d, { out, force })))) console.log(`${d}: no briefing or notes already there, skipped`);
        } catch (e) {
          failed++;
          console.error(`${d}: notes FAILED ${e.message}`);
        }
      }
    };
    await Promise.all([work(), work(), work(), work()]);
    B.writeIndex(out);
    if (failed) process.exit(1);
    return;
  }
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
  // the News feed's New-today stories get the same Rocket-form notes (today and yesterday's late picks)
  if (!arg('from') && !arg('date')) {
    for (const d of [addDays(from, -1), from]) {
      try {
        await NN.noteNewsDay(out, d, { respond: AI.respond });
      } catch (e) {
        console.error(`${d}: news notes FAILED ${e.message}`);
      }
    }
  }
  // a failure makes the run fail, so the scheduled retries try again
  if (failed) process.exit(1);
}

main();
