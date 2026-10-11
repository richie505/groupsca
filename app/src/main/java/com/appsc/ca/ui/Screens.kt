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
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Quiz
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
import com.appsc.ca.data.books
import com.appsc.ca.data.Lane
import com.appsc.ca.data.groupByUnit
import com.appsc.ca.data.longDate
import com.appsc.ca.data.shortDate

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    BRIEFING("Briefing", Icons.Filled.Today),
    NEWS("News", Icons.Filled.Newspaper),
    SUBJECTS("Subjects", Icons.AutoMirrored.Filled.LibraryBooks),
    PRACTICE("Practice", Icons.Filled.Quiz),
}

private sealed interface Route {
    data class DayRoute(val date: String) : Route
    data class UnitRoute(val code: String) : Route
    data object SettingsRoute : Route
    data class SubjectRoute(val book: String) : Route
    data class DigestRoute(val kind: String, val start: String) : Route
    data class ArticleRoute(val ids: List<String>, val index: Int) : Route
    data object SearchRoute : Route
    data object SavedRoute : Route
    data object TrendingRoute : Route
}

@Composable
fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Route>() }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    androidx.compose.runtime.LaunchedEffect(vm.openBriefing) { if (vm.openBriefing != null) { stack.clear(); tab = Tab.BRIEFING } }
    val openArticle: (List<String>, Int) -> Unit = { ids, i -> stack.add(Route.ArticleRoute(ids, i)) }
    val openDigest: (Period) -> Unit = { p -> stack.add(Route.DigestRoute(p.kind, p.start.toString())) }

    // Settings → Larger text
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * if (vm.largeText) 1.18f else 1f),
    ) {
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
                is Route.SubjectRoute -> SubjectScreen(vm, r.book, onBack = back, onArticle = openArticle)
                is Route.DigestRoute -> DigestScreen(vm, Period(r.kind, java.time.LocalDate.parse(r.start)), onBack = back)
                is Route.ArticleRoute -> ArticleScreen(vm, r.ids, r.index, onBack = back)
                Route.SearchRoute -> SearchScreen(vm, onArticle = openArticle, onBack = back)
                Route.SavedRoute -> SavedScreen(vm, onArticle = openArticle, onBack = back)
                Route.TrendingRoute -> TrendingScreen(vm, onArticle = openArticle, onBack = back)
                null -> when (tab) {
                    Tab.HOME -> HomeScreen(
                        vm,
                        onArticle = openArticle,
                        onNews = { tab = Tab.NEWS },
                        onPractice = { tab = Tab.PRACTICE },
                        onDigest = openDigest,
                        onSearch = { stack.add(Route.SearchRoute) },
                        onSettings = { stack.add(Route.SettingsRoute) },
                        onSaved = { stack.add(Route.SavedRoute) },
                        onBriefing = { tab = Tab.BRIEFING },
                        onTrending = { stack.add(Route.TrendingRoute) },
                    )
                    Tab.BRIEFING -> BriefingScreen(vm)
                    Tab.NEWS -> NewsScreen(vm, onArticle = openArticle, onDigest = openDigest)
                    Tab.SUBJECTS -> SubjectsScreen(vm) { stack.add(Route.SubjectRoute(it)) }
                    Tab.PRACTICE -> PracticeScreen(vm)
                }
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

/** New today: the feed's new topics, without stories the reader put in an earlier topic, plus those taken out of one. */
private fun newTopicsOf(vm: AppViewModel, filtered: List<Item>, hasTop: Boolean): List<Item> {
    if (!hasTop) return filtered
    val marked = filtered.filter { (it.top && !vm.isUpdate(it)) || (it.update && !vm.isUpdate(it) && !it.oneLiner) }
    // as many as the reader chose in Settings: the best by score, topped up from the day's other new topics
    val n = vm.perDay
    if (marked.size >= n) return marked.sortedByDescending { it.score }.take(n)
    val more = filtered.filter { it !in marked && !vm.isUpdate(it) && !it.oneLiner && !it.digest }.sortedByDescending { it.score }
    return marked + more.take(n - marked.size)
}

/** A card in a list that read-aloud can start from and highlight. */
@Composable
private fun ListCard(vm: AppViewModel, list: List<Item>, index: Int, readFlag: Boolean? = null, savedFlag: Boolean? = null) {
    val i = vm.visible(list[index])
    val pb by ReadAloud.playback
    ItemCard(
        i,
        timeline = vm.timeline(list[index]),
        done = vm.isDone(i),
        onDone = { vm.toggleDone(i) },
        isUpdate = vm.isUpdate(list[index]),
        earlierTopics = { vm.earlierTopics(list[index]) },
        onLinkTopic = { thread -> vm.linkTopic(list[index], thread) },
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
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    val date = fixedDate ?: picked ?: vm.days.firstOrNull()?.date
    val day = vm.days.find { it.date == date }
    val summary = vm.index?.days?.find { it.date == date }
    val dayItems = vm.shown(day?.items.orEmpty())
    val base = Filter(exam = vm.exam)
    val filtered = Filter(lane, vm.exam, subject).apply(dayItems)
    // Top 25 needs a feed that marks it; an older day file shows everything
    val hasTop = dayItems.any { it.top }
    val shown = when (view) {
        // new topics, the ones marked done last
        DayView.TOP -> newTopicsOf(vm, filtered, hasTop).sortedBy { vm.isDone(it) }
        DayView.UPDATES -> filtered.filter { vm.isUpdate(it) && !it.oneLiner }
        DayView.ONE_LINERS -> filtered.filter { it.oneLiner }
        DayView.ALL -> filtered
        DayView.QUIZ, DayView.REVISE -> emptyList()
    }
    val newTopics = newTopicsOf(vm, filtered, hasTop)
    val updateCount = filtered.count { vm.isUpdate(it) && !it.oneLiner }
    val oneLinerCount = filtered.count { it.oneLiner }
    val perBook = Filter(lane = lane, exam = vm.exam).apply(dayItems).groupingBy { it.book }.eachCount()

    // rows before the stories, in the order they are added below
    val headRows = 1 + (if (date != null) 1 else 0) + (if (vm.message != null) 1 else 0) +
        (if (fixedDate == null && vm.days.size > 1) 1 else 0) + 2 + (if (filtersOpen) 2 else 0) + (if (day != null && hasTop) 1 else 0) +
        (if (day == null || shown.isEmpty()) 1 else 0)
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
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
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
                    // the quiz and revision have their own tab
                    for (v in DayView.entries.filter { it != DayView.QUIZ && it != DayView.REVISE }) {
                        val n = when (v) {
                            DayView.TOP -> newTopics.size
                            DayView.UPDATES -> updateCount
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
            if (day != null && hasTop) {
                item {
                    val left = newTopics.count { !vm.isDone(it) }
                    Text(
                        "≈ ${readingMinutes(newTopics.filter { !vm.isDone(it) }, updateCount + oneLinerCount)} min today: " +
                            "$left new topics to read in full, $updateCount updates on topics read before, " +
                            "$oneLinerCount one-liners, then the quiz.",
                        style = MaterialTheme.typography.labelMedium,
                        color = C.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            // filters, folded away until needed
            item {
                ChipRow {
                    FilterChip(
                        selected = filtersOpen,
                        onClick = { filtersOpen = !filtersOpen },
                        label = {
                            Text(
                                "Filter: " + listOfNotNull(
                                    lane.label,
                                    vm.exam.label,
                                    subject?.let { "${bookNumber(it)}. $it" } ?: "All books",
                                ).joinToString(" · ") + if (filtersOpen) "  ▲" else "  ▼",
                            )
                        },
                    )
                }
            }
            if (filtersOpen) item {
                ChipRow {
                    for (l in Lane.entries) {
                        val n = base.copy(lane = l).apply(dayItems).size
                        FilterChip(selected = lane == l, onClick = { lane = l }, label = { Text("${l.label} ($n)") })
                    }
                }
            }
            if (filtersOpen) item {
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
                DayView.UPDATES -> {
                    if (shown.isEmpty()) item { Empty("No updates on earlier topics for ${shortDate(day.date)}.") }
                    else item { Empty("Follow-ups on topics that started on an earlier day: the static notes are on the first day's card. Tap one for the full story and the topic's timeline.") }
                    itemsIndexed(shown, key = { _, x -> x.id }) { idx, x ->
                        UpdateRow(x, vm.isDone(x)) { ListCard(vm, shown, idx) }
                    }
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
    val all = Filter(exam = vm.exam).apply(vm.shown(vm.allItems)).filter { !it.digest || it.oneLiner }
    val latest = vm.days.firstOrNull()?.date
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Subjects", "Current affairs by the 6 books of your Combined Notes · last ${vm.days.size} days") }
        item {
            ChipRow {
                for (e in Exam.entries) FilterChip(selected = vm.exam == e, onClick = { vm.chooseExam(e) }, label = { Text(e.label) })
            }
        }
        items(BOOKS, key = { it }) { b ->
            val list = all.filter { it.book == b }
            val today = list.count { it.date == latest && !it.oneLiner }
            val ap = list.count { it.ap }
            val unread = list.count { it.id !in vm.readIds && !it.oneLiner }
            val (fg, bg) = bookColor(b)
            val briefN = vm.briefings.sumOf { br -> br.allItems.count { b in it.books() } }
            val briefToday = vm.briefings.firstOrNull()?.allItems?.count { b in it.books() } ?: 0
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                    .clickable { onOpen(b) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${bookNumber(b)}", style = MaterialTheme.typography.headlineSmall, color = fg, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(b, style = MaterialTheme.typography.titleMedium, color = C.Ink, fontWeight = FontWeight.Bold)
                    if (briefN > 0) Text(
                        "📰 Briefing: $briefToday in the latest · $briefN in all",
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                    )
                    Text(
                        "News feed: $today new today · ${list.count { !it.oneLiner }} stories · $ap Andhra Pradesh · $unread unread",
                        style = MaterialTheme.typography.labelMedium,
                        color = C.Muted,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** One book's current affairs, newest day first, with the lanes on top. */
@Composable
private fun SubjectScreen(vm: AppViewModel, book: String, onBack: () -> Unit, onArticle: (List<String>, Int) -> Unit) {
    var lane by rememberSaveable { mutableStateOf(Lane.ALL) }
    var showBrief by rememberSaveable { mutableStateOf(vm.briefings.isNotEmpty()) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    // the daily briefing's items filed under this book, newest day first
    val briefed = vm.briefings.map { br -> br.date to br.allItems.filter { book in it.books() && (section == null || it.section == section) }.sortedBy { it.importance } }
        .filter { it.second.isNotEmpty() }
    val inBook = Filter(exam = vm.exam).apply(vm.shown(vm.allItems)).filter { it.book == book && !it.digest }
    val stories = Filter(lane, vm.exam).apply(inBook).filter { !it.oneLiner }
        .sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.top }.thenByDescending { it.score })
    val oneLiners = Filter(lane, vm.exam).apply(inBook).filter { it.oneLiner }.sortedByDescending { it.date }
    val ids = stories.map { it.id }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("${bookNumber(book)}. $book", "${briefed.sumOf { it.second.size }} briefing items · ${stories.size} news stories", onBack = onBack) }
        item {
            ChipRow {
                FilterChip(selected = showBrief, onClick = { showBrief = true }, label = { Text("📰 Daily briefing (${briefed.sumOf { it.second.size }})") })
                FilterChip(selected = !showBrief, onClick = { showBrief = false }, label = { Text("News feed (${stories.size})") })
            }
        }
        if (showBrief) {
            item {
                ChipRow {
                    for ((label, key) in listOf("All" to null, "International" to "INTERNATIONAL", "National" to "NATIONAL", "Andhra Pradesh" to "ANDHRA PRADESH")) {
                        FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label) })
                    }
                }
            }
            if (briefed.isEmpty()) item { Empty("No briefing items under this subject yet.") }
            for ((d, list) in briefed) {
                item(key = "bd-$d") {
                    Text(longDate(d).uppercase(), style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp))
                }
                items(list, key = { "b-$d-" + it.section + it.headline }) { BriefCard(vm, d, it) }
            }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }
        item {
            ChipRow {
                for (l in Lane.entries) {
                    val n = inBook.count { l.matches(it) && !it.oneLiner }
                    FilterChip(selected = lane == l, onClick = { lane = l }, label = { Text("${l.label} ($n)") })
                }
            }
        }
        if (stories.isEmpty()) item { Empty("No stories under this subject yet.") }
        var lastDate = ""
        for (i in stories) {
            if (i.date != lastDate) {
                lastDate = i.date
                item(key = "d-${i.date}") {
                    Text(longDate(i.date).uppercase(), style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp))
                }
            }
            item(key = i.id) { ArticleRow(i, i.id in vm.readIds, vm.isDone(i)) { onArticle(ids, ids.indexOf(i.id)) } }
        }
        if (oneLiners.isNotEmpty()) {
            item { Text("ONE-LINERS", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp)) }
            items(oneLiners, key = { "o-" + it.id }) { OneLinerRow(it) }
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
private fun DaysScreen(vm: AppViewModel, onDigest: (Period) -> Unit, onOpen: (String) -> Unit) {
    val dates = vm.days.map { it.date }
    val weeks = periodsOf(dates, "week", vm.index?.digests.orEmpty())
    val months = periodsOf(dates, "month", vm.index?.digests.orEmpty())
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Days & digests", "Last ${vm.days.size} days are on this phone and read offline") }
        if (weeks.isNotEmpty()) {
            item {
                Text(
                    "Revise from the digests: each topic once, by book, with its timeline and static notes, the one-liners and the quiz.",
                    style = MaterialTheme.typography.labelMedium,
                    color = C.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                ChipRow {
                    for (w in weeks) FilterChip(selected = false, onClick = { onDigest(w) }, label = { Text(if (w == weeks.first()) "This week" else w.title.removePrefix("Week of ")) })
                }
            }
            item {
                ChipRow {
                    for (m in months) FilterChip(selected = false, onClick = { onDigest(m) }, label = { Text(m.title) })
                }
            }
        }
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
private fun SavedScreen(vm: AppViewModel, onArticle: (List<String>, Int) -> Unit, onBack: () -> Unit) {
    val ids = vm.saved.map { it.id }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Saved", "${vm.saved.size} stories kept for revision", onBack = onBack) }
        if (vm.saved.isEmpty()) item { Empty("Tap the bookmark on any story to keep it here. Saved stories stay even after the day is removed from the phone.") }
        items(vm.saved, key = { it.id }) { i -> ArticleRow(i, true, vm.isDone(i)) { onArticle(ids, ids.indexOf(i.id)) } }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

/** Quiz tab: what is due for revision first, then any day's quiz. */
@Composable
private fun PracticeScreen(vm: AppViewModel) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val days = vm.days.filter { it.quiz.isNotEmpty() }
    val day = days.find { it.date == picked } ?: days.firstOrNull()
    val due = vm.dueCards
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Quiz & revise", "Questions from each day's own figures and years; wrong answers come back after 1, 3 and 7 days") }
        item {
            Text("Revise today (${due.size})", style = MaterialTheme.typography.titleMedium, color = C.Accent, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        }
        if (due.isEmpty()) {
            item { Empty(if (vm.revise.isEmpty()) "Nothing to revise yet." else "Nothing due today. ${vm.revise.size} questions are waiting for later days.") }
        }
        items(due, key = { "r-" + it.q.id }) { c -> ReviseCardView(c) { ok -> vm.reviseAnswer(c, ok) } }
        if (day != null) {
            item {
                Text("Day's quiz", style = MaterialTheme.typography.titleMedium, color = C.Accent, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            }
            item {
                ChipRow {
                    for (d in days.take(14)) {
                        val done = d.quiz.count { it.id in vm.answers }
                        FilterChip(selected = d.date == day.date, onClick = { picked = d.date }, label = { Text("${shortDate(d.date)} ($done/${d.quiz.size})") })
                    }
                }
            }
            itemsIndexed(day.quiz, key = { _, q -> "q-" + q.id }) { n, q -> QuizCard(n + 1, q, vm.answers[q.id]) { vm.answer(q, it) } }
        } else item { Empty("No quiz yet: pull down on Today to download.") }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel, onBack: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Settings", onBack = onBack)
        Text("New topics a day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        ChipRow {
            for (n in listOf(10, 15, 25)) FilterChip(selected = vm.perDay == n, onClick = { vm.choosePerDay(n) }, label = { Text("$n") })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Larger text", style = MaterialTheme.typography.titleMedium)
            }
            Switch(checked = vm.largeText, onCheckedChange = vm::chooseLargeText)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Telugu headlines (Eenadu)", style = MaterialTheme.typography.titleMedium)
                Text("Headlines only; read aloud skips them", style = MaterialTheme.typography.labelMedium, color = C.Muted)
            }
            Switch(checked = vm.telugu, onCheckedChange = vm::chooseTelugu)
        }
        Text("Daily briefing notification", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        ChipRow {
            for (h in listOf(20, 21, 22)) FilterChip(selected = vm.briefHour == h, onClick = { vm.chooseBriefHour(h) }, label = { Text("${h - 12}:00 PM") })
        }
        for ((label, on, set) in listOf(
            Triple("Show Mains angles", vm.showMains, vm::chooseShowMains),
            Triple("Show AP links", vm.showApLink, vm::chooseShowApLink),
            Triple("Show quizzes", vm.showQuiz, vm::chooseShowQuiz),
        )) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = set)
            }
        }
        Text("Read-aloud speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        ChipRow {
            for (r in listOf(0.75f, 1f, 1.25f, 1.5f, 2f)) FilterChip(selected = vm.speechRate == r, onClick = { vm.chooseRate(r) }, label = { Text("${r}×") })
        }
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
            "Only exam news: party politics, protests, crime, markets, festivals and leaders' statements are left out. " +
                "Static notes come from your Rocket Sheets' key facts. " +
                "Where the stories come from: PIB (Delhi, Vijayawada, Hyderabad), AIR News, The Hindu, Times of India, " +
                "Hindustan Times, NDTV, Mint, Business Standard, BusinessLine, Mongabay India, The Hans India, Eenadu, " +
                "GKToday and AffairsCloud, collected every morning and evening. Each story is " +
                "filed under one of the 6 books of your Combined Notes. A news day runs from 6:00 AM to 5:59 AM the next " +
                "morning (IST), the way a newspaper does: Eenadu and The Hans India upload their papers at 3-6 AM and those " +
                "stories belong to the day before. Updates come at about 6:30 AM (which closes the previous day), 1 PM, " +
                "6:30 PM and 11:30 PM. No AI is used. Each story is " +
                "scored by fixed rules out of 100: combined G1 + G2 syllabus units (30), APPSC blueprint keyword angles (20), " +
                "Andhra Pradesh (20), an official act such as an order, Bill, judgment or appointment (15) and use in both " +
                "exams (15). Stories scoring 40 or more are kept; Andhra Pradesh stories from 35 (30 for Eenadu headlines) when they name a syllabus unit, a blueprint angle or an official act. " +
                "Key facts are the article's own sentences.",
            style = MaterialTheme.typography.bodyMedium,
            color = C.Body,
            modifier = Modifier.padding(16.dp),
        )
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = C.Faint, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
