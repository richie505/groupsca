package com.appsc.ca.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.ca.data.BRIEF_FILTERS
import com.appsc.ca.data.BriefItem
import com.appsc.ca.data.Briefing
import com.appsc.ca.data.Mcq
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val bigDate = DateTimeFormatter.ofPattern("d MMMM yyyy")
private fun fmt(iso: String) = runCatching { LocalDate.parse(iso).format(bigDate).uppercase() }.getOrDefault(iso)

private enum class BPage { BRIEF, AUDIT, MCQ, MAINS, MARKS }

private val MARK_KINDS = listOf("Revise Today", "Important", "AP", "Prelims", "Mains")

/** The daily APPSC briefing (AI, from the server): sections, subject filters, items; audit, MCQs, Mains; archive. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefingScreen(vm: AppViewModel) {
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf(BPage.BRIEF) }
    var picking by remember { mutableStateOf(false) }
    // the 9 PM notification opens its day
    LaunchedEffect(vm.openBriefing) {
        vm.openBriefing?.let { date = it; page = BPage.BRIEF; vm.openBriefing = null }
    }
    val b = vm.briefings.firstOrNull { it.date == date } ?: vm.briefings.firstOrNull()
    if (b == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("APPSC Daily Briefing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "The daily International, National and Andhra Pradesh briefing appears here at about 9 PM IST, " +
                    "once it has been researched and checked. Pull down on Home to download.",
                color = C.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }
    when (page) {
        BPage.AUDIT -> return AuditPage(b) { page = BPage.BRIEF }
        BPage.MCQ -> return McqPage(vm, b) { page = BPage.BRIEF }
        BPage.MAINS -> return MainsPage(b) { page = BPage.BRIEF }
        BPage.MARKS -> return MarksPage(vm) { page = BPage.BRIEF }
        BPage.BRIEF -> {}
    }
    val sections = listOf(
        "ALL" to b.allItems,
        "INTERNATIONAL" to b.sections.international,
        "NATIONAL" to b.sections.national,
        "ANDHRA PRADESH" to b.sections.andhraPradesh,
    )
    val wanted = BRIEF_FILTERS.firstOrNull { it.first == filter }?.second
    val shown = sections[section].second.filter { wanted == null || it.subjects.any { s -> s in wanted } }
        .sortedBy { it.importance }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("APPSC GROUP 1 & GROUP 2", style = MaterialTheme.typography.labelMedium, color = C.Accent, fontWeight = FontWeight.Bold)
                    Text("DAILY CURRENT AFFAIRS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.Ink)
                    Text(fmt(b.date), style = MaterialTheme.typography.titleMedium, color = C.Muted)
                }
                IconButton(onClick = { page = BPage.MARKS }) { Icon(Icons.Filled.Bookmarks, "Bookmarks") }
                IconButton(onClick = { picking = true }) { Icon(Icons.Filled.CalendarMonth, "Archive") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick("📚 Coverage audit") { page = BPage.AUDIT }
                if (vm.showQuiz) Quick("❓ MCQs (${b.mcqs.size})") { page = BPage.MCQ }
                if (vm.showMains) Quick("📝 Mains (${b.mainsQuestions.size})") { page = BPage.MAINS }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                sections.forEachIndexed { n, (label, list) ->
                    Column(Modifier.clickable { section = n }) {
                        Text("$label (${list.size})", style = MaterialTheme.typography.titleSmall, color = if (section == n) C.Ink else C.Faint, fontWeight = if (section == n) FontWeight.Bold else FontWeight.Medium)
                        if (section == n) Spacer(Modifier.height(3.dp).width(40.dp).background(C.Accent))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("All subjects", filter == null) { filter = null }
                for ((f, _) in BRIEF_FILTERS) FilterPill(f, filter == f) { filter = if (filter == f) null else f }
            }
        }
        if (shown.isEmpty()) item { Text("No significant verified current-affairs item today.", color = C.Muted, modifier = Modifier.padding(18.dp)) }
        items(shown, key = { it.section + it.headline }) { BriefCard(vm, b.date, it) }
        item {
            Text(
                "Researched and checked on the server by ${b.model.ifBlank { "AI" }} with live web search · ${b.sourceCount} sources. " +
                    "Open the sources to confirm before relying on a figure.",
                style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(18.dp),
            )
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Archive") },
            text = {
                LazyColumn {
                    items(vm.briefings, key = { it.date }) { d ->
                        val label = when (d.date) {
                            LocalDate.now().toString() -> "Today · "
                            LocalDate.now().minusDays(1).toString() -> "Yesterday · "
                            else -> ""
                        } + fmt(d.date)
                        Text(
                            "$label  (${d.allItems.size} items, AP ${d.sections.andhraPradesh.size})",
                            color = if (d.date == b.date) C.Accent else C.Ink,
                            modifier = Modifier.fillMaxWidth().clickable { date = d.date; picking = false }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Quick(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = C.Accent,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.AccentSoft).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))
}

@Composable
private fun FilterPill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (on) Color.White else C.Muted,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.Ink else Color.White)
            .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

private fun statusColors(s: String): Pair<Color, Color> = when (s) {
    "COMPLETED", "OPERATIONAL", "FINAL_RESULT", "LAUNCHED" -> Color(0xFF15803D) to Color(0xFFE7F6EC)
    "APPROVED", "MOU_SIGNED", "FOUNDATION_LAID", "UNDER_CONSTRUCTION", "ONGOING" -> Color(0xFF1D4ED8) to Color(0xFFE8EEFC)
    else -> Color(0xFFB45309) to Color(0xFFFFF4E0) // ANNOUNCED, PROPOSED, FORECAST
}

/** One briefing item: headline, chips, paper mapping, status, text, Prelims, Mains, AP link, sources. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefCard(vm: AppViewModel, date: String, it: BriefItem) {
    val context = LocalContext.current
    var open by rememberSaveable(it.headline) { mutableStateOf(false) }
    var marking by remember { mutableStateOf(false) }
    val mark = vm.briefMarks["$date|${it.headline}"]
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (it.importance <= 1) Color(0xFFF5C2A0) else C.Line, RoundedCornerShape(14.dp))
            .clickable { open = !open }.padding(14.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip(it.section.ifBlank { "NEWS" }, C.Muted, C.Chip)
            if (it.category.isNotBlank()) Chip(it.category, C.Fact, C.FactSoft)
            if (it.verifiedFirst) Chip("⭐ FIRST", Color(0xFF9A6700), Color(0xFFFFF4C2))
            if (it.isUpdate) Chip("Update", C.Medium, C.MediumSoft)
            if (it.status.isNotBlank()) statusColors(it.status).let { (f, bg) -> Chip(it.status.replace('_', ' '), f, bg) }
        }
        Text((if (it.importance <= 1) "🔥 " else "") + it.headline, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp),
            color = C.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        if (it.subjects.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (s in it.subjects) Chip(s, C.Accent, C.AccentSoft)
            }
        }
        val map = listOfNotNull(
            it.group1.takeIf { g -> g.isNotEmpty() }?.let { g -> "Group 1: " + g.joinToString(", ") },
            it.group2.takeIf { g -> g.isNotEmpty() }?.let { g -> "Group 2: " + g.joinToString(", ") },
        ).joinToString(" · ")
        if (map.isNotBlank()) Text(map, style = MaterialTheme.typography.labelMedium, color = C.Muted, modifier = Modifier.padding(top = 4.dp))
        if (it.summary.isNotBlank()) Text(it.summary, style = MaterialTheme.typography.bodyMedium, color = C.Body, maxLines = if (open) Int.MAX_VALUE else 3, modifier = Modifier.padding(top = 8.dp))
        if (open) {
            if (it.facts.isNotEmpty()) {
                Label("Important facts")
                for (f in it.facts) Bullet(f)
            }
            if (it.prelimsTakeaway.isNotBlank()) Block("🎯 Prelims facts", it.prelimsTakeaway)
            if (vm.showMains && it.mainsAngle.isNotBlank()) Block("📝 Mains angle", it.mainsAngle)
            if (vm.showApLink && it.apLink.isNotBlank()) Block("📍 AP link", it.apLink)
            if (it.staticLink.isNotBlank()) Block("📚 Static link", it.staticLink)
            if (it.sources.isNotEmpty()) {
                Label("Sources")
                for (s in it.sources) {
                    Text(
                        "[" + listOf(s.publisher, s.title).filter { t -> t.isNotBlank() }.joinToString(" — ") + "]" + if (s.isPrimary) "  ✓ primary" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = C.Medium,
                        modifier = Modifier.padding(top = 4.dp).clickable { openUrl(context, s.url) },
                    )
                }
            }
            Row(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { marking = true }) {
                    Icon(if (mark != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null)
                    Spacer(Modifier.width(4.dp))
                    Text(mark ?: "Bookmark")
                }
                IconButton(onClick = { copyText(context, briefText(date, it)) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                IconButton(onClick = { shareText(context, briefText(date, it)) }) { Icon(Icons.Filled.Share, "Share") }
            }
        } else if (it.sources.isNotEmpty()) {
            Text("Tap for Prelims facts, Mains angle, AP link and ${it.sources.size} sources", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (marking) {
        AlertDialog(
            onDismissRequest = { marking = false },
            title = { Text("Bookmark as") },
            text = {
                Column {
                    for (k in MARK_KINDS) Text(k, color = if (k == mark) C.Accent else C.Ink,
                        modifier = Modifier.fillMaxWidth().clickable { vm.markBrief(date, it, k); marking = false }.padding(vertical = 10.dp))
                }
            },
            confirmButton = { if (mark != null) TextButton(onClick = { vm.markBrief(date, it, null); marking = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { marking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Block(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(C.Surface).padding(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = C.Ink, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = C.Body, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun briefText(date: String, it: BriefItem) = buildString {
    append(it.headline).append(" (").append(date).append(")\n")
    if (it.summary.isNotBlank()) append(it.summary).append('\n')
    for (f in it.facts) append("• ").append(f).append('\n')
    if (it.prelimsTakeaway.isNotBlank()) append("Prelims: ").append(it.prelimsTakeaway).append('\n')
    if (it.mainsAngle.isNotBlank()) append("Mains: ").append(it.mainsAngle).append('\n')
    if (it.apLink.isNotBlank()) append("AP link: ").append(it.apLink).append('\n')
    for (s in it.sources) append(s.publisher).append(": ").append(s.url).append('\n')
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("APPSC current affairs", text))
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PageTop(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = C.Muted)
        }
    }
}

/** Subject-wise audit: which subjects today's briefing covers. */
@Composable
private fun AuditPage(b: Briefing, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop("Subject coverage", fmt(b.date), onBack) }
        items(b.coverageAudit, key = { it.subject }) { r ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Row {
                    Text(r.subject, style = MaterialTheme.typography.bodyLarge, color = C.Ink, modifier = Modifier.weight(1f))
                    Text(if (r.covered) "✅" else "⚪")
                }
                val note = if (r.covered) r.items.joinToString(", ") { n -> b.allItems.getOrNull(n - 1)?.headline ?: "#$n" } else r.note.ifBlank { "No significant verified current-affairs item today." }
                Text(note, style = MaterialTheme.typography.labelMedium, color = C.Muted)
            }
            HorizontalDivider(color = C.Line, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

/** The day's MCQs: tap A-D; right in green, wrong with the right answer and a short explanation. */
@Composable
private fun McqPage(vm: AppViewModel, b: Briefing, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop("Daily MCQs", "${fmt(b.date)} · ${b.mcqs.size} questions", onBack) }
        itemsIndexed(b.mcqs, key = { n, _ -> "m$n" }) { n, q -> McqCard(n + 1, q, "${b.date}#$n") }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun McqCard(n: Int, q: Mcq, key: String) {
    var chosen by rememberSaveable(key) { mutableIntStateOf(-1) }
    val right = "ABCD".indexOf(q.answer)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text("Q$n. ${q.question}", style = MaterialTheme.typography.bodyLarge, color = C.Ink)
        q.options.forEachIndexed { i, o ->
            val bg = when {
                chosen < 0 -> C.Surface
                i == right -> Color(0xFFDCF3E3)
                i == chosen -> Color(0xFFFBE0E0)
                else -> C.Surface
            }
            Text("${'A' + i}.  $o", style = MaterialTheme.typography.bodyMedium, color = C.Body,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                    .clickable(enabled = chosen < 0) { chosen = i }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        if (chosen >= 0) {
            Text(if (chosen == right) "Correct ✓" else "Incorrect · Correct answer: ${q.answer}", style = MaterialTheme.typography.titleSmall,
                color = if (chosen == right) Color(0xFF15803D) else C.Critical, modifier = Modifier.padding(top = 6.dp))
            if (q.explanation.isNotBlank()) Text(q.explanation, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
    }
}

/** Today's Group 1 Mains practice: question, paper, subject and answer dimensions (no model answers). */
@Composable
private fun MainsPage(b: Briefing, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop("Today's Group 1 Mains practice", fmt(b.date), onBack) }
        itemsIndexed(b.mainsQuestions, key = { n, _ -> "q$n" }) { n, q ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text("Q${n + 1}. ${q.question}", style = MaterialTheme.typography.bodyLarge, color = C.Ink, fontWeight = FontWeight.SemiBold)
                Text(listOf(q.paper, q.subject).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = C.Accent, modifier = Modifier.padding(top = 4.dp))
                if (q.dimensions.isNotEmpty()) {
                    Label("Suggested dimensions")
                    for (d in q.dimensions) Bullet(d)
                }
            }
        }
    }
}

/** Bookmarked briefing items, by category. */
@Composable
private fun MarksPage(vm: AppViewModel, onBack: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val marked = vm.briefings.flatMap { b -> b.allItems.mapNotNull { i -> vm.briefMarks["${b.date}|${i.headline}"]?.let { k -> Triple(b.date, i, k) } } }
        .filter { kind == null || it.third == kind }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTop("Bookmarks", "${marked.size} items", onBack) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("All", kind == null) { kind = null }
                for (k in MARK_KINDS) FilterPill(k, kind == k) { kind = if (kind == k) null else k }
            }
        }
        if (marked.isEmpty()) item { Text("Nothing bookmarked here yet.", color = C.Muted, modifier = Modifier.padding(18.dp)) }
        items(marked, key = { it.first + it.second.headline }) { (d, i, _) -> BriefCard(vm, d, i) }
    }
}
