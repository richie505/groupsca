'use strict';

// Fetching and parsing the sources: RSS channels and PIB's release listing.
//
// Ported from the portal's content-pipeline/ca-daily/sweep.js and
// fetch-source.js, minus the database and the model. No npm dependencies, so
// the GitHub Actions job needs nothing but Node.

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
const FETCH_TIMEOUT_MS = 20000;

const MONTHS = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

const ENTITIES = {
  '&nbsp;': ' ', '&amp;': '&', '&quot;': '"', '&#39;': "'", '&apos;': "'", '&lt;': '<', '&gt;': '>',
  '&rsquo;': '’', '&lsquo;': '‘', '&rdquo;': '”', '&ldquo;': '“', '&ndash;': '–', '&mdash;': '—',
  '&hellip;': '…', '&rupee;': '₹', '&#8377;': '₹',
};

function entities(s) {
  let out = String(s);
  // &amp; last-but-numeric, so "&amp;quot;" becomes "&quot;" and not '"'.
  for (const [k, v] of Object.entries(ENTITIES)) if (k !== '&amp;') out = out.split(k).join(v);
  out = out
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)));
  return out.split('&amp;').join('&');
}

/** Markup and entities out, whitespace collapsed. */
function decode(s) {
  return entities(
    String(s)
      .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
      // Feeds often escape the HTML inside <description>; unescape once so the
      // tags can be stripped rather than shown.
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/<[^>]+>/g, ' ')
  )
    .replace(/\s+/g, ' ')
    .trim();
}

// Every date is the INDIAN calendar day the story was published.
//
// A timestamp with a zone ('Fri, 02 Oct 2026 22:00:00 +0000',
// '2026-10-02T22:50:54+05:30') is converted to IST first: Mint and NDTV print
// UTC, so a story published at 3:30 AM IST on 3 October reads "2 Oct ...
// 22:00 +0000" and belongs to 3 October. A date with no time or zone (PIB's
// 'Posted on: 19 Aug 2026', a digest's "Current Affairs 1 October 2026") is
// already an Indian date and is taken as printed.
const IST_MS = 5.5 * 3600 * 1000;

function toIso(raw) {
  const s = decode(raw);
  if (/\d{1,2}:\d{2}/.test(s) && /(?:[+-]\d{2}:?\d{2}|\bGMT|\bUTC|\dZ)\s*$/.test(s)) {
    const t = Date.parse(s);
    if (!Number.isNaN(t)) return new Date(t + IST_MS).toISOString().slice(0, 10);
  }
  let m = s.match(/(\d{1,2})\s+([A-Za-z]{3})[a-z]*\.?,?\s+(\d{4})/);
  if (m) {
    const mon = MONTHS[m[2].toLowerCase()];
    if (mon) return `${m[3]}-${mon}-${String(m[1]).padStart(2, '0')}`;
  }
  m = s.match(/(\d{4})-(\d{2})-(\d{2})/);
  if (m) return `${m[1]}-${m[2]}-${m[3]}`;
  return null;
}

async function fetchText(url, { timeoutMs = FETCH_TIMEOUT_MS } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      headers: {
        'User-Agent': UA,
        Accept: 'application/rss+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8',
        'Accept-Language': 'en-IN,en;q=0.9',
      },
      redirect: 'follow',
      signal: controller.signal,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.text();
  } catch (e) {
    throw new Error(e.name === 'AbortError' ? 'timed out' : e.message);
  } finally {
    clearTimeout(timer);
  }
}

function tag(block, name) {
  const m = block.match(new RegExp(`<${name}[^>]*>([\\s\\S]*?)</${name}>`, 'i'));
  return m ? decode(m[1]) : '';
}

/** RSS 2.0 items and Atom entries. */
function parseRss(xml) {
  const out = [];
  for (const m of xml.matchAll(/<(item|entry)[\s>][\s\S]*?<\/\1>/gi)) {
    const block = m[0];
    const headline = tag(block, 'title');
    const link = tag(block, 'link') || (block.match(/<link[^>]*href="([^"]+)"/i) || [])[1] || '';
    const date = toIso(tag(block, 'pubDate') || tag(block, 'dc:date') || tag(block, 'updated') || tag(block, 'published'));
    if (!headline || !link || !date) continue;
    out.push({
      headline,
      date,
      url: link.trim(),
      summary: (tag(block, 'description') || tag(block, 'summary')).slice(0, 700),
      category: tag(block, 'category'),
    });
  }
  return out;
}

