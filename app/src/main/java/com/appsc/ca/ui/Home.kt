package com.appsc.ca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.ca.data.BOOKS
import com.appsc.ca.data.Filter
import com.appsc.ca.data.Item
import com.appsc.ca.data.bookNumber
import com.appsc.ca.data.longDate
import com.appsc.ca.data.shortDate

/** The day's lists as the reader set them up: new topics (Settings' count), updates and one-liners. */
data class DayLists(val date: String, val newTopics: List<Item>, val updates: List<Item>, val oneLiners: List<Item>)

fun dayLists(vm: AppViewModel, date: String?, book: String? = null, apOnly: Boolean = false): DayLists? {
    val day = vm.days.find { it.date == date } ?: vm.days.firstOrNull() ?: return null
    val all = Filter(exam = vm.exam, subject = book).apply(vm.shown(day.items)).filter { !apOnly || it.ap }
    val hasTop = all.any { it.top } || day.items.any { it.top }
    val marked = if (!hasTop) all.filter { !it.oneLiner } else all.filter { (it.top && !vm.isUpdate(it)) || (it.update && !vm.isUpdate(it) && !it.oneLiner) }
    val n = vm.perDay
    val newTopics = (if (marked.size >= n) marked.sortedByDescending { it.score }.take(n)
    else marked + all.filter { it !in marked && !vm.isUpdate(it) && !it.oneLiner && !it.digest }.sortedByDescending { it.score }.take(n - marked.size))
        .sortedWith(compareBy<Item> { vm.isDone(it) }.thenByDescending { it.score })
    return DayLists(day.date, newTopics, all.filter { vm.isUpdate(it) && !it.oneLiner }, all.filter { it.oneLiner })
}

@Composable
private fun TitleBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack == null) 18.dp else 4.dp, end = 6.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = C.Ink, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = C.Muted)
        }
        actions()
    }
}

@Composable
private fun Heading(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = C.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = C.Accent, fontWeight = FontWeight.SemiBold) }
    }
}

