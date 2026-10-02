package com.appsc.ca.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        items.filter { lane.matches(it) && exam.matches(it) && (subject == null || subject in it.subjects) }
            .sortedWith(compareByDescending<Item> { it.score }.thenBy { it.title })
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
    for (i in items.distinctBy { it.id }) for (u in i.units) {
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

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

fun longDate(iso: String): String = runCatching { LocalDate.parse(iso).format(longDate) }.getOrDefault(iso)
fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso).format(shortDate) }.getOrDefault(iso)
