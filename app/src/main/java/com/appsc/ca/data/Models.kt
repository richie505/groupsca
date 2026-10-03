package com.appsc.ca.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The files written by pipeline/daily.js: feed/index.json and feed/days/<date>.json.
// Every field has a default, so an older app keeps reading a newer feed.

@Serializable
data class FeedIndex(
    val version: Int = 1,
    val updated: String = "",
    /** A news day runs from this IST time to just before it the next morning. */
    val newsDayStarts: String = "06:00",
    /** IST times of the collection runs. */
    val schedule: List<String> = listOf("06:30", "13:00", "18:30", "23:30"),
    val days: List<DaySummary> = emptyList(),
)

@Serializable
data class DaySummary(
    val date: String,
    /** The next news day has begun and the morning run has collected the papers: no more additions. */
    val final: Boolean = false,
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
    /** When it was published (IST); `date` is the news day it is filed under. */
    val pubDate: String = "",
    /** IST "HH:MM", when the source gives a time. */
    val time: String = "",
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
    /** Which of the 6 books of the Combined Notes the story is filed under. */
    val subject: String = "",
    /** Every book it touches, main one first. */
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
    /** "te" for Telugu sources (Eenadu). */
    val lang: String = "en",
    /** A current-affairs site's daily digest post (AffairsCloud). */
    val digest: Boolean = false,
    /** Static notes linked to the story: the Prep app's books and the Rocket Sheets' key facts. */
    val notes: List<StaticNote> = emptyList(),
    /** The article's static part: the notes by topic, after the current matter. */
    val brief: Brief = Brief(),
) {
    /** The book to file it under, for feeds written before `subject` existed too. */
    val book: String get() = subject.ifBlank { subjects.firstOrNull { it in BOOKS } ?: BOOKS.last() }
}

/** Static notes for a story, one numbered section per topic of the story the notes cover. */
@Serializable
data class Brief(
    val v: Int = 0,
    val sections: List<StaticSection> = emptyList(),
    /** Names in the story the notes do not cover. */
    val gaps: List<String> = emptyList(),
)

@Serializable
data class StaticSection(
    /** The topic, as the notes head it: "Biogas And Biomass", "Horticulture" */
    val topic: String = "",
    /** "Prep notes" or "Rocket Sheets" */
    val src: String = "",
    val where: String = "",
    /** The subsection's bullets about the story, in notes order. */
    val bullets: List<String> = emptyList(),
)

@Serializable
data class StaticNote(
    /** exact (the note names the same thing) · unit (same syllabus unit) · book (same book) */
    val tier: String = "book",
    /** "Prep notes" or "Rocket Sheets" */
    val src: String = "",
    /** Where it is: "Prep notes · Book 5 … › 22 › Wetlands › Ramsar sites" */
    val where: String = "",
    val text: String = "",
    /** The names it was matched on, for an exact note. */
    val match: List<String> = emptyList(),
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
