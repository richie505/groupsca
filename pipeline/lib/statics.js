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
            secs.push({ id, src: 'prep', book: n, unit: u.code || '', where, title: `${row.title} ${s.t}`, entries: [] });
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
            secs.push({ id, src: 'rocket', book: n, unit: u.title, where, title: row.title, entries: [] });
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
  return { entries, lower, postings, secs, secDf, size: entries.length };
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
      // drop generic words at the ends of a run: "Centre approves Teesta Bridge" -> "Teesta Bridge"
      while (run.length > 1 && GENERIC.has(run[0])) run.shift();
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
