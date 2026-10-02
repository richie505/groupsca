package com.appsc.ca.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appsc.ca.data.Item
import com.appsc.ca.data.bookNumber
import com.appsc.ca.data.shortDate

/**
 * One story. Collapsed: badges, headline, source and the summary. Tapped open:
 * the key facts with the question angle each answers, the syllabus units, the
 * blueprint angles, and why it scored what it did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemCard(
    item: Item,
    read: Boolean,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (item.band == "critical") C.Critical.copy(alpha = 0.35f) else C.Line, RoundedCornerShape(14.dp))
            .clickable {
                open = !open
                onRead()
            }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BandBadge(item.band, item.score)
                if (item.ap) Badge("Andhra Pradesh", C.Ap, C.ApSoft)
                if (item.lang == "te") Badge("తెలుగు", C.Muted, C.Chip)
                if (item.digest) Badge(if (item.title.startsWith("Current Affairs")) "Daily digest" else "CA site pick", C.High, C.HighSoft)
                if (item.scope == "international") Badge("International", C.Muted, C.Chip)
                else if (!item.ap || item.scope == "national") Badge("National", C.Muted, C.Chip)
                Badge("Book ${bookNumber(item.book)} · ${item.book}", C.Fact, C.FactSoft)
                Badge(item.exams.joinToString(" · ") { if (it == "G1") "Group-I" else "Group-II" }, C.Accent, C.AccentSoft)
            }
            if (!read) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(C.Unread))
            IconButton(onClick = onToggleSave) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (saved) "Remove from saved" else "Save",
                    tint = if (saved) C.Accent else C.Faint,
                )
            }
        }
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = C.Ink)
        Text(
            buildString {
                append(item.source)
                if (item.ministry.isNotBlank()) append(" · ").append(item.ministry)
                append(" · ").append(shortDate(item.date))
                if (item.alsoIn.isNotEmpty()) append(" · also in ${item.alsoIn.size} more")
            },
            style = MaterialTheme.typography.labelMedium,
            color = C.Faint,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        if (item.summary.isNotBlank()) {
            Text(
                item.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = C.Body,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (open) {
            if (item.facts.isNotEmpty()) {
                SectionLabel("Key facts")
                for (f in item.facts) {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Badge(f.angle, C.Fact, C.FactSoft)
                        Text(f.text, style = MaterialTheme.typography.bodyMedium, color = C.Body, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            if (item.units.isNotEmpty()) {
                SectionLabel("Syllabus")
                for (u in item.units) {
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text(u.code, style = MaterialTheme.typography.labelMedium, color = C.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                        Text(u.label, style = MaterialTheme.typography.labelMedium, color = C.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (item.angles.isNotEmpty() || item.subjects.isNotEmpty()) {
                SectionLabel("Blueprint angles · also in")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (a in item.angles) Badge(a, C.Fact, C.FactSoft)
                    for (s in item.subjects.filter { it != item.book }) Badge(s, C.Muted, C.Chip)
                }
            }
            SectionLabel("Why it is here")
            val w = item.why
            Text(
                "Score ${item.score}/100 = syllabus ${w.syllabus}/30 + blueprint ${w.angles}/20 + Andhra Pradesh ${w.ap}/20 " +
                    "+ importance ${w.importance}/15 + both exams ${w.reuse}/15",
                style = MaterialTheme.typography.labelMedium,
                color = C.Muted,
            )
            Row(Modifier.padding(top = 6.dp)) {
                if (item.url.isNotBlank()) {
                    TextButton(onClick = { openUrl(context, item.url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (item.source.startsWith("PIB")) "Read on PIB" else "Read full story"
                        )
                    }
                }
                TextButton(onClick = { share(context, item) }) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, fg: Color, bg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun BandBadge(band: String, score: Int) {
    val (fg, bg) = when (band) {
        "critical" -> C.Critical to C.CriticalSoft
        "high" -> C.High to C.HighSoft
        else -> C.Medium to C.MediumSoft
    }
    Badge("${band.uppercase()} $score", fg, bg)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = C.Faint,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun share(context: Context, item: Item) {
    val text = buildString {
        append(item.title).append('\n')
        for (f in item.facts) append("• ").append(f.text).append('\n')
        if (item.units.isNotEmpty()) append("Syllabus: ").append(item.units.joinToString { it.code }).append('\n')
        append(item.url)
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
