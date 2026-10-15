#!/usr/bin/env node
'use strict';
// Reads uploaded newspaper PDFs into the day's briefing (pipeline/ai/paper.js). Server side only.
//   node pipeline/tools/paper.js --dir inbox      every PDF there, named "<YYYY-MM-DD>__<hindu|eenadu>__<anything>.pdf"
//   node pipeline/tools/paper.js --file x.pdf --paper hindu --date 2026-10-16
// Writes the names it finished to <dir>/done.txt, so the workflow removes only those from the inbox.
const fs = require('fs');
const path = require('path');
const NP = require('../ai/paper');
const { todayIst } = require('../daily');

const arg = (name) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : '';
};
const out = arg('out') || path.join(__dirname, '..', '..', 'feed');

async function main() {
  const dir = arg('dir');
  const jobs = dir
    ? fs.readdirSync(dir).filter((f) => /\.pdf$/i.test(f)).sort().map((f) => {
      const date = (f.match(/^(\d{4}-\d{2}-\d{2})/) || [])[1] || todayIst();
      return { file: path.join(dir, f), name: f, date, paper: NP.paperOf(f.split('__')[1] || f) || NP.paperOf(f) };
    })
    : [{ file: arg('file'), name: path.basename(arg('file')), date: arg('date') || todayIst(), paper: arg('paper') || NP.paperOf(arg('file')) }];
  const done = [];
  let failed = 0;
  for (const j of jobs) {
    if (!j.paper) {
      console.error(`${j.name}: which paper is it? Name it ..__hindu__.. or ..__eenadu__..`);
      done.push(j.name); // nothing to retry
      continue;
    }
    try {
      await NP.readPaper(j.date, j.file, j.paper, { out });
      done.push(j.name);
    } catch (e) {
      failed++;
      console.error(`${j.name}: FAILED ${e.message}`);
    }
  }
  if (dir) fs.writeFileSync(path.join(dir, 'done.txt'), done.join('\n') + (done.length ? '\n' : ''));
  if (failed) process.exit(1);
}

main();
