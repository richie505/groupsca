package com.appsc.ca.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appsc.ca.BuildConfig
import com.appsc.ca.data.BOOKS
import com.appsc.ca.platform.ReadAloud
import com.appsc.ca.data.Exam
import com.appsc.ca.data.bookNumber
import com.appsc.ca.data.clockTime
import com.appsc.ca.data.istClock
import com.appsc.ca.data.newsDaySpan
import com.appsc.ca.data.nextUpdate
import com.appsc.ca.data.Filter
import com.appsc.ca.data.Item
import com.appsc.ca.data.Lane
import com.appsc.ca.data.groupByUnit
import com.appsc.ca.data.longDate
import com.appsc.ca.data.shortDate

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Today),
    SUBJECTS("Subjects", Icons.AutoMirrored.Filled.LibraryBooks),
    DAYS("Days", Icons.Filled.DateRange),
    SYLLABUS("Syllabus", Icons.AutoMirrored.Filled.MenuBook),
    SAVED("Saved", Icons.Filled.Bookmark),
}

private sealed interface Route {
    data class DayRoute(val date: String) : Route
    data class UnitRoute(val code: String) : Route
    data object SettingsRoute : Route
    data class SubjectRoute(val book: String) : Route
}

@Composable
fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val stack = remember { mutableStateListOf<Route>() }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    Scaffold(
        bottomBar = {
            Column {
            // read-aloud's controls, on every screen while a session runs
            PlayerBar(rate = vm.speechRate, onRate = vm::chooseRate)
            if (stack.isEmpty()) {
                NavigationBar {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val r = stack.lastOrNull()) {
                is Route.DayRoute -> DayFeed(vm, fixedDate = r.date, onBack = back)
                is Route.UnitRoute -> UnitScreen(vm, r.code, onBack = back)
                Route.SettingsRoute -> SettingsScreen(vm, onBack = back)
                is Route.SubjectRoute -> SubjectScreen(vm, r.book, onBack = back)
                null -> when (tab) {
                    Tab.TODAY -> DayFeed(vm, fixedDate = null, onSettings = { stack.add(Route.SettingsRoute) })
                    Tab.DAYS -> DaysScreen(vm) { stack.add(Route.DayRoute(it)) }
                    Tab.SYLLABUS -> SyllabusScreen(vm) { stack.add(Route.UnitRoute(it)) }
                    Tab.SUBJECTS -> SubjectsScreen(vm) { stack.add(Route.SubjectRoute(it)) }
                    Tab.SAVED -> SavedScreen(vm)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = C.Muted)
        }
        actions()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = C.Ap,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp)).background(C.ApSoft).padding(12.dp),
    )
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = C.Muted, modifier = Modifier.padding(24.dp))
}

/** A card in a list that read-aloud can start from and highlight. */
@Composable
private fun ListCard(vm: AppViewModel, list: List<Item>, index: Int, readFlag: Boolean? = null, savedFlag: Boolean? = null) {
    val i = vm.visible(list[index])
    val pb by ReadAloud.playback
    ItemCard(
        i,
        onHideNote = { sec -> vm.hideNote(list[index], sec) },
        read = readFlag ?: (i.id in vm.readIds),
        saved = savedFlag ?: vm.isSaved(i.id),
        onToggleSave = { vm.toggleSave(i) },
        onRead = { vm.markRead(i.id) },
        reading = pb.active && pb.pageId == i.id,
        onListen = if (Listening.canRead(i)) ({ Listening.play(list.map(vm::visible), index, vm.speechRate) }) else null,
    )
}

/** Keeps the story being read in view. [rowOf] gives a story's row in the LazyColumn. */
@Composable
private fun FollowReading(state: androidx.compose.foundation.lazy.LazyListState, rowOf: (String) -> Int) {
    val pb by ReadAloud.playback
    androidx.compose.runtime.LaunchedEffect(pb.pageId) {
        if (pb.active && pb.pageId.isNotBlank()) {
            val row = rowOf(pb.pageId)
            if (row >= 0) state.animateScrollToItem(row)
        }
    }
}

