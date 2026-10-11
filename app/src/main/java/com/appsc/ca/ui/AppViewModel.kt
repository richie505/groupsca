package com.appsc.ca.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appsc.ca.data.Day
import com.appsc.ca.data.Exam
import com.appsc.ca.data.FeedIndex
import com.appsc.ca.data.FeedStore
import com.appsc.ca.data.Item
import com.appsc.ca.data.QuizQ
import com.appsc.ca.data.RefreshResult
import com.appsc.ca.data.ReviseCard
import com.appsc.ca.data.StaticSection
import com.appsc.ca.data.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val feed = FeedStore(app)
    private val user = UserStore(app)

    var days by mutableStateOf<List<Day>>(emptyList())
        private set
    var index by mutableStateOf<FeedIndex?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var lastResult by mutableStateOf<RefreshResult?>(null)
        private set
    var saved by mutableStateOf<List<Item>>(emptyList())
        private set
    var readIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var exam by mutableStateOf(Exam.BOTH)
        private set
    var notify by mutableStateOf(true)
        private set
    var speechRate by mutableStateOf(1f)
        private set
    var trending by mutableStateOf<com.appsc.ca.data.Trending?>(null)
        private set

    /** A story by id, from the days on the phone or the saved ones. */
    fun itemById(id: String): Item? = allItems.firstOrNull { it.id == id } ?: saved.firstOrNull { it.id == id }

    /** Stories whose headline or summary has every word of [query] (search). */
    fun search(query: String): List<Item> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        if (words.isEmpty()) return emptyList()
        return shown(allItems).filter { it.topicOf.isEmpty() }.filter { i ->
            val text = (i.title + " " + i.summary).lowercase()
            words.all { it in text }
        }.distinctBy { it.id }.sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.score }).take(60)
    }

    var perDay by mutableStateOf(15)
        private set
    var largeText by mutableStateOf(false)
        private set
    var telugu by mutableStateOf(true)
        private set

    fun choosePerDay(n: Int) {
        perDay = n
        user.perDay = n
    }

    fun chooseLargeText(on: Boolean) {
        largeText = on
        user.largeText = on
    }

    fun chooseTelugu(on: Boolean) {
        telugu = on
        user.telugu = on
    }

    /** A list as the reader set it up: Telugu headlines left out when turned off. */
    fun shown(items: List<Item>): List<Item> = if (telugu) items else items.filter { it.lang != "te" }
    var answers by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set
    var revise by mutableStateOf<List<ReviseCard>>(emptyList())
        private set
    var hiddenNotes by mutableStateOf<Set<String>>(emptySet())
        private set
    var doneThreads by mutableStateOf<Set<String>>(emptySet())
        private set

    var topicLinks by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** The topic a story belongs to: the reader's correction, else the feed's (older feeds: the story itself). */
    fun threadOf(item: Item): String = topicLinks[item.id]?.ifEmpty { item.id } ?: item.thread.ifBlank { item.id }

    /** A follow-up on a topic from an earlier day (after the reader's corrections). */
    fun isUpdate(item: Item): Boolean = topicLinks[item.id]?.let { it.isNotEmpty() && it != item.id } ?: item.update

    /** Puts a story in a topic ([thread]) or, with "", makes it a topic of its own. */
    fun linkTopic(item: Item, thread: String) {
        topicLinks = topicLinks + (item.id to thread)
        user.setTopicLinks(topicLinks)
    }

    /** Topics that started before [item]'s day (last 3 weeks), newest first: to put it in one. */
    fun earlierTopics(item: Item): List<Item> {
        val from = runCatching { java.time.LocalDate.parse(item.date).minusDays(21).toString() }.getOrDefault("")
        return allItems
            .filter { it.topicOf.isEmpty() && !it.digest && !it.oneLiner && it.date < item.date && it.date >= from && !isUpdate(it) }
            .distinctBy { threadOf(it) }
            .sortedWith(compareByDescending<Item> { it.date }.thenByDescending { it.score })
            .take(40)
    }

    /** A weekly or monthly digest file from the feed (null offline with no copy). */
    suspend fun digest(ref: com.appsc.ca.data.DigestRef) = feed.digest(ref)

    fun isDone(item: Item) = threadOf(item) in doneThreads

    fun toggleDone(item: Item) {
        val t = threadOf(item)
        doneThreads = if (t in doneThreads) doneThreads - t else doneThreads + t
        user.setDoneThreads(doneThreads)
    }

    /** Every story of a topic on this phone, oldest first: the topic's timeline. */
    fun timeline(item: Item): List<Item> {
        val t = threadOf(item)
        return allItems.filter { it.topicOf.isEmpty() && threadOf(it) == t }.distinctBy { it.id }.sortedBy { it.date }
    }

    private fun today() = java.time.LocalDate.now().toEpochDay()

    /** Revision cards due today or earlier. */
    val dueCards: List<ReviseCard> get() = revise.filter { it.due <= today() }

    /** A day's quiz answer; a wrong one goes to revision (back tomorrow). */
    fun answer(q: QuizQ, chosen: Int) {
        if (q.id in answers) return
        val right = chosen == q.answer
        answers = answers + (q.id to right)
        user.setAnswers(answers)
        if (!right && revise.none { it.q.id == q.id }) saveRevise(revise + ReviseCard(q, today() + 1, 0))
    }

    /** A revision answer: right moves it to 3, then 7 days on, then it is learnt; wrong starts it again. */
    fun reviseAnswer(card: ReviseCard, right: Boolean) {
        val gaps = listOf(1L, 3L, 7L)
        val rest = revise.filterNot { it.q.id == card.q.id }
        saveRevise(
            when {
                !right -> rest + card.copy(due = today() + 1, step = 0)
                card.step + 1 >= gaps.size -> rest
                else -> rest + card.copy(due = today() + gaps[card.step + 1], step = card.step + 1)
            },
        )
    }

    private fun saveRevise(cards: List<ReviseCard>) {
        revise = cards
        viewModelScope.launch(Dispatchers.IO) { user.setRevise(cards) }
    }

    /** The story with the static notes hidden on this phone left out. */
    fun visible(item: Item): Item {
        if (hiddenNotes.isEmpty() || item.brief.sections.none { "${item.id}|${it.where}" in hiddenNotes }) return item
        return item.copy(brief = item.brief.copy(sections = item.brief.sections.filterNot { "${item.id}|${it.where}" in hiddenNotes }))
    }

    fun hideNote(item: Item, sec: StaticSection) {
        hiddenNotes = hiddenNotes + "${item.id}|${sec.where}"
        user.setHiddenNotes(hiddenNotes)
    }

    fun chooseRate(r: Float) {
        speechRate = r
        user.speechRate = r
    }

    val allItems: List<Item> get() = days.flatMap { it.items }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val d = feed.cachedDays()
                val i = feed.cachedIndex()
                val tr = feed.cachedTrending()
                val s = user.saved()
                val r = user.readIds()
                val a = user.answers()
                val rv = user.revise()
                val h = user.hiddenNotes()
                val dn = user.doneThreads()
                val tl = user.topicLinks()
                withContext(Dispatchers.Main) {
                    days = d; index = i; trending = tr; saved = s; readIds = r; answers = a; revise = rv; hiddenNotes = h; doneThreads = dn; topicLinks = tl
                    exam = user.exam; notify = user.notify; speechRate = user.speechRate
                    perDay = user.perDay; largeText = user.largeText; telugu = user.telugu
                }
            }
            refresh()
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            message = null
            try {
                lastResult = feed.refresh()
                val d = withContext(Dispatchers.IO) { feed.cachedDays() }
                days = d
                index = feed.cachedIndex()
                trending = withContext(Dispatchers.IO) { feed.cachedTrending() }
                pruneRead(d)
            } catch (e: Exception) {
                message = if (days.isEmpty()) {
                    "Could not download today's updates. Check the internet connection and pull down to try again."
                } else {
                    "Offline — showing the copy on this phone."
                }
            } finally {
                refreshing = false
            }
        }
    }

    fun isSaved(id: String) = saved.any { it.id == id }

    fun toggleSave(item: Item) {
        saved = if (isSaved(item.id)) saved.filterNot { it.id == item.id } else listOf(item) + saved
        val copy = saved
        viewModelScope.launch(Dispatchers.IO) { user.setSaved(copy) }
    }

    fun markRead(id: String) {
        if (id in readIds) return
        readIds = readIds + id
        user.setReadIds(readIds)
    }

    fun chooseExam(e: Exam) {
        exam = e
        user.exam = e
    }

    fun chooseNotify(on: Boolean) {
        notify = on
        user.notify = on
    }

    // Read marks only matter for stories still on the phone.
    private fun pruneRead(d: List<Day>) {
        val live = d.flatMapTo(HashSet()) { day -> day.items.map { it.id } }
        val kept = readIds.filterTo(HashSet()) { it in live }
        if (kept.size != readIds.size) {
            readIds = kept
            user.setReadIds(kept)
        }
    }
}
