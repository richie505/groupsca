#!/usr/bin/env node
'use strict';

// Writes vocab/*.json from the APPSC Current Affairs Portal
// (github.com/richie505/groups-current-affairsapp), so this pipeline scores
// against the same syllabus map, blueprint angles and topics as the portal
// without needing its database.
//
//   node pipeline/tools/export-vocab.js <groups-current-affairsapp> <blueprint-keywords.md> <groupsrocket/data/syllabus.json>
//
// The units are shown under the COMBINED syllabus tracker's ids (G1-A1 ... G1-F22,
// G2-S1-*, G2-M1A-U1 ... G2-M2B-U5, from groupsrocket's data/syllabus.json), the
// same list the other apps use; the portal's units carry the newspaper words
// that match them. vocab/ap-vocab.json (hand-kept) adds Andhra Pradesh terms
// and extra aliases for the AP units.
//
// Run it again whenever the portal's syllabus files or the blueprint change.

const fs = require('fs');
const path = require('path');

const [portal, blueprint, trackerFile] = process.argv.slice(2);
if (!portal || !blueprint || !trackerFile) {
  console.error('Usage: node pipeline/tools/export-vocab.js <groups-current-affairsapp> <blueprint-keywords.md> <syllabus.json>');
  process.exit(1);
}
const OUT = path.join(__dirname, '..', 'vocab');
const scripts = path.join(portal, 'server', 'scripts');

const { G2_UNITS } = require(path.join(scripts, 'g2-syllabus'));
const { G1P_UNITS } = require(path.join(scripts, 'g1-prelims-syllabus'));
const { TOPICS } = require(path.join(scripts, 'topic-data'));
const { AP_TERMS } = require(path.join(portal, 'content-pipeline', 'ca-daily', 'sweep'));

// The portal's rule (seed-g2-syllabus.js): a short all-caps alias is matched
// case-sensitively, so 'ASI' does not fire inside "Asian".
const strictOf = (a) => /^[A-Z0-9&.\- ]+$/.test(a) && a.length <= 5;

// G2-S5 and G1P-CE are "current affairs" itself — every article matches them,
// so they are evidence of nothing. G2-S4 is mental ability, which news never
// feeds. The portal flags these broad / unfeedable; here they are simply left out.
const SKIP = new Set(['G2-S4', 'G2-S5', 'G1P-CE']);

const tracker = JSON.parse(fs.readFileSync(trackerFile, 'utf8'));
const trackerById = new Map(tracker.units.map((u) => [u.id, u]));
const apVocab = JSON.parse(fs.readFileSync(path.join(OUT, 'ap-vocab.json'), 'utf8'));

// Portal unit -> tracker unit(s). Same syllabus, two numberings.
function trackerIds(code) {
  let m;
  if ((m = code.match(/^G1P-([A-D])(\d)$/))) return [`G1-${m[1]}${m[2]}`];
  if ((m = code.match(/^G1P-S(\d)$/))) return [`G1-F${17 + Number(m[1])}`];
  if ((m = code.match(/^G2-(S\d)$/))) return tracker.units.filter((u) => u.id.startsWith(`G2-${m[1]}-`)).map((u) => u.id);
  if ((m = code.match(/^G2-P1-U(\d+)$/))) return [Number(m[1]) <= 5 ? `G2-M1A-U${m[1]}` : `G2-M1B-U${m[1]}`];
  if ((m = code.match(/^G2-P2-U(\d+)$/))) return [Number(m[1]) <= 5 ? `G2-M2A-U${m[1]}` : `G2-M2B-U${Number(m[1]) - 5}`];
  return [];
}

const units = [...G1P_UNITS.map((u) => ({ ...u, exam: 'G1' })), ...G2_UNITS.map((u) => ({ ...u, exam: 'G2' }))]
  .filter((u) => !SKIP.has(u.code))
  .map((u) => {
    const ids = trackerIds(u.code);
    const missing = ids.filter((id) => !trackerById.has(id));
    if (!ids.length || missing.length) throw new Error(`${u.code}: no tracker unit ${missing.join(', ')}`);
    const t = trackerById.get(ids[0]);
    return {
      code: u.code,
      exam: u.exam,
      paper: u.paper,
      // What the app shows: the tracker's id and section. A Screening unit
      // spans several tracker rows (G2-S1-ANC/MED/MOD), so it is named by its
      // section rather than one row.
      tracker: ids.length > 1 ? ids[0].replace(/-[A-Z]+$/, '') : ids[0],
      section: t.section,
      label: ids.length > 1 ? t.section.replace(/^Screening /, 'Screening ') : t.title,
      aliases: [...new Set([
        ...(u.aliases || []),
        ...(apVocab.unitAliases[u.code] || []),
        ...((apVocab.telugu && apVocab.telugu.units[u.code]) || []),
      ])]
        .map((a) => ({ a, strict: strictOf(a) })),
    };
  });
for (const code of [...Object.keys(apVocab.unitAliases), ...Object.keys((apVocab.telugu || {}).units || {})]) {
  if (!units.some((u) => u.code === code)) throw new Error(`ap-vocab.json: unknown unit ${code}`);
}

const topics = TOPICS.map((t) => ({
  slug: t.slug,
  name: t.name,
  ap: !!t.ap,
  tier: t.tier,
  aliases: [...new Set(t.aliases || [])].map((a) => ({ a, strict: strictOf(a) })),
}));

// blueprint-keywords.md: "## Subject" then one comma-separated line of angles.
const SUBJECT = { 'Indian Economy / AP Economy': 'Economy' };
const keywords = {};
let subject = null;
for (const line of fs.readFileSync(blueprint, 'utf8').split('\n')) {
  const h = line.match(/^##\s+(.+?)\s*$/);
  if (h) {
    subject = SUBJECT[h[1]] || h[1];
    continue;
  }
  if (!subject || !line.trim() || line.startsWith('#') || line.startsWith('---')) continue;
  keywords[subject] = (keywords[subject] || []).concat(
    line.split(',').map((k) => k.trim()).filter(Boolean)
  );
}

fs.mkdirSync(OUT, { recursive: true });
const write = (name, data) => fs.writeFileSync(path.join(OUT, name), JSON.stringify(data, null, 1) + '\n');
write('units.json', units);
write('topics.json', topics);
write('keywords.json', keywords);
write('ap-terms.json', [...new Set([...AP_TERMS, ...apVocab.terms])]);

const nk = Object.values(keywords).flat().length;
console.log(`units ${units.length} (${units.reduce((n, u) => n + u.aliases.length, 0)} aliases), ` +
  `topics ${topics.length}, blueprint angles ${nk} in ${Object.keys(keywords).length} subjects, AP terms ${AP_TERMS.length}`);