// ---------------------------------------------------------------------------
// Today, and any one day
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayFeed(
    vm: AppViewModel,
    fixedDate: String?,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var lane by rememberSaveable { mutableStateOf(Lane.ALL) }
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf(DayView.TOP) }

    val date = fixedDate ?: picked ?: vm.days.firstOrNull()?.date
    val day = vm.days.find { it.date == date }
    val summary = vm.index?.days?.find { it.date == date }
    val dayItems = day?.items.orEmpty()
    val base = Filter(exam = vm.exam)
    val filtered = Filter(lane, vm.exam, subject).apply(dayItems)
    // Top 25 needs a feed that marks it; an older day file shows everything
    val hasTop = dayItems.any { it.top }
    val shown = when (view) {
        DayView.TOP -> if (hasTop) filtered.filter { it.top } else filtered
        DayView.ONE_LINERS -> filtered.filter { it.oneLiner }
        DayView.ALL -> filtered
        DayView.QUIZ, DayView.REVISE -> emptyList()
    }
    val perBook = Filter(lane = lane, exam = vm.exam).apply(dayItems).groupingBy { it.book }.eachCount()

    // rows before the stories, in the order they are added below
    val headRows = 1 + (if (date != null) 1 else 0) + (if (vm.message != null) 1 else 0) +
        (if (fixedDate == null && vm.days.size > 1) 1 else 0) + 3 + (if (day == null || shown.isEmpty()) 1 else 0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    FollowReading(listState) { id -> shown.indexOfFirst { it.id == id }.let { if (it < 0) -1 else headRows + it } }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item {
                Header(
                    title = when {
                        summary != null && !summary.final && date == vm.index?.days?.firstOrNull()?.date -> "Today's current affairs"
                        else -> "Current affairs"
                    },
                    subtitle = date?.let { d ->
                        val apN = dayItems.count { it.ap }
                        "${longDate(d)} · ${dayItems.size} updates · $apN Andhra Pradesh"
                    },
                    onBack = onBack,
                ) {
                    val pb by ReadAloud.playback
                    IconButton(onClick = {
                        if (pb.active) ReadAloud.stop() else Listening.play(shown, 0, vm.speechRate)
                    }) {
                        Icon(if (pb.active) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp, if (pb.active) "Stop" else "Listen to this list")
                    }
                    if (onSettings != null) {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    }
                }
            }
            if (date != null) {
                item { NewsDayBar(date, summary, day?.updated.orEmpty(), vm.index) }
            }
            vm.message?.let { m -> item { Notice(m) } }

            if (fixedDate == null && vm.days.size > 1) {
                item {
                    ChipRow {
                        for (d in vm.days.take(10)) {
                            FilterChip(
                                selected = d.date == date,
                                onClick = { picked = d.date },
                                label = { Text("${shortDate(d.date)} (${d.items.size})") },
                            )
                        }
                    }
                }
            }
            item {
                ChipRow {
                    for (v in DayView.entries) {
                        val n = when (v) {
                            DayView.TOP -> if (hasTop) filtered.count { it.top } else filtered.size
                            DayView.ALL -> filtered.size
                            DayView.ONE_LINERS -> filtered.count { it.oneLiner }
                            DayView.QUIZ -> day?.quiz?.size ?: 0
                            DayView.REVISE -> vm.dueCards.size
                        }
                        val label = when (v) {
                            DayView.QUIZ -> "Quiz (${day?.quiz?.count { it.id in vm.answers } ?: 0}/$n)"
                            else -> "${v.label} ($n)"
                        }
                        FilterChip(selected = view == v, onClick = { view = v }, label = { Text(label) })
                    }
                }
            }
            item {
                ChipRow {
                    for (l in Lane.entries) {
                        val n = base.copy(lane = l).apply(dayItems).size
                        FilterChip(selected = lane == l, onClick = { lane = l }, label = { Text("${l.label} ($n)") })
                    }
                }
            }
            item {
                ChipRow {
                    for (e in Exam.entries) {
                        FilterChip(selected = vm.exam == e, onClick = { vm.chooseExam(e) }, label = { Text(e.label) })
                    }
                    FilterChip(selected = subject == null, onClick = { subject = null }, label = { Text("All 6 books") })
                    for (b in BOOKS) {
                        val n = perBook[b] ?: 0
                        if (n > 0 || subject == b) {
                            FilterChip(selected = subject == b, onClick = { subject = if (subject == b) null else b }, label = { Text("${bookNumber(b)}. $b ($n)") })
                        }
                    }
                }
            }
            if (day == null) {
                item {
                    Empty(if (vm.refreshing) "Downloading today's updates…" else "No updates on this phone yet. Pull down to download.")
                }
            } else when (view) {
                DayView.QUIZ -> {
                    if (day.quiz.isEmpty()) item { Empty("No quiz for ${shortDate(day.date)} yet: it is made at the next update.") }
                    else item {
                        val got = day.quiz.count { vm.answers[it.id] == true }
                        val done = day.quiz.count { it.id in vm.answers }
                        Empty("From the day's own figures and years. $got right of $done answered; wrong ones go to Revise.")
                    }
                    itemsIndexed(day.quiz, key = { _, q -> q.id }) { n, q ->
                        QuizCard(n + 1, q, vm.answers[q.id]) { vm.answer(q, it) }
                    }
                }
                DayView.REVISE -> {
                    val due = vm.dueCards
                    if (due.isEmpty()) {
                        item { Empty(if (vm.revise.isEmpty()) "Nothing to revise. Quiz answers you get wrong come back here after 1, 3 and 7 days." else "Nothing due today. ${vm.revise.size} questions are waiting for later days.") }
                    }
                    items(due, key = { it.q.id }) { c -> ReviseCardView(c) { ok -> vm.reviseAnswer(c, ok) } }
                }
                DayView.ONE_LINERS -> {
                    if (shown.isEmpty()) item { Empty("No one-liners under this filter for ${shortDate(day.date)}.") }
                    items(shown, key = { it.id }) { OneLinerRow(it) }
                }
                else -> {
                    if (shown.isEmpty()) item { Empty("Nothing under this filter for ${shortDate(day.date)}.") }
                    itemsIndexed(shown, key = { _, x -> x.id }) { idx, _ -> ListCard(vm, shown, idx) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Subjects: the 6 books of the Combined Notes
// ---------------------------------------------------------------------------

@Composable
private fun SubjectsScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val all = Filter(exam = vm.exam).apply(vm.allItems)
    val latest = vm.days.firstOrNull()?.date
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Current affairs by subject", "The 6 books of your Combined Notes · last ${vm.days.size} days") }
        item {
            ChipRow {
                for (e in Exam.entries) FilterChip(selected = vm.exam == e, onClick = { vm.chooseExam(e) }, label = { Text(e.label) })
            }
        }
        items(BOOKS, key = { it }) { b ->
            val list = all.filter { it.book == b }
            val today = list.count { it.date == latest }
            val ap = list.count { it.ap }
            val unread = list.count { it.id !in vm.readIds }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(b) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${bookNumber(b)}", style = MaterialTheme.typography.titleLarge, color = C.Accent, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(b, style = MaterialTheme.typography.titleMedium, color = C.Ink)
                    Text("$today today · ${list.size} in all · $ap Andhra Pradesh · $unread unread", style = MaterialTheme.typography.labelMedium, color = C.Muted)
                }
            }
            HorizontalDivider(color = C.Line)
        }
    }
}

