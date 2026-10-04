package com.appsc.ca

import com.appsc.ca.data.BOOKS
import com.appsc.ca.data.Day
import com.appsc.ca.data.Exam
import com.appsc.ca.data.Fact
import com.appsc.ca.data.FeedIndex
import com.appsc.ca.data.FeedJson
import com.appsc.ca.data.Filter
import com.appsc.ca.data.Lane
import com.appsc.ca.data.groupByUnit
import com.appsc.ca.data.newsDaySpan
import com.appsc.ca.data.oneCardPerTopic
import com.appsc.ca.data.Related
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
        val english = day.items.first { com.appsc.ca.ui.Listening.canRead(it) && it.notes.isNotEmpty() && it.brief.sections.isEmpty() }
        val page = com.appsc.ca.ui.Listening.pageOf(english)
        assertEquals(english.id, page.id)
        assertTrue(page.parts.first().isNotBlank())
        assertTrue(page.parts.last().startsWith("From your notes: "))
        assertTrue(day.items.filter { it.lang == "te" }.none { com.appsc.ca.ui.Listening.canRead(it) })
    }

    @Test
    fun aBriefIsReadAfterTheCurrentMatterTopicByTopic() {
        val item = day.items.first { it.brief.sections.isNotEmpty() }
        assertEquals("Biogas And Biomass", item.brief.sections[0].topic)
        assertEquals(listOf("School of Agriculture"), item.brief.gaps)
        val parts = com.appsc.ca.ui.Listening.pageOf(item).parts
        val at = parts.indexOf("Static notes.")
        assertTrue(at > 0)
        assertTrue(parts[at + 1].startsWith("1. "))
        assertTrue(parts.none { it.startsWith("From your notes: ") })
    }

    @Test
    fun oneCardPerTopicAndNoRepeatedFacts() {
        val lead = day.items[0].copy(id = "lead", related = listOf(Related("other", "Same story", "Mint", "")))
        val other = day.items[1].copy(id = "other", topicOf = "lead")
        assertEquals(listOf("lead"), oneCardPerTopic(listOf(lead, other)).map { it.id })
        // without its lead in the list, the report stays
        assertEquals(listOf("other"), oneCardPerTopic(listOf(other)).map { it.id })
        val repeat = lead.copy(summary = "The RBI kept the repo rate at 5.5%. It meets again in December.", facts = listOf(Fact("Figures", "The RBI kept the repo rate at 5.5%."), Fact("Index", "A new fact.")))
        assertEquals(listOf("A new fact."), repeat.newFacts.map { it.text })
    }

    @Test
    fun readsTheStudyLayer() {
        val d = FeedJson.decodeFromString<Day>(
            """{"date":"2026-10-02","items":[{"id":"a","date":"2026-10-02","title":"t","top":true},
               {"id":"b","date":"2026-10-02","title":"Panghal wins gold","oneLiner":true,"line":"Panghal won 80 kg gold."}],
              "quiz":[{"id":"a:1","item":"a","title":"t","q":"NABARD sets aside ____ for horticulture.",
                       "options":["₹3,985 crore","₹5,313 crore","₹7,970 crore","₹10,626 crore"],"answer":1,"kind":"figure"}]}""".trimIndent(),
        )
        assertTrue(d.items[0].top)
        assertEquals("Panghal won 80 kg gold.", d.items[1].line)
        assertEquals("₹5,313 crore", d.quiz[0].options[d.quiz[0].answer])
    }

    @Test
    fun weeksAndMonthsForTheDigest() {
        val weeks = com.appsc.ca.ui.periodsOf(listOf("2026-10-04", "2026-10-03", "2026-09-30", "2026-09-27"), "week")
        assertEquals(listOf("2026-09-28", "2026-09-21"), weeks.map { it.start.toString() })
        assertTrue(weeks[0].has("2026-10-04") && !weeks[0].has("2026-10-05"))
        val months = com.appsc.ca.ui.periodsOf(listOf("2026-10-04", "2026-09-30"), "month")
        assertEquals(listOf("2026-10-01", "2026-09-01"), months.map { it.start.toString() })
    }

    @Test
    fun speechDropsCitations() {
        val said = com.appsc.ca.data.SpeechText.speakable("RBI cut the repo rate to 5.5% (TH, 2 Oct 2026) [GK]")
        assertTrue(said, !said.contains("[GK]") && !said.contains("TH,"))
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
