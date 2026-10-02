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

test('IST day', () => {
  assert.equal(todayIst(Date.parse('2026-10-01T19:00:00Z')), '2026-10-02');
});

test('offline run: AP items are kept, merged per day, and not repeated on the next run', async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'feed-'));
  const args = { date: '2026-10-02', fixtures: FIX, out, dryRun: false };
  const first = await run(args);
  assert.ok(first.report.kept >= 5, JSON.stringify(first.report));
  assert.ok(first.report.keptAp >= 3);
  const day = JSON.parse(fs.readFileSync(path.join(out, 'days', '2026-10-02.json'), 'utf8'));
  assert.ok(day.items.every((i) => i.score >= (i.ap ? 30 : 40) || i.apFloor));
  assert.ok(!day.items.some((i) => /arrested|film/i.test(i.title)));
  const index = JSON.parse(fs.readFileSync(path.join(out, 'index.json'), 'utf8'));
  assert.equal(index.days[0].date, '2026-10-02');

  const second = await run(args);
  assert.equal(second.report.fresh, 0);
  const again = JSON.parse(fs.readFileSync(path.join(out, 'days', '2026-10-02.json'), 'utf8'));
  assert.equal(again.items.length, day.items.length);
});
