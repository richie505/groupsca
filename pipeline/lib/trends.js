'use strict';
// Trending: what India is searching for (Google Trends' daily trending searches), kept only when it
// is exam material by the same rules as the news (syllabus score, exam filter), with its subject,
// the stories on file about it and its Rocket notes; and the topics the papers covered most this
// week. Written to feed/trending.json, which keeps 14 days of trends (the feed lists only the
// latest few at a time, and the daily run reads it four times a day).

const fs = require('fs');
const path = require('path');
const F = require('./fetch');
const S = require('./score');
const St = require('./statics');
const E = require('./exam');

const TRENDS_URL = 'https://trends.google.com/trending/rss?geo=IN';
const KEEP_DAYS = 14;
// searches that are never exam material, whatever news comes with them
const NOT_EXAM = /\b(lottery|sambad|result today|live score|vs\b|match|ipl|t20|odi|bigg boss|box office|movie|film|song|trailer|teaser|ott|serial|actor|actress|horoscope|rashi|gold rate|silver rate|petrol price|weather today|holiday|bank holiday|school holiday|wishes|quotes|images)\b/i;

const tag = (block, name) => {
  const m = String(block).match(new RegExp(`<${name}[^>]*>([\\s\\S]*?)</${name}>`, 'i'));
  return m ? F.decode(m[1].replace(/<!\[CDATA\[|\]\]>/g, '')).trim() : '';
};

/** The trending searches: [{ query, traffic, date, news: [{ title, url, source }] }]. */
function parseTrends(xml) {
  const out = [];
  for (const m of String(xml).matchAll(/<item>([\s\S]*?)<\/item>/gi)) {
    const block = m[1];
    const query = tag(block, 'title');
    if (!query) continue;
    const st = F.toIstStamp(tag(block, 'pubDate'));
    const news = [...block.matchAll(/<ht:news_item>([\s\S]*?)<\/ht:news_item>/gi)].map((n) => ({
      title: tag(n[1], 'ht:news_item_title'),
      url: tag(n[1], 'ht:news_item_url'),
      source: tag(n[1], 'ht:news_item_source'),
    })).filter((n) => n.title);
    out.push({ query, traffic: tag(block, 'ht:approx_traffic'), date: st ? st.date : null, time: st ? st.time : '', news });
  }
  return out;
}

const words = (t) => new Set(St.tokens(t).filter((w) => !St.NEWS_WORDS.has(w)));

/**
 * A trend as an exam topic, or null: scored like a story on its search and news headlines, through
 * the exam filter, with its subject, units, the stories on file it matches and its Rocket notes.
 */
function assess(trend, { vocab, notes, stories = [] }) {
  if (NOT_EXAM.test(trend.query) || /[ఀ-౿ऀ-ॿ]/.test(trend.query)) return null;
  const lead = trend.news[0] ? trend.news[0].title : trend.query;
  const article = {
    headline: lead,
    summary: [trend.query, ...trend.news.slice(1).map((n) => n.title)].join('. '),
    sourceName: 'Google Trends',
    primary: false,
  };
  const r = S.score(article, vocab);
  if (r.vetoed || r.score < 35) return null;
  const item = { title: lead, summary: article.summary, score: r.score, angles: r.angles, why: r.why, subject: r.subject, scope: r.scope };
  if (E.notExam(item)) return null;
  // the stories on file about it: two of the search's words in their headline
  const want = words(`${trend.query} ${lead}`);
  const related = stories
    .filter((s) => {
      let n = 0;
      for (const w of words(s.title)) if (want.has(w)) n++;
      return n >= 2;
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 3)
    .map((s) => ({ id: s.id, date: s.date, title: s.title }));
  const brief = notes && notes.size ? St.briefOf(notes, { title: lead, summary: article.summary, subject: r.subject, subjects: r.subjects }) : { sections: [] };
  return {
    query: trend.query,
    traffic: trend.traffic,
    date: trend.date,
    time: trend.time,
    subject: r.subject,
    units: r.units,
    score: r.score,
    ap: r.ap,
    news: trend.news.slice(0, 3),
    related,
    sections: brief.sections.slice(0, 2),
  };
}

/** The week's most covered topics: threads with the most reports over the last 7 days on file. */
function topTopics(days) {
  const recent = [...days].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 7);
  const byThread = new Map();
  for (const d of recent) {
    for (const i of d.items) {
      if (i.digest || i.oneLiner) continue;
      const t = i.thread || i.topicOf || i.id;
      if (!byThread.has(t)) byThread.set(t, { reports: 0, days: new Set(), items: [] });
      const e = byThread.get(t);
      e.reports += 1 + ((i.related || []).length);
      e.days.add(d.date);
      if (!i.topicOf) e.items.push(i);
    }
  }
  return [...byThread.entries()]
    .filter(([, e]) => e.items.length && (e.days.size >= 2 || e.reports >= 3))
    .map(([thread, e]) => {
      const first = e.items.sort((a, b) => a.date.localeCompare(b.date))[0];
      const latest = e.items[e.items.length - 1];
      return {
        thread,
        title: first.title,
        latest: latest.title,
        latestId: latest.id,
        subject: first.subject,
        ap: e.items.some((i) => i.ap),
        reports: e.reports,
        days: e.days.size,
        since: first.date,
      };
    })
    .sort((a, b) => b.days - a.days || b.reports - a.reports)
    .slice(0, 12);
}

/** Merges new trends into feed/trending.json (14 days kept) and refreshes the top topics. */
function writeTrending(out, fresh, days) {
  const file = path.join(out, 'trending.json');
  let old = { google: [] };
  try {
    old = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    // first run
  }
  const key = (t) => `${t.date}|${t.query.toLowerCase()}`;
  const byKey = new Map((old.google || []).map((t) => [key(t), t]));
  for (const t of fresh) byKey.set(key(t), t);
  const cutoff = new Date(Date.now() - KEEP_DAYS * 86400000).toISOString().slice(0, 10);
  const google = [...byKey.values()].filter((t) => t.date && t.date >= cutoff).sort((a, b) => b.date.localeCompare(a.date) || b.score - a.score);
  const data = { updated: new Date().toISOString(), google, topics: topTopics(days) };
  fs.writeFileSync(file, JSON.stringify(data, null, 1) + '\n');
  return data;
}

module.exports = { TRENDS_URL, parseTrends, assess, topTopics, writeTrending, NOT_EXAM };
