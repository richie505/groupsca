package com.appsc.ca.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * What belongs to the reader: saved stories (kept in full, so they outlive the
 * 60-day cache), which stories were opened, and settings.
 */
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

    private companion object {
        const val KEY_READ = "read"
        const val KEY_NOTIFY = "notify"
        const val KEY_EXAM = "exam"
    }
}
