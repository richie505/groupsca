'use strict';
// The exam filter: what an APPSC Group-I / Group-II student needs, not what a
// newspaper reader wants. A story stays only if it carries an exam fact
// (a scheme, a body, a report or index, a law or judgment, an appointment, an
// award, a project, a figure from an official source) and is not politics,
// crime, markets or a local event. Fixed rules, no AI.

// Party politics: who said what about whom.
const POLITICS =
  /\b(YSRCP|YSR ?Congress|TDP|Telugu Desam|Jana ?Sena|JSP|BJP|BRS|Congress(?! of)|CPI ?\(?M?\)?|CPI|DMK|AIADMK|AAP|TMC|Trinamool|Shiv Sena|NCP|RJD|JD\(U\)|SP|BSP|Left parties|opposition)\b|\b(slams?|flays?|attacks?|alleges?|allegations?|accuses?|blames?|criticis\w+|hits out|lashes out|takes a dig|jibe|vendetta|conspiracy|gimmick|kutra|U-turn|war of words|counter|rebuts?|demands? (?:resignation|apology)|resign|defection|joins (?:the )?(?:party|BJP|TDP|YSRCP|Congress))\b/i;
// Agitations and campaigns.
const AGITATION =
  /\b(protests?|protesters|protested|dharna|rally|rallies|bandh|agitation|boycott|walkout|hunger strike|sit-in|stir|gherao|campaign trail|road ?show|candidate|ticket|nomination|by-?election|bypoll|exit poll|manifesto|poll promise)\b/i;
// Crime, police and court cases about people.
const CRIME =
  /\b(arrest\w*|murder\w*|rape\w*|police|FIR|ED\b|CBI|money laundering|bail|custody|SIT\b|accused|absconding|raid\w*|seized|smuggl\w*|ganja|liquor scam|scam|fraud|cheat\w*|killed|dead|died|death toll|injured|accident|mishap|suicide|stabbed|assault\w*|robbery|theft|POCSO case|missing)\b/i;
// Markets and forecasts of them.
const MARKETS =
  /\b(Sensex|Nifty|stock markets?|stocks? (?:rise|fall|gain|slip|surge)|share price|shares of|IPO|bourses|brokerage|rupee (?:closes|opens|falls|rises|ends)|gold (?:price|rate)s?|silver price|crude (?:price|futures)|may hike|may cut|analysts?|economists? (?:poll|say)|poll of economists|target price|Q[1-4] (?:results|profit|earnings)|net profit|quarterly results)\b/i;
// Local and routine events, weather bulletins, lifestyle.
const ROUTINE =
  /\b(festival|dasara|dussehra|navaratri|brahmotsavam|utsavam|celebrations? begin|devotees|darshan|weather (?:update|forecast)|heavy rain(?:fall)? (?:alert|forecast)|rain alert|IMD (?:forecasts?|predicts|issues)|traffic|power cut|water supply cut|felicitat\w*|fetes?|condol\w*|pays tribute|birthday|wedding|film|movie|actor|actress|box office|OTT|cricket score|wins toss|horoscope|rashi)\b/i;
// Opinion, explainers about people, interviews.
const OPINION = /^(?:How|Why|What|When|Opinion|Editorial|Interview|Letters?)\b|\|\s*(?:Opinion|Interview)|\bsays\b.*\b(?:MLA|MP|MLC|leader|spokesperson)\b/i;

// Statements and praise: who said what, with nothing to learn.
const STATEMENT =
  /\b(says|said|told|urges|calls? for|calls? upon|hails|lauds|praises?|all praise|assures?|promises?|vows?|exhorts?|appeals?|highlights role|stresses|emphasises|asks (?:officials|people|youth))\b/i;
// Small-scale sport and personal court cases.
const SMALL =
  /\b(junior|sub-junior|district-level|state-level|inter-school|inter-college|zonal|mandal-level|local body polls schedule)\b|\bHigh Court\b.*\b(adultery|divorce|maintenance to wife|bail|custody|matrimonial|marriage dispute)\b/i;
