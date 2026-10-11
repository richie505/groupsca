package com.appsc.ca.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * What belongs to the reader: saved stories (kept in full, so they outlive the
 * 60-day cache), which stories were opened, and settings.
 */
@kotlinx.serialization.Serializable
data class ReviseCard(val q: QuizQ, val due: Long, val step: Int = 0)

class UserStore(context: Context) {
    private val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)
    private val savedFile = File(context.filesDir, "saved.json")

    fun saved(): List<Item> =
        runCatching { FeedJson.decodeFromString<List<Item>>(savedFile.readText()) }.getOrDefault(emptyList())

    fun setSaved(items: List<Item>) {
        savedFile.writeText(FeedJson.encodeToString(items))
    }

    fun readIds(): Set<String> = prefs.getStringSet(KEY_READ, emptySet()).orEmpty()

    fun setReadIds(ids: Set<String>) = prefs.edit().putStringSet(KEY_READ, HashSet(ids)).apply()

    var notify: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFY, v).apply()

    var exam: Exam
        get() = runCatching { Exam.valueOf(prefs.getString(KEY_EXAM, null) ?: "BOTH") }.getOrDefault(Exam.BOTH)
        set(v) = prefs.edit().putString(KEY_EXAM, v.name).apply()

    /** Quiz answers: question id -> answered right. */
    fun answers(): Map<String, Boolean> =
        prefs.getStringSet(KEY_ANSWERS, emptySet()).orEmpty().associate { it.substringBeforeLast('|') to it.endsWith("|1") }

    fun setAnswers(a: Map<String, Boolean>) =
        prefs.edit().putStringSet(KEY_ANSWERS, a.mapTo(HashSet()) { (k, v) -> "$k|${if (v) 1 else 0}" }).apply()

    private val reviseFile = File(context.filesDir, "revise.json")

    /** Questions answered wrong, coming back after 1, 3 and 7 days. */
    fun revise(): List<ReviseCard> =
        runCatching { FeedJson.decodeFromString<List<ReviseCard>>(reviseFile.readText()) }.getOrDefault(emptyList())

    fun setRevise(cards: List<ReviseCard>) {
        reviseFile.writeText(FeedJson.encodeToString(cards))
    }

    /** Topics marked done (read and revised): their thread ids. */
    fun doneThreads(): Set<String> = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()

    fun setDoneThreads(s: Set<String>) = prefs.edit().putStringSet(KEY_DONE, HashSet(s)).apply()

    /** The reader's topic corrections: story id -> the topic it belongs to ("" = a topic of its own). */
    fun topicLinks(): Map<String, String> =
        prefs.getStringSet(KEY_LINKS, emptySet()).orEmpty().associate { it.substringBefore('|') to it.substringAfter('|') }

    fun setTopicLinks(m: Map<String, String>) =
        prefs.edit().putStringSet(KEY_LINKS, m.mapTo(HashSet()) { (k, v) -> "$k|$v" }).apply()

    /** Static notes hidden on this phone as wrong for a story: "storyId|where". */
    fun hiddenNotes(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun setHiddenNotes(s: Set<String>) = prefs.edit().putStringSet(KEY_HIDDEN, HashSet(s)).apply()

    /** How many new topics a day to show in full (10, 15 or 25). */
    var perDay: Int
        get() = prefs.getInt(KEY_PER_DAY, 15)
        set(v) = prefs.edit().putInt(KEY_PER_DAY, v).apply()

    var largeText: Boolean
        get() = prefs.getBoolean(KEY_LARGE, false)
        set(v) = prefs.edit().putBoolean(KEY_LARGE, v).apply()

    /** Eenadu's Telugu headlines in the lists. */
    var telugu: Boolean
        get() = prefs.getBoolean(KEY_TELUGU, true)
        set(v) = prefs.edit().putBoolean(KEY_TELUGU, v).apply()

    /** The hour (IST) of the daily briefing notification: 20, 21 or 22. */
    var briefHour: Int
        get() = prefs.getInt(KEY_BRIEF_HOUR, 21)
        set(v) = prefs.edit().putInt(KEY_BRIEF_HOUR, v).apply()

    var showMains: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MAINS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_MAINS, v).apply()

    var showApLink: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AP, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_AP, v).apply()

    var showQuiz: Boolean
        get() = prefs.getBoolean(KEY_SHOW_QUIZ, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_QUIZ, v).apply()

    /** The last briefing date the notification was sent for. */
    var briefNotified: String
        get() = prefs.getString(KEY_BRIEF_NOTIFIED, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BRIEF_NOTIFIED, v).apply()

    /** Bookmarked briefing items: "date|headline" -> category (Revise Today, Important, AP, Prelims, Mains). */
    fun briefMarks(): Map<String, String> =
        prefs.getStringSet(KEY_BRIEF_MARKS, emptySet()).orEmpty().associate { it.substringBeforeLast('\t') to it.substringAfterLast('\t') }

    fun setBriefMarks(m: Map<String, String>) =
        prefs.edit().putStringSet(KEY_BRIEF_MARKS, m.mapTo(HashSet()) { (k, v) -> "$k\t$v" }).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 1f)
        set(v) = prefs.edit().putFloat(KEY_RATE, v).apply()

    private companion object {
        const val KEY_RATE = "speech_rate"
        const val KEY_ANSWERS = "quiz_answers"
        const val KEY_HIDDEN = "hidden_notes"
        const val KEY_DONE = "done_threads"
        const val KEY_LINKS = "topic_links"
        const val KEY_PER_DAY = "per_day"
        const val KEY_LARGE = "large_text"
        const val KEY_TELUGU = "telugu"
        const val KEY_BRIEF_HOUR = "brief_hour"
        const val KEY_SHOW_MAINS = "show_mains"
        const val KEY_SHOW_AP = "show_ap"
        const val KEY_SHOW_QUIZ = "show_quiz"
        const val KEY_BRIEF_NOTIFIED = "brief_notified"
        const val KEY_BRIEF_MARKS = "brief_marks"
        const val KEY_READ = "read"
        const val KEY_NOTIFY = "notify"
        const val KEY_EXAM = "exam"
    }
}