/** One book's current affairs, newest day first, with the lanes on top. */
@Composable
private fun SubjectScreen(vm: AppViewModel, book: String, onBack: () -> Unit) {
    var lane by rememberSaveable { mutableStateOf(Lane.ALL) }
    val inBook = Filter(exam = vm.exam).apply(vm.allItems).filter { it.book == book }
    val shown = Filter(lane, vm.exam).apply(inBook).sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.score })
    // story id -> its row (2 head rows, then a date heading before each new day)
    val rows = remember(shown) {
        val map = HashMap<String, Int>()
        var row = 2 + (if (shown.isEmpty()) 1 else 0)
        var last = ""
        for ((idx, i) in shown.withIndex()) {
            if (i.date != last) { last = i.date; row++ }
            map[i.id] = row++
        }
        map
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    FollowReading(listState) { id -> rows[id] ?: -1 }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { Header("${bookNumber(book)}. $book", "${inBook.size} stories · last ${vm.days.size} days", onBack = onBack) }
        item {
            ChipRow {
                for (l in Lane.entries) {
                    val n = inBook.count { l.matches(it) }
                    FilterChip(selected = lane == l, onClick = { lane = l }, label = { Text("${l.label} ($n)") })
                }
            }
        }
        if (shown.isEmpty()) item { Empty("No stories under this subject yet.") }
        var lastDate = ""
        for ((idx, i) in shown.withIndex()) {
            if (i.date != lastDate) {
                lastDate = i.date
                item(key = "d-${i.date}") {
                    Text(longDate(i.date).uppercase(), style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
                }
            }
            item(key = i.id) {
                ListCard(vm, shown, idx)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * What "today" means, on screen: the span of the news day, and whether it is
 * still being added to (with the last and next update) or final.
 */
@Composable
private fun NewsDayBar(date: String, summary: com.appsc.ca.data.DaySummary?, updated: String, index: com.appsc.ca.data.FeedIndex?) {
    val starts = index?.newsDayStarts ?: "06:00"
    val final = summary?.final == true
    val status = if (final) {
        "FINAL · nothing more will be added"
    } else {
        val last = istClock(updated)
        val next = nextUpdate(index?.schedule ?: listOf("06:30", "13:00", "18:30", "23:30"))
        "UPDATING" + (if (last.isNotBlank()) " · last update $last" else "") + (if (next.isNotBlank()) " · next ~$next" else "")
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp)).background(if (final) C.Chip else C.AccentSoft).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(status, style = MaterialTheme.typography.labelMedium, color = if (final) C.Muted else C.Accent, fontWeight = FontWeight.Bold)
        Text(
            "News day: ${newsDaySpan(date, starts)} (IST). Morning-paper uploads before ${clockTime(starts)} count for the day before.",
            style = MaterialTheme.typography.labelMedium,
            color = C.Muted,
        )
    }
}

// ---------------------------------------------------------------------------
// Days
// ---------------------------------------------------------------------------

@Composable
private fun DaysScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("All days", "Last ${vm.days.size} days are on this phone and read offline") }
        if (vm.days.isEmpty()) item { Empty("Nothing downloaded yet.") }
        items(vm.days, key = { it.date }) { d ->
            val ap = d.items.count { it.ap }
            val critical = d.items.count { it.band == "critical" }
            val unread = d.items.count { it.id !in vm.readIds }
            Column(Modifier.fillMaxWidth().clickable { onOpen(d.date) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(longDate(d.date), style = MaterialTheme.typography.titleMedium, color = C.Ink)
                Text(
                    (if (vm.index?.days?.find { it.date == d.date }?.final == true) "Final · " else "Updating · ") +
                        "${d.items.size} updates · $ap Andhra Pradesh · $critical critical · $unread unread",
                    style = MaterialTheme.typography.labelMedium,
                    color = C.Muted,
                )
            }
            HorizontalDivider(color = C.Line)
        }
    }
}

