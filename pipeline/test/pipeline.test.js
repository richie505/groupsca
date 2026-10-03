'use strict';

// node --test pipeline/test
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const F = require('../lib/fetch');
const S = require('../lib/score');
const { keyFacts, summarise } = require('../lib/facts');
const { run, todayIst } = require('../daily');
const St = require('../lib/statics');
const NOTES = St.loadNotes({ prepDir: path.join(__dirname, 'fixtures', 'notes', 'prep'), rocketDir: path.join(__dirname, 'fixtures', 'notes', 'rocket') });

const vocab = S.loadVocab();
const FIX = path.join(__dirname, 'fixtures');

test('RSS parsing decodes CDATA and escaped HTML', () => {
  const rows = F.parseRss(fs.readFileSync(path.join(FIX, 'hindu-national.xml'), 'utf8'));
  assert.equal(rows.length, 3);
  assert.equal(rows[0].date, '2026-10-02');
  assert.ok(!rows[0].summary.includes('<p>'));
});

test('PIB index keeps the ministry and the release id', () => {
  const rows = F.parsePibIndex(fs.readFileSync(path.join(FIX, 'pib.html'), 'utf8'));
  assert.equal(rows.length, 3);
  assert.equal(rows[0].category, 'Cabinet');
  assert.match(rows[1].url, /PRID=2200002/);
});

test('PIB release body drops the page chrome and the footer', () => {
  const html = fs.readFileSync(path.join(FIX, 'pib-2200001.html'), 'utf8');
  const body = F.releaseBody(html, 'Cabinet approves Rs 12,500 crore PM-Surya Ghar extension');
  assert.match(body, /^The Union Cabinet/);
  assert.ok(!/Release ID|Visitor Counter|Posted On/.test(body));
});

test('the same story from two papers is kept once, official copy first', () => {
  const out = F.dedupe([
    { headline: 'Cabinet okays Rs 12,500 crore PM-Surya Ghar extension', sourceName: 'The Hindu' },
    { headline: 'Cabinet approves Rs 12,500 crore PM-Surya Ghar extension', sourceName: 'PIB', primary: true },
  ]);
  assert.equal(out.length, 1);
  assert.equal(out[0].sourceName, 'PIB');
  assert.deepEqual(out[0].alsoIn, ['The Hindu']);
});

test('crime, films and match reports are vetoed', () => {
  for (const headline of ['Two arrested for robbery in Nandyal', 'Actor’s film trailer released', 'India beat Australia by 6 wickets']) {
    assert.ok(S.score({ headline }, vocab).vetoed, headline);
  }
});

test('Andhra Pradesh stories are AP in full when the place is in the summary', () => {
  const r = S.score({
    headline: 'Etikoppaka toys get GI tag',
    summary: 'The GI Registry granted the tag to lacquerware toys of Etikoppaka in Anakapalli district.',
  }, vocab);
  assert.equal(r.ap, true);
  assert.equal(r.why.ap, 20);
  assert.equal(r.bucket, 'ap');
});

test('an AP story about a Union decision also has a national scope', () => {
  const r = S.score({
    headline: 'Union Cabinet approves Rs 2,800 crore for Polavaram project',
    summary: 'The Union Cabinet approved funds for the national project in Andhra Pradesh.',
  }, vocab);
  assert.equal(r.bucket, 'ap');
  assert.equal(r.scope, 'national');
});

test('units are named by the combined syllabus tracker', () => {
  const r = S.score({ headline: 'RBI keeps repo rate unchanged at 5.5%', summary: 'Monetary Policy Committee; GDP growth projection; inflation.' }, vocab);
  const codes = r.units.map((u) => u.code);
  assert.ok(codes.includes('G1-C4'), codes.join());
  assert.ok(codes.some((c) => c.startsWith('G2-M2A')), codes.join());
  assert.deepEqual(r.exams, ['G1', 'G2']);
});

test('a foreign country\'s domestic story does not claim Indian syllabus units', () => {
  const r = S.score({ headline: 'Imran Khan shifted back to jail after medical check-up', summary: 'The Supreme Court of Pakistan heard the plea.' }, vocab);
  assert.ok(r.score < 40, String(r.score));
});

