'use strict';

// Static notes for each current-affairs story, from the reader's own notes:
// the APPSC Prep app's 6 books (Group-app) and the Rocket Sheets' key facts
// (groupsrocket). Every story gets at least one, in three tiers:
//
//   exact  a note that names the same specific thing as the story: "Buxa Tiger
//          Reserve", "Ramsar", "Uniform Civil Code", "BrahMos", "repo rate".
//          A name counts only if it is rare in the notes (so "India",
//          "Government", "Supreme Court" never make a match on their own).
//   unit   no exact note: the subsection of the story's own syllabus unit in the
//          Prep notes (tracker G1-C4 -> Book 3, unit C-4) whose words overlap
//          the story's most.
//   book   failing that, the closest subsection in the story's book.
//
// No model: phrase matching and word overlap, weighted by how rare a word is.

const fs = require('fs');
const path = require('path');

const MAX_TEXT = 420;

// Telugu -> English glossary (vocab/ap-vocab.json, telugu.glossary): an Eenadu
// headline is matched to the English notes through the English it glosses to.
let GLOSSARY = null;
function glossTerms(text) {
  if (!GLOSSARY) {
    try {
      const v = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'vocab', 'ap-vocab.json'), 'utf8'));
      GLOSSARY = Object.entries((v.telugu && v.telugu.glossary) || {}).map(([te, en]) => [te.replace(/[\u200b-\u200d]/g, ''), en]);
    } catch {
      GLOSSARY = [];
    }
  }
  const t = String(text).replace(/[\u200b-\u200d]/g, '');
  return [...new Set(GLOSSARY.filter(([te]) => t.includes(te)).map(([, en]) => en))];
}

// ---------------------------------------------------------------------------
// loading the notes
// ---------------------------------------------------------------------------

/** Plain text of a run list, without the grey source tags ("[GK]", "[Indian History · ROCKET SHEET #1]"). */
function runsText(runs) {
  if (typeof runs === 'string') return runs;
  return (runs || [])
    .filter((r) => !(Array.isArray(r) && r[1] === 4))
    .map((r) => (Array.isArray(r) ? r[0] : String(r)))
    .join('')
    .replace(/\s+/g, ' ')
    .trim();
}

function blockTexts(block) {
  if (!block) return [];
  if (block.k === 't') {
    const head = (block.h || []).map(runsText);
    return (block.r || []).map((row) => {
      const cells = row.map(runsText);
      if (cells.length < 2) return cells.join('');
      return `${cells[0]}: ${cells
        .slice(1)
        .map((c, i) => (head[i + 1] ? `${head[i + 1]} ${c}` : c))
        .join('; ')}`;
    });
  }
  if (block.x) return [runsText(block.x)];
  return [];
}

const clip = (s) => (s.length > MAX_TEXT ? `${s.slice(0, MAX_TEXT - 1).replace(/\s+\S*$/, '')}…` : s);

/**
 * Every note bullet (and table row) as { src, book, unit, where, sec, text }.
 * `sec` groups the bullets of one subsection, for the unit/book tiers.
 */