// ---------------------------------------------------------------------------
// Syllabus: every unit of the combined tracker that has stories
// ---------------------------------------------------------------------------

@Composable
private fun SyllabusScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val groups = groupByUnit(Filter(exam = vm.exam).apply(vm.allItems))
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Header("By syllabus", "Combined G1 + G2 tracker · stories from the last ${vm.days.size} days")
        }
        item {
            ChipRow {
                for (e in Exam.entries) {
                    FilterChip(selected = vm.exam == e, onClick = { vm.chooseExam(e) }, label = { Text(e.label) })
                }
            }
        }
        if (groups.isEmpty()) item { Empty("No stories yet.") }
        var lastExam = ""
        for (g in groups) {
            if (g.exam != lastExam) {
                lastExam = g.exam
                val title = if (g.exam == "G1") "Group-I Prelims (A–F)" else "Group-II Screening and Mains"
                item(key = "h-${g.exam}") {
                    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
            }
            item(key = g.code) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(g.code) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.code, style = MaterialTheme.typography.labelMedium, color = C.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                    Text(g.label, style = MaterialTheme.typography.bodyMedium, color = C.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Badge("${g.items.size}", C.Accent, C.AccentSoft)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun UnitScreen(vm: AppViewModel, code: String, onBack: () -> Unit) {
    val group = groupByUnit(Filter(exam = vm.exam).apply(vm.allItems)).find { it.code == code }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header(code, group?.label, onBack = onBack) }
        if (group == null) item { Empty("No stories under this unit.") }
        val list = group?.items.orEmpty()
        itemsIndexed(list, key = { _, x -> x.id }) { idx, _ -> ListCard(vm, list, idx) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Saved
// ---------------------------------------------------------------------------

@Composable
private fun SavedScreen(vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Saved", "${vm.saved.size} stories kept for revision") }
        if (vm.saved.isEmpty()) item { Empty("Tap the bookmark on any story to keep it here. Saved stories stay even after the day is removed from the phone.") }
        itemsIndexed(vm.saved, key = { _, x -> x.id }) { idx, _ -> ListCard(vm, vm.saved, idx, readFlag = true, savedFlag = true) }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
private fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Settings", onBack = onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notify me of new updates", style = MaterialTheme.typography.titleMedium)
                Text("After the morning and evening downloads", style = MaterialTheme.typography.labelMedium, color = C.Muted)
            }
            Switch(checked = vm.notify, onCheckedChange = vm::chooseNotify)
        }
        Text("Show stories for", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
        ChipRow {
            for (e in Exam.entries) FilterChip(selected = vm.exam == e, onClick = { vm.chooseExam(e) }, label = { Text(e.label) })
        }
        OutlinedButton(onClick = vm::refresh, modifier = Modifier.padding(16.dp)) { Text(if (vm.refreshing) "Downloading…" else "Download now") }
        vm.index?.updated?.takeIf { it.isNotBlank() }?.let {
            Text("Feed last updated ${it.replace('T', ' ').take(16)} UTC", style = MaterialTheme.typography.labelMedium, color = C.Muted, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Text(
            "Where the stories come from: PIB (Delhi, Vijayawada, Hyderabad), AIR News, The Hindu, Times of India, " +
                "Hindustan Times, NDTV, Mint, Business Standard, BusinessLine, Mongabay India, The Hans India, Eenadu, " +
                "GKToday and AffairsCloud, collected every morning and evening. Each story is " +
                "filed under one of the 6 books of your Combined Notes. A news day runs from 6:00 AM to 5:59 AM the next " +
                "morning (IST), the way a newspaper does: Eenadu and The Hans India upload their papers at 3-6 AM and those " +
                "stories belong to the day before. Updates come at about 6:30 AM (which closes the previous day), 1 PM, " +
                "6:30 PM and 11:30 PM. No AI is used. Each story is " +
                "scored by fixed rules out of 100: combined G1 + G2 syllabus units (30), APPSC blueprint keyword angles (20), " +
                "Andhra Pradesh (20), an official act such as an order, Bill, judgment or appointment (15) and use in both " +
                "exams (15). Stories scoring 40 or more are kept; Andhra Pradesh stories from 35 (30 for Eenadu headlines) when they name a syllabus unit, a blueprint angle or an official act. Crime, films, weather and " +
                "match reports are left out. Key facts are the article's own sentences.",
            style = MaterialTheme.typography.bodyMedium,
            color = C.Body,
            modifier = Modifier.padding(16.dp),
        )
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
