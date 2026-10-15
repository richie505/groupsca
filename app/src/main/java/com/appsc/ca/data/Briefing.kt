package com.appsc.ca.data

import kotlinx.serialization.Serializable

/*
 * The APPSC daily briefing (feed/briefings/), written each evening by the AI research pipeline on the
 * server (pipeline/ai): International, National and Andhra Pradesh items with Prelims facts, Mains angle,
 * AP link, status and clickable sources; a subject coverage audit; MCQs and Mains questions.
 */

@Serializable
data class BriefingIndex(val updated: String = "", val briefings: List<BriefingSummary> = emptyList())

@Serializable
data class BriefingSummary(
    val date: String,
    /** published · needs_review */
    val status: String = "",
    val generatedAt: String = "",
    val international: Int = 0,
    val national: Int = 0,
    val andhraPradesh: Int = 0,
    val mcqs: Int = 0,
    val sources: Int = 0,
)

@Serializable
data class Briefing(
    val date: String,
    val title: String = "",
    val status: String = "",
    val model: String = "",
    val generatedAt: String = "",
    val sections: BriefingSections = BriefingSections(),
    val coverageAudit: List<CoverageRow> = emptyList(),
    val mcqs: List<Mcq> = emptyList(),
    val mainsQuestions: List<MainsQuestion> = emptyList(),
    val sourceCount: Int = 0,
) {
    val allItems: List<BriefItem> get() = sections.international + sections.national + sections.andhraPradesh
}

@Serializable
data class BriefingSections(
    val international: List<BriefItem> = emptyList(),
    val national: List<BriefItem> = emptyList(),
    val andhraPradesh: List<BriefItem> = emptyList(),
)

@Serializable
data class BriefItem(
    val canonicalTopic: String = "",
    val isUpdate: Boolean = false,
    /** INTERNATIONAL · NATIONAL · ANDHRA PRADESH */
    val section: String = "",
    val headline: String,
    val category: String = "",
    val subjects: List<String> = emptyList(),
    val group1: List<String> = emptyList(),
    val group2: List<String> = emptyList(),
    /** 1 highest */
    val importance: Int = 2,
    val date: String = "",
    val facts: List<String> = emptyList(),
    val summary: String = "",
    val staticLink: String = "",
    val prelimsTakeaway: String = "",
    val mainsAngle: String = "",
    val apLink: String = "",
    val status: String = "",
    val verifiedFirst: Boolean = false,
    val sources: List<BriefSource> = emptyList(),
    /** Rocket-form notes (markdown subset, see ui/Notes.kt) */
    val notes: String = "",
    /** APPSC Current Affairs blueprint angles this item answers */
    val blueprint: List<String> = emptyList(),
)

@Serializable
data class BriefSource(
    val title: String = "",
    val publisher: String = "",
    val url: String = "",
    val publishedDate: String = "",
    val isPrimary: Boolean = false,
)

@Serializable
data class CoverageRow(val subject: String = "", val covered: Boolean = false, val items: List<Int> = emptyList(), val note: String = "")

@Serializable
data class Mcq(val question: String, val options: List<String> = emptyList(), val answer: String = "A", val explanation: String = "", val item: Int = 0)

@Serializable
data class MainsQuestion(val question: String, val paper: String = "", val subject: String = "", val dimensions: List<String> = emptyList())

/** The subject filters of the briefing screen and the subjects each covers. */
val BRIEF_FILTERS: List<Pair<String, List<String>>> = listOf(
    "History" to listOf("Indian History & Culture"),
    "AP History" to listOf("AP History & Culture"),
    "Geography" to listOf("Indian Geography", "AP Geography"),
    "Polity" to listOf("Constitution", "Polity"),
    "Society" to listOf("Indian Society", "Social Justice"),
    "Economy" to listOf("Indian Economy", "AP Economy"),
    "S&T" to listOf("Science & Technology", "Health"),
    "Environment" to listOf("Environment & Ecology"),
    "IR" to listOf("International Relations"),
    "Governance" to listOf("Governance", "Public Administration"),
    "Ethics" to listOf("Ethics"),
    "Disaster Mgmt" to listOf("Disaster Management"),
)

/** The 6 books of the Combined Notes each briefing subject belongs to. */
private val SUBJECT_BOOK = mapOf(
    "Indian History & Culture" to "History & Culture",
    "AP History & Culture" to "History & Culture",
    "Constitution" to "Polity, Society & IR",
    "Polity" to "Polity, Society & IR",
    "Indian Society" to "Polity, Society & IR",
    "Social Justice" to "Polity, Society & IR",
    "International Relations" to "Polity, Society & IR",
    "Governance" to "Polity, Society & IR",
    "Public Administration" to "Polity, Society & IR",
    "Ethics" to "Polity, Society & IR",
    "Indian Economy" to "Economy",
    "AP Economy" to "Economy",
    "Indian Geography" to "Geography",
    "AP Geography" to "Geography",
    "Science & Technology" to "Science, Tech & Environment",
    "Health" to "Science, Tech & Environment",
    "Environment & Ecology" to "Science, Tech & Environment",
    "Disaster Management" to "Science, Tech & Environment",
)

/** Categories read best under book 6 (general current affairs). */
private val GENERAL = Regex("Awards|Sports|Appointments|Persons in News|Important Days|Books")

/** The books (of the 6) this briefing item is filed under: by its subjects, and book 6 for awards, sports, people and days. */
fun BriefItem.books(): List<String> {
    val b = subjects.mapNotNull { SUBJECT_BOOK[it] }.toMutableList()
    if (GENERAL.containsMatchIn(category) || b.isEmpty()) b += "Current Affairs"
    return b.distinct()
}
