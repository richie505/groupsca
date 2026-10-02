'use strict';

// The fixed rules that decide what is examinable. No model, no API key.
//
// Copied from the APPSC Current Affairs Portal (groups-current-affairsapp):
// VETO, SPORT_*, INSTRUMENT and KEYWORD_STOPLIST from
// content-pipeline/np-daily/gate-rules.js; the bucket and subject patterns from
// server/src/lib/relevance.js. The comments explaining each rule are kept,
// because every one of them records a real article that went wrong.

// 1. veto
// ---------------------------------------------------------------------------

// A newspaper is mostly not examinable, and these are the categories that make
// up most of the bulk. A veto here is absolute: no accumulation of keyword hits
// rescues a robbery report, because the keywords it matched are incidental.
const VETO = [
  {
    label: 'local crime',
    // `booked` takes any preposition: the first version of this rule said
    // "booked under" and let "MP's son booked after mall employee knocked down
    // by car" straight through. Likewise `body ... retrieved` needs to span the
    // words between them ("Body of engineer's daughter retrieved").
    re: /\b(?:robbed|robbery|murder(?:ed)?|stabbed|arrest(?:ed|s)?|booked\b|remand(?:ed)?|absconding|kidnap(?:ped)?|molest|rape[ds]?|smuggl|seiz(?:ed|ure) of (?:ganja|liquor)|drowned|electrocuted|knocked down|road accident|died after|suicide|hooch)\b/i,
  },
  {
    label: 'death or recovery of a body',
    re: /\bbod(?:y|ies)\b[^.]{0,40}\b(?:retrieved|recovered|found|fished out)\b/i,
  },
  {
    label: 'weather report',
    re: /\b(?:morning showers|humid weather|light to moderate rain|maximum temperature|minimum temperature|heat wave conditions|brings? relief from)\b/i,
  },
  {
    label: 'personal engagement or commemoration',
    re: /\b(?:to attend (?:the )?wedding|wedding of|remembered on|birth anniversary|death anniversary|jayanti (?:was |celebrat)|paid (?:floral )?tributes)\b/i,
  },
  {
    label: 'civic complaint',
    re: /\b(?:pothole|garbage|open drain|sewage overflow|stray dog|power cut|water supply disrupt|encroachment|traffic jam|street light)\b/i,
  },
  {
    label: 'film, TV or celebrity',
    re: /\b(?:film|movie|cinema|actor|actress|heroine|director's next|box office|teaser|trailer|web series|serial|OTT|audio launch|pre-release)\b/i,
  },
  {
    label: 'ceremonial or promotional event',
    re: /\b(?:felicitat|condolence|obituary|passed away|inaugurat(?:ed|ion) of the (?:building|office|showroom)|awareness (?:programme|rally|walk)|blood donation camp|freshers|alumni meet|career guidance|seminar (?:on|was)|valedictory|fashion show|food festival)\b/i,
  },
  {
    label: 'festival logistics',
    re: /\b(?:darshan|queue (?:line|complex)|laddu|annaprasadam|crowd management|devotees (?:were|are) allowed|special buses (?:were|will be) run|toll|kalyanotsavam)\b/i,
  },
  {
    label: 'listings, weather or filler',
    re: /\b(?:horoscope|rashi|today's programmes|classifieds|tenders? invited|weather (?:forecast|update)|maximum temperature|letters to the editor)\b/i,
  },
];

// Sport is vetoed only when it is a match report. A governance, doping, policy or
// major-tournament story is genuinely examinable - the blueprint carries CWG and
// Olympics as keyword angles - so this is a narrower rule than the others.
const SPORT_MATCH_REPORT =
  /\b(?:beat|defeated|thrashed|drew with|won by \d+|innings|wicket|not out|runs off|goal(?:s)? in the|full-time|kick-off|set point|semifinal berth|lost to)\b/i;
const SPORT_EXEMPT =
  /\b(?:Olympic|Commonwealth Games|CWG|Asian Games|World Cup|doping|NADA|WADA|suspended for testing|sports policy|Khelo India|stadium (?:project|funding)|federation (?:election|dispute)|Sports Authority)\b/i;

// ---------------------------------------------------------------------------
// 2-3. positive signals
// ---------------------------------------------------------------------------

// Words that name a findable official act. This is the difference between
// "State to spend Rs 2,400 crore on irrigation, says Minister" - whose
// underlying order can be located and cited - and "Irrigation projects
// reviewed at meeting", which cannot.
const INSTRUMENT = [
  { w: 3, re: /\b(?:Government Order|G\.?O\.?\s?(?:Ms|Rt)?\.?\s?No|ordinance|gazette notification)\b/i, label: 'order or notification' },
  { w: 3, re: /\b(?:Bill|Act|amendment|Rules? (?:were |was )?notified|Section \d+|Article \d+)\b/, label: 'legislation' },
  { w: 3, re: /\b(?:Cabinet|CCEA|Council of Ministers)\b.{0,40}\b(?:approv|clear|decid|nod)/i, label: 'cabinet decision' },
  { w: 3, re: /\b(?:Supreme Court|High Court)\b.{0,60}\b(?:held|ruled|struck down|directed|stayed|upheld|judgment|verdict)/i, label: 'judgment' },
  { w: 3, re: /\b(?:appointed|sworn in|takes? charge as|designated as|nominated as)\b/i, label: 'appointment' },
  { w: 2, re: /\b(?:committee|commission|task force|panel)\b.{0,40}\b(?:constituted|set up|formed|headed by|chaired by|recommend)/i, label: 'committee' },
  { w: 2, re: /\b(?:report|survey|index|ranking|census|estimates?)\b.{0,30}\b(?:released|published|tabled|submitted)/i, label: 'report or index' },
  { w: 2, re: /\b(?:scheme|mission|yojana|programme)\b.{0,40}\b(?:launched|approved|extended|outlay|allocat)/i, label: 'scheme' },
  { w: 2, re: /\b(?:MoU|memorandum of understanding|agreement|treaty|summit|bilateral)\b/i, label: 'agreement or summit' },
  { w: 2, re: /\b(?:GI tag|geographical indication|Ramsar|biosphere reserve|tiger reserve|UNESCO|World Heritage)\b/i, label: 'designation' },
  { w: 2, re: /\b(?:budget|allocation|grant|deficit|tariff|GST|repo rate|MSP|subsidy)\b/i, label: 'fiscal instrument' },
  { w: 1, re: /(?:Rs\.?|₹)\s?[\d,.]+\s*(?:crore|lakh)\b/i, label: 'a figure' },
  { w: 1, re: /\b\d+(?:\.\d+)?\s*(?:per cent|percent|%)\b/i, label: 'a percentage' },
];

// Over-generic blueprint entries. Each is a real APPSC question angle, but as a
// text match it fires on nearly every article and so carries no information -
// "first" and "last" appear in any prose. Dropping them keeps the keyword signal
// discriminating rather than universal.
const KEYWORD_STOPLIST = new Set([
  'last', 'first', 'new', 'best', 'top', 'largest', 'highest', 'lowest',
  'longest', 'oldest', 'total', 'number', 'place', 'location', 'name',
  'year', 'day', 'state', 'city', 'district', 'area', 'people', 'group',
  // Institutions and offices that appear in nearly every Indian news article,
  // whatever it is about. Measured, not guessed: on the first rule-gated run
  // "Minister" was the matched angle for a weather report, a wedding and a
  // strike alike, so it separates nothing. They remain perfectly good APPSC
  // question angles - they are simply useless as a *filter* on a newspaper.
  'minister', 'chief minister', 'prime minister', 'president', 'india',
  'ministry', 'government', 'party', 'world', 'days', 'website', 'platform',
  'programme', 'policy', 'capital', 'council', 'defence', 'cases', 'report',
  'committee', 'commission', 'chairman', 'chairperson', 'commissioner',
  'secretary', 'officer', 'department', 'scheme', 'project', 'meeting',
]);

// ---------------------------------------------------------------------------
// the four Group-II buckets
// ---------------------------------------------------------------------------

// Requires India-as-party framing, not merely a country name. The first version
// listed bare country names and filed "Collectors empowered to grant citizenship
// under CAA" as international, because a CAA story naturally mentions Pakistan
// and Bangladesh. A country appearing in a story is not the story being about
// that country.
const INTERNATIONAL =
  /\b(?:United Nations|UNESCO|UNICEF|WTO|G20|G7|BRICS|ASEAN|QUAD|COP\d+|bilateral|multilateral|summit|treaty|foreign minister|external affairs|ambassador|diplomatic|envoy)\b|\bIndia\s*(?:[-–—]|and)\s*[A-Z][a-z]+\b/;
const NATIONAL = /\b(?:Union Cabinet|Parliament|Lok Sabha|Rajya Sabha|Supreme Court|Centre|Government of India|Union Minister|Ministry of|RBI|NITI Aayog|Election Commission|CAG|President of India)\b/i;

// 'dynamic' is the fast-changing edge of another subject — a fresh GI tag, a new
// index rank, a rate decision. It is current affairs by recency, but its home is
// Economy or Environment rather than Current Affairs, which is exactly the
// distinction the Group-II bucket scheme draws.
const DYNAMIC = /\b(?:GI tag|index|ranking|rank\b|repo rate|inflation|GDP|growth rate|survey|census|report released|data released|tiger reserve|Ramsar|biosphere|launch(?:ed)? (?:of )?(?:a )?satellite|mission)\b/i;

const SUBJECT_HINTS = [
  ['Polity', /\b(?:Constitution|Article \d+|Amendment|Parliament|Assembly|judiciary|Supreme Court|High Court|Governor|federalism|Panchayat|municipal|writ|Bill|Act\b)/i],
  ['Economy', /\b(?:GDP|inflation|repo|fiscal|deficit|budget|tax|GST|MSME|industry|investment|export|import|bank|subsidy|MSP|crore|lakh crore)/i],
  ['Geography', /\b(?:river|monsoon|rainfall|drought|cyclone|soil|mineral|coast|forest cover|landform|irrigation|canal|dam)/i],
  ['Environment', /\b(?:pollution|emission|climate|biodiversity|wildlife|conservation|Ramsar|tiger|ecosystem|CPCB|environmental clearance)/i],
  ['Science & Technology', /\b(?:ISRO|satellite|space|vaccine|AI|artificial intelligence|semiconductor|nuclear|research|DRDO|biotechnology|quantum)/i],
  ['Society', /\b(?:caste|tribal|women|SC\/ST|literacy|education|health|poverty|migration|urbanisation|welfare|reservation)/i],
  ['AP History', /\b(?:Satavahana|Kakatiya|Vijayanagara|Ikshvaku|Qutb Shahi|Reddi|Telugu literature|inscription|dynasty)/i],
  ['Indian History', /\b(?:freedom struggle|Gandhi|Nehru|colonial|British rule|revolt|independence movement|Mughal|Maurya)/i],
];

// Another country's internal affairs with no Indian thread: the topic-derived
// part of the syllabus score is withdrawn (relevance.js, FOREIGN_NAME).
const FOREIGN_NAME =
  /\b(?:Pakistan|Pakistani|Bangladesh|Sri Lanka|Nepal|Myanmar|Afghanistan|Iran|Iraq|Israel|Palestin\w*|Ukraine|Russia|Russian|China|Chinese|Beijing|Taiwan|Japan|Japanese|Korea|Vietnam|Indonesia|Malaysia|Thailand|Turkey|Egypt|Nigeria|Kenya|Brazil|Argentina|Mexico|Venezuela|Cuba|Canada|Australia|Britain|British|United Kingdom|France|French|Germany|German|Italy|Spain|Netherlands|Sweden|Norway|Poland|Greece|Washington|Moscow|Islamabad|Dhaka|Kathmandu|Colombo|Kabul|Tehran|Gaza|Imran Khan|Trump|Putin|Xi Jinping|Netanyahu|Zelensky\w*)\b/i;
const INDIA_TERM =
  /\b(?:India|Indian|Bharat|Andhra|Telangana|Amaravati|Vijayawada|Visakhapatnam|Tirupati|Delhi|Mumbai|Chennai|Kolkata|Bengaluru|Kerala|Karnataka|Tamil Nadu|Maharashtra|Gujarat|Rajasthan|Bihar|Odisha|Jharkhand|Assam|Punjab|Haryana|Centre|Union Government|Parliament|Lok Sabha|Rajya Sabha|RBI|NITI Aayog|Supreme Court of India)\b/i;

const FOREIGN_MARGIN = 1.5;

// Extra stoplist entries for this app's keyword matching: angles that are also
// everyday words in any news story, so they say nothing about the story.
const EXTRA_STOP = ['first', 'launched', 'developed', 'established', 'introduced', 'visited', 'elected',
  'declared', 'celebrated', 'built', 'published', 'objective', 'objectives', 'aim', 'terms', 'location',
  'smallest', 'biggest', 'earliest', 'person', 'persons in news', 'places in news', 'leader', 'law', 'god',
  'court', 'text', 'book', 'author', 'women', 'school', 'college', 'institute', 'institution', 'organisation',
  'organisations', 'initiative', 'campaign', 'energy', 'rail', 'bank', 'fund', 'tax', 'exchange', 'revenue',
  'expenditure', 'education', 'crime', 'society', 'system', 'device', 'research', 'project', 'mission',
  'events', 'event', 'era', 'war', 'battle', 'son', 'daughter', 'queen', 'title', 'poet', 'song', 'music',
  'dance', 'art', 'temple', 'religion', 'stone', 'site', 'tool', 'river', 'sea', 'rocks', 'crops', 'tribe',
  'migration', 'population', 'poverty', 'unemployment', 'inflation', 'budget', 'subsidies', 'productivity',
  'infrastructure', 'platform', 'app', 'portal', 'deal', 'operation', 'flood', 'party', 'session',
  'month', 'date', 'bird', 'animal', 'plant', 'metal', 'line', 'scholars', 'saints', 'leader', 'theory',
  // Measured on the first live run (2 Oct 2026): each matched dozens of stories
  // it said nothing about.
  'election', 'elections', 'elected', 'lists', 'officials', 'philosophy', 'movement', 'period',
  'contribution', 'parts', 'town', 'port', 'ports', 'occupation', 'aim', 'association', 'congress',
  'conference', 'visited', 'initiative', 'celebrated', 'inaugurated', 'festival', 'anniversary',
  'days', 'chief guest', 'awards', 'prizes', 'women representation', 'age', 'salary', 'judge'];
for (const w of EXTRA_STOP) KEYWORD_STOPLIST.add(w);

module.exports = {
  VETO, SPORT_MATCH_REPORT, SPORT_EXEMPT, INSTRUMENT, KEYWORD_STOPLIST,
  INTERNATIONAL, NATIONAL, DYNAMIC, SUBJECT_HINTS, FOREIGN_NAME, INDIA_TERM, FOREIGN_MARGIN,
};
