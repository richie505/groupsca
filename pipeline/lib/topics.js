'use strict';
// One topic, one card: reports of the same story from different papers (and
// the Telugu one) are grouped under the best of them, so the day reads like a
// current-affairs magazine with no topic twice.

const St = require('./statics');

const SKIP = new Set(
  ('andhra pradesh india indian govt government state centre union minister cm chief says said new over after amid ' +
    'crore lakh rs per cent percent day days year years first how why what explained').split(' ')
);

// words in many unrelated headlines
const WEAK = new Set(
  ('prime shri smt calls call use uses using proposals prepare courage lauds says flags hit hits case cases ' +
    'chief party parties leader leaders police court high supreme district city village rural urban west east ' +
    'north south global world national regional major key big top')
    .split(' ')
);

// words headlines use for the same thing
const SAME = { revenue: 'collections', collection: 'collections', mop: 'collections', kitty: 'collections', quota: 'reservation', reservations: 'reservation', polls: 'elections', election: 'elections', blast: 'explosion' };

// short forms in too many unrelated headlines to say "same story" alone
const COMMON_ACR = new Set(['rbi', 'bjp', 'tdp', 'ysrcp', 'nda', 'cpi', 'dmk', 'aap', 'upsc', 'pib', 'gdp']);

function words(item) {
  const out = new Set(
    St.tokens(item.title)
      .map((w) => SAME[w] || w)
      .filter((w) => !SKIP.has(w) && !St.NEWS_WORDS.has(w) && !WEAK.has(w))
  );
  // short forms and figures tokens() leaves out: BC, SC, HC, 34%
  for (const m of String(item.title).matchAll(/\b([A-Z]{2})\b|(\d+(?:\.\d+)?%)/g)) {
    const w = (m[1] || m[2]).toLowerCase();
    if (!['ap', 'cm', 'pm'].includes(w)) out.add(w);
  }
  return out;
}

function jaccard(a, b) {
  let n = 0;
  for (const w of a) if (b.has(w)) n++;
  return n / (a.size + b.size - n || 1);
}

const secsOf = (item) => new Set(((item.brief && item.brief.sections) || []).map((s) => s.where));

/** Same story? Headline words, or the same static notes and a shared headline word. */
function sameTopic(a, b) {
  const wa = words(a);
  const wb = words(b);
  let shared = 0;
  for (const w of wa) if (wb.has(w)) shared++;
  if (jaccard(wa, wb) >= 0.34 && shared >= 2) return true;
  const sa = secsOf(a);
  const sb = secsOf(b);
  let common = 0;
  for (const w of sa) if (sb.has(w)) common++;
  const j = jaccard(wa, wb);
  if (common >= 2 && shared >= 1) return true;
  if (common >= 1 && shared >= 2 && j >= 0.2) return true;
  // a Telugu report has no English headline words: the same notes say it
  const telugu = (x) => x.lang === 'te';
  if ((telugu(a) || telugu(b)) && common >= 2) return true;
  // the same body or scheme by its short form ("GST", "ISRO") and one more word
  const acr = (x) => new Set((String(x.title).match(/\b[A-Z]{3,6}\b/g) || []).map((w) => w.toLowerCase()));
  const aa = acr(a);
  const sharedAcr = [...acr(b)].filter((w) => aa.has(w) && wa.has(w) && !COMMON_ACR.has(w));
  if (sharedAcr.length && ((shared >= 2 && j >= 0.1) || common >= 1)) return true;
  return false;
}

const rank = (i) => (i.lang === 'te' ? -100 : 0) + i.score + (i.official ? 5 : 0) + (i.digest ? -50 : 0) + Math.min(10, (i.summary || '').length / 40);

/**
 * Sets `related` on each topic's lead ({id, title, source, url} of the others)
 * and `topicOf` (the lead's id) on the others. Digest posts never group.
 * Returns how many items were grouped under another.
 */
function groupTopics(items) {
  for (const it of items) {
    delete it.related;
    delete it.topicOf;
  }
  const order = [...items].filter((i) => !i.digest).sort((a, b) => rank(b) - rank(a));
  const leads = [];
  let grouped = 0;
  for (const it of order) {
    // the same topic as the lead or as any report already under it
    const lead = leads.find((l) => sameTopic(l, it) || (l.members || []).some((m) => sameTopic(m, it)));
    if (!lead) {
      Object.defineProperty(it, 'members', { value: [], enumerable: false, writable: true });
      leads.push(it);
      continue;
    }
    lead.members.push(it);
    it.topicOf = lead.id;
    (lead.related = lead.related || []).push({ id: it.id, title: it.title, source: it.source, url: it.url });
    grouped++;
  }
  return grouped;
}

module.exports = { groupTopics, sameTopic };
