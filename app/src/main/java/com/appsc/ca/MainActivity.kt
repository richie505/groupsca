package com.appsc.ca

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.appsc.ca.data.SpeechText
import com.appsc.ca.platform.ReadAloud
import com.appsc.ca.ui.App
import com.appsc.ca.ui.AppViewModel
import com.appsc.ca.ui.CaTheme
import com.appsc.ca.work.RefreshWorker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onDestroy() {
        // closing the app ends read-aloud (as in the APPSC Prep app)
        ReadAloud.onStart = null
        if (isFinishing) ReadAloud.shutdown()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        ReadAloud.init(this)
        // the short forms the notes define, so read-aloud says them in full (assets/abbr.json, from the Prep app)
        if (SpeechText.fromNotes.isEmpty()) {
            thread {
                runCatching {
                    val text = assets.open("abbr.json").bufferedReader().use { it.readText() }
                    SpeechText.fromNotes = Json.parseToJsonElement(text).jsonObject
                        .mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
                }
            }
        }
        setContent {
            CaTheme {
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    // the daily-update and read-aloud notifications
                    if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                App(vm)
            }
        }
    }
}
