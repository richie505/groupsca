package com.appsc.ca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appsc.ca.data.Item
import com.appsc.ca.data.QuizQ
import com.appsc.ca.data.ReviseCard
import com.appsc.ca.data.shortDate

/** What the day screen lists. */
enum class DayView(val label: String) {
    TOP("New today"), UPDATES("Updates"), ONE_LINERS("One-liners"), QUIZ("Quiz"), REVISE("Revise"), ALL("All"),
}

/** Minutes to read a day: new topics in full (200 words a minute), updates and one-liners as lines. */
fun readingMinutes(newTopics: List<Item>, lines: Int): Int {
    val words = newTopics.sumOf { i ->
        val text = i.summary + " " + i.newFacts.joinToString(" ") { it.text } + " " +
            i.brief.sections.joinToString(" ") { s -> s.bullets.joinToString(" ") }
        text.split(Regex("\\s+")).size
    }
    return maxOf(1, (words / 200.0 + lines * 0.15).toInt() + 1)
}

/** An update on a topic read before: its headline, the topic and when it started; tap for the full story. */
@Composable
fun UpdateRow(item: Item, done: Boolean, expanded: @Composable () -> Unit) {
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row {
                Text("↻", style = MaterialTheme.typography.bodyMedium, color = C.Accent, modifier = Modifier.width(18.dp))
                Column {
                    Text(item.line.ifBlank { item.title }, style = MaterialTheme.typography.bodyMedium, color = C.Body)
                    Text(
                        (if (done) "✓ " else "") + "Topic since ${shortDate(item.threadStart)}: ${item.threadTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = C.Faint,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (open) expanded()
    }
}

/** A one-liner: the line, then where it is from; tap for the headline. */
@Composable
fun OneLinerRow(item: Item) {
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = C.Accent, modifier = Modifier.width(14.dp))
            Column {
                Text(item.line.ifBlank { item.title }, style = MaterialTheme.typography.bodyMedium, color = C.Body)
                Text(
                    if (open) "${item.source}: ${item.title}" else shortSource(item.source),
                    style = MaterialTheme.typography.labelMedium,
                    color = C.Faint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * A fill-in-the-blank question. Answered once: the choice is kept, right in green, wrong in red with the right
 * answer shown; a wrong answer goes to Revise.
 */
@Composable
fun QuizCard(n: Int, q: QuizQ, answered: Boolean?, onAnswer: (Int) -> Unit) {
    var chosen by rememberSaveable(q.id) { mutableIntStateOf(-1) }
    val done = answered != null || chosen >= 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text("Q$n · ${q.title}", style = MaterialTheme.typography.labelMedium, color = C.Faint, maxLines = 2)
        Text(q.q, style = MaterialTheme.typography.bodyLarge, color = C.Ink, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
        q.options.forEachIndexed { i, o ->
            val right = i == q.answer
            val bg = when {
                !done -> C.Surface
                right -> Color(0xFFDCF3E3)
                i == chosen -> Color(0xFFFBE0E0)
                else -> C.Surface
            }
            Text(
                "${'A' + i}.  $o",
                style = MaterialTheme.typography.bodyMedium,
                color = C.Body,
                fontWeight = if (done && right) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(enabled = !done) {
                        chosen = i
                        onAnswer(i)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        if (done) {
            val ok = if (chosen >= 0) chosen == q.answer else answered == true
            Text(
                if (ok) "Right." else "Answer: ${q.options.getOrNull(q.answer)}. Added to Revise: it comes back tomorrow.",
                style = MaterialTheme.typography.labelMedium,
                color = if (ok) Color(0xFF15803D) else C.Critical,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A revision card: answer again; right moves it on (3, then 7 days), wrong brings it back tomorrow. */
@Composable
fun ReviseCardView(card: ReviseCard, onDone: (Boolean) -> Unit) {
    var chosen by rememberSaveable(card.q.id + card.due) { mutableIntStateOf(-1) }
    val q = card.q
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text("Revision ${card.step + 1} of 3 · ${q.title}", style = MaterialTheme.typography.labelMedium, color = C.Faint, maxLines = 2)
        Text(q.q, style = MaterialTheme.typography.bodyLarge, color = C.Ink, modifier = Modifier.padding(vertical = 6.dp))
        q.options.forEachIndexed { i, o ->
            val bg = when {
                chosen < 0 -> C.Surface
                i == q.answer -> Color(0xFFDCF3E3)
                i == chosen -> Color(0xFFFBE0E0)
                else -> C.Surface
            }
            Text(
                "${'A' + i}.  $o",
                style = MaterialTheme.typography.bodyMedium,
                color = C.Body,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(enabled = chosen < 0) { chosen = i }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        if (chosen >= 0) {
            val ok = chosen == q.answer
            Row {
                Text(
                    if (ok) "Right." else "Answer: ${q.options.getOrNull(q.answer)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) Color(0xFF15803D) else C.Critical,
                    modifier = Modifier.weight(1f).padding(top = 14.dp),
                )
                TextButton(onClick = { onDone(ok) }) { Text("Next") }
            }
        }
    }
}
