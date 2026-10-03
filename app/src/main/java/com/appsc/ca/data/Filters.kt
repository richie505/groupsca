package com.appsc.ca.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The 6 books of the Combined Notes (the Group-app notes), in order. Every
 * story is filed under one; book 6 holds appointments, awards, sports, days
 * and persons in news.
 */
val BOOKS = listOf(
    "History & Culture",
    "Polity, Society & IR",
    "Economy",
    "Geography",
    "Science, Tech & Environment",
    "Current Affairs",
)

fun bookNumber(book: String): Int = BOOKS.indexOf(book) + 1

/** The lanes of the Group-II current-affairs paper, with Andhra Pradesh first: APPSC sets this exam. */
enum class Lane(val label: String) {
    ALL("All"),
    AP("Andhra Pradesh"),
    NATIONAL("National"),
    INTERNATIONAL("International");

    fun matches(i: Item): Boolean = when (this) {
        ALL -> true
        AP -> i.ap
        NATIONAL -> i.scope == "national" || i.scope == "dynamic"
        INTERNATIONAL -> i.scope == "international"
    }
}

enum class Exam(val label: String) {
    BOTH("Group-I + II"),
    G1("Group-I"),
    G2("Group-II");

    fun matches(i: Item): Boolean = when (this) {
        BOTH -> true
        G1 -> "G1" in i.exams
        G2 -> "G2" in i.exams
    }
}

data class Filter(
    val lane: Lane = Lane.ALL,
    val exam: Exam = Exam.BOTH,
    val subject: String? = null,
) {
    fun apply(items: List<Item>): List<Item> =
        oneCardPerTopic(items.filter { lane.matches(it) && exam.matches(it) && (subject == null || it.book == subject) })
            .sortedWith(compareByDescending<Item> { it.score }.thenBy { it.title })
}

/** Drops the reports shown on another card of the same list (the topic's lead). */
fun oneCardPerTopic(items: List<Item>): List<Item> {
    val ids = items.mapTo(HashSet()) { it.id }
    return items.filter { it.topicOf.isEmpty() || it.topicOf !in ids }
}

/** One syllabus unit with the stories filed under it. */
data class UnitGroup(val code: String, val exam: String, val label: String, val items: List<Item>)

/**
 * Every unit that has stories, in tracker order (G1-A1 … G1-F22, then G2-S1 … G2-M2B-U5),
 * each with its stories newest first.
 */
fun groupByUnit(items: List<Item>): List<UnitGroup> {
    val byCode = linkedMapOf<String, MutableList<Item>>()
    val meta = hashMapOf<String, SyllabusUnit>()
    for (i in oneCardPerTopic(items.distinctBy { it.id })) for (u in i.units) {
        byCode.getOrPut(u.code) { mutableListOf() }.add(i)
        meta.putIfAbsent(u.code, u)
    }
    return byCode.map { (code, list) ->
        val u = meta.getValue(code)
        UnitGroup(code, u.exam, u.label, list.sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.score }))
    }.sortedWith(compareBy<UnitGroup>({ it.exam }, { unitOrder(it.code) }))
}

/** G1-C4 < G1-C5 < G1-F18; G2-S1 < G2-M1A-U1 < G2-M1B-U10 < G2-M2A-U1. */
fun unitOrder(code: String): String {
    val section = when {
        code.startsWith("G2-S") -> "0"
        code.startsWith("G2-M1A") -> "1"
        code.startsWith("G2-M1B") -> "2"
        code.startsWith("G2-M2A") -> "3"
        code.startsWith("G2-M2B") -> "4"
        else -> ""
    }
    return section + code.replace(Regex("\\d+")) { it.value.padStart(3, '0') }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val IST = java.time.ZoneId.of("Asia/Kolkata")

/** "6:00 AM 2 Oct → 5:59 AM 3 Oct": the span a news day covers. */
fun newsDaySpan(iso: String, starts: String = "06:00"): String = runCatching {
    val d = LocalDate.parse(iso)
    val start = java.time.LocalTime.parse(starts)
    val end = start.minusMinutes(1)
    "${start.format(clock)} ${d.format(dayMonth)} → ${end.format(clock)} ${d.plusDays(1).format(dayMonth)}"
}.getOrDefault(iso)

/** "9:41 PM" from an IST "21:41". */
fun clockTime(hhmm: String): String =
    runCatching { java.time.LocalTime.parse(hhmm).format(clock) }.getOrDefault(hhmm)

/** IST clock time of a UTC ISO instant ("2026-10-02T13:27:00Z" → "6:57 PM"). */
fun istClock(isoUtc: String): String =
    runCatching { java.time.Instant.parse(isoUtc).atZone(IST).format(clock) }.getOrDefault("")

/** The next scheduled update after now, as "11:30 PM" (IST). */
fun nextUpdate(schedule: List<String>, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now(IST)): String {
    val times = schedule.mapNotNull { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }.sorted()
    if (times.isEmpty()) return ""
    val t = now.toLocalTime()
    val next = times.firstOrNull { it.isAfter(t) } ?: times.first()
    return next.format(clock) + if (next.isAfter(t)) "" else " tomorrow"
}

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

fun longDate(iso: String): String = runCatching { LocalDate.parse(iso).format(longDate) }.getOrDefault(iso)
fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso).format(shortDate) }.getOrDefault(iso)
