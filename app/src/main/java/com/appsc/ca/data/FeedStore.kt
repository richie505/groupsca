package com.appsc.ca.data

import android.content.Context
import com.appsc.ca.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class RefreshResult(val newItems: Int, val newAp: Int, val firstRun: Boolean)

/**
 * Downloads the daily files and keeps the last [KEEP_DAYS] days on the phone,
 * so everything already downloaded reads offline.
 *
 * Only days whose `updated` stamp changed are downloaded again: the pipeline
 * runs twice a day and only touches the days it added stories to.
 */
class FeedStore(context: Context, private val baseUrl: String = BuildConfig.FEED_URL) {
    private val root = File(context.filesDir, "feed")
    private val daysDir = File(root, "days")
    private val indexFile = File(root, "index.json")
    private val trendingFile = File(root, "trending.json")

    fun cachedTrending(): Trending? = read(trendingFile)

    private val briefDir = File(root, "briefings")

    /** The AI briefings on the phone (published ones), newest first. */
    fun cachedBriefings(): List<Briefing> =
        (briefDir.listFiles { f -> f.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.json")) } ?: emptyArray())
            .mapNotNull { read<Briefing>(it) }
            .filter { it.status == "published" }
            .sortedByDescending { it.date }

    /** Downloads new or changed published briefings (the last [KEEP_DAYS]); true if today's (IST) is among them. */
    fun refreshBriefings(): Boolean {
        val text = get("briefings/index.json")
        val index = FeedJson.decodeFromString<BriefingIndex>(text)
        briefDir.mkdirs()
        val old = read<BriefingIndex>(File(briefDir, "index.json"))?.briefings?.associate { it.date to it.generatedAt }.orEmpty()
        val keep = index.briefings.filter { it.status == "published" }.take(KEEP_UPLOAD_DAYS)
        for (b in keep) {
            val f = File(briefDir, "${b.date}.json")
            if (f.exists() && old[b.date] == b.generatedAt) continue
            val body = get("briefings/${b.date}.json")
            FeedJson.decodeFromString<Briefing>(body)
            writeAtomically(f, body)
        }
        writeAtomically(File(briefDir, "index.json"), text)
        // days no longer listed (the old AI briefings) leave the phone too
        val listed = keep.map { "${it.date}.json" }.toSet() + "index.json"
        briefDir.listFiles()?.filter { it.name !in listed }?.forEach { it.delete() }
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()
        return keep.any { it.date == today }
    }

    fun cachedIndex(): FeedIndex? = read(indexFile)

    fun cachedDays(): List<Day> =
        (daysDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { read<Day>(it) }
            .sortedByDescending { it.date }

    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        daysDir.mkdirs()
        val old = cachedIndex()
        val oldStamp = old?.days?.associate { it.date to it.updated }.orEmpty()
        val known = cachedDays().flatMapTo(HashSet()) { d -> d.items.map { it.id } }

        val indexText = get("index.json")
        val index = FeedJson.decodeFromString<FeedIndex>(indexText)
        val keep = index.days.take(KEEP_DAYS)

        var newItems = 0
        var newAp = 0
        for (d in keep) {
            val file = File(daysDir, "${d.date}.json")
            if (file.exists() && oldStamp[d.date] == d.updated) continue
            val text = get("days/${d.date}.json")
            val day = FeedJson.decodeFromString<Day>(text) // a broken file is never cached
            writeAtomically(file, text)
            for (i in day.items) if (i.id !in known) {
                newItems++
                if (i.ap) newAp++
            }
        }
        writeAtomically(indexFile, indexText)
        // the AI briefing and Trending are extras: a failure there never fails the refresh
        runCatching { refreshBriefings() }
        runCatching {
            val t = get("trending.json")
            FeedJson.decodeFromString<Trending>(t)
            writeAtomically(trendingFile, t)
        }

        val wanted = keep.mapTo(HashSet()) { "${it.date}.json" }
        daysDir.listFiles()?.filter { it.name !in wanted }?.forEach { it.delete() }

        RefreshResult(newItems, newAp, firstRun = old == null)
    }

    private val digestDir = File(daysDir.parentFile, "digests")

    /** A weekly or monthly digest: downloaded when the feed has a newer one, else the copy kept from before. */
    suspend fun digest(ref: DigestRef): DigestFile? = withContext(Dispatchers.IO) {
        val file = File(digestDir, ref.file.substringAfterLast('/'))
        val fresh = runCatching {
            val text = get(ref.file)
            FeedJson.decodeFromString<DigestFile>(text).also { writeAtomically(file, text) }
        }.getOrNull()
        fresh ?: read<DigestFile>(file)
    }

    private fun get(path: String): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        conn.useCaches = false
        try {
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code for $path")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private inline fun <reified T> read(file: File): T? =
        runCatching { FeedJson.decodeFromString<T>(file.readText()) }.getOrNull()

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val KEEP_DAYS = 60
        /** Days read from uploaded papers and notes: a monthly notes PDF fills a month at once, so far more are kept. */
        const val KEEP_UPLOAD_DAYS = 400
    }
}