test('key facts are labelled sentences, figures first', () => {
  const f = keyFacts('India has added two wetlands to the list of Ramsar sites, taking the total to 93. The sites are in Andhra Pradesh and Tamil Nadu, says the Ministry release today.');
  assert.equal(f[0].angle, 'GI tag / designation');
  assert.ok(summarise('A'.repeat(1000)).length <= 420);
});

test('dates are the Indian calendar day of publication', () => {
  assert.equal(F.toIso('Fri, 02 Oct 2026 22:00:00 +0000'), '2026-10-03', 'UTC evening is the next IST day');
  assert.equal(F.toIso('Fri, 02 Oct 2026 17:00:00 +0000'), '2026-10-02');
  assert.equal(F.toIso('Fri, 02 Oct 2026 23:50:00 +0530'), '2026-10-02');
  assert.equal(F.toIso('2026-10-02T22:50:54+05:30'), '2026-10-02');
  assert.equal(F.toIso('Posted on: 02 Oct 2026 11:30PM'), '2026-10-02', 'no zone: taken as printed');
  assert.equal(F.toIso('Current Affairs 1 October 2026'), '2026-10-01');
});

test('news day: 06:00 IST to 05:59 IST next morning', () => {
  assert.equal(todayIst(Date.parse('2026-10-01T19:00:00Z')), '2026-10-01', '00:30 IST on 2 Oct is still the 1 Oct news day');
  assert.equal(todayIst(Date.parse('2026-10-02T01:00:00Z')), '2026-10-02', '06:30 IST starts the 2 Oct news day');
  assert.equal(F.newsDay({ date: '2026-10-03', time: '05:10' }), '2026-10-02', "Eenadu's 5 AM upload reports the day before");
  assert.equal(F.newsDay({ date: '2026-10-03', time: '06:00' }), '2026-10-03');
  assert.equal(F.newsDay({ date: '2026-10-02', time: '23:50' }), '2026-10-02');
  assert.equal(F.newsDay({ date: '2026-10-02', time: null }), '2026-10-02', 'no time: the date as given');
  assert.equal(F.pibPosted('<p>Posted On: 02 OCT 2026 3:15PM by PIB Delhi</p>').time, '15:15');
});

test('offline run: AP items are kept, merged per day, and not repeated on the next run', async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'feed-'));
  const args = { date: '2026-10-02', fixtures: FIX, out, dryRun: false, notes: NOTES };
  const first = await run(args);
  assert.ok(first.report.kept >= 5, JSON.stringify(first.report));
  assert.ok(first.report.keptAp >= 3);
  const day = JSON.parse(fs.readFileSync(path.join(out, 'days', '2026-10-02.json'), 'utf8'));
  assert.ok(day.items.every((i) => i.score >= (i.ap ? 30 : 40) || i.apFloor || i.digest));
  assert.ok(day.items.every((i) => S.SUBJECTS.includes(i.subject)));
  assert.ok(!day.items.some((i) => /arrested|film/i.test(i.title)));
  const index = JSON.parse(fs.readFileSync(path.join(out, 'index.json'), 'utf8'));
  assert.equal(index.days[0].date, '2026-10-02');

  // Vetoed stories are looked at again (only published ones are remembered),
  // but nothing is published twice.
  const second = await run(args);
  assert.equal(second.kept.length, 0);
  const again = JSON.parse(fs.readFileSync(path.join(out, 'days', '2026-10-02.json'), 'utf8'));
  assert.equal(again.items.length, day.items.length);
});

test('news sitemap: Telugu headlines, filtered to the AP section', () => {
  const rows = F.parseSitemap(fs.readFileSync(path.join(FIX, 'eenadu-ap.xml'), 'utf8'), {
    pathIncludes: ['/telugu-news/andhra-pradesh/'],
  });
  assert.equal(rows.length, 2);
  assert.equal(rows[0].date, '2026-10-02');
  assert.match(rows[0].headline, /పోలవరం/);
});

