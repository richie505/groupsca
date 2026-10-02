'use strict';

// Key facts and a short summary, picked out of the article's own sentences.
//
// No rewriting and no model: a sentence is either quoted as published or left
// out. Each fact is labelled with the blueprint angle it answers ("Appointed",
// "GI tag", "Index / rank"…), which is the question APPSC would ask about it.

// Order matters: a sentence takes the first angle it matches.
const ANGLES = [
  ['Appointed', /\b(?:appointed|sworn in|takes? charge|took charge|elected (?:as )?(?:the )?(?:new )?(?:chair|president|chief|speaker)|named (?:as )?(?:the )?new|nominated as|re-?elected|assumes? office)\b/i],
  ['GI tag / designation', /\b(?:GI tag|geographical indication|Ramsar|tiger reserve|biosphere reserve|World Heritage|UNESCO|eco-sensitive zone|national park|wildlife sanctuary)\b/i],
  ['Index / rank', /\b(?:rank(?:ed|s|ing)?|index|indices|tops? the list|position)\b[^.]{0,80}\d|\d[^.]{0,40}\b(?:rank|position|place)\b/i],
  ['Judgment', /\b(?:Supreme Court|High Court|NGT|tribunal)\b[^.]{0,80}\b(?:held|ruled|struck down|directed|stayed|upheld|quashed|verdict|judgment)\b/i],
  ['Law / Bill', /\b(?:Bill|Act|Ordinance|amendment|Rules?)\b[^.]{0,60}\b(?:passed|introduced|notified|tabled|cleared|promulgated|came into force|assent)\b/i],
  ['Cabinet decision', /\b(?:Cabinet|CCEA)\b[^.]{0,80}\b(?:approv|clear|decid|nod)/i],
  ['Scheme / launch', /\b(?:launch(?:ed|es)?|unveil(?:ed|s)?|roll(?:ed)? out|inaugurat(?:ed|es))\b[^.]{0,80}\b(?:scheme|yojana|mission|portal|app|programme|policy|initiative|satellite|campaign|project)\b|\b(?:scheme|yojana|mission)\b[^.]{0,60}\b(?:launched|approved|extended)\b/i],
  ['Agreement / MoU', /\b(?:MoU|memorandum of understanding|agreement|pact|treaty|signed)\b/i],
  ['Summit / exercise', /\b(?:summit|joint (?:military |naval )?exercise|exercise [A-Z][a-z]+|conference|hosted|will host)\b/i],
  ['Report / committee', /\b(?:report|survey|committee|panel|commission|task force)\b[^.]{0,60}\b(?:released|published|submitted|constituted|headed by|chaired by|recommend)/i],
  ['Award', /\b(?:award(?:ed)?|prize|conferred|honoured with|Padma|Bharat Ratna)\b/i],
  ['Space / defence', /\b(?:ISRO|DRDO|PSLV|GSLV|LVM3|SSLV|missile|launch vehicle|satellite|Gaganyaan|test-?fired)\b/i],
  ['Firsts / records', /\b(?:first|largest|highest|lowest|longest|biggest|record)\b/i],
  ['Days / observance', /\b(?:observed|celebrated|commemorat)\w*\b[^.]{0,40}\bDay\b|\b(?:World|International|National) [A-Z][a-z]+(?: [A-Z][a-z]+)* Day\b/],
  ['Figures', /(?:₹|Rs\.?)\s?[\d,.]+\s*(?:crore|lakh|billion|million)?|\b\d+(?:\.\d+)?\s*(?:per cent|percent|%|crore|lakh|million|billion|MW|GW|km|hectares?|acres?|tonnes?)\b/i],
];

/** Sentences of a text, without the bits that are not sentences. */
function sentences(text) {
  return String(text || '')
    .replace(/\s+/g, ' ')
    // Do not split inside "Rs. 5", "No. 3", "Dr. Rao", "U.S." or decimals.
    .replace(/\b(Rs|No|Dr|Mr|Mrs|Ms|Shri|Smt|St|Sr|Jr|Prof|Gen|Lt|Col|Capt|Govt|Dept|vs|viz|i\.e|e\.g|U\.S|U\.K)\./g, '$1§')
    .replace(/(\d)\.(\d)/g, '$1§$2')
    .split(/(?<=[.!?])\s+(?=[A-Z“"‘'(₹0-9])/)
    .map((s) => s.replace(/§/g, '.').trim())
    .filter((s) => s.length >= 40 && s.length <= 400)
    .filter((s) => !/^(Also read|Read more|Click here|Follow us|Subscribe|Photo:|Image:|File photo)/i.test(s));
}

/** Up to `max` labelled facts, figures preferred, in reading order. */
function keyFacts(text, max = 4) {
  const picked = [];
  const seen = new Set();
  for (const s of sentences(text)) {
    const hit = ANGLES.find(([, re]) => re.test(s));
    if (!hit) continue;
    const key = s.toLowerCase().slice(0, 60);
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push({ angle: hit[0], text: s.length > 300 ? `${s.slice(0, 297)}…` : s, numeric: /\d/.test(s) });
  }
  // Facts with a number first (that is what an MCQ is built on), then the rest,
  // each group kept in reading order.
  const ordered = [...picked.filter((f) => f.numeric), ...picked.filter((f) => !f.numeric)].slice(0, max);
  return ordered
    .sort((a, b) => picked.indexOf(a) - picked.indexOf(b))
    .map(({ angle, text: t }) => ({ angle, text: t }));
}

/** A summary of at most ~`limit` characters, ending on a sentence where possible. */
function summarise(text, limit = 420) {
  const parts = sentences(text);
  if (!parts.length) {
    const t = String(text || '').replace(/\s+/g, ' ').trim();
    return t.length > limit ? `${t.slice(0, limit - 1).replace(/\s+\S*$/, '')}…` : t;
  }
  let out = '';
  for (const s of parts) {
    if (out && out.length + s.length + 1 > limit) break;
    out = out ? `${out} ${s}` : s;
  }
  return out.length > limit ? `${out.slice(0, limit - 1).replace(/\s+\S*$/, '')}…` : out;
}

module.exports = { keyFacts, summarise, sentences, ANGLES };
