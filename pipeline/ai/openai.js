'use strict';
// The OpenAI Responses API, called from the server side only (GitHub Actions; the key is the
// OPENAI_API_KEY secret and never reaches the app). No SDK: plain fetch, so the pipeline keeps
// no npm dependencies. The model is a setting (AI_MODEL), so it can change without code.

const API = 'https://api.openai.com/v1/responses';

function config() {
  return {
    key: process.env.OPENAI_API_KEY || '',
    model: process.env.AI_MODEL || 'gpt-6-luna',
    tool: process.env.AI_SEARCH_TOOL || 'web_search',
    timeoutMs: Number(process.env.AI_TIMEOUT_MS || 600000),
  };
}

/** The text of a Responses API result and its URL citations ([{ url, title }]). */
function readResponse(json) {
  let text = typeof json.output_text === 'string' ? json.output_text : '';
  const citations = [];
  const searches = [];
  for (const out of json.output || []) {
    if (out.type === 'web_search_call') searches.push((out.action && out.action.query) || '');
    if (out.type !== 'message') continue;
    for (const c of out.content || []) {
      if (c.type === 'output_text') {
        if (!json.output_text) text += c.text || '';
        for (const a of c.annotations || []) {
          if (a.type === 'url_citation' && a.url) citations.push({ url: a.url, title: a.title || '' });
        }
      }
    }
  }
  return { text, citations, searches };
}

/** The JSON object in a model's text: the whole text, else the first {...} block (fenced or not). */
function parseJson(text) {
  const t = String(text || '').trim();
  try {
    return JSON.parse(t);
  } catch {
    // fall through
  }
  const fenced = t.match(/```(?:json)?\s*([\s\S]*?)```/);
  if (fenced) {
    try {
      return JSON.parse(fenced[1]);
    } catch {
      // fall through
    }
  }
  const a = t.indexOf('{');
  const b = t.lastIndexOf('}');
  if (a >= 0 && b > a) return JSON.parse(t.slice(a, b + 1));
  throw new Error('no JSON in the model output');
}

/**
 * One Responses API call: instructions + input, live web search when `search`, structured JSON
 * output by `schema`. If the API refuses the structured format together with the search tool,
 * it asks again for plain JSON and parses it. Returns { data, citations, searches, model }.
 */
async function respond({ instructions, input, schema, name, search = true }, fetchImpl = fetch) {
  const c = config();
  if (!c.key) throw new Error('OPENAI_API_KEY is not set (GitHub → Settings → Secrets and variables → Actions)');
  const body = (structured) => ({
    model: c.model,
    instructions,
    input,
    ...(search ? { tools: [{ type: c.tool, search_context_size: 'high' }] } : {}),
    ...(structured ? { text: { format: { type: 'json_schema', name, schema, strict: true } } } : {}),
  });
  const call = async (structured) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), c.timeoutMs);
    try {
      const res = await fetchImpl(API, {
        method: 'POST',
        headers: { Authorization: `Bearer ${c.key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(body(structured)),
        signal: controller.signal,
      });
      const json = await res.json().catch(() => ({}));
      if (!res.ok) {
        const msg = (json.error && json.error.message) || `HTTP ${res.status}`;
        const err = new Error(`OpenAI: ${msg}`);
        err.status = res.status;
        throw err;
      }
      return json;
    } finally {
      clearTimeout(timer);
    }
  };
  let json;
  try {
    json = await call(true);
  } catch (e) {
    // a 400 about the format: ask for plain JSON instead
    if (e.status === 400 && /format|schema|json/i.test(e.message)) json = await call(false);
    else throw e;
  }
  const { text, citations, searches } = readResponse(json);
  return { data: parseJson(text), citations, searches, model: json.model || c.model };
}

module.exports = { respond, readResponse, parseJson, config };
