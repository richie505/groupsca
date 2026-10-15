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

/** A file name as a short source label: "PRAVEEN-SIR-s-CA-360-2026-SEP-ENGLISH-MAGAZINE-96385380-2026" -> "Praveen Sir CA 360 Sep 2026". */
function cleanLabel(name) {
  const seen = new Set();
  const words = String(name).replace(/[_-]+/g, ' ').split(/\s+/).filter((w) => {
    const k = w.toLowerCase();
    if (!w || /^\d{5,}$/.test(w) || /^(s|english|magazine|pdf|final|compressed)$/i.test(w) || seen.has(k)) return false;
    seen.add(k);
    return true;
  });
  const year = words.find((w) => /^20\d\d$/.test(w));
  const rest = words.filter((w) => w !== year).map((w) => (/^[A-Z]{3,}$/.test(w) ? w[0] + w.slice(1).toLowerCase() : w));
  return [...rest, year].filter(Boolean).join(' ').replace(/\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\b/i, (m) => m[0].toUpperCase() + m.slice(1, 3).toLowerCase()).trim() || 'CA notes';
}

async function main() {
  const dir = arg('dir');
  const jobs = dir
    ? fs.readdirSync(dir).filter((f) => /\.pdf$/i.test(f)).sort().map((f) => {
      const date = (f.match(/^(\d{4}-\d{2}-\d{2})/) || [])[1] || todayIst();
      const bits = f.replace(/\.pdf$/i, '').split('__');
      const kind = bits[1] === 'notes' ? 'notes' : NP.paperOf(bits[1] || f) || NP.paperOf(f);
      // notes keep the name they were uploaded with ("Vision Sep 2026") as their source label
      const label = kind === 'notes' ? cleanLabel(bits.slice(3).join(' ')) : '';
      return { file: path.join(dir, f), name: f, date, paper: kind, label };
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
      await NP.readPaper(j.date, j.file, j.paper, { out, label: j.label });
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
