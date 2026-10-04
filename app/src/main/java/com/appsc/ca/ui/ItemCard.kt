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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appsc.ca.data.Item
import com.appsc.ca.data.StaticNote
import com.appsc.ca.data.StaticSection
import com.appsc.ca.data.bookNumber
import com.appsc.ca.data.clockTime
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
    /** Read-aloud is on this story now. */
    reading: Boolean = false,
    /** Starts read-aloud from this story (null: not offered, e.g. a Telugu headline). */
    onListen: (() -> Unit)? = null,
    /** Hides a static note the reader marks wrong for this story (null: no "Wrong note" button). */
    onHideNote: ((StaticSection) -> Unit)? = null,
    /** Every story of this story's topic on the phone, oldest first (shown when there are several). */
    timeline: List<Item> = emptyList(),
    /** The topic is marked done; [onDone] marks or unmarks it (null: no button). */
    done: Boolean = false,
    onDone: (() -> Unit)? = null,
    /** This story is an update on an earlier topic (after the reader's corrections). */
    isUpdate: Boolean = false,
    /** Earlier topics to put this story in, and how ("" = a topic of its own); null: no topic buttons. */
    earlierTopics: (() -> List<Item>)? = null,
    onLinkTopic: ((String) -> Unit)? = null,
) {
    // "Not this topic" / "Same topic as ...": the choice, then whether to report it too
    var picking by remember { mutableStateOf(false) }
    var linkTo by remember { mutableStateOf<Pair<String, String>?>(null) } // thread id ("" = own) to its label
    if (picking && earlierTopics != null) {
        val options = remember(item.id) { earlierTopics() }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Same topic as…") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (options.isEmpty()) Text("No topics from the 3 weeks before this day on the phone.")
                    for (o in options) {
                        Text(
                            "${shortDate(o.date)} · ${o.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                linkTo = (o.thread.ifBlank { o.id }) to o.title
                                picking = false
                            }.padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
    linkTo?.let { (thread, label) ->
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { linkTo = null },
            title = { Text(if (thread.isEmpty()) "Not this topic" else "Same topic") },
            text = {
                Text(
                    (if (thread.isEmpty()) "This story becomes a topic of its own." else "This story becomes an update on \"$label\".") +
                        "\n\nOnly on this phone, or also report it: the report opens a GitHub page (sign in once) and from the " +
                        "next update the feed links it this way for everyone, on every day.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onLinkTopic?.invoke(thread)
                    openUrl(ctx, topicLinkUrl(item, thread, label))
                    linkTo = null
                }) { Text("Apply and report") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onLinkTopic?.invoke(thread)
                    linkTo = null
                }) { Text("Only on this phone") }
            },
        )
    }
    var wrong by remember { mutableStateOf<StaticSection?>(null) }
    wrong?.let { sec ->
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { wrong = null },
            title = { Text("Wrong note?") },
            text = {
                Text(
                    "\"${sec.topic}\" does not fit this story.\n\nHide it here, or also report it: the report opens a " +
                        "GitHub page (sign in once) and from the next update this note is no longer linked to stories like this one, on every day.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openUrl(ctx, wrongNoteUrl(item, sec))
                    onHideNote?.invoke(sec)
                    wrong = null
                }) { Text("Hide and report") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onHideNote?.invoke(sec)
                    wrong = null
                }) { Text("Hide on this phone") }
            },
        )
    }
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                if (reading) 2.dp else 1.dp,
                when {
                    reading -> C.Accent
                    item.band == "critical" -> C.Critical.copy(alpha = 0.35f)
                    else -> C.Line
                },
                RoundedCornerShape(14.dp),
            )
            .background(if (reading) C.AccentSoft.copy(alpha = 0.45f) else Color.Transparent)
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
        Text(topicTag(item), style = MaterialTheme.typography.labelMedium, color = C.Accent, fontWeight = FontWeight.Bold)
        Text((if (done) "✓ " else "") + item.title, style = MaterialTheme.typography.titleMedium, color = if (done) C.Muted else C.Ink)
        Text(
            buildString {
                append(item.source)
                if (item.ministry.isNotBlank()) append(" · ").append(item.ministry)
                // When it was published; a 5 AM paper upload shows its own date
                // though it is filed under the news day before.
                append(" · ")
                if (item.time.isNotBlank()) append(shortDate(item.pubDate.ifBlank { item.date })).append(", ").append(clockTime(item.time))
                else append(shortDate(item.pubDate.ifBlank { item.date }))
                val more = item.alsoIn.size + item.related.size
                if (more > 0) append(" · also in $more more")
            },
            style = MaterialTheme.typography.labelMedium,
            color = C.Faint,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        if (item.summary.isNotBlank()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Ink)) { append("Context (${shortSource(item.source)}): ") }
                    append(item.summary)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = C.Body,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // what the static notes cover, in one line, even when the card is closed
        if (item.brief.sections.isNotEmpty()) {
            Text(
                "📘 Static notes: " + item.brief.sections.joinToString(" · ") { it.topic },
                style = MaterialTheme.typography.labelMedium,
                color = C.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else item.notes.firstOrNull()?.let { n ->
            Text(
                "📘 " + noteLabel(n) + " · " + n.where.substringAfter(" · ").substringBefore(" › ").ifBlank { n.src },
                style = MaterialTheme.typography.labelMedium,
                color = C.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (open) {
            // the current matter: what the context line does not already say
            if (item.newFacts.isNotEmpty()) {
                SectionLabel("Current matter")
                for (f in item.newFacts) Bullet(f.text, label = f.angle)
            }
            if (timeline.size > 1) {
                SectionLabel("Topic timeline")
                for (t in timeline) {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            shortDate(t.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (t.id == item.id) C.Accent else C.Muted,
                            fontWeight = if (t.id == item.id) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(76.dp),
                        )
                        Text(
                            t.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (t.id == item.id) C.Ink else C.Body,
                            modifier = Modifier.clickable(enabled = t.url.isNotBlank()) { openUrl(context, t.url) },
                        )
                    }
                }
            }
            if (item.related.isNotEmpty()) {
                SectionLabel("Same topic, also reported")
                for (r in item.related) {
                    Text(
                        "${r.source}: ${r.title}",
                        style = MaterialTheme.typography.labelMedium,
                        color = C.Muted,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = r.url.isNotBlank()) { openUrl(context, r.url) }.padding(vertical = 3.dp),
                    )
                }
            }
            if (item.brief.sections.isNotEmpty()) {
                SectionLabel("Static notes")
                item.brief.sections.forEachIndexed { i, sec ->
                    StaticSectionView(i + 1, sec, onWrong = onHideNote?.let { { wrong = sec } })
                }
                if (item.brief.gaps.isNotEmpty()) {
                    Text(
                        "Not in your notes: " + item.brief.gaps.joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = C.Faint,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            } else if (item.notes.isNotEmpty()) {
                SectionLabel("Static notes (closest in your notes)")
                for (n in item.notes) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (n.tier == "exact") C.AccentSoft else C.Surface).padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(noteLabel(n), if (n.tier == "exact") C.Accent else C.Muted, Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(n.src, style = MaterialTheme.typography.labelMedium, color = C.Faint)
                        }
                        Text(n.where.substringAfter(" · "), style = MaterialTheme.typography.labelMedium, color = C.Muted, modifier = Modifier.padding(top = 4.dp))
                        Text(n.text, style = MaterialTheme.typography.bodyMedium, color = C.Body, modifier = Modifier.padding(top = 4.dp))
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
            FlowRow(Modifier.padding(top = 6.dp)) {
                if (item.url.isNotBlank()) {
                    TextButton(onClick = { openUrl(context, item.url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (item.source.startsWith("PIB")) "Read on PIB" else "Read full story"
                        )
                    }
                }
                if (onListen != null) {
                    TextButton(onClick = onListen) {
                        Icon(Icons.Filled.Headphones, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Listen from here")
                    }
                }
                if (onDone != null) {
                    TextButton(onClick = onDone) { Text(if (done) "✓ Done" else "Mark done") }
                }
                if (onLinkTopic != null) {
                    if (isUpdate) TextButton(onClick = { linkTo = "" to "" }) { Text("Not this topic") }
                    else if (earlierTopics != null) TextButton(onClick = { picking = true }) { Text("Same topic as…") }
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

/** "1. Biogas And Biomass", where it is in the notes, then its bullets. */
@Composable
fun StaticSectionView(n: Int, sec: StaticSection, onWrong: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(C.AccentSoft).padding(10.dp),
    ) {
        Text("$n. ${sec.topic}", style = MaterialTheme.typography.titleSmall, color = C.Ink, fontWeight = FontWeight.Bold)
        for (b in sec.bullets) Bullet(b)
        Text(
            "Read more > " + sec.where.substringAfter(" · ").ifBlank { sec.src } + if (sec.src == "Rocket Sheets") " (Rocket Sheets)" else "",
            style = MaterialTheme.typography.labelMedium,
            color = C.Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (onWrong != null) {
            Text(
                "Wrong note?",
                style = MaterialTheme.typography.labelMedium,
                color = C.Faint,
                modifier = Modifier.padding(top = 4.dp).clickable(onClick = onWrong).padding(vertical = 4.dp),
            )
        }
    }
}

/** Where a "Wrong note" report goes: a new issue in the feed's repository, read by the next update. */
const val REPORT_REPO = "https://github.com/richie505/groupsca"

fun wrongNoteUrl(item: Item, sec: StaticSection): String {
    val enc = { t: String -> java.net.URLEncoder.encode(t, "UTF-8") }
    val title = "Wrong note: ${sec.topic}".take(120)
    val body = "where: ${sec.where}\nstory: ${item.title}\n\n(Sent from the APPSC Daily CA app. The next update stops linking this note to stories like this one.)"
    return "$REPORT_REPO/issues/new?title=${enc(title)}&body=${enc(body)}"
}

/** A "Topic link" report: a new issue the next update reads ("thread: none" = a topic of its own). */
fun topicLinkUrl(item: Item, thread: String, label: String): String {
    val enc = { t: String -> java.net.URLEncoder.encode(t, "UTF-8") }
    val title = "Topic link: ${item.title}".take(120)
    val body = "story: ${item.id}\nthread: ${thread.ifEmpty { "none" }}\nheadline: ${item.title}\n" +
        (if (thread.isEmpty()) "(Not the same topic as before.)" else "topic: $label") +
        "\n\n(Sent from the APPSC Daily CA app. The next update links this story this way.)"
    return "$REPORT_REPO/issues/new?title=${enc(title)}&body=${enc(body)}"
}

/** A bullet, with a bold "Label:" lead when given (the LENS way: "Coverage: ..."). */
@Composable
fun Bullet(text: String, label: String = "") {
    Row(Modifier.padding(top = 4.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = C.Accent, modifier = Modifier.width(14.dp))
        Text(
            buildAnnotatedString {
                if (label.isNotBlank()) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Ink)) { append("$label: ") }
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = C.Body,
        )
    }
}

/** "{Group-I · II – Economy – C-3} **": exams, book, syllabus unit; ** critical, * high. */
fun topicTag(item: Item): String {
    val exams = when {
        item.exams.containsAll(listOf("G1", "G2")) -> "Group-I · II"
        "G1" in item.exams -> "Group-I"
        else -> "Group-II"
    }
    val unit = item.units.firstOrNull()?.code?.let { " – $it" } ?: ""
    val stars = when (item.band) {
        "critical" -> " **"
        "high" -> " *"
        else -> ""
    }
    return "{$exams – ${bookNumber(item.book)}. ${item.book}$unit}$stars"
}

/** "The Hindu — Andhra Pradesh" → "The Hindu". */
fun shortSource(s: String): String = s.substringBefore(" — ").substringBefore(" - ").trim()

/** How the note was linked, in words. */
fun noteLabel(n: StaticNote): String = when (n.tier) {
    "exact" -> if (n.match.isNotEmpty()) "Exact: " + n.match.joinToString(", ") else "Exact match"
    "unit" -> "Same syllabus unit"
    else -> "Same book"
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
        if (item.summary.isNotBlank()) append("Context: ").append(item.summary).append('\n')
        for (f in item.newFacts) append("• ").append(f.text).append('\n')
        if (item.units.isNotEmpty()) append("Syllabus: ").append(item.units.joinToString { it.code }).append('\n')
        if (item.brief.sections.isNotEmpty()) {
            append("\nStatic notes\n")
            item.brief.sections.forEachIndexed { i, sec ->
                append(i + 1).append(". ").append(sec.topic).append('\n')
                for (b in sec.bullets) append("  • ").append(b).append('\n')
            }
        } else item.notes.firstOrNull { it.src == "Prep notes" }?.let { append("From the notes: ").append(it.text).append('\n') }
        append(item.url)
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
