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
import com.appsc.ca.data.RefreshResult
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
                val s = user.saved()
                val r = user.readIds()
                withContext(Dispatchers.Main) {
                    days = d; index = i; saved = s; readIds = r
                    exam = user.exam; notify = user.notify; speechRate = user.speechRate
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