/** A big card for the day's highlight stories, in the subject's colour. */
@Composable
private fun HighlightCard(item: Item, onClick: () -> Unit) {
    val (fg, bg) = bookColor(item.book)
    Column(
        Modifier.width(290.dp).height(230.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(16.dp),
    ) {
        Row {
            Text("News Today", style = MaterialTheme.typography.labelMedium, color = fg,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.White).padding(horizontal = 8.dp, vertical = 3.dp))
            Spacer(Modifier.width(6.dp))
            Text("${bookNumber(item.book)}. ${item.book}", style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Text(item.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp), color = C.Ink, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = C.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(shortDate(item.date), style = MaterialTheme.typography.labelMedium, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = C.Ink, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = C.Muted, modifier = Modifier.padding(top = 4.dp))
        Text(button, style = MaterialTheme.typography.titleSmall, color = C.Accent, modifier = Modifier.padding(top = 8.dp))
    }
}

/** For you: the day's highlights, what to read today, revision and the week's digest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onArticle: (List<String>, Int) -> Unit,
    onNews: () -> Unit,
    onPractice: () -> Unit,
    onDigest: (Period) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSaved: () -> Unit,
) {
    val lists = dayLists(vm, vm.days.firstOrNull()?.date)
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                TitleBar("Current Affairs", "APPSC Group-I · Group-II · exam news only") {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                    IconButton(onClick = onSaved) { Icon(Icons.Filled.Bookmark, "Saved") }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            }
            vm.message?.let { m -> item { Text(m, color = C.Critical, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 18.dp)) } }
            if (lists == null) {
                item { Text(if (vm.refreshing) "Downloading today's current affairs…" else "Pull down to download today's current affairs.", color = C.Muted, modifier = Modifier.padding(24.dp)) }
                return@LazyColumn
            }
            val ids = lists.newTopics.map { it.id }
            item { Heading("Highlights", "View all", onNews) }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lists.newTopics.take(6), key = { it.id }) { i -> HighlightCard(i) { onArticle(ids, ids.indexOf(i.id)) } }
                }
            }
            item {
                val left = lists.newTopics.filter { !vm.isDone(it) }
                InfoCard(
                    "Today · ${longDate(lists.date)}",
                    "≈ ${readingMinutes(left, lists.updates.size + lists.oneLiners.size)} min: ${left.size} new topics to read, " +
                        "${lists.updates.size} updates on earlier topics, ${lists.oneLiners.size} one-liners.",
                    "Start reading →",
                ) { if (ids.isNotEmpty()) onArticle(ids, 0) }
            }
            val due = vm.dueCards.size
            item {
                InfoCard(
                    "Revise",
                    if (due > 0) "$due questions due today from your earlier quizzes." else "Nothing due. Take today's quiz: ${vm.days.firstOrNull()?.quiz?.size ?: 0} questions.",
                    if (due > 0) "Revise now →" else "Take the quiz →",
                    onPractice,
                )
            }
            val week = periodsOf(vm.days.map { it.date }, "week", vm.index?.digests.orEmpty()).firstOrNull()
            if (week != null) item { InfoCard("Weekly focus", week.title + ": every topic of the week once, by subject, with its notes and quiz.", "Open the digest →") { onDigest(week) } }
            if (lists.updates.isNotEmpty()) {
                item { Heading("Updates on earlier topics") }
                val uids = lists.updates.map { it.id }
                items(lists.updates.take(5), key = { "u-" + it.id }) { u ->
                    Column(Modifier.fillMaxWidth().clickable { onArticle(uids, uids.indexOf(u.id)) }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text("↻ " + u.title, style = MaterialTheme.typography.bodyMedium, color = C.Body)
                        if (u.threadStart.isNotBlank()) Text("Topic since ${shortDate(u.threadStart)}", style = MaterialTheme.typography.labelMedium, color = C.Faint)
                    }
                }
            }
            if (lists.oneLiners.isNotEmpty()) {
                item { Heading("One-liners") }
                items(lists.oneLiners.take(8), key = { "o-" + it.id }) { OneLinerRow(it) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private val SHORT = mapOf(
    "History & Culture" to "History & Culture",
    "Polity, Society & IR" to "Polity & IR",
    "Economy" to "Economy",
    "Geography" to "Geography",
    "Science, Tech & Environment" to "Sci-Tech & Env",
    "Current Affairs" to "General",
)

/** News: News Today (6 subjects, any day), Weekly focus and Monthly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(vm: AppViewModel, onArticle: (List<String>, Int) -> Unit, onDigest: (Period) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var book by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    var apOnly by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TitleBar("Current Affairs")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("NEWS TODAY", "WEEKLY FOCUS", "MONTHLY").forEachIndexed { n, t ->
                if (n > 0) Text("|", color = C.Line, modifier = Modifier.padding(horizontal = 12.dp))
                Text(t, style = MaterialTheme.typography.titleMedium, color = if (tab == n) C.Ink else C.Faint, fontWeight = if (tab == n) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.clickable { tab = n })
            }
        }
        when (tab) {
            0 -> {
                val lists = dayLists(vm, date, book, apOnly)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("All", book == null && !apOnly) { book = null; apOnly = false }
                        Pill("Andhra Pradesh", apOnly) { apOnly = !apOnly }
                        for (b in BOOKS) Pill(SHORT[b] ?: b, book == b) { book = if (book == b) null else b }
                    }
                    IconButton(onClick = { picking = true }) { Icon(Icons.Filled.CalendarMonth, "Pick a day") }
                }
                if (lists == null) {
                    Text("Pull down on Home to download.", color = C.Muted, modifier = Modifier.padding(24.dp))
                    return@Column
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Text(longDate(lists.date) + " · ${lists.newTopics.size} new topics", style = MaterialTheme.typography.labelMedium, color = C.Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) }
                    val ids = lists.newTopics.map { it.id }
                    if (lists.newTopics.isEmpty()) item { Text("Nothing under this subject on this day.", color = C.Muted, modifier = Modifier.padding(18.dp)) }
                    items(lists.newTopics, key = { it.id }) { i -> ArticleRow(i, i.id in vm.readIds, vm.isDone(i)) { onArticle(ids, ids.indexOf(i.id)) } }
                    if (lists.updates.isNotEmpty()) {
                        item { Text("UPDATES ON EARLIER TOPICS", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp)) }
                        val uids = lists.updates.map { it.id }
                        items(lists.updates, key = { "u-" + it.id }) { u ->
                            Column(Modifier.fillMaxWidth().clickable { onArticle(uids, uids.indexOf(u.id)) }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                                Text("↻ " + u.title, style = MaterialTheme.typography.bodyMedium, color = C.Body)
                                if (u.threadStart.isNotBlank()) Text("Topic since ${shortDate(u.threadStart)}: ${u.threadTitle}", style = MaterialTheme.typography.labelMedium, color = C.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (lists.oneLiners.isNotEmpty()) {
                        item { Text("ONE-LINERS", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp)) }
                        items(lists.oneLiners, key = { "o-" + it.id }) { OneLinerRow(it) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            else -> {
                val kind = if (tab == 1) "week" else "month"
                val periods = periodsOf(vm.days.map { it.date }, kind, vm.index?.digests.orEmpty())
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            if (kind == "week") "Each week's topics once, by subject, with their notes, one-liners and quiz." else "Each month's topics once, by subject: for revision before the exam.",
                            style = MaterialTheme.typography.bodyMedium, color = C.Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                    if (periods.isEmpty()) item { Text("Nothing yet.", color = C.Muted, modifier = Modifier.padding(18.dp)) }
                    items(periods, key = { it.kind + it.start }) { p ->
                        val ref = vm.index?.digests?.firstOrNull { it.kind == p.kind && it.start == p.start.toString() }
                        InfoCard(p.title, ref?.let { "${it.main} main topics · ${it.topics} in all" } ?: "From the days on this phone", "Open →") { onDigest(p) }
                    }
                }
            }
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Pick a day") },
            text = {
                LazyColumn {
                    items(vm.days, key = { it.date }) { d ->
                        Text(
                            "${longDate(d.date)} (${d.items.count { it.top }} new topics)",
                            modifier = Modifier.fillMaxWidth().clickable { date = d.date; picking = false }.padding(vertical = 10.dp),
                            color = if (d.date == (date ?: vm.days.firstOrNull()?.date)) C.Accent else C.Ink,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (on) androidx.compose.ui.graphics.Color.White else C.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) C.Ink else androidx.compose.ui.graphics.Color.White)
            .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Trending: exam topics among Google's trending searches in India, and the week's most covered topics. */
@Composable
fun TrendingScreen(vm: AppViewModel, onArticle: (List<String>, Int) -> Unit) {
    val t = vm.trending
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item { TitleBar("Trending", "What India is searching for, kept only when it matters for your exam") }
        if (t == null) {
            item { Text("Pull down on Home to download.", color = C.Muted, modifier = Modifier.padding(24.dp)) }
            return@LazyColumn
        }
        item { Heading("On Google this week") }
        if (t.google.isEmpty()) item { Text("No exam topic among today's trending searches yet.", color = C.Muted, modifier = Modifier.padding(horizontal = 18.dp)) }
        items(t.google, key = { "g-" + it.date + it.query }) { g ->
            var open by rememberSaveable(g.date + g.query) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, C.Line, RoundedCornerShape(14.dp)).clickable { open = !open }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectTag(g.subject.ifBlank { "Current Affairs" })
                    Spacer(Modifier.weight(1f))
                    Text("🔥 ${g.traffic} searches · ${shortDate(g.date)}", style = MaterialTheme.typography.labelMedium, color = C.High)
                }
                Text(g.query.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, color = C.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                for (n in g.news.take(if (open) 3 else 1)) {
                    Text("${n.source}: ${n.title}", style = MaterialTheme.typography.bodyMedium, color = C.Body,
                        modifier = Modifier.padding(top = 4.dp).clickable(enabled = n.url.isNotBlank()) { openUrl(context, n.url) })
                }
                if (open) {
                    for (s in g.sections) {
                        Text("About ${s.topic}", style = MaterialTheme.typography.titleSmall, color = C.Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        for (b in s.bullets) Bullet(b)
                    }
                }
                val related = g.related.mapNotNull { r -> vm.itemById(r.id) }
                if (related.isNotEmpty()) {
                    val rids = related.map { it.id }
                    Text("In your current affairs:", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(top = 8.dp))
                    for (r in related) Text("→ ${r.title}", style = MaterialTheme.typography.bodyMedium, color = C.Accent,
                        modifier = Modifier.padding(top = 3.dp).clickable { onArticle(rids, rids.indexOf(r.id)) })
                } else if (!open && g.sections.isNotEmpty()) {
                    Text("📘 " + g.sections.joinToString(" · ") { it.topic }, style = MaterialTheme.typography.labelMedium, color = C.Accent, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Heading("Most covered this week") }
        if (t.topics.isEmpty()) item { Text("Nothing yet.", color = C.Muted, modifier = Modifier.padding(horizontal = 18.dp)) }
        items(t.topics, key = { "t-" + it.thread }) { tp ->
            Row(
                Modifier.fillMaxWidth().clickable { if (vm.itemById(tp.latestId) != null) onArticle(listOf(tp.latestId), 0) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectTag(tp.subject.ifBlank { "Current Affairs" })
                        if (tp.ap) Text("  AP", style = MaterialTheme.typography.labelMedium, color = C.Ap)
                    }
                    Text(tp.title, style = MaterialTheme.typography.titleMedium, color = C.Ink, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (tp.latest != tp.title) Text("Latest: ${tp.latest}", style = MaterialTheme.typography.labelMedium, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${tp.days} days\n${tp.reports} reports", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(color = C.Line, modifier = Modifier.padding(horizontal = 18.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Search across every story on the phone. */
@Composable
fun SearchScreen(vm: AppViewModel, onArticle: (List<String>, Int) -> Unit, onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = vm.search(q)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text("Search: Polavaram, repo rate, tiger reserve…") }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (q.isNotBlank() && results.isEmpty()) item { Text("No story on the phone matches.", color = C.Muted, modifier = Modifier.padding(24.dp)) }
            val ids = results.map { it.id }
            items(results, key = { it.id }) { i -> ArticleRow(i, i.id in vm.readIds, vm.isDone(i)) { onArticle(ids, ids.indexOf(i.id)) } }
        }
    }
}