// PIB's listing groups releases under <h3>Ministry</h3> headings, then lists
// each as an anchor carrying the headline in its title attribute, the release id
// in the href and the date in a following span.
function parsePibIndex(html) {
  const out = [];
  let ministry = '';
  const token =
    /<h3>([\s\S]*?)<\/h3>|<a\s+title='([^']*)'\s+href='\/PressRele[a-zA-Z]*\.aspx\?PRID=(\d+)'[\s\S]{0,400}?publishdatesmall'>\s*Posted on:\s*([^<]*)/g;
  let m;
  while ((m = token.exec(html)) !== null) {
    if (m[1] !== undefined) {
      ministry = decode(m[1]);
      continue;
    }
    const headline = decode(m[2]);
    const date = toIso(m[4]);
    if (!headline || !date) continue;
    out.push({
      headline,
      date,
      url: `https://www.pib.gov.in/PressReleasePage.aspx?PRID=${m[3]}&reg=3&lang=1`,
      summary: '',
      category: ministry,
    });
  }
  return out;
}

// A Google News sitemap (<news:title>, <news:publication_date>) or a plain
// sitemap (<loc>, <lastmod> only, in which case the headline is read from the
// URL's slug). Used for sites with no RSS whose robots.txt allows the sitemap:
// Eenadu (Telugu), Vision IAS.
function slugTitle(url) {
  const parts = decodeURIComponent(String(url).replace(/[?#].*$/, '').replace(/\/+$/, '')).split('/');
  // Eenadu-style URLs end in numeric ids (/1701/126178528): use the last word segment.
  const slug = [...parts].reverse().find((p) => /[a-z]/i.test(p)) || '';
  const words = slug.replace(/\.[a-z]+$/i, '').replace(/[-_]+/g, ' ').trim();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : '';
}

function parseSitemap(xml, { pathIncludes = null } = {}) {
  const out = [];
  for (const m of String(xml).matchAll(/<url>([\s\S]*?)<\/url>/gi)) {
    const block = m[1];
    const url = tag(block, 'loc');
    if (!url || (pathIncludes && !pathIncludes.some((p) => url.includes(p)))) continue;
    const date = toIso(tag(block, 'news:publication_date') || tag(block, 'lastmod'));
    const headline = tag(block, 'news:title') || slugTitle(url);
    if (!headline || !date) continue;
    out.push({ headline, date, url, summary: '', category: tag(block, 'news:keywords') });
  }
  return out;
}

/**
 * An article page's own title, description and publish date, from its
 * Open Graph / article meta tags. Used for GKToday, whose sitemap gives only
 * URLs.
 */
function pageMeta(html) {
  const meta = (attr, name) => {
    const re = new RegExp(`<meta[^>]+${attr}=["']${name}["'][^>]*>`, 'i');
    const tagHtml = (String(html).match(re) || [])[0] || '';
    return decode((tagHtml.match(/content=["']([^"']*)["']/i) || [])[1] || '');
  };
  const title = meta('property', 'og:title') || decode((String(html).match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1] || '');
  return {
    title: title.replace(/\s+[-–|]\s+GKToday\s*$/i, '').trim(),
    description: meta('property', 'og:description') || meta('name', 'description'),
    published: toIso(meta('property', 'article:published_time') || meta('property', 'og:updated_time')),
  };
}

/** The child sitemaps of a sitemap index, in the order listed. */
function parseSitemapIndex(xml) {
  return [...String(xml).matchAll(/<sitemap>[\s\S]*?<loc>([\s\S]*?)<\/loc>[\s\S]*?<\/sitemap>/gi)].map((m) => decode(m[1]));
}

/**
 * Readable text of a page, one block per line. Block tags become newlines
 * before the rest of the markup is stripped, so a PIB table of figures stays
 * readable. Nav chrome survives; releaseBody() below trims it for PIB.
 */
function extractText(html) {
  let t = String(html)
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<noscript[\s\S]*?<\/noscript>/gi, ' ')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|tr|h[1-6]|section|article|blockquote)>/gi, '\n')
    .replace(/<\/(td|th)>/gi, ' · ')
    .replace(/<[^>]+>/g, ' ');
  t = entities(t);
  return t
    .split('\n')
    .map((line) => line.replace(/[ \t ]+/g, ' ').trim())
    .filter(Boolean)
    .filter((line) => line.split(' ').length > 2 || /\d/.test(line))
    .join('\n');
}

