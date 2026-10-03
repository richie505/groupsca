package com.appsc.ca.platform

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** Something to read: one story, as the pieces read in turn. */
data class SpeechPage(val id: String, val title: String, val parts: List<String>)

/** Where read-aloud is: [active] while a session runs (playing or paused), the page and part being read. */
data class Playback(val active: Boolean = false, val playing: Boolean = false, val pageId: String = "", val part: Int = 0)

/**
 * Ported from the APPSC Prep app (Group-app, platform/ReadAloud.kt), so read-aloud behaves the same:
 * Read-aloud with Android's text-to-speech (offline, Indian English voice first). One session per app:
 * [ReadAloudService] keeps the app running with a notification while it reads, so it carries on with the
 * screen locked or the app in the background; it ends on Stop or when the app is closed.
 */
object ReadAloud {
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    private var page: SpeechPage? = null
    private var part = 0
    private var rate = 1f
    private var next: () -> SpeechPage? = { null }
    // every (re)start of speech gets a new session, so callbacks from cut-off speech are ignored
    private var session = 0
    private var wake: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null

    /** Called the first time reading starts, so the app can ask for permission to show the notification. */
    var onStart: (() -> Unit)? = null

    private val state = mutableStateOf(Playback())
    val playback: State<Playback> get() = state

    val title: String get() = page?.title.orEmpty()

    fun init(context: Context): ReadAloud {
        if (!::app.isInitialized) app = context.applicationContext
        return this
    }

    fun play(page: SpeechPage, from: Int, rate: Float, next: () -> SpeechPage?) {
        this.page = page
        this.part = from.coerceIn(0, (page.parts.size - 1).coerceAtLeast(0))
        this.rate = rate
        this.next = next
        onStart?.invoke()
        ReadAloudService.start(app)
        speakFromHere()
    }

    fun pause() {
        if (page == null) return
        session++
        tts?.stop()
        release()
        publish(playing = false)
    }

    fun resume() {
        if (page != null && !state.value.playing) speakFromHere()
    }

    fun seek(part: Int) {
        val p = page ?: return
        this.part = part.coerceIn(0, p.parts.lastIndex)
        if (state.value.playing) speakFromHere() else publish(playing = false)
    }

    fun setRate(rate: Float) {
        this.rate = rate
        if (state.value.playing) speakFromHere()
    }

    fun stop() {
        session++
        pending = null
        tts?.stop()
        page = null
        release()
        state.value = Playback()
        if (::app.isInitialized) ReadAloudService.stop(app)
    }

    /** The app is closing: stop and free the speech engine. */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun publish(playing: Boolean) {
        val p = page ?: return
        state.value = Playback(active = true, playing = playing, pageId = p.id, part = part)
        ReadAloudService.refresh(app)
    }

    private fun speakFromHere() {
        val p = page ?: return
        val id = ++session
        hold()
        publish(playing = true)
        withEngine {
            val engine = tts ?: return@withEngine
            if (id != session) return@withEngine
            engine.setSpeechRate(rate)
            val max = TextToSpeech.getMaxSpeechInputLength() - 1
            if (p.parts.isEmpty()) return@withEngine pageDone(id)
            for (i in part..p.parts.lastIndex) {
                engine.speak(p.parts[i].take(max), if (i == part) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$id:$i")
            }
        }
    }

    /** The page has been read: go on to the next one, or end the session. */
    private fun pageDone(id: Int) {
        if (id != session) return
        val following = runCatching { next() }.getOrNull()
        if (following == null) {
            stop()
            return
        }
        page = following
        part = 0
        speakFromHere()
    }

    private val listener = object : UtteranceProgressListener() {
        private fun parse(u: String?): Pair<Int, Int>? =
            u?.split(':')?.takeIf { it.size == 2 }?.let { it[0].toInt() to it[1].toInt() }

        override fun onStart(u: String?) {
            val (id, i) = parse(u) ?: return
            main.post {
                if (id == session) {
                    part = i
                    publish(playing = true)
                }
            }
        }

        override fun onDone(u: String?) {
            val (id, i) = parse(u) ?: return
            main.post { if (id == session && i == (page?.parts?.lastIndex ?: -1)) pageDone(id) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(u: String?) = onDone(u)
    }

    private fun withEngine(block: () -> Unit) {
        if (ready) return block()
        pending = block
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            main.post {
                val engine = tts ?: return@post
                if (status != TextToSpeech.SUCCESS) return@post
                // Indian English first, then English, then the phone's default voice
                listOf(Locale("en", "IN"), Locale.UK, Locale.US)
                    .firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                    ?.let { engine.language = it }
                engine.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                engine.setOnUtteranceProgressListener(listener)
                ready = true
                pending?.invoke()
                pending = null
            }
        }
    }

    // ---- keep running with the screen off, and give way to calls ----

    private fun hold() {
        if (wake?.isHeld != true) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "appscca:readaloud").apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60 * 1000L)
            }
        }
        if (focus == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) main.post { pause() }
                }
                .build()
                .also { am.requestAudioFocus(it) }
        }
    }

    private fun release() {
        wake?.takeIf { it.isHeld }?.release()
        wake = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus?.let { (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        }
        focus = null
    }

    /** Opens the app from the notification. */
    fun openApp(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
