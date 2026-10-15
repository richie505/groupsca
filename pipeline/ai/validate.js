'use strict';
// The quality validator (spec section 44). It cleans what it safely can (drops items without a
// source URL, duplicate stories, repeats of an earlier day's topic with no new development,
// unsupported FIRST claims, aptitude-style MCQs) and reports what it cannot. A briefing that
// still fails a hard check is NEEDS_REVIEW: saved, but not published and not notified.

const { STATUSES, PRIMARY_DOMAINS } = require('./prompt');

const NO_AP_LINK = 'No direct AP-specific link established.';
const APTITUDE = /\b(ratio|percentage of|average of|probability|how many (?:ways|days|minutes)|speed|distance|train|interest rate of \d|simple interest|compound interest|profit and loss|series|next number|odd one out|coding[- ]decoding|blood relation|syllogism|data interpretation|bar graph|pie chart|calculate|compute)\b/i;
const AP_MIN = 3;

const host = (url) => {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return '';
  }
};
const isPrimaryUrl = (url) => PRIMARY_DOMAINS.some((d) => host(url) === d || host(url).endsWith(`.${d}`));

const words = (s) => new Set(String(s).toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter((w) => w.length > 3));
function similar(a, b) {
  const wa = words(a);
  const wb = words(b);
  let n = 0;
  for (const w of wa) if (wb.has(w)) n++;
  return n / Math.max(1, Math.min(wa.size, wb.size));
}

/**
 * Validates and cleans a briefing in place. `recent`: canonical topics of the previous days.
 * Returns { status: 'published' | 'needs_review', problems: [..], fixes: [..] }.
 */
function validate(b, recent = []) {
  const problems = [];
  const fixes = [];
  const recentSet = new Set(recent);
  const seen = [];
  for (const key of ['international', 'national', 'andhraPradesh']) {
    const list = Array.isArray(b.sections[key]) ? b.sections[key] : [];
    b.sections[key] = list.filter((it) => {
      if (!it || !String(it.headline || '').trim()) {
        fixes.push(`${key}: dropped an item with no headline`);
        return false;
      }
      // an item from an uploaded paper or notes names its page instead of a link
      it.sources = (it.sources || []).filter((s) => s && (/^https?:\/\//.test(s.url || '') || (it.paper && s.publisher)));
      if (!it.sources.length && !it.paper) {
        fixes.push(`${key}: dropped "${it.headline}" (no source URL)`);
        return false;
      }
      for (const s of it.sources) s.isPrimary = isPrimaryUrl(s.url);
      if (seen.some((h) => similar(h, it.headline) >= 0.75)) {
        fixes.push(`${key}: dropped duplicate "${it.headline}"`);
        return false;
      }
      if (it.canonicalTopic && recentSet.has(it.canonicalTopic) && !it.isUpdate) {
        fixes.push(`${key}: dropped "${it.headline}" (covered earlier, no new development)`);
        return false;
      }
      seen.push(it.headline);
      if (!STATUSES.includes(it.status)) {
        fixes.push(`${key}: "${it.headline}" status "${it.status}" -> ONGOING`);
        it.status = 'ONGOING';
      }
      // a FIRST / largest claim needs an authoritative primary source
      if (it.verifiedFirst && !it.sources.some((s) => s.isPrimary)) {
        fixes.push(`${key}: "${it.headline}" FIRST claim without a primary source -> unmarked`);
        it.verifiedFirst = false;
      }
      if (key !== 'andhraPradesh' && !String(it.apLink || '').trim()) it.apLink = NO_AP_LINK;
      return true;
    });
  }
  if (!b.sections.international.length) problems.push('International section is empty');
  if (!b.sections.national.length) problems.push('National section is empty');
  if (b.sections.andhraPradesh.length < AP_MIN) problems.push(`Andhra Pradesh has ${b.sections.andhraPradesh.length} items (need ${AP_MIN})`);

  // MCQs: four options, a valid key, no aptitude or Data Interpretation
  b.mcqs = (b.mcqs || []).filter((q) => {
    const ok = q && q.question && Array.isArray(q.options) && q.options.length === 4 && ['A', 'B', 'C', 'D'].includes(q.answer);
    if (!ok) fixes.push('dropped an MCQ with a bad answer key or options');
    else if (APTITUDE.test(q.question)) fixes.push(`dropped an aptitude-style MCQ: "${q.question.slice(0, 60)}"`);
    return ok && !APTITUDE.test(q.question);
  });
  if (b.mcqs.length < 5) problems.push(`only ${b.mcqs.length} valid MCQs (need 5)`);
  if (!Array.isArray(b.coverageAudit) || !b.coverageAudit.length) problems.push('coverage audit missing');
  b.mainsQuestions = (b.mainsQuestions || []).filter((q) => q && q.question && !APTITUDE.test(q.question));
  return { status: problems.length ? 'needs_review' : 'published', problems, fixes };
}

module.exports = { validate, isPrimaryUrl, similar, NO_AP_LINK, APTITUDE };
