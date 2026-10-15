package com.appsc.ca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appsc.ca.data.BriefItem
import com.appsc.ca.data.Item
import com.appsc.ca.data.SpeechText
import com.appsc.ca.data.bookNumber
import com.appsc.ca.platform.ReadAloud
import com.appsc.ca.platform.SpeechPage

/** One story as read aloud: each part is a line on screen, [keys] says which (for the highlight and tap-to-seek). */
data class Readable(val id: String, val title: String, val parts: List<String>, val keys: List<String>) {
    val page get() = SpeechPage(id, title, parts)
}

/**
 * Reading aloud as in the Rocket Prep app: one line at a time (the headline, the context, then every note line or
 * key fact in turn, tables row by row), the line being read highlighted and followed on screen, a tap on a line
 * reads from there. Then the next story, unless "This story" is on. Text goes through the Rocket app's
 * [SpeechText] (citations out, 4,800 short forms said in full, the story's own words deciding the meaning).
 * Telugu headlines are skipped: the voice is Indian English.
 */
object Listening {
    /** The stories being read, in order. */
    var queue by mutableStateOf<List<Readable>>(emptyList())
        private set

    /** Stop at the end of the story being read ("This story"), instead of reading on. */
    var thisOnly by mutableStateOf(false)

    fun canRead(item: Item) = item.lang != "te"

    /** Starts reading news [items] at [from] (skipping Telugu ones), then carries on down the list. */
    fun play(items: List<Item>, from: Int, rate: Float, part: Int = 0) {
        val start = items.drop(from.coerceAtLeast(0)).firstOrNull(::canRead)?.id
        val list = items.filter(::canRead).map(::readable)
        playList(list, list.indexOfFirst { it.id == start }.coerceAtLeast(0), rate, part)
    }

    /** Starts reading a day's briefing [items] at [from]. */
    fun playBrief(date: String, items: List<BriefItem>, from: Int, rate: Float, part: Int = 0) {
        playList(items.map { readable(date, it) }, from, rate, part)
    }

    fun playList(list: List<Readable>, from: Int, rate: Float, part: Int = 0) {
        if (list.isEmpty()) return
        var i = from.coerceIn(0, list.lastIndex)
        queue = list
        ReadAloud.play(list[i].page, part, rate) {
            if (thisOnly) null else {
                i += 1
                list.getOrNull(i)?.page
            }
        }
    }

    /** Index of the story being read in [queue], or -1. */
    fun currentIndex(): Int = queue.indexOfFirst { it.id == ReadAloud.playback.value.pageId }

    fun current(): Readable? = queue.getOrNull(currentIndex())

    /** True when the line [key] of story [id] is the one being read. */
    fun isReading(id: String, key: String): Boolean {
        val pb = ReadAloud.playback.value
        return pb.active && pb.pageId == id && current()?.keys?.getOrNull(pb.part) == key
    }

    /** True when story [id] is being read. */
    fun isOn(id: String): Boolean = ReadAloud.playback.value.let { it.active && it.pageId == id }

    /** Reads from line [key] of story [id] (a tap on that line while listening). */
    fun seekTo(id: String, key: String) {
        val r = current() ?: return
        if (r.id != id) return
        val at = r.keys.indexOf(key)
        if (at < 0) return
        ReadAloud.seek(at)
        ReadAloud.resume()
    }

    /** The previous or next line; past the last line, the next story. */
    fun step(by: Int, rate: Float) {
        val pb = ReadAloud.playback.value
        val r = current() ?: return
        val to = pb.part + by
        when {
            to in r.parts.indices -> ReadAloud.seek(to)
            to > r.parts.lastIndex -> skip(1, rate)
            else -> skip(-1, rate)
        }
    }

    fun skip(by: Int, rate: Float) {
        val i = currentIndex()
        if (i < 0) return
        playList(queue, (i + by).coerceIn(0, queue.lastIndex), rate)
    }

    /** Adds a line: what is said, and which line on screen it is. */
    private class Lines(val book: Int, val context: String) {
        val parts = mutableListOf<String>()
        val keys = mutableListOf<String>()
        fun add(key: String, text: String) {
            val said = SpeechText.speakable(text.replace("**", "").replace("⚠", ""), book, context)
            if (said.any(Char::isLetterOrDigit)) {
                parts += said
                keys += key
            }
        }
    }

    /** The Rocket-form notes, line by line: a table is announced once, then read row by row. */
    private fun Lines.notes(md: String) {
        parseNotes(md).forEachIndexed { i, b ->
            when (b) {
                is NoteLine -> add("note:$i", when (b.kind) {
                    't' -> "Trap. " + b.text.replace(Regex("""\*\*Traps?:\*\*"""), "")
                    'x' -> "Exam angle. " + b.text.removePrefix("Exam angle:")
                    else -> b.text
                })
                is NoteTable -> {
                    add("note:$i", "Table: " + b.head.joinToString(", ") + ".")
                    for (r in b.rows) add("note:$i", r.first() + ": " + r.drop(1).filter { it.isNotBlank() }.joinToString("; ") + ".")
                }
            }
        }
    }