/**
 * The body of one PIB release: the paragraphs after its own headline, up to
 * the footer. Falls back to every long line on the page when the headline is
 * not found (PIB changes its markup now and then).
 */
function releaseBody(html, headline) {
  const lines = extractText(html).split('\n');
  const key = String(headline || '').toLowerCase().slice(0, 40);
  let start = key ? lines.findIndex((l) => l.toLowerCase().includes(key)) : -1;
  const body = [];
  for (const line of lines.slice(start + 1)) {
    if (/^(\*{3}|\(Release ID|Visitor Counter|Follow us on|Please wait)/i.test(line)) break;
    if (/^Posted On:|^PIB (Delhi|Hyderabad|Vijayawada)|^Ministry of /i.test(line)) continue;
    if (line.length < 50 && !/\d/.test(line)) continue;
    body.push(line);
    if (body.join(' ').length > 6000) break;
  }
  if (!body.length && start !== -1) return releaseBody(html, '');
  return body.join('\n');
}

// ---------------------------------------------------------------------------
// dedupe
// ---------------------------------------------------------------------------

// The same story appears in four papers on the same day. Matched on a signature
// of the significant words rather than the whole headline, because papers
// rewrite headlines freely while keeping the nouns and the numbers.
const STOP = new Set(
  ('a an the and or but of in on at to for from with by as is are was were be been being this that these those ' +
    'it its his her their our your new says said will would may can could s t after over amid ahead')
    .split(' ')
);

function words(headline) {
  return String(headline)
    .toLowerCase()
    // Any script's letters, not just a-z: with ASCII only, every Telugu
    // headline without a digit had the same empty signature and all of them
    // were merged into one story (2 Oct 2026, Eenadu: 162 -> 1).
    .replace(/[^\p{L}\p{M}\p{N}₹%\s]/gu, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 2 && !STOP.has(w));
}

function signature(headline) {
  const w = words(headline);
  const nums = w.filter((x) => /\d/.test(x));
  const rest = w.filter((x) => !/\d/.test(x)).slice(0, 6).sort();
  return [...nums.sort(), ...rest].join('|');
}

/** Share of content words two headlines have in common (0..1). */
function overlap(a, b) {
  const A = new Set(words(a));
  const B = new Set(words(b));
  if (!A.size || !B.size) return 0;
  let n = 0;
  for (const w of A) if (B.has(w)) n++;
  return n / Math.min(A.size, B.size);
}

// Which of two copies of the same story to keep: the official one, then the
// AP desk's, then anything but opinion.
function copyRank(it) {
  let r = 0;
  if (it.primary) r += 100;
  if (it.apSource) r += 20;
  if (it.ap) r += 10;
  if (it.opinion) r -= 5;
  return r;
}

/** One copy per story; the others are listed in `alsoIn`. */
function dedupe(items) {
  const kept = [];
  for (const it of items) {
    const sig = signature(it.headline);
    const dup = kept.find((k) => k.sig === sig || overlap(k.headline, it.headline) >= 0.75);
    if (!dup) {
      kept.push({ ...it, sig, alsoIn: [] });
      continue;
    }
    if (copyRank(it) > copyRank(dup)) {
      const also = [...dup.alsoIn, dup.sourceName].filter((s) => s !== it.sourceName);
      Object.assign(dup, { ...it, sig, alsoIn: [...new Set(also)] });
    } else if (dup.sourceName !== it.sourceName && !dup.alsoIn.includes(it.sourceName)) {
      dup.alsoIn.push(it.sourceName);
    }
  }
  return kept;
}

module.exports = {
  UA, decode, toIso, fetchText, parseRss, parsePibIndex, parseSitemap, parseSitemapIndex, slugTitle, pageMeta, extractText, releaseBody,
  signature, overlap, dedupe,
};