function loadNotes({ prepDir, rocketDir } = {}) {
  const entries = [];
  const secs = [];
  const read = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));

  if (prepDir && fs.existsSync(path.join(prepDir, 'book1.json'))) {
    for (let n = 1; n <= 6; n++) {
      const f = path.join(prepDir, `book${n}.json`);
      if (!fs.existsSync(f)) continue;
      const book = read(f);
      for (const u of book.units || []) {
        for (const row of u.rows || []) {
          for (const s of row.secs || []) {
            const id = secs.length;
            const where = `Prep notes · Book ${n} ${book.short || ''} › ${u.code ? `${u.code} › ` : ''}${row.title} › ${s.t}`;
            // `head`: what the subsection is about - each title before its " - " list
            const head = `${String(row.title).split(' - ')[0]} | ${String(s.t).split(' - ')[0]}`;
            secs.push({ id, src: 'prep', book: n, unit: u.code || '', where, title: `${row.title} ${s.t}`, head, entries: [] });
            for (const b of s.b || []) {
              for (const text of blockTexts(b)) {
                if (text.length < 25) continue;
                const e = { id: entries.length, src: 'prep', book: n, unit: u.code || '', sec: id, where, text };
                entries.push(e);
                secs[id].entries.push(e.id);
              }
            }
          }
        }
      }
    }
  }

  if (rocketDir && fs.existsSync(path.join(rocketDir, 'book1.json'))) {
    for (let n = 1; n <= 5; n++) {
      const f = path.join(rocketDir, `book${n}.json`);
      if (!fs.existsSync(f)) continue;
      const book = read(f);
      for (const u of book.units || []) {
        for (const row of u.rows || []) {
          for (const s of row.secs || []) {
            // the sheet's key facts; "Sheet text (PDF)" is the raw scan
            if (!/^Key facts/i.test(s.t)) continue;
            const id = secs.length;
            const where = `Rocket Sheets · ${u.title} › ${row.title}`;
            secs.push({ id, src: 'rocket', book: n, unit: u.title, where, title: row.title, head: String(row.title).replace(/^#\d+\s*·\s*/, ''), entries: [] });
            for (const b of s.b || []) {
              if (b.k !== 'b') continue;
              const text = runsText(b.x);
              if (text.length < 25) continue;
              const e = { id: entries.length, src: 'rocket', book: n, unit: u.title, sec: id, where, text };
              entries.push(e);
              secs[id].entries.push(e.id);
            }
          }
        }
      }
    }
  }
  return buildIndex(entries, secs);
}

// ---------------------------------------------------------------------------
// the index
// ---------------------------------------------------------------------------

const STOP = new Set(
  ('a an the and or but of in on at to for from with by as is are was were be been being this that these those it its ' +
    'his her their our your new says said will would may can could has have had not also after over amid into about ' +
    'than more most other such which who whom what when where while during under between under per its all any each ' +
    'india indian state states government govt centre central union minister ministry year years day days first one two ' +
    'three part key notes source sources gk')
    .split(' ')
);

function tokens(text) {
  return String(text)
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^\p{L}\p{N}\s-]/gu, ' ')
    .split(/[\s-]+/)
    .filter((w) => w.length > 2 && !STOP.has(w) && !/^\d+$/.test(w));
}

function buildIndex(entries, secs) {
  const lower = entries.map((e) => e.text.toLowerCase());
  const postings = new Map(); // word -> entry ids
  entries.forEach((e, i) => {
    for (const w of new Set(tokens(e.text))) {
      if (!postings.has(w)) postings.set(w, []);
      postings.get(w).push(i);
    }
  });
  // subsection bags, for the unit / book tiers
  const secDf = new Map();
  for (const s of secs) {
    const bag = new Map();
    for (const w of tokens(s.title)) bag.set(w, (bag.get(w) || 0) + 2);
    for (const id of s.entries) for (const w of tokens(entries[id].text)) bag.set(w, (bag.get(w) || 0) + 1);
    s.bag = bag;
    for (const w of bag.keys()) secDf.set(w, (secDf.get(w) || 0) + 1);
  }
  // how many subsection headings name a word: a word in very many headings
  // ("programmes", "initiatives", "environment") is generic, not a topic
  const headDf = new Map();
  for (const s of secs) for (const w of new Set(tokens(s.head || s.title))) headDf.set(w, (headDf.get(w) || 0) + 1);
  return { entries, lower, postings, secs, secDf, headDf, size: entries.length };
}

// ---------------------------------------------------------------------------
// phrases a story is about
// ---------------------------------------------------------------------------

// Words that start a capitalised run in a headline without naming anything.
const GENERIC = new Set(
  ('India Indian Andhra Pradesh Telangana Government Govt Centre Central Union State States Minister Ministry Chief CM PM ' +
    'President Prime Court Supreme High Department Board Office National International World Global New Day Week Year ' +
    'Today Says Said First Second Third Mr Mrs Ms Dr Shri Smt Sri Jan Feb Mar Apr May Jun Jul Aug Sep Sept Oct Nov Dec ' +
    'January February March April June July August September October November December Monday Tuesday Wednesday ' +
    'Thursday Friday Saturday Sunday The A An In On At Of For To And With By From As Is After Over Amid How Why What ' +
    'Who When Where Will Can Big Top Key Live Watch Explained Opinion Editorial Report Reports AP US UK UN EU Vizag ' +
    'District Collector Police Officials Official City Town Village Party Leader PIB PTI ANI IANS AIR TH IST ET HT TOI ' +
    'NDTV Reuters AFP')
    .split(' ')
);

