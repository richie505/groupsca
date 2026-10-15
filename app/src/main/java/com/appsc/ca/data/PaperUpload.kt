package com.appsc.ca.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/*
 * Today's newspaper PDF (The Hindu, Eenadu) sent for reading: uploaded to the "inbox" release of the private
 * richie505/groupsca-papers repository, then the server's "paper.yml" job is started. The token is the user's own
 * GitHub fine-grained token (Contents on groupsca-papers, Actions on groupsca), typed into Settings and kept only
 * on this phone. The OpenAI key is never here: the reading happens on GitHub's servers.
 */
object PaperUpload {
    const val INBOX = "richie505/groupsca-papers"
    const val FEED_REPO = "richie505/groupsca"
    private const val API = "https://api.github.com"

    enum class Paper(val key: String, val label: String) { HINDU("hindu", "The Hindu"), EENADU("eenadu", "Eenadu") }

    /** The paper from a file name, or null to ask. */
    fun guess(name: String): Paper? {
        val n = name.lowercase()
        return when {
            "eenadu" in n || "ఈనాడు" in n -> Paper.EENADU
            "hindu" in n || n.startsWith("th_") -> Paper.HINDU
            else -> null
        }
    }

    fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "paper.pdf"

    private fun sizeOf(context: Context, uri: Uri): Long =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L

    private fun call(method: String, url: String, token: String, body: String? = null): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    private fun fail(what: String, code: Int, text: String): Nothing {
        val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
        throw IOException(
            when (code) {
                401 -> "The token was refused. Check it in Settings → Newspaper upload."
                403, 404 -> "$what: no access ($code). The token needs Contents (read and write) on groupsca-papers and Actions (read and write) on groupsca."
                else -> "$what failed ($code) $msg"
            },
        )
    }

    /** The "inbox" release's id, made the first time. */
    private fun inbox(token: String): Long {
        val (code, text) = call("GET", "$API/repos/$INBOX/releases/tags/inbox", token)
        if (code == 200) return JSONObject(text).getLong("id")
        if (code != 404) fail("Opening the inbox", code, text)
        val (c2, t2) = call("POST", "$API/repos/$INBOX/releases", token, JSONObject().put("tag_name", "inbox").put("name", "inbox").put("body", "Newspaper PDFs waiting to be read").toString())
        if (c2 !in 200..299) fail("Making the inbox (does groupsca-papers have a README commit?)", c2, t2)
        return JSONObject(t2).getLong("id")
    }

    /** Uploads one PDF as "<date>__<paper>__<n>.pdf" and starts the reading. [progress] gets 0..1. */
    fun send(context: Context, uri: Uri, paper: Paper, date: String, n: Int, token: String, progress: (Float) -> Unit) {
        val id = inbox(token)
        val name = "${date}__${paper.key}__$n.pdf"
        // the same name sent again replaces the first upload
        val (lc, lt) = call("GET", "$API/repos/$INBOX/releases/$id/assets?per_page=100", token)
        if (lc == 200) {
            val assets = JSONArray(lt)
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == name) call("DELETE", "$API/repos/$INBOX/releases/assets/${a.getLong("id")}", token)
            }
        }
        val size = sizeOf(context, uri)
        val conn = URL("https://uploads.github.com/repos/$INBOX/releases/$id/assets?name=${URLEncoder.encode(name, "UTF-8")}").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 300000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Content-Type", "application/pdf")
        if (size > 0) conn.setFixedLengthStreamingMode(size) else conn.setChunkedStreamingMode(256 * 1024)
        context.contentResolver.openInputStream(uri)?.use { input ->
            conn.outputStream.use { out ->
                val buf = ByteArray(256 * 1024)
                var sent = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    sent += r
                    if (size > 0) progress(sent.toFloat() / size)
                }
            }
        } ?: throw IOException("Could not open the PDF")
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) fail("Uploading", code, text)
    }

    /** Starts the server's reading job (paper.yml) once the papers are in. */
    fun startReading(token: String) {
        val (code, text) = call("POST", "$API/repos/$FEED_REPO/actions/workflows/paper.yml/dispatches", token, """{"ref":"main"}""")
        if (code !in 200..299) fail("Starting the reading", code, text)
    }
}
