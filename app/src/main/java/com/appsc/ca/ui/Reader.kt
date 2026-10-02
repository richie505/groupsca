package com.appsc.ca.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** The coaching sites, opened at their current-affairs pages. */
data class CoachingSite(val name: String, val url: String, val note: String)

val COACHING_SITES = listOf(
    CoachingSite("Vision IAS", "https://visionias.in/current-affairs/news-today", "News Today, daily current affairs and monthly magazine"),
    CoachingSite("Vajiram & Ravi", "https://vajiramandravi.com/current-affairs/", "Daily current affairs, editorials and The Hindu analysis"),
    CoachingSite("Drishti IAS", "https://www.drishtiias.com/current-affairs-news-analysis-editorials", "Daily news analysis, editorials and State PCS current affairs"),
    CoachingSite("KP IAS Academy", "https://kpiasacademy.com/", "Daily APPSC, TGPSC and UPSC current affairs (Hyderabad)"),
    CoachingSite("Civic Centre IAS", "https://www.civiccentre.in/", "APPSC / TGPSC Groups coaching (Hyderabad)"),
)

/**
 * Coaching tab. The sites open inside the app so a paid login is entered once
 * and kept on this phone (in the WebView's own cookie store) — nothing about
 * the account, or what is read, leaves the phone.
 */
@Composable
fun CoachingScreen(onOpen: (String, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Coaching current affairs", style = MaterialTheme.typography.headlineSmall, color = C.Ink)
                Text(
                    "Opens each site inside this app. Log in once with your own subscription and it stays logged in on this " +
                        "phone. Stories from Vajiram and KP IAS in your daily feed open here too (\"Read full analysis\").",
                    style = MaterialTheme.typography.bodyMedium,
                    color = C.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(COACHING_SITES) { site ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(site.name, site.url) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(site.name, style = MaterialTheme.typography.titleMedium, color = C.Ink)
                Text(site.note, style = MaterialTheme.typography.labelMedium, color = C.Muted)
            }
            HorizontalDivider(color = C.Line)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(title: String, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(title) }
    var current by remember { mutableStateOf(url) }
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) {
                    current = u
                    view.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) {
                    progress = p
                }
            }
            loadUrl(url)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            web.destroy()
        }
    }
    BackHandler { if (web.canGoBack()) web.goBack() else onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(pageTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { openUrl(context, current) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser") }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
    }
}