const LINK = new Set(['of', 'and', 'for', 'the', 'in', 'on', 'de', '&']);

/**
 * Names in a headline/summary: runs of Capitalised words ("Buxa Tiger
 * Reserve", "Financial Stability Report"), acronyms ("UCC", "MSP"), hyphenated
 * names ("PM-Surya Ghar", "Aditya-L1"), and lower-case technical phrases the
 * story's syllabus units matched are passed in as `extra`.
 */
function phrasesOf(text, extra = []) {
  const out = new Map();
  const add = (p, kind) => {
    const t = p.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '').trim();
    if (t.length < 3) return;
    const words = t.split(/\s+/);
    if (words.length === 1 && GENERIC.has(t)) return;
    if (words.every((w) => GENERIC.has(w) || LINK.has(w.toLowerCase()))) return;
    if (!out.has(t.toLowerCase())) out.set(t.toLowerCase(), { text: t, kind, words: words.length });
  };
  const sentences = String(text).split(/(?<=[.!?:;])\s+|\s+[–—|]\s+/);
  for (const s of sentences) {
    const words = s.split(/\s+/);
    let run = [];
    const flush = () => {
      while (run.length && LINK.has(run[run.length - 1].toLowerCase())) run.pop();
      while (run.length && LINK.has(run[0].toLowerCase())) run.shift();
      // drop generic and linking words at the start: "Ministry For Environment" -> "Environment"
      while (run.length > 1 && (GENERIC.has(run[0]) || LINK.has(run[0].toLowerCase()))) run.shift();
      if (run.length >= 2) add(run.join(' '), 'name');
      // A lone capitalised word that opens a sentence ("Under the scheme",
      // "Only one day") is just a capital letter, not a name.
      if (run.length === 1 && runStart > 0 && /^\p{Lu}[\p{L}'’-]{3,}$/u.test(run[0])) add(run[0], 'word');
      run = [];
    };
    let runStart = 0;
    words.forEach((raw, i) => {
      const w = raw.replace(/['’]s$/, '').replace(/[,"'‘’“”()]/g, '');
      if (!run.length) runStart = i;
      if (/^[A-Z][A-Z0-9&]{1,6}s?$/.test(w) && !GENERIC.has(w)) {
        add(w.replace(/s$/, ''), 'acronym');
        run.push(w);
      } else if (/^\p{Lu}[\p{L}\p{N}'’.-]*$/u.test(w) && (i > 0 || words.length < 4 || !GENERIC.has(w))) {
        run.push(w);
      } else if (run.length && LINK.has(w.toLowerCase())) {
        run.push(w);
      } else flush();
      if (/-/.test(w) && /\p{Lu}/u.test(w)) add(w, 'name');
      // "Naidu, Sitharaman launch ..." is two names
      if (/[,;:]$/.test(raw)) flush();
    });
    flush();
  }
  for (const e of extra) add(e, 'alias');
  return [...out.values()];
}

// ---------------------------------------------------------------------------
// matching
// ---------------------------------------------------------------------------

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Entries whose text contains the phrase (word-bounded; acronyms case-sensitive). */
function findPhrase(index, phrase) {
  const ws = tokens(phrase.text);
  let cand = null;
  for (const w of ws) {
    const p = index.postings.get(w);
    if (!p) return [];
    if (cand) {
      const ps = new Set(p);
      cand = cand.filter((id) => ps.has(id));
    } else cand = p.slice();
    if (!cand.length) return [];
    if (cand.length > 4000) break;
  }
  if (!cand) return [];
  const acronym = phrase.kind === 'acronym';
  const re = new RegExp(`(?<![\\p{L}\\p{N}])${escapeRe(acronym ? phrase.text : phrase.text.toLowerCase())}(?:e?s)?(?![\\p{L}\\p{N}])`, 'u');
  return cand.filter((id) => re.test(acronym ? index.entries[id].text : index.lower[id]));
}

// Rare enough to identify the topic: at most this many note bullets name it.
const MAX_EXACT_DF = 40;

function exactMatches(index, phrases) {
  const N = index.size || 1;
  const scores = new Map();
  for (const p of phrases) {
    const ids = findPhrase(index, p);
    if (!ids.length || ids.length > MAX_EXACT_DF) continue;
    // a single plain word must be really rare; names and acronyms less so
    if (p.kind === 'word' && ids.length > 12) continue;
    const w = Math.log(N / ids.length) * (p.words > 1 ? 1.5 : 1);
    for (const id of ids) {
      const s = scores.get(id) || { score: 0, match: [] };
      s.score += w;
      s.match.push(p.text);
      scores.set(id, s);
    }
  }
  return [...scores.entries()]
    .map(([id, s]) => ({ entry: index.entries[id], score: s.score, match: s.match }))
    .sort((a, b) => b.score - a.score || a.entry.text.length - b.entry.text.length);
}

/** The subsection whose words overlap the story's most (TF-IDF), within `filter`. */
function bestSection(index, storyTokens, filter) {
  const S = index.secs.length || 1;
  let best = null;
  for (const s of index.secs) {
    if (!filter(s) || !s.entries.length) continue;
    let score = 0;
    for (const w of storyTokens) {
      const tf = s.bag.get(w);
      if (tf) score += (1 + Math.log(tf)) * Math.log(S / (index.secDf.get(w) || 1));
    }
    score /= Math.sqrt(s.bag.size + 20);
    if (score > 0 && (!best || score > best.score)) best = { sec: s, score };
  }
  return best;
}

/** The bullet of a subsection that shares most rare words with the story. */
function bestEntryOf(index, sec, storyTokens) {
  const set = new Set(storyTokens);
  let best = null;
  for (const id of sec.entries) {
    const e = index.entries[id];
    let score = 0;
    for (const w of new Set(tokens(e.text))) if (set.has(w)) score += Math.log((index.size || 1) / ((index.postings.get(w) || []).length || 1));
    if (!best || score > best.score) best = { entry: e, score };
  }
  return best;
}

/**
 * The bullet within `filter` sharing the most rare words with the story,
 * headline words counting double; it must share at least `minShared` words
 * (two by default), so a single common word never makes a link.
 */
function bestEntry(index, head, body, filter, minShared = 2) {
  const N = index.size || 1;
  const weights = new Map();
  for (const w of body) weights.set(w, 1);
  for (const w of head) weights.set(w, 2);
  const acc = new Map();
  for (const [w, k] of weights) {
    const p = index.postings.get(w);
    if (!p || p.length > Math.max(50, N / 20)) continue; // too common to say anything
    const idf = Math.log(N / p.length);
    for (const id of p) {
      const e = index.entries[id];
      if (!filter(e)) continue;
      const a = acc.get(id) || { score: 0, shared: [] };
      a.score += k * idf;
      a.shared.push(w);
      acc.set(id, a);
    }
  }
  let best = null;
  for (const [id, a] of acc) {
    if (a.shared.length < minShared) continue;
    const e = index.entries[id];
    // a little against very long bullets, which share words by size alone
    const score = a.score / Math.log(10 + e.text.length / 40);
    if (!best || score > best.score) best = { entry: e, score, shared: a.shared };
  }
  return best;
}

const prepUnitOf = (tracker) => {
  const m = String(tracker).match(/^G1-([A-D])(\d)$/);
  if (m) return `${m[1]}-${m[2]}`;
  const f = String(tracker).match(/^G1-F(\d{2})$/);
  return f ? f[1] : null;
};

const BOOK_NO = {
  'History & Culture': 1, 'Polity, Society & IR': 2, Economy: 3, Geography: 4,
  'Science, Tech & Environment': 5, 'Current Affairs': 6,
};

const out = (e, tier, match) => ({
  tier,
  src: e.src === 'prep' ? 'Prep notes' : 'Rocket Sheets',
  where: e.where,
  text: clip(e.text),
  ...(match && match.length ? { match: [...new Set(match)].slice(0, 3) } : {}),
});

/**
 * Static notes for one story: up to 2 exact notes from each source; when the
 * Prep notes have none, the closest bullet of the story's unit (else book).
 * Never empty when notes are loaded.
 */
function staticFor(index, item0, aliases = []) {
  if (!index || !index.size) return [];
  // A Telugu story is matched through its English gloss.
  const telugu = /[\u0C00-\u0C7F]/.test(item0.title);
  const terms = telugu ? glossTerms(item0.title) : [];
  const item = telugu ? { ...item0, title: terms.join('. ') || item0.title } : item0;
  // each glossed term is a name in its own right ("Polavaram project")
  if (telugu) aliases = [...aliases, ...terms];
  // Exact links come from the HEADLINE's names: a summary mentions side
  // players ("Mahatma Gandhi remembered" names a party in its summary). Only a
  // headline with no names at all lets the summary's names count.
  const headPhrases = phrasesOf(item.title, aliases);
  const phrases = headPhrases.length ? headPhrases : phrasesOf(`${item.title}. ${item.summary || ''}`, aliases);
  const exact = exactMatches(index, phrases);
  const picked = [];
  const seenText = new Set();
  for (const src of ['prep', 'rocket']) {
    let n = 0;
    for (const m of exact) {
      if (m.entry.src !== src || n >= 2) continue;
      const key = m.entry.text.slice(0, 80).toLowerCase();
      if (seenText.has(key)) continue;
      seenText.add(key);
      picked.push(out(m.entry, 'exact', m.match));
      n++;
    }
  }
  if (picked.some((p) => p.src === 'Prep notes')) return picked;

  // No exact Prep note: the bullet of the story's own unit (then book) that
  // shares the most rare words with it.
  const head = tokens(item.title);
  // The story's English tags count too: its syllabus units' titles and its
  // blueprint angles. For a Telugu headline they are the only English there is.
  const tags = `${(item.units || []).map((u) => u.label).join(' ')} ${(item.angles || []).join(' ')} ${(item.topics || []).join(' ')}`;
  const body = tokens(`${item.summary || ''} ${(item.facts || []).map((f) => f.text).join(' ')} ${tags}`);
  const units = (item.units || []).map((u) => prepUnitOf(u.code)).filter(Boolean);
  const bookNo = BOOK_NO[item.subject] || 6;
  const tiers = [
    ['unit', (e) => e.src === 'prep' && units.includes(e.unit)],
    ['book', (e) => e.src === 'prep' && e.book === bookNo],
    ['book', (e) => e.src === 'prep'],
  ];
  // Two shared words first; then one, so that every story gets a note.
  outer: for (const minShared of [2, 1]) {
    for (const [tier, filter] of tiers) {
      if (tier === 'unit' && !units.length) continue;
      const best = bestEntry(index, head, body, filter, minShared);
      if (best) {
        picked.unshift(out(best.entry, tier, best.shared));
        break outer;
      }
    }
  }
  // Nothing shares a word with it (rare): the opening note of its book, so that
  // every story still has one, marked as the loosest link.
  if (!picked.some((p) => p.src === 'Prep notes')) {
    const first = index.entries.find((e) => e.src === 'prep' && e.book === bookNo) || index.entries.find((e) => e.src === 'prep');
    if (first) picked.unshift(out(first, 'book'));
  }
  if (!picked.some((p) => p.src === 'Rocket Sheets')) {
    const best = bestEntry(index, head, body, (e) => e.src === 'rocket' && e.book === bookNo, 2);
    if (best) picked.push(out(best.entry, 'book', best.shared));
  }
  return picked;
}

/** Where the notes are: env NOTES_PREP / NOTES_ROCKET, else the sibling clones. */
function defaultDirs(root = path.join(__dirname, '..', '..')) {
  const pick = (env, ...cands) => [process.env[env], ...cands].find((d) => d && fs.existsSync(path.join(d, 'book1.json')));
  return {
    prepDir: pick('NOTES_PREP', path.join(root, 'ext', 'Group-app', 'app', 'src', 'main', 'assets'), path.join(root, '..', 'Group-app', 'app', 'src', 'main', 'assets')),
    rocketDir: pick('NOTES_ROCKET', path.join(root, 'ext', 'groupsrocket', 'android', 'app', 'src', 'main', 'assets'), path.join(root, '..', 'groupsrocket', 'android', 'app', 'src', 'main', 'assets')),
  };
}

module.exports = { loadNotes, buildIndex, staticFor, phrasesOf, tokens, defaultDirs, prepUnitOf };

// ---------------------------------------------------------------------------
// the static brief: numbered topic sections, the way a study note is laid out
// ---------------------------------------------------------------------------
//
// After the current matter, a story gets up to MAX_SECTIONS sections, one per
// topic it touches. A topic is a rare word or a name from the story
// ("horticulture", "Rayalaseema", "biogas", "PM-KUSUM"); its section is the
// subsection of the notes that covers it best - the topic in the subsection's
// own title counts most, then how often the bullets name it, then how many of
// the story's other topics the subsection also covers (so "Rayalaseema" next
// to "horticulture" and "drought" finds the Rayalaseema plateau sheet, not the
// Andhra Movement). The bullets are the subsection's own, the ones naming the
// story's topics first. Topics the notes do not cover are returned as gaps.

const MAX_SECTIONS = 5;
const MAX_BULLETS = 5;

function titleCase(s) {
  return s.replace(/(^|\s)(\p{Ll})/gu, (m, a, b) => a + b.toUpperCase());
}

// News words that are never a study topic, however rare in the notes.
const NEWS_WORDS = new Set(
  ('approve approves approved launch launches launched release releases released record records recorded support ' +
    'supports special chief director directors matter matters step steps billion million crore crores lakh lakhs ' +
    'thousand percent amend amends amended amendment calls call says said plans plan boost boosts push pushes cross ' +
    'crosses crossed inaugurate inaugurates inaugurated unveil unveils unveiled announce announces announced pledge ' +
    'pledges pledged promise promises promised seek seeks sought urge urges urged hold holds held take takes taken ' +
    'gets get got win wins won highlight highlights discuss discusses discussed review reviews reviewed sign signs ' +
    'signed visit visits visited receive receives received mark marks marked observe observes observed organise ' +
    'organises organised organize organizes conduct conducts conducted celebrate celebrates celebrated complete ' +
    'completes completed begin begins began end ends ended rise rises rose fall falls fell plunge plunges plunged jump ' +
    'jumps jumped surge surges surged decline declines declined today tomorrow yesterday week weeks month months ' +
    'january february march april june july august september october november december monday tuesday wednesday ' +
    'thursday friday saturday sunday officials official officer officers executive managing chairman chairperson ' +
    'secretary member members team people public major minor latest recent across ahead amid after before over under ' +
    'grassroots heart along global national local regional total overall approach approaches correcting correct ' +
    'transforming transform transforms driving drive drives empowering empower empowers deepening deepen discuss ' +
    'continue continues continued remain remains remained likely unlikely despite towards toward inclusive growth ' +
    'year years days role roles move moves moved order orders ordered decision decisions report reports reported ' +
    'issue issues issued meet meets meeting held hits hit sets set says rules rule notified directed directs ' +
    'presidential centric initiative initiatives programme programmes scheme schemes agreement agreements plants ' +
    'environment development developments process measures measure efforts effort implementation doing start ' +
    'starts started group groups theme themes proposals proposal north south east oct nov dec sept jan feb aug ' +
    'times india indian lays stone foundation')
    .split(' ')
);

/** A headline in Title Case ("RBI Approves Anup Bagchi As HDFC Bank Chief"): its capitals say nothing. */
function isTitleCase(s) {
  const ws = String(s).split(/\s+/).filter((w) => /^\p{L}/u.test(w) && w.length > 3);
  if (ws.length < 4) return false;
  return ws.filter((w) => /^\p{Lu}/u.test(w)).length / ws.length >= 0.75;
}

/** The story's topics: names first, then rare words, most specific first. */
function topicsOf(index, item, extra = []) {
  // names are read from the summary when the headline is in Title Case
  const titleCased = isTitleCase(item.title);
  const nameText = `${titleCased ? '' : item.title + '. '}${item.summary || ''}`;
  const text = `${item.title}. ${item.summary || ''} ${(item.facts || []).map((f) => f.text).join(' ')}`;
  const N = index.size || 1;
  const head = new Set(tokens(item.title));
  const out = [];
  const seen = new Set();
  const push = (label, words, weight, kind) => {
    const key = words.join(' ');
    if (!key || seen.has(key)) return;
    // "Vijayawada on Friday": a dateline, not a name
    if (/\b(on|last|this|next)\s+(mon|tues|wednes|thurs|fri|satur|sun)day\b/i.test(label)) return;
    seen.add(key);
    out.push({ label, words, weight, kind, inHead: words.every((w) => head.has(w)) });
  };
  const headAcronyms = titleCased ? phrasesOf(item.title).filter((p) => p.kind === 'acronym') : [];
  for (const p of [...phrasesOf(nameText, extra), ...headAcronyms]) {
    const ws = tokens(p.text).filter((w) => !NEWS_WORDS.has(w));
    if (!ws.length) continue;
    const df = Math.min(...ws.map((w) => (index.postings.get(w) || []).length));
    if (df < 1 || df > 1500) continue;
    push(p.text, ws, Math.log(N / df) * (ws.some((w) => head.has(w)) ? 2 : 1) * (ws.length > 1 ? 1.3 : 1), p.kind === 'alias' ? 'name' : p.kind);
  }
  // Ordinary words: only where the story writes them in lower case, so the
  // pieces of a name ("Reserve" of Reserve Bank, "Reliance", "Embassy" REIT)
  // never become topics. A Title Case headline's words count as lower case.
  const lowerText = `${titleCased ? item.title.toLowerCase() : item.title}. ${item.summary || ''} ${(item.facts || []).map((f) => f.text).join(' ')}`;
  const counts = new Map();
  for (const m of lowerText.matchAll(/(?<![\p{L}\p{N}])\p{Ll}[\p{Ll}\p{N}-]+/gu)) {
    for (const w of tokens(m[0])) counts.set(w, (counts.get(w) || 0) + 1);
  }
  for (const [w, n] of counts) {
    // A topic word is one the notes use as a heading somewhere (1-30 headings):
    // "verdict", "increase", "proof" head no subsection; "programmes" heads hundreds.
    const hdf = index.headDf ? index.headDf.get(w) || 0 : 1;
    if (w.length < 5 || NEWS_WORDS.has(w) || hdf < 1 || hdf > 30) continue;
    const df = (index.postings.get(w) || []).length;
    if (df < 2 || df > 900) continue;
    push(w, [w], Math.log(N / df) * (head.has(w) ? 2 : 1) * (1 + 0.3 * (n - 1)), 'word');
  }
  return out.sort((a, b) => b.weight - a.weight).slice(0, 10);
}

function secHas(index, sec, words) {
  const re = new RegExp(`(?<![\\p{L}\\p{N}])${words.map(escapeRe).join('[\\s-]+')}`, 'iu');
  let n = 0;
  for (const id of sec.entries) if (re.test(index.entries[id].text)) n++;
  return { n, title: re.test(sec.title) };
}

/**
 * { sections: [{ topic, src, where, bullets: [..] }], gaps: [topic, ..] }.
 * `item.lang === 'te'` stories are read through their English gloss.
 */
function briefFor(index, item0) {
  if (!index || !index.size) return { sections: [], gaps: [] };
  const telugu = /[ఀ-౿]/.test(item0.title);
  const terms = telugu ? glossTerms(item0.title) : [];
  const item = telugu ? { ...item0, title: terms.join('. ') || item0.title } : item0;
  const topics = topicsOf(index, item, terms);
  // the story's real topics, for "does this subsection also cover the story?"
  const allWords = new Set(
    topics.filter((t) => t.kind === 'word' || t.words.length > 1).flatMap((t) => t.words)
  );

  const sections = [];
  const gaps = [];
  const usedSecs = new Set();
  const chosenTitles = [];
  for (const t of topics) {
    if (sections.length >= MAX_SECTIONS) break;
    // already covered by a chosen section's heading ("Reserve" after "Tiger Reserves")
    if (chosenTitles.length && t.words.every((w) => chosenTitles.some((ct) => ct.has(w)))) continue;
    // candidate subsections: those with a bullet naming the topic
    const ids = t.words.length > 1 ? findPhrase(index, { text: t.label, kind: 'name' }) : index.postings.get(t.words[0]) || [];
    const secIds = new Set(ids.map((id) => index.entries[id].sec));
    // and subsections headed by a several-word name its bullets do not repeat
    // ("Polavaram Project | Funding")
    if (t.words.length > 1) {
      const reName = new RegExp(`(?<![\\p{L}\\p{N}])${t.words.map(escapeRe).join('[\\s-]+')}`, 'iu');
      for (const sec of index.secs) if (reName.test(sec.head || '')) secIds.add(sec.id);
    }
    const isName = t.kind !== 'word' && (t.words.length > 1 || /^[A-Z0-9-]{2,}$/.test(t.label)) && t.label.split(/\s+/).length <= 4;
    if (!secIds.size) {
      if (isName) gaps.push(t.label);
      continue;
    }
    let best = null;
    for (const sid of secIds) {
      if (usedSecs.has(sid)) continue;
      const sec = index.secs[sid];
      const own = secHas(index, sec, t.words);
      if (!own.n && !own.title) continue;
      // the story's other topics this subsection also covers
      let ctx = 0;
      for (const w of allWords) if (!t.words.includes(w) && sec.bag.has(w)) ctx++;
      // The subsection must be ABOUT the topic. Always good enough: two
      // bullets naming it alongside two more of the story's topics.
      const strongBody = own.n >= 2 && ctx >= 2;
      const reHead = new RegExp(`(?<![\\p{L}\\p{N}])${t.words.map(escapeRe).join('[\\s-]+')}`, 'iu');
      const inHeading = reHead.test(sec.head || sec.title);
      let ok = strongBody && t.kind !== 'word';
      if (t.kind === 'word') {
        // an ordinary word: what the subsection is ABOUT (its heading before the
        // " - " list) must be it - "Horticulture - MIDH and NHM ...", "Biogas and
        // biomass - ...", "Tiger Reserves and Landscapes" - not a word in the list
        // and it must share another of the story's topics, so "Quota" alone
        // never pulls in the IMF quota or "Left" the Left parties
        // - unless the headline itself names it and the heading opens with it
        // ("Biogas and biomass", "Horticulture")
        const opens = (sec.head || sec.title).split('|').some((part) => tokens(part)[0] === t.words[0]);
        // Only headline words: the body's ordinary words ("family", "senior",
        // "sessions") are too loose to be the story's topic.
        ok = t.inHead && inHeading && (ctx >= 1 || opens);
      } else if (t.words.length > 1) {
        // a several-word name: the exact name in the heading or bullets
        // (only in the bullets: the strong rule above, so a leader's name does
        // not pull in whatever bullet mentions them)
        ok = ok || inHeading;
      } else {
        // a one-word name or an acronym (a place, a person, a party, a body):
        // a heading about it that also covers another of the story's topics
        ok = ok || (inHeading && ctx >= 1);
      }
      if (!ok) continue;

      const score = (own.title ? 6 : 0) + Math.min(own.n, 8) + 1.5 * ctx + (sec.src === 'prep' ? 0.5 : 0);
      if (!best || score > best.score) best = { sec, score, own };
    }
    if (!best || best.score < 2.5) {
      if (!best && isName) gaps.push(t.label);
      continue;
    }
    usedSecs.add(best.sec.id);
    chosenTitles.push(new Set(tokens(best.sec.title)));
    // bullets naming this topic or the story's other topics first, in notes order
    const re = new RegExp(`(?<![\\p{L}\\p{N}])${t.words.map(escapeRe).join('[\\s-]+')}`, 'iu');
    const ranked = best.sec.entries
      .map((id, order) => {
        const e = index.entries[id];
        const ws = new Set(tokens(e.text));
        let s = re.test(e.text) ? 3 : 0;
        for (const w of allWords) if (ws.has(w)) s += 1;
        return { e, s, order };
      })
      .filter((x) => x.s > 0 || best.own.title);
    const chosen = ranked
      .sort((a, b) => b.s - a.s || a.order - b.order)
      .slice(0, MAX_BULLETS)
      .sort((a, b) => a.order - b.order)
      .map((x) => clip(x.e.text));
    if (!chosen.length) continue;
    const sec = best.sec;
    // an ordinary word is labelled by the heading it was found under
    // ("prone" -> "Drought-prone areas")
    let label = t.label;
    if (t.kind === 'word') {
      const part = (sec.head || '').split('|').map((x) => x.trim()).find((x) => re.test(x));
      if (part && part.split(/\s+/).length <= 6) label = part;
    }
    sections.push({
      topic: titleCase(label),
      src: sec.src === 'prep' ? 'Prep notes' : 'Rocket Sheets',
      where: sec.where,
      bullets: chosen,
    });
  }
  return { sections, gaps: gaps.slice(0, 4) };
}

// Bumped when briefFor changes, so stories on file are re-linked.
const BRIEF_VERSION = 1;

/** briefFor as stored on a story: { v, sections, gaps }. */
function briefOf(index, item) {
  const { sections, gaps } = briefFor(index, item);
  return { v: BRIEF_VERSION, sections, gaps };
}

module.exports.briefFor = briefFor;
module.exports.briefOf = briefOf;
module.exports.BRIEF_VERSION = BRIEF_VERSION;
module.exports.topicsOf = topicsOf;
