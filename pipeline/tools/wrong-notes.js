#!/usr/bin/env node
'use strict';
// Reads "Wrong note" reports (GitHub issues the app opens, titled "Wrong note: ...")
// from stdin (the issues API's JSON) and writes feed/wrong-notes.json, which the
// daily run uses to stop linking that note to similar stories. A closed report
// whose label is "not-wrong" is ignored, so a mistaken report can be undone.
//
//   gh api 'repos/OWNER/groupsca/issues?state=all&per_page=100' | node pipeline/tools/wrong-notes.js
const fs = require('fs');
const path = require('path');
const { parseReports } = require('../lib/statics');
const { parseLinks } = require('../lib/topics');

const issues = JSON.parse(fs.readFileSync(0, 'utf8') || '[]');
const reports = parseReports(issues);
const out = path.join(__dirname, '..', '..', 'feed', 'wrong-notes.json');
fs.writeFileSync(out, JSON.stringify(reports, null, 1) + '\n');
console.log(`${reports.length} wrong-note reports`);
// "Topic link" reports: a story put in (or taken out of) a topic by the reader
const links = parseLinks(issues);
fs.writeFileSync(path.join(__dirname, '..', '..', 'feed', 'topic-links.json'), JSON.stringify(links, null, 1) + '\n');
console.log(`${Object.keys(links).length} topic-link reports`);
