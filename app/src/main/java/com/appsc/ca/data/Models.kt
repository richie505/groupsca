package com.appsc.ca.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The files written by pipeline/daily.js: feed/index.json and feed/days/<date>.json.
// Every field has a default, so an older app keeps reading a newer feed.

@Serializable
data class FeedIndex(
    val version: Int = 1,
    val updated: String = "",
    val days: List<DaySummary> = emptyList(),
)

@Serializable
data class DaySummary(
    val date: String,
    val updated: String = "",
    val count: Int = 0,
    val ap: Int = 0,
    val critical: Int = 0,
    val high: Int = 0,
)

@Serializable
data class Day(
    val version: Int = 1,
    val date: String,
    val updated: String = "",
    val items: List<Item> = emptyList(),
)

@Serializable
data class Item(
    val id: String,
    val date: String,
    val title: String,
    val summary: String = "",
    val facts: List<Fact> = emptyList(),
    val source: String = "",
    val sourceId: String = "",
    val official: Boolean = false,
    val ministry: String = "",
    val alsoIn: List<String> = emptyList(),
    val url: String = "",
    /** ap · national · international · dynamic */
    val bucket: String = "national",
    /** The bucket leaving Andhra Pradesh aside, so an AP story about a Union decision is also National. */
    val scope: String = "national",
    val ap: Boolean = false,
    /** "G1", "G2" */
    val exams: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    /** Combined syllabus tracker units: G1-A1 … G1-F22, G2-S1 … G2-M2B-U5. */
    val units: List<SyllabusUnit> = emptyList(),
    val topics: List<String> = emptyList(),
    /** Blueprint keyword angles the story matches. */
    val angles: List<String> = emptyList(),
    val score: Int = 0,
    /** critical · high · medium */
    val band: String = "medium",
    val why: Why = Why(),
)

@Serializable
data class Fact(val angle: String, val text: String)

@Serializable
data class SyllabusUnit(val code: String, val exam: String = "", val label: String = "")

@Serializable
data class Why(
    val syllabus: Int = 0,
    val angles: Int = 0,
    val ap: Int = 0,
    val importance: Int = 0,
    val reuse: Int = 0,
)

val FeedJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