    fun readable(item: Item): Readable {
        val context = listOf(item.title, item.summary, item.rocket?.notes.orEmpty(), item.facts.joinToString(" ") { it.text }).joinToString(" ")
        val l = Lines(bookNumber(item.book), context)
        l.add("title", item.title.trimEnd('.') + ".")
        if (item.summary.isNotBlank()) l.add("summary", item.summary)
        val rocket = item.rocket?.notes.orEmpty()
        if (rocket.isNotBlank()) l.notes(rocket)
        else item.newFacts.forEachIndexed { i, f -> l.add("fact:$i", f.text) }
        // then the static notes, topic by topic
        item.brief.sections.forEachIndexed { s, sec ->
            l.add("brief:$s", "${s + 1}. " + sec.topic.substringAfter(" · ") + ".")
            sec.bullets.forEachIndexed { i, b -> l.add("brief:$s:$i", b) }
        }
        return Readable(item.id, item.title, l.parts, l.keys)
    }

    /** The briefing card's id when read aloud. */
    fun briefId(date: String, it: BriefItem) = "brief:$date:${it.section}:${it.headline}"

    fun readable(date: String, it: BriefItem): Readable {
        val context = listOf(it.headline, it.summary, it.notes, it.facts.joinToString(" ")).joinToString(" ")
        val l = Lines(0, context)
        l.add("title", it.headline.trimEnd('.') + ".")
        if (it.summary.isNotBlank()) l.add("summary", it.summary)
        if (it.notes.isNotBlank()) l.notes(it.notes)
        else it.facts.forEachIndexed { i, f -> l.add("fact:$i", f) }
        return Readable(briefId(date, it), it.headline, l.parts, l.keys)
    }
}

/** The Rocket Prep app's player bar: previous / next line, play-pause, speed, This story / Read on, where we are, stop. */
@Composable
fun PlayerBar(rate: Float, onRate: (Float) -> Unit, modifier: Modifier = Modifier) {
    val pb by ReadAloud.playback
    if (!pb.active) return
    val i = Listening.currentIndex()
    val item = Listening.queue.getOrNull(i)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { Listening.step(-1, rate) }) { Icon(Icons.Filled.SkipPrevious, "Previous line", tint = C.Ink) }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Accent)
                .clickable { if (pb.playing) ReadAloud.pause() else ReadAloud.resume() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (pb.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (pb.playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = { Listening.step(1, rate) }) { Icon(Icons.Filled.SkipNext, "Next line", tint = C.Ink) }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(C.AccentSoft).clickable {
                val rates = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                val r = rates[(rates.indexOf(rate) + 1) % rates.size]
                onRate(r)
                ReadAloud.setRate(r)
            }.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("${if (rate % 1f == 0f) rate.toInt() else rate}×", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = C.Accent)
        }
        Box(
            Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (Listening.thisOnly) C.Accent else C.AccentSoft)
                .clickable { Listening.thisOnly = !Listening.thisOnly }.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(if (Listening.thisOnly) "This story" else "Read on", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (Listening.thisOnly) Color.White else C.Accent)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                if (item != null) "${pb.part + 1} / ${item.parts.size}" + if (Listening.queue.size > 1) " · story ${i + 1}/${Listening.queue.size}" else "" else "",
                style = MaterialTheme.typography.labelMedium, color = C.Muted, maxLines = 1,
            )
            Text(item?.title.orEmpty(), style = MaterialTheme.typography.labelMedium, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { ReadAloud.stop() }) { Icon(Icons.Filled.Close, "Stop listening", tint = C.Muted) }
    }
}

/**
 * A line of a story on screen: highlighted while it is being read (and scrolled into view), and a tap on it
 * while the story is being read reads from there. [id] null: not readable, shown as is.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReadLine(id: String?, key: String, content: @Composable () -> Unit) {
    if (id == null) return content()
    val pb by ReadAloud.playback
    val on = pb.active && pb.pageId == id
    val here = on && Listening.current()?.keys?.getOrNull(pb.part) == key
    val bring = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(here) { if (here) runCatching { bring.bringIntoView() } }
    Box(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(bring)
            .clip(RoundedCornerShape(8.dp))
            .background(if (here) C.AccentSoft else Color.Transparent)
            .then(if (on) Modifier.clickable { Listening.seekTo(id, key) } else Modifier)
            .padding(horizontal = 4.dp),
    ) { content() }
}
