package com.appsc.ca.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appsc.ca.data.BOOKS
import com.appsc.ca.data.Item
import com.appsc.ca.data.bookNumber
import com.appsc.ca.data.shortDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A week (Monday to Sunday) or a month of news days. */
data class Period(val kind: String, val start: LocalDate) {
    val end: LocalDate get() = if (kind == "week") start.plusDays(6) else start.plusMonths(1).minusDays(1)
    val title: String
        get() = if (kind == "week") {
            "Week of ${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        } else {
            start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }

    fun has(iso: String) = runCatching { LocalDate.parse(iso).let { !it.isBefore(start) && !it.isAfter(end) } }.getOrDefault(false)

    companion object {
        fun weekOf(iso: String) = Period("week", LocalDate.parse(iso).with(DayOfWeek.MONDAY))
        fun monthOf(iso: String) = Period("month", LocalDate.parse(iso).withDayOfMonth(1))
    }
}

/** One topic of the period: where it started, its stories in the period, and its static notes. */
private data class DigestTopic(val lead: Item, val stories: List<Item>, val notesFrom: Item?)

/** The weeks and months the days on this phone fall in, newest first. */
fun periodsOf(dates: List<String>, kind: String): List<Period> =
    dates.mapNotNull { runCatching { if (kind == "week") Period.weekOf(it) else Period.monthOf(it) }.getOrNull() }
        .distinct().sortedByDescending { it.start }

/**
 * The weekly or monthly digest: every topic once, by book, with its timeline in the period and its static notes,
 * then the period's one-liners and its quiz.
 */
@Composable
fun DigestScreen(vm: AppViewModel, period: Period, onBack: () -> Unit) {
    val days = vm.days.filter { period.has(it.date) }.sortedBy { it.date }
    val items = days.flatMap { it.items }.filter { it.topicOf.isEmpty() && !it.digest }
    val oneLiners = items.filter { it.oneLiner }
    val quiz = days.flatMap { it.quiz }
    // topics: stories of the same thread together; the topic's first story leads
    val topics = items.filterNot { it.oneLiner }
        .groupBy { vm.threadOf(it) }
        .map { (thread, list) ->
            val all = vm.allItems.filter { vm.threadOf(it) == thread && it.topicOf.isEmpty() }.sortedBy { it.date }
            val lead = all.firstOrNull() ?: list.first()
            DigestTopic(lead, list.sortedBy { it.date }, (all + list).firstOrNull { it.brief.sections.isNotEmpty() })
        }
        // topics that made New today on some day, then the rest by score
        .sortedWith(compareByDescending<DigestTopic> { t -> t.stories.any { it.top } }.thenByDescending { t -> t.stories.maxOf { it.score } })
    var onlyTop by rememberSaveable { mutableStateOf(true) }
    val shown = if (onlyTop && topics.any { t -> t.stories.any { it.top } }) topics.filter { t -> t.stories.any { it.top } } else topics
    val byBook = BOOKS.associateWith { b -> shown.filter { it.lead.book == b } }.filter { it.value.isNotEmpty() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(if (period.kind == "week") "Weekly digest" else "Monthly digest", style = MaterialTheme.typography.headlineSmall, color = C.Ink)
                    Text(
                        "${period.title} · ${days.size} days on this phone · ${topics.size} topics · ${oneLiners.size} one-liners",
                        style = MaterialTheme.typography.labelMedium,
                        color = C.Muted,
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FilterChip(selected = onlyTop, onClick = { onlyTop = true }, label = { Text("Main topics") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = !onlyTop, onClick = { onlyTop = false }, label = { Text("All topics") })
            }
        }
        if (days.isEmpty()) item { Text("No days of this period are on the phone.", color = C.Muted, modifier = Modifier.padding(24.dp)) }
        for ((book, list) in byBook) {
            item(key = "book-$book") {
                Text(
                    "${bookNumber(book)}. $book (${list.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = C.Accent,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "t-" + it.lead.id }) { t -> TopicCard(vm, t) }
        }
        if (oneLiners.isNotEmpty()) {
            item(key = "ol") {
                Text("One-liners", style = MaterialTheme.typography.titleMedium, color = C.Accent, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(oneLiners, key = { "ol-" + it.id }) { OneLinerRow(it) }
        }
        if (quiz.isNotEmpty()) {
            item(key = "qz") {
                val got = quiz.count { vm.answers[it.id] == true }
                val done = quiz.count { it.id in vm.answers }
                Text(
                    "Quiz of the ${period.kind}: $got right of $done answered (${quiz.size} questions)",
                    style = MaterialTheme.typography.titleMedium,
                    color = C.Accent,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(quiz, key = { _, q -> "q-" + q.id }) { n, q -> QuizCard(n + 1, q, vm.answers[q.id]) { vm.answer(q, it) } }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** A topic: its headline, the period's timeline, and the static notes (tap to open), with Mark done. */
@Composable
private fun TopicCard(vm: AppViewModel, t: DigestTopic) {
    var open by rememberSaveable(t.lead.id) { mutableStateOf(false) }
    val done = vm.isDone(t.lead)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .clickable { open = !open }
            .padding(14.dp),
    ) {
        Text(topicTag(t.lead), style = MaterialTheme.typography.labelMedium, color = C.Accent, fontWeight = FontWeight.Bold)
        Text((if (done) "✓ " else "") + t.lead.title, style = MaterialTheme.typography.titleMedium, color = if (done) C.Muted else C.Ink)
        if (t.lead.summary.isNotBlank() && open) {
            Text(t.lead.summary, style = MaterialTheme.typography.bodyMedium, color = C.Body, modifier = Modifier.padding(top = 4.dp))
        }
        // the period's stories of this topic
        for (s in t.stories) {
            Row(Modifier.padding(top = 4.dp)) {
                Text(shortDate(s.date), style = MaterialTheme.typography.labelMedium, color = C.Muted, modifier = Modifier.width(76.dp))
                Text(s.line.ifBlank { s.title }, style = MaterialTheme.typography.labelMedium, color = C.Body)
            }
        }
        val notes = t.notesFrom?.let { vm.visible(it) }?.brief?.sections.orEmpty()
        if (open) {
            if (notes.isNotEmpty()) {
                Text("STATIC NOTES", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                notes.forEachIndexed { i, sec -> StaticSectionView(i + 1, sec) }
            }
            TextButton(onClick = { vm.toggleDone(t.lead) }) { Text(if (done) "✓ Done" else "Mark done") }
        } else if (notes.isNotEmpty()) {
            Text(
                "📘 " + notes.joinToString(" · ") { it.topic },
                style = MaterialTheme.typography.labelMedium,
                color = C.Accent,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