test('plain sitemap: the headline is read from the slug, numbers kept', () => {
  assert.equal(F.slugTitle('https://visionias.in/current-affairs/news-today/green-energy-corridor-phase-iii/'), 'Green energy corridor phase iii');
  assert.equal(F.slugTitle('https://www.gktoday.in/centre-approves-1200-crore-teesta-bridge/'), 'Centre approves 1200 crore teesta bridge');
});

test('Telugu: a Cabinet decision on Polavaram is examinable, a murder is vetoed', () => {
  const good = S.score({ headline: 'పోలవరం నిధులకు కేబినెట్‌ ఆమోదం: రూ.2,800 కోట్లు', apSource: true }, vocab);
  assert.ok(!good.vetoed);
  assert.ok(good.anchored, JSON.stringify(good));
  assert.ok(good.angles.includes('Cabinet'));
  assert.ok(good.why.importance > 0);
  assert.ok(good.score >= 35, String(good.score));
  assert.ok(S.score({ headline: 'గుంటూరులో హత్య కేసులో ఇద్దరి అరెస్టు', apSource: true }, vocab).vetoed);
});

test('Telugu headlines without digits are not merged into one story', () => {
  const out = F.dedupe([
    { headline: 'రాష్ట్ర అప్రెంటిస్‌షిప్‌ కౌన్సిల్‌ ఏర్పాటు', sourceName: 'Eenadu' },
    { headline: 'పరిశ్రమల కోసం భూముల గుర్తింపు', sourceName: 'Eenadu' },
    { headline: 'ఎగుమతుల్లో విశాఖ అగ్రస్థానం', sourceName: 'Eenadu' },
  ]);
  assert.equal(out.length, 3);
});

test('every story is filed under one of the 6 books', () => {
  assert.equal(S.SUBJECTS.length, 6);
  const cases = [
    ['RBI keeps repo rate unchanged at 5.5%', 'Monetary Policy Committee; GDP growth projection; inflation.', 'Economy'],
    ['India, Japan sign maritime security pact at bilateral summit', 'External Affairs Minister said the treaty...', 'Polity, Society & IR'],
    ['ISRO launches PSLV with EOS-09 satellite from Sriharikota', '', 'Science, Tech & Environment'],
    ['Two new Ramsar sites added in India', 'Wetlands of international importance; biodiversity conservation.', 'Science, Tech & Environment'],
    ['Supreme Court strikes down electoral bond scheme under Article 19', 'Constitution bench held', 'Polity, Society & IR'],
    ['Nagarjunakonda excavation finds Ikshvaku inscription', 'Archaeological Survey of India', 'History & Culture'],
    ['Neeraj Chopra wins gold at Diamond League final', '', 'Current Affairs'],
    ['Ankush Panghal Wins Asian Games Men’s 80 kg Gold', 'Boxing', 'Current Affairs'],
  ];
  for (const [headline, summary, want] of cases) {
    const r = S.score({ headline, summary }, vocab);
    assert.equal(r.subject, want, headline);
    assert.equal(r.subjects[0], want);
    assert.ok(S.SUBJECTS.includes(r.subject));
  }
});

test('sitemap index: child sitemaps in order', () => {
  const kids = F.parseSitemapIndex(fs.readFileSync(path.join(FIX, 'gktoday.xml'), 'utf8'));
  assert.equal(kids.length, 3);
  assert.match(kids[1], /posts-post-2\.xml$/);
});

