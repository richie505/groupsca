'use strict';
// Rocket-form notes for the News feed's "New today" stories (feed/days/<date>.json, items marked `top`),
// written by the same notes stage as the briefing (no web search: the story's own facts only). Saved on
// the item as `rocket: { notes, blueprint }` ("notes" on a news item was the old static-notes field).

const fs = require('fs');
const path = require('path');
const N = require('./notes');

const CHUNK = 8;

const asBriefItem = (it, date) => ({
  headline: it.title,
  category: (it.angles || []).join(', '),
  status: '',
  date,
  facts: (it.facts || []).map((f) => f.text || String(f)),
  summary: it.summary || '',
  prelimsTakeaway: '',
  apLink: it.ap ? 'An Andhra Pradesh story' : '',
  staticLink: '',
});

/** Notes the day's top stories that have none yet. Returns how many were noted. */
async function noteNewsDay(out, date, { respond, log = () => {}, force = false } = {}) {
  const file = path.join(out, 'days', `${date}.json`);
  if (!fs.existsSync(file)) return 0;
  const day = JSON.parse(fs.readFileSync(file, 'utf8'));
  const todo = day.items.filter((i) => i.top && (force || !i.rocket));
  let done = 0;
  for (let k = 0; k < todo.length; k += CHUNK) {
    const part = todo.slice(k, k + CHUNK);
    try {
      const res = await N.noteItems(date, part.map((i) => asBriefItem(i, date)), respond, 'appsc_news_notes');
      res.forEach((n, i) => {
        if (n) {
          part[i].rocket = n;
          done++;
        }
      });
    } catch (e) {
      log(`${date}: news notes failed: ${e.message}`);
    }
  }
  if (done) fs.writeFileSync(file, JSON.stringify({ ...day, updated: new Date().toISOString() }, null, 1) + '\n');
  log(`${date}: news notes ${done}/${todo.length}`);
  return done;
}

module.exports = { noteNewsDay, asBriefItem };
