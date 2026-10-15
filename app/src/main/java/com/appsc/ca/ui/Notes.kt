package com.appsc.ca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Notes in the Rocket notes form (as in the Rocket Prep app): "# 1. Heading", "- **Label:** value",
// "  - sub-point", tables, "⚠ Trap" lines and a "> Exam angle:" callout. [GK] marks static background.

sealed interface NoteBlock
data class NoteLine(val kind: Char, val text: String) : NoteBlock
data class NoteTable(val head: List<String>, val rows: List<List<String>>) : NoteBlock

fun parseNotes(md: String): List<NoteBlock> {
    val out = mutableListOf<NoteBlock>()
    val lines = md.lines()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i++]
        val line = raw.trimEnd()
        val t = line.trim()
        when {
            t.isEmpty() -> {}
            t.startsWith("|") -> {
                val group = mutableListOf(t)
                while (i < lines.size && lines[i].trim().startsWith("|")) group += lines[i++].trim()
                val cells = group.map { r -> r.trim('|').split('|').map { it.trim() } }
                    .filterNot { r -> r.all { it.matches(Regex(":?-{2,}:?")) || it.isEmpty() } }
                if (cells.isNotEmpty()) {
                    val n = cells[0].size
                    out += NoteTable(cells[0], cells.drop(1).map { (it + List(n) { "" }).take(n) })
                }
            }
            t.startsWith("# ") || t.startsWith("## ") -> out += NoteLine('h', t.trimStart('#').trim())
            t.startsWith(">") -> out += NoteLine('x', t.removePrefix(">").trim())
            line.startsWith("  ") && t.startsWith("- ") -> out += NoteLine(if (t.contains("⚠")) 't' else 's', t.removePrefix("- "))
            t.startsWith("- ") -> out += NoteLine(if (t.contains("⚠")) 't' else 'b', t.removePrefix("- "))
            else -> out += NoteLine('p', t)
        }
    }
    return out
}

/** **bold** runs in bold, [GK] small and grey. */
fun noteText(text: String, base: Color = C.Body, strong: Color = C.Ink): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in Regex("""\*\*(.+?)\*\*|\[GK]""").findAll(text)) {
        if (m.range.first > at) withStyle(SpanStyle(color = base)) { append(text.substring(at, m.range.first)) }
        if (m.value == "[GK]") withStyle(SpanStyle(color = C.Faint, fontSize = 0.78.em)) { append("[GK]") }
        else withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(m.groupValues[1]) }
        at = m.range.last + 1
    }
    if (at < text.length) withStyle(SpanStyle(color = base)) { append(text.substring(at)) }
}

@Composable
fun NotesView(md: String, readId: String? = null) {
    val blocks = remember(md) { parseNotes(md) }
    val body = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = C.Body)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        blocks.forEachIndexed { i, b -> ReadLine(readId, "note:$i") { NoteBlockView(b, body) } }
    }
}

@Composable
private fun NoteBlockView(b: NoteBlock, body: TextStyle) {
    Column {
        when (b) {
            is NoteTable -> NoteTableView(b)
            is NoteLine -> when (b.kind) {
                'h' -> Text(remember(b) { noteText(b.text, C.Ink, C.Ink) }, style = body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                'b', 's' -> {
                    val sub = b.kind == 's'
                    Row(Modifier.padding(start = if (sub) 22.dp else 4.dp, top = 3.dp, bottom = 3.dp)) {
                        Text(if (sub) "–" else "•", style = body.copy(color = if (sub) C.Muted else C.Ink, fontWeight = FontWeight.Bold), modifier = Modifier.width(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(remember(b) { noteText(b.text) }, style = if (sub) body.copy(fontSize = 15.sp) else body)
                    }
                }
                't' -> Callout(b.text.replace("⚠", "").trim().removePrefix("**Trap:**").removePrefix("**Traps:**").trim(), C.CriticalSoft, C.Critical, "⚠ Trap")
                'x' -> Callout(b.text.removePrefix("Exam angle:").trim(), C.ExamBg, C.ExamInk, "Exam angle")
                else -> Text(remember(b) { noteText(b.text) }, style = body, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun Callout(text: String, bg: Color, ink: Color, label: String) {
    Row(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(ink))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink, letterSpacing = 0.8.sp))
            Spacer(Modifier.height(2.dp))
            Text(remember(text) { noteText(text, ink, ink) }, style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontStyle = FontStyle.Normal))
        }
    }
}

@Composable
private fun NoteTableView(t: NoteTable) {
    val cols = maxOf(t.head.size, t.rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return
    val style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = C.Body)
    val all = listOf(t.head) + t.rows
    // narrow tables fit the card; wider ones scroll sideways
    val fit = cols <= 3
    Box(
        Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp))
            .then(if (fit) Modifier else Modifier.horizontalScroll(rememberScrollState())),
    ) {
        Column(if (fit) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max)) {
            all.forEachIndexed { ri, row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .background(if (ri == 0) C.AccentSoft else if (ri % 2 == 0) C.Surface else Color.White),
                ) {
                    for (c in 0 until cols) {
                        val cell = row.getOrNull(c) ?: ""
                        Text(
                            remember(cell, ri) { if (ri == 0) noteText("**${cell.replace("**", "")}**", C.Accent, C.Accent) else noteText(cell) },
                            style = style,
                            modifier = (if (fit) Modifier.weight(1f) else Modifier.widthIn(min = 90.dp, max = 220.dp))
                                .fillMaxHeight().padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        if (c < cols - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
                    }
                }
                if (ri < all.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
            }
        }
    }
}
