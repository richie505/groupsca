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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appsc.ca.data.Item
import com.appsc.ca.data.SpeechText
import com.appsc.ca.data.bookNumber
import com.appsc.ca.platform.ReadAloud
import com.appsc.ca.platform.SpeechPage

/**
 * Reading a list of stories aloud with the APPSC Prep app's read-aloud: one story after another, with the
 * screen locked too, until Stop. Each story is read as its headline, its summary, its key facts and the first
 * linked note from the Prep notes, all through the Prep app's [SpeechText] (citations out, short forms in
 * full). Telugu headlines are skipped: the voice is Indian English.
 */
object Listening {
    /** The stories being read, in order. */
    var queue by mutableStateOf<List<Item>>(emptyList())
        private set

    fun canRead(item: Item) = item.lang != "te"

    /** Starts reading [items] at [from] (skipping Telugu ones), then carries on down the list. */
    fun play(items: List<Item>, from: Int, rate: Float) {
        val list = items.filter(::canRead)
        if (list.isEmpty()) return
        val startId = items.drop(from.coerceAtLeast(0)).firstOrNull(::canRead)?.id ?: list.first().id
        var i = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        queue = list
        ReadAloud.play(pageOf(list[i]), 0, rate) {
            i += 1
            list.getOrNull(i)?.let(::pageOf)
        }
    }

    /** Index of the story being read in [queue], or -1. */
    fun currentIndex(): Int = queue.indexOfFirst { it.id == ReadAloud.playback.value.pageId }

    fun skip(by: Int, rate: Float) {
        val i = currentIndex()
        if (i < 0) return
        val to = (i + by).coerceIn(0, queue.lastIndex)
        play(queue, to, rate)
    }

    fun pageOf(item: Item): SpeechPage {
        val book = bookNumber(item.book)
        val say = { t: String -> SpeechText.speakable(t, book) }
        val parts = buildList {
            add(say(item.title.trimEnd('.') + "."))
            val facts = item.facts.map { it.text }
            // the summary, unless the key facts already say it
            if (item.summary.isNotBlank() && facts.none { item.summary.startsWith(it.take(60)) }) add(say(item.summary))
            facts.forEach { add(say(it)) }
            item.notes.firstOrNull { it.src == "Prep notes" }?.let { add("From your notes: " + say(it.text)) }
        }.filter { it.any(Char::isLetterOrDigit) }
        return SpeechPage(item.id, item.title, parts)
    }
}

/** The Prep app's player bar: previous / next story, play-pause, speed, where we are, stop. */
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
        IconButton(onClick = { Listening.skip(-1, rate) }) { Icon(Icons.Filled.SkipPrevious, "Previous story", tint = C.Ink) }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Accent)
                .clickable { if (pb.playing) ReadAloud.pause() else ReadAloud.resume() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (pb.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (pb.playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = { Listening.skip(1, rate) }) { Icon(Icons.Filled.SkipNext, "Next story", tint = C.Ink) }
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
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                if (i >= 0) "Story ${i + 1} / ${Listening.queue.size}" else "",
                style = MaterialTheme.typography.labelMedium, color = C.Muted,
            )
            Text(item?.title.orEmpty(), style = MaterialTheme.typography.labelMedium, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { ReadAloud.stop() }) { Icon(Icons.Filled.Close, "Stop listening", tint = C.Muted) }
    }
}
