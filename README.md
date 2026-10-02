# APPSC Daily CA (Android)

Daily current affairs for APPSC **Group-I and Group-II**, with **Andhra Pradesh as a full lane of its own** —
fetched from the internet twice a day and sorted by the **combined syllabus** and the **APPSC blueprint**.
**No AI and no API keys**: everything is decided by fixed rules you can read in `pipeline/`.

```
GitHub Actions, 06:11 and 20:11 IST (.github/workflows/daily.yml)
  1. Fetch   PIB (Delhi, Vijayawada, Hyderabad) · AIR News (National, International, Business) ·
             The Hindu (National, AP, Vizag, Vijayawada, International, Economy, Sci-Tech, Environment,
             Editorial, Telangana) · TOI · BusinessLine · Hans India AP · Eenadu (Telugu, news sitemap)
  2. Clean   noise filter, last 2 days only, one copy per story, nothing already published
  3. Read    the full text of PIB releases (official, so quotable)
  4. Score   out of 100 — see below
  5. Keep    40+; Andhra Pradesh 35+ (Eenadu headlines 30+) if examinable;
             at least 15 AP stories a day when there are that many
  6. Write   feed/days/<date>.json + feed/index.json (committed to this repo)
        │
        ▼
The app downloads new days every 12 hours (and on pull-to-refresh), keeps 60 days offline,
and notifies "18 new exam-relevant updates · 6 Andhra Pradesh".
```

## Subjects

Each story gets `subject`: one of the 6 books of the Combined Notes (Group-app `book1` … `book6`), so current
affairs are read under the same subject they are studied under. Its syllabus units vote (G1-A / G2-S1 / G2-M1A →
History & Culture, G1-B / G2-S3 / G2-M1B → Polity, Society & IR, G1-C / G2-M2A → Economy, G1-D / G2-S2 → Geography,
G1-F / G2-M2B → Science, Tech & Environment), a unit named in the headline counting double; with no unit, keyword
hints decide; anything left (appointments, awards, sports, days) is book 6, Current Affairs.

## The score (pipeline/lib/score.js)

Same weights as the CA portal's `relevance.js` ([groups-current-affairsapp](https://github.com/richie505/groups-current-affairsapp)):

| Factor | Points | What earns it |
|---|---|---|
| Syllabus | 30 | Units of the **combined tracker** (G1 Prelims A–F + G2 Screening and Mains) the story is about, plus the portal's 43 master topics (Polavaram, Amaravati, …) |
| Blueprint angles | 20 | APPSC question angles from `blueprint-keywords.md` — *Appointed, GI tag, Index, Repo, Tiger reserve, …* (Telugu equivalents for Eenadu in `ap-vocab.json`) |
| Andhra Pradesh | 20 | AP districts, places, institutions, schemes and leaders (`pipeline/vocab/ap-vocab.json`), or an AP-desk feed |
| Importance | 15 | A findable official act: Government Order, Bill, Cabinet decision, judgment, appointment, report, scheme, MoU; PIB itself; carried by several papers |
| Both exams | 15 | Pays in Group-I **and** Group-II, or in more than one paper |

80+ CRITICAL · 60+ HIGH · 40+ MEDIUM. Crime, films, weather, festival crowds and match reports are vetoed first,
whatever they score. Each story keeps its breakdown, shown in the app under "Why it is here".

**Why AP has its own rules:** APPSC sets this exam. An AP story is listed in the Andhra Pradesh lane *and* in
National/International when it is also that (a Union Cabinet decision on Polavaram is both), is kept from 30
instead of 40, and the day file carries at least 15 AP stories when the feeds have them, so a busy national
news day cannot crowd the state out.

**Key facts** are the article's own sentences, each labelled with the angle it answers (Appointed, GI tag /
designation, Index / rank, Judgment, Scheme / launch, Figures, …). Nothing is rewritten.

## The app (app/)

- **Today** — the latest day; lanes *All · Andhra Pradesh · National · International*; *Group-I + II / Group-I /
  Group-II*; subject chips; the last 10 days one tap away; **Listen** reads the list aloud (Indian English voice).
- **Days** — every downloaded day with counts (AP, critical, unread).
- **Syllabus** — every unit of the combined tracker (G1-A1 … G1-F22, G2-S1 … G2-M2B-U5) with the stories filed under it.
- **Saved** — bookmarked stories, kept even after their day leaves the phone.
- **Subjects** — the 6 books of the Combined Notes (1 History & Culture · 2 Polity, Society & IR · 3 Economy ·
  4 Geography · 5 Science, Tech & Environment · 6 Current Affairs). Every story is filed under one book, worked out
  from its syllabus units (tracker sections A–F); appointments, awards, sports and days go to book 6. Tap a book
  for its stories day by day, with the AP / National / International lanes.
- Each story: score badge, AP / National / International, Group-I / Group-II, summary; tap for key facts, syllabus
  units, blueprint angles, the score breakdown, "Read full story" and Share.

Get the APK from **Releases** (built by `.github/workflows/android.yml` on every change to `app/`). It is signed
with `keystore/appsc-ca.jks`, so a new APK installs over the old one.

## Working on it

```
npm test                                   # pipeline tests, offline (pipeline/test/fixtures)
node pipeline/daily.js --dry-run           # live run, prints what it would keep, writes nothing
node pipeline/daily.js --date 2026-10-02   # live run for a day, writes feed/
./gradlew assembleRelease                  # the APK (needs the Android SDK)
```

`pipeline/vocab/*.json` is exported from the other repos — re-run after their syllabus or blueprint changes:

```
node pipeline/tools/export-vocab.js ../groups-current-affairsapp \
  ../groupsrocket/prompts/blueprint-keywords.md ../groupsrocket/data/syllabus.json
```

`pipeline/vocab/ap-vocab.json` is kept by hand: add new AP schemes, projects or bodies there, and Telugu words
under `telugu`.

To try a new source from GitHub's network: Actions → **Probe sources** → Run workflow with the URLs
(`--explain=<source id>` scores every item a configured source returns today).
A feed that fails is listed in the run's summary (Actions → Daily current affairs) and in each day file's
`sources`; a dead one goes to `DEAD` in `pipeline/sources.js` with the reason.

The app reads the feed from `https://raw.githubusercontent.com/richie505/groupsca/main/feed/` (`app/build.gradle.kts`),
so this repository must stay **public** (or the feed moved somewhere public).
