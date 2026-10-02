package com.appsc.ca

import com.appsc.ca.data.Day
import com.appsc.ca.data.Exam
import com.appsc.ca.data.FeedIndex
import com.appsc.ca.data.FeedJson
import com.appsc.ca.data.Filter
import com.appsc.ca.data.Lane
import com.appsc.ca.data.groupByUnit
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
    fun unitsInTrackerOrder() {
        assertTrue(unitOrder("G1-C4") < unitOrder("G1-C10"))
        assertTrue(unitOrder("G2-S2") < unitOrder("G2-M1A-U1"))
        assertTrue(unitOrder("G2-M1B-U10") < unitOrder("G2-M2A-U1"))
        val groups = groupByUnit(day.items)
        assertEquals(groups.sortedBy { it.exam }.map { it.exam }, groups.map { it.exam })
    }
}
