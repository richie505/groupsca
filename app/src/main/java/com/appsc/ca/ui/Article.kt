package com.appsc.ca.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.ca.data.Item
import com.appsc.ca.data.StaticSection
import com.appsc.ca.data.bookNumber
import com.appsc.ca.data.clockTime
import com.appsc.ca.data.longDate
import com.appsc.ca.data.shortDate
import com.appsc.ca.platform.ReadAloud

/** A colour for each of the 6 books, for tags and cards. */
fun bookColor(book: String): Pair<Color, Color> = when (bookNumber(book)) {
    1 -> Color(0xFF8D4B16) to Color(0xFFFBEEDF)
    2 -> Color(0xFF3949AB) to Color(0xFFE8EAF6)
    3 -> Color(0xFF0F5132) to Color(0xFFE3F1E9)
    4 -> Color(0xFF00695C) to Color(0xFFE0F2F1)
    5 -> Color(0xFF6D28D9) to Color(0xFFF3EEFF)
    else -> Color(0xFF5A6B88) to Color(0xFFF1F3F6)
}

/** "Economy" as a small coloured tag. */
@Composable
fun SubjectTag(book: String, modifier: Modifier = Modifier) {
    val (fg, bg) = bookColor(book)
    Text(
        book,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Minutes to read one story: context, facts and its static notes at 200 words a minute. */
fun minutesOf(i: Item): Int {
    val words = (i.summary + " " + i.newFacts.joinToString(" ") { it.text } + " " +
        i.brief.sections.joinToString(" ") { s -> s.bullets.joinToString(" ") }).split(Regex("\\s+")).size
    return maxOf(1, (words + 199) / 200)
}

/** [text] with the story's syllabus topics and blueprint angles in bold, the way CA notes highlight key terms. */
private fun highlighted(text: String, item: Item) = buildAnnotatedString {
    val terms = (item.topics + item.angles).map { it.trim() }.filter { it.length >= 4 }.distinct().sortedByDescending { it.length }
    var at = 0
    val lower = text.lowercase()
    while (at < text.length) {
        val hit = terms.mapNotNull { t -> lower.indexOf(t.lowercase(), at).takeIf { it >= 0 }?.let { it to t } }.minByOrNull { it.first }
        if (hit == null) {
            append(text.substring(at))
            break
        }
        append(text.substring(at, hit.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Ink)) { append(text.substring(hit.first, hit.first + hit.second.length)) }
        at = hit.first + hit.second.length
    }
}

/**
 * One story as a page: subject, title, posted date and reading time; the context; the current matter; the
 * static notes from the Rocket Sheets; the topic's timeline; tags; sources; Previous / Next through [ids], and a
 * table of contents of them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleScreen(vm: AppViewModel, ids: List<String>, start: Int, onBack: () -> Unit) {
    var at by rememberSaveable { mutableIntStateOf(start.coerceIn(0, (ids.size - 1).coerceAtLeast(0))) }
    var toc by remember { mutableStateOf(false) }
    val list = remember(ids, vm.days) { ids.mapNotNull { vm.itemById(it) } }
    val raw = list.getOrNull(at) ?: run {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("This story is no longer on the phone.", modifier = Modifier.padding(24.dp), color = C.Muted)
        }
        return
    }
    val item = vm.visible(raw)
    val context = LocalContext.current
    val scroll = rememberScrollState()
    LaunchedEffect(item.id) {
        scroll.scrollTo(0)
        vm.markRead(item.id)
    }
    val pb by ReadAloud.playback
    val saved = vm.isSaved(item.id)
    var sources by rememberSaveable(item.id) { mutableStateOf(false) }
    var wrong by remember { mutableStateOf<StaticSection?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (Listening.canRead(item)) {
                    IconButton(onClick = { if (pb.active) ReadAloud.stop() else Listening.play(list.map(vm::visible), at, vm.speechRate) }) {
                        Icon(if (pb.active) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp, "Listen")
                    }
                }
                IconButton(onClick = { vm.toggleSave(raw) }) {
                    Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Save", tint = if (saved) C.Accent else C.Muted)
                }
                IconButton(onClick = { share(context, item) }) { Icon(Icons.Filled.Share, "Share") }
            }
            HorizontalDivider(color = C.Line)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubjectTag(item.book)
                    if (item.ap) Text("Andhra Pradesh", style = MaterialTheme.typography.labelMedium, color = C.Ap,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.ApSoft).padding(horizontal = 8.dp, vertical = 3.dp))
                    if (vm.isUpdate(item)) Text("Update", style = MaterialTheme.typography.labelMedium, color = C.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.MediumSoft).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(item.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 29.sp), color = C.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 10.dp))
                Row {
                    Text(
                        "Posted ${shortDate(item.pubDate.ifBlank { item.date })}" + (if (item.time.isNotBlank()) ", ${clockTime(item.time)}" else ""),
                        style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.weight(1f),
                    )
                    Text("${minutesOf(item)} min read", style = MaterialTheme.typography.labelMedium, color = C.Faint)
                }
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(vertical = 10.dp))

                if (item.summary.isNotBlank()) {
                    Text(highlighted(item.summary, item), style = MaterialTheme.typography.bodyLarge, color = C.Body)
                }
                val rocket = item.rocket
                if (rocket != null && rocket.notes.isNotBlank()) {
                    if (rocket.blueprint.isNotEmpty()) {
                        Text(rocket.blueprint.joinToString("  ·  ") { "🎯 $it" }, style = MaterialTheme.typography.labelMedium, color = C.High,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    SectionTitle("Notes")
                    NotesView(rocket.notes)
                } else if (item.newFacts.isNotEmpty()) {
                    SectionTitle("In the news")
                    for (f in item.newFacts) Bullet(f.text, label = f.angle)
                }
                if (item.brief.sections.isNotEmpty()) {
                    for (sec in item.brief.sections) {
                        SectionTitle("About ${sec.topic.substringAfter(" · ")}")
                        for (b in sec.bullets) Bullet(b)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Rocket Sheets · " + sec.where.substringAfter(" · ").substringBefore(" › #"), style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.weight(1f))
                            Text("Wrong note?", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.clickable { wrong = sec }.padding(4.dp))
                        }
                    }
                }
                val timeline = vm.timeline(raw)
                if (timeline.size > 1) {
                    SectionTitle("How the story moved")
                    for (t in timeline) {
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(shortDate(t.date), style = MaterialTheme.typography.labelMedium, color = if (t.id == item.id) C.Accent else C.Muted, modifier = Modifier.width(76.dp))
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, color = if (t.id == item.id) C.Ink else C.Body, fontWeight = if (t.id == item.id) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                // tags: syllabus units and blueprint angles
                val tags = item.units.map { it.code } + item.angles
                if (tags.isNotEmpty()) {
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.Top) {
                        Text("Tags:", style = MaterialTheme.typography.bodyMedium, color = C.Muted, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (t in tags.distinct().take(8)) Text(t, style = MaterialTheme.typography.labelMedium, color = C.Muted,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(C.Chip).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
                // sources
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, C.Line, RoundedCornerShape(8.dp))
                        .clickable { sources = !sources }.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Article sources", style = MaterialTheme.typography.titleSmall, color = C.Accent, modifier = Modifier.weight(1f))
                        Icon(if (sources) Icons.Filled.Remove else Icons.Filled.Add, null, tint = C.Accent)
                    }
                    if (sources) {
                        Text(item.source, style = MaterialTheme.typography.bodyMedium, color = C.Body, modifier = Modifier.padding(top = 8.dp)
                            .clickable(enabled = item.url.isNotBlank()) { openUrl(context, item.url) })
                        for (r in item.related) Text("${r.source}: ${r.title}", style = MaterialTheme.typography.labelMedium, color = C.Muted,
                            modifier = Modifier.padding(top = 6.dp).clickable(enabled = r.url.isNotBlank()) { openUrl(context, r.url) })
                        for (a in item.alsoIn) Text("Also in $a", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(top = 4.dp))
                        if (item.url.isNotBlank()) TextButton(onClick = { openUrl(context, item.url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Read the full story")
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { vm.toggleDone(raw) }) { Text(if (vm.isDone(raw)) "✓ Done" else "Mark topic done") }
                    if (vm.isUpdate(raw)) TextButton(onClick = { vm.linkTopic(raw, "") }) { Text("Not this topic") }
                }
                // previous / next
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    val prev = list.getOrNull(at - 1)
                    val next = list.getOrNull(at + 1)
                    Column(Modifier.weight(1f).clickable(enabled = prev != null) { at -= 1 }) {
                        if (prev != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = C.Accent)
                                Text("Previous", style = MaterialTheme.typography.titleSmall, color = C.Accent)
                            }
                            Text(prev.title, style = MaterialTheme.typography.labelMedium, color = C.Faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(enabled = next != null) { at += 1 }, horizontalAlignment = Alignment.End) {
                        if (next != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Next article", style = MaterialTheme.typography.titleSmall, color = C.Accent)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Accent)
                            }
                            Text(next.title, style = MaterialTheme.typography.labelMedium, color = C.Faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
        if (list.size > 1) {
            FloatingActionButton(
                onClick = { toc = true },
                containerColor = C.AccentSoft,
                contentColor = C.Accent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            ) { Icon(Icons.AutoMirrored.Filled.List, "Table of contents") }
        }
    }

    if (toc) {
        ModalBottomSheet(onDismissRequest = { toc = false }) {
            Text("Table of contents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                list.forEachIndexed { n, s ->
                    HorizontalDivider(color = C.Line)
                    Text(
                        "${n + 1}.  ${s.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (n == at) C.Accent else C.Ink,
                        modifier = Modifier.fillMaxWidth().clickable { at = n; toc = false }.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    wrong?.let { sec ->
        AlertDialog(
            onDismissRequest = { wrong = null },
            title = { Text("Wrong note?") },
            text = { Text("\"${sec.topic}\" does not fit this story. Hide it here, or also report it so the next update stops linking it to stories like this one.") },
            confirmButton = { TextButton(onClick = { openUrl(context, wrongNoteUrl(item, sec)); vm.hideNote(raw, sec); wrong = null }) { Text("Hide and report") } },
            dismissButton = { TextButton(onClick = { vm.hideNote(raw, sec); wrong = null }) { Text("Hide on this phone") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = C.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
}

/** A story in a list, the way CA apps list the day's articles: subject tag, title, date. */
@Composable
fun ArticleRow(item: Item, read: Boolean, done: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, C.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectTag(item.book)
            if (item.ap) {
                Spacer(Modifier.width(6.dp))
                Text("AP", style = MaterialTheme.typography.labelMedium, color = C.Ap,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.ApSoft).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!read) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Unread))
        }
        Text(
            (if (done) "✓ " else "") + item.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp),
            color = if (done) C.Muted else C.Accent,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${longDate(item.date)} · ${minutesOf(item)} min" + (if (item.brief.sections.isNotEmpty()) " · 📘 ${item.brief.sections.size} notes" else ""),
            style = MaterialTheme.typography.labelMedium,
            color = C.Faint,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
