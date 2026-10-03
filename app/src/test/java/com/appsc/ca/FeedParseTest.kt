package com.appsc.ca

import com.appsc.ca.data.BOOKS
import com.appsc.ca.data.Day
import com.appsc.ca.data.Exam
import com.appsc.ca.data.FeedIndex
import com.appsc.ca.data.FeedJson
import com.appsc.ca.data.Filter
import com.appsc.ca.data.Lane
import com.appsc.ca.data.groupByUnit
import com.appsc.ca.data.newsDaySpan
import com.appsc.ca.data.nextUpdate
import com.appsc.ca.data.unitOrder
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app reads exactly what pipeline/daily.js writes (test/resources is a copy of a real run). */
class FeedParseTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val day = FeedJson.decodeFromString<Day>(resource("day.json"))

    @Test
    fun readsIndexAndDay() {
        val index = FeedJson.decodeFromString<FeedIndex>(resource("index.json"))
        assertTrue(index.days.isNotEmpty())
        assertEquals(index.days.first().date, day.date)
        assertTrue(day.items.isNotEmpty())
        assertTrue(day.items.all { it.units.all { u -> u.code.startsWith("G1-") || u.code.startsWith("G2-") } })
    }

    @Test
    fun andhraPradeshLaneAndNationalLaneOverlapForUnionDecisionsOnAp() {
        val ap = Filter(Lane.AP).apply(day.items)
        val national = Filter(Lane.NATIONAL).apply(day.items)
        assertTrue(ap.isNotEmpty())
        assertTrue(ap.all { it.ap })
        assertTrue(national.any { it.ap })
    }

    @Test
    fun examFilterAndSorting() {
        val g2 = Filter(exam = Exam.G2).apply(day.items)
        assertTrue(g2.all { "G2" in it.exams })
        val all = Filter().apply(day.items)
        assertEquals(all.sortedByDescending { it.score }.map { it.score }, all.map { it.score })
    }

    @Test
    fun everyStoryIsInOneOfTheSixBooks() {
        assertEquals(6, BOOKS.size)
        assertTrue(day.items.all { it.book in BOOKS })
        val economy = Filter(subject = "Economy").apply(day.items)
        assertTrue(economy.isNotEmpty())
        assertTrue(economy.all { it.book == "Economy" })
    }

    @Test
    fun newsDayIsSixAmToSixAm() {
        assertEquals("6:00 AM 2 Oct → 5:59 AM 3 Oct", newsDaySpan("2026-10-02"))
        val ist = java.time.ZoneId.of("Asia/Kolkata")
        val at = { h: Int, m: Int -> java.time.ZonedDateTime.of(2026, 10, 2, h, m, 0, 0, ist) }
        val schedule = listOf("06:30", "13:00", "18:30", "23:30")
        assertEquals("6:30 PM", nextUpdate(schedule, at(14, 0)))
        assertEquals("6:30 AM tomorrow", nextUpdate(schedule, at(23, 45)))
        val index = FeedJson.decodeFromString<FeedIndex>(resource("index.json"))
        assertEquals("06:00", index.newsDayStarts)
        assertEquals(4, index.schedule.size)
    }

    @Test
    fun everyStoryHasAStaticNoteAndReadsAloudWithIt() {
        assertTrue(day.items.all { i -> i.notes.any { it.src == "Prep notes" } })
        val english = day.items.first { com.appsc.ca.ui.Listening.canRead(it) && it.notes.isNotEmpty() }
        val page = com.appsc.ca.ui.Listening.pageOf(english)
        assertEquals(english.id, page.id)
        assertTrue(page.parts.first().isNotBlank())
        assertTrue(page.parts.last().startsWith("From your notes: "))
        assertTrue(day.items.filter { it.lang == "te" }.none { com.appsc.ca.ui.Listening.canRead(it) })
    }

    @Test
    fun speechSaysShortFormsInFull() {
        val said = com.appsc.ca.data.SpeechText.speakable("RBI cut the repo rate to 5.5% (TH, 2 Oct 2026) [GK]")
        assertTrue(said, !said.contains("[GK]") && !said.contains("TH,"))
        assertTrue(said, said.contains("per cent"))
    }

    @Test
    fun unitsInTrackerOrder() {
        assertTrue(unitOrder("G1-C4") < unitOrder("G1-C10"))
        assertTrue(unitOrder("G2-S2") < unitOrder("G2-M1A-U1"))
        assertTrue(unitOrder("G2-M1B-U10") < unitOrder("G2-M2A-U1"))
        val groups = groupByUnit(day.items)
        assertEquals(groups.sortedBy { it.exam }.map { it.exam }, groups.map { it.exam })
    }
}