test('offline run: GKToday newest posts (quizzes skipped) and the AffairsCloud digest are taken', async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'feed-'));
  const r = await run({ date: '2026-10-02', fixtures: FIX, out, dryRun: true, notes: NOTES });
  const urls = r.scored.map((x) => x.article.url);
  assert.ok(urls.some((u) => /financial-stability-report/.test(u)));
  assert.ok(!urls.some((u) => /quizbase/.test(u)));
  const gk = r.scored.find((x) => /p4-initiative/.test(x.article.url));
  assert.equal(gk.article.headline, 'Andhra Pradesh launches P4 initiative to end poverty', 'title read from the page');
  assert.equal(gk.article.date, '2026-10-01', 'publish date read from the page');
  assert.match(gk.article.summary, /Swarna Andhra/);
  assert.ok(!urls.some((u) => /old-story/.test(u)), 'a post published outside the window is dropped');
  const rbi = r.kept.find((i) => /Financial Stability/.test(i.title));
  assert.equal(rbi.title, 'RBI Releases Financial Stability Report, December 2026', '"- GKToday" is trimmed');
  const digest = r.kept.find((i) => i.title === 'Current Affairs 2 October 2026');
  assert.ok(digest && digest.digest);
  assert.equal(digest.date, '2026-10-02', 'filed under the day its title names');
  assert.ok(!urls.some((u) => /sbi-po/.test(u)), 'AffairsCloud non-digest posts are skipped');
});

test('no coaching sources are configured', () => {
  const { SOURCES } = require('../sources');
  assert.ok(!SOURCES.some((s) => /vajiram|kpias|vision|drishti/i.test(s.id + s.url)));
});

test('GKToday posts are kept as current-affairs picks', async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'feed-'));
  const r = await run({ date: '2026-10-02', fixtures: FIX, out, dryRun: true, notes: NOTES });
  const gk = r.kept.filter((i) => i.sourceId === 'gktoday');
  assert.ok(gk.some((i) => /Financial Stability Report/.test(i.title)));
  assert.ok(gk.every((i) => i.digest));
});

test('static notes: exact links from the headline, a unit/book note for every story', () => {
  const ramsar = St.staticFor(NOTES, { title: 'Kolleru Lake declared a Ramsar site', summary: '', units: [], subject: 'Science, Tech & Environment' });
  assert.equal(ramsar[0].tier, 'exact');
  assert.match(ramsar[0].text, /Kolleru Lake/);
  const repo = St.staticFor(NOTES, { title: 'RBI keeps repo rate unchanged', summary: '', units: [{ code: 'G1-C4' }], subject: 'Economy' });
  assert.ok(repo.some((n) => n.src === 'Prep notes' && /repo rate/.test(n.text)));
  assert.ok(repo.some((n) => n.src === 'Rocket Sheets' && /repo rate/.test(n.text)), 'Rocket key facts, not the raw sheet text');
  assert.ok(!repo.some((n) => /raw scan/.test(n.text)));
  assert.ok(!repo.some((n) => /\[Indian Economy/.test(n.text)), 'source tags are stripped');
  // headline names win over side names in the summary
  const g = St.staticFor(NOTES, { title: 'Mahatma Gandhi remembered', summary: 'Polavaram project leaders paid tributes.', units: [{ code: 'G1-A6' }], subject: 'History & Culture' });
  assert.match(g[0].text, /Mahatma/);
  // a Telugu headline links through its English gloss
  const te = St.staticFor(NOTES, { title: 'పోలవరం నిధులకు కేబినెట్‌ ఆమోదం', summary: '', units: [], subject: 'Economy', lang: 'te' });
  assert.match(te[0].text, /Polavaram/);
});

test('offline run: every kept story carries a Prep note', async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'feed-'));
  const r = await run({ date: '2026-10-02', fixtures: FIX, out, dryRun: true, notes: NOTES });
  const missing = r.kept.filter((i) => !(i.notes || []).some((n) => n.src === 'Prep notes'));
  assert.deepEqual(missing.map((i) => i.title), []);
});

test('stories published before notes existed are given notes on the next run', async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'feed-'));
  await run({ date: '2026-10-02', fixtures: FIX, out, dryRun: false, notes: { size: 0 } });
  const before = JSON.parse(fs.readFileSync(path.join(out, 'days', '2026-10-02.json'), 'utf8'));
  assert.ok(before.items.every((i) => !i.notes));
  await run({ date: '2026-10-02', fixtures: FIX, out, dryRun: false, notes: NOTES });
  const after = JSON.parse(fs.readFileSync(path.join(out, 'days', '2026-10-02.json'), 'utf8'));
  assert.ok(after.items.every((i) => (i.notes || []).length > 0));
});
