'use strict';
// Topic threads across days: a story that continues a topic from an earlier
// day (BC quota: HC strikes the GOs -> AP moves SC -> SLP filed) is an update
// on that topic, not a new topic. The topic is read in full once, on the day
// it starts; later days show its updates as one line each.

const T = require('./topics');

/** How long a topic stays open for updates without any news. */
const OPEN_DAYS = 21;

const dayNo = (iso) => Math.floor(Date.parse(`${iso}T00:00:00Z`) / 86400000);

/**
 * `days`: every day file, any order ({ date, items }). Sets on each topic's lead
 * (items without topicOf, digests left out):
 *   thread       the id of the story that started the topic
 *   update       true when the topic started on an earlier day
 *   threadTitle  that first story's headline (on updates)
 *   threadStart  the date it started (on updates)
 * Returns the number of updates.
 */
function threadDays(days) {
  const sorted = [...days].sort((a, b) => a.date.localeCompare(b.date));
  const threads = [];
  let updates = 0;
  for (const day of sorted) {
    const today = dayNo(day.date);
    const opened = [];
    for (const it of day.items) {
      delete it.thread;
      delete it.update;
      delete it.threadTitle;
      delete it.threadStart;
    }
    const leads = day.items.filter((i) => !i.topicOf && !i.digest).sort((a, b) => b.score - a.score);
    for (const it of leads) {
      // the topic it continues: an earlier day's, still open, matching its first story or a recent update
      const t = threads.find(
        (x) => today - x.last <= OPEN_DAYS && [x.members[0], ...x.members.slice(-3)].some((m) => T.sameThread(m, it))
      );
      if (t) {
        it.thread = t.id;
        it.update = true;
        it.threadTitle = t.title;
        it.threadStart = t.first;
        t.members.push(it);
        t.lastToday = today;
        updates++;
      } else {
        it.thread = it.id;
        opened.push({ id: it.id, title: it.title, first: day.date, last: today, members: [it] });
      }
    }
    // a day's own stories join threads only from the next day (same-day reports are grouped already)
    for (const t of threads) if (t.lastToday === today) t.last = today;
    threads.push(...opened);
  }
  return updates;
}

module.exports = { threadDays, OPEN_DAYS };
