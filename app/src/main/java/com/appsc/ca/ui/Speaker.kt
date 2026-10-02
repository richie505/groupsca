package com.appsc.ca.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Reads a list of stories aloud (Indian English voice when the phone has one). */
class Speaker(context: Context) {
    var speaking by mutableStateOf(false)
        private set
    private var ready = false
    private var pending: List<String>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val india = Locale("en", "IN")
            if (tts.isLanguageAvailable(india) >= TextToSpeech.LANG_AVAILABLE) tts.language = india
            pending?.let { speak(it) }
            pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == LAST) speaking = false
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                speaking = false
            }
        })
    }

    fun speak(parts: List<String>) {
        if (parts.isEmpty()) return
        if (!ready) {
            pending = parts
            return
        }
        speaking = true
        tts.stop()
        parts.forEachIndexed { i, p ->
            val id = if (i == parts.lastIndex) LAST else "p$i"
            tts.speak(spoken(p), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun stop() {
        tts.stop()
        speaking = false
    }

    fun shutdown() = tts.shutdown()

    /** Say the short forms in full: ₹ 5,000 crore → 5,000 crore rupees, Rs. → rupees. */
    private fun spoken(s: String): String = s
        .replace(Regex("(?:₹|Rs\\.?)\\s?([\\d,.]+)\\s*(crore|lakh|billion|million)?")) {
            "${it.groupValues[1]} ${it.groupValues[2]} rupees".replace("  ", " ")
        }
        .replace("%", " per cent")
        .replace(Regex("\\bAP\\b"), "Andhra Pradesh")
        .replace(Regex("\\bGI\\b"), "G I")

    private companion object {
        const val LAST = "last"
    }
}