// Words that make a story an exam story even with a negative word in it (a council's decision about police, an
// RBI forecast, a scheme announced at a rally).
const STRONG =
  /\b(scheme|yojana|mission|abhiyan|policy|act\b|bill\b|ordinance|amendment|article \d+|commission|council|authority|index|ranking|report|survey|census|GDP|GVA|growth forecast|inflation|RBI|repo|budget|GST|MoU|summit|treaty|agreement|launch\w*|project|award\w*|prize|Nobel|appointed|tiger reserve|Ramsar|GI tag|UNESCO|satellite|ISRO|DRDO|missile|vaccine|Supreme Court|constitution\w*)\b/i;
const TELUGU_POLITICS = /(వైకాపా|తెదేపా|జనసేన|భాజపా|కాంగ్రెస్|కుట్ర|విమర్శ|ఆరోపణ|ధ్వజం|మండిపాటు|నిరసన|ధర్నా|అరెస్టు|హత్య)/;

// Exam signals, from the title and summary.
const EXAM_WORDS =
  /\b(scheme|yojana|mission|abhiyan|programme|policy|act\b|bill\b|ordinance|amendment|article \d+|judgment|verdict|Supreme Court|High Court|constitution\w*|commission|committee|council|authority|board|index|ranking|ranks?|report|survey|census|data|GDP|GSDP|GVA|inflation|CPI inflation|WPI|repo|RBI|SEBI|budget|fiscal|tax|GST|exports?|imports?|MoU|agreement|treaty|summit|conference|launch\w*|inaugurat\w*|foundation stone|project|corridor|port|airport|railway|highway|dam|reservoir|irrigation|tiger reserve|sanctuary|national park|wetland|Ramsar|biodiversity|species|GI tag|heritage|UNESCO|award\w*|prize|medal|appointed|appointment|takes charge|elected|sworn|first|record|mission|satellite|ISRO|DRDO|missile|exercise|defence|vaccine|disease|health|education|scholarship|welfare|pension|reservation|quota|Backward Classes|Scheduled|tribal|women|SHG|farmers|MSP|horticulture|monsoon|drought|cyclone|earthquake|disaster|climate|emission|renewable|solar|wind|hydrogen|semiconductor|AI\b|digital|cyber|day\b|week\b|anniversary|jayanti)\b/i;

/** Why a story is not for the exam ("" when it is). */
function notExam(it) {
  const title = String(it.title || '');
  const text = `${title} ${it.summary || ''}`;
  const strong = STRONG.test(title);
  // nature features from abroad (Mongabay): not about India
  if (/mongabay/i.test(it.sourceId || '') && it.scope === 'international') return 'abroad';
  if (it.digest) return '';
  // official releases are kept unless they are routine (courtesy calls, festivals)
  if (it.official) return ROUTINE.test(title) && !EXAM_WORDS.test(title) ? 'routine' : '';
  if (it.lang === 'te') {
    // Telugu headlines: party politics and incidents out; the rest needs the score of an exam story
    if (TELUGU_POLITICS.test(title)) return 'politics';
    return (it.score || 0) >= 40 ? '' : 'no exam fact';
  }
  if (POLITICS.test(title)) return 'politics';
  if (AGITATION.test(title)) return 'agitation';
  if (CRIME.test(title) && !strong) return 'crime';
  if (MARKETS.test(title) && !strong) return 'markets';
  if (ROUTINE.test(title) && !strong) return 'routine';
  if (SMALL.test(title)) return 'small';
  if (OPINION.test(title) && !strong) return 'opinion';
  if (STATEMENT.test(title) && !strong) return 'statement';
  // a political story whose headline hides it: a party named in the summary and no exam word in the headline
  if (POLITICS.test(it.summary || '') && !EXAM_WORDS.test(title)) return 'politics';
  // nothing to learn: no exam word, no blueprint angle, no syllabus unit
  const angles = (it.angles || []).length;
  if (!EXAM_WORDS.test(text) && !angles) return 'no exam fact';
  if (it.why && it.why.syllabus === 0 && !angles) return 'no syllabus link';
  if ((it.score || 0) < 35 && !strong) return 'weak';
  if ((it.score || 0) < 25) return 'weak';
  return '';
}

const isExam = (it) => !notExam(it);

/** A GK capsule on a CA site ("Important Endangered Birds of India"): background, kept as a one-liner. */
const isGkPost = (it) => !!it.digest && /^(?:Important|Major|List of(?! Outcomes)|Top \d+|Famous|Types of|Know about)\b/i.test(String(it.title || ''));

module.exports = { notExam, isExam, isGkPost, POLITICS, AGITATION, CRIME, MARKETS, ROUTINE };
