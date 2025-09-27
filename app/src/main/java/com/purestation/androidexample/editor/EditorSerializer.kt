package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

/**
 * Editor 행(List<Row>) ↔ HTML/JSON 직렬화/역직렬화 전담 유틸.
 * - HTML: 에디터 저장/불러오기용
 * - JSON: Undo/Redo 스냅샷용
 */
object EditorSerializer {

    // ---------- HTML ----------
    fun toHtml(rows: List<Row>): String {
        val sb = StringBuilder()
        sb.append("<div class='editor'>")
        rows.forEach { row ->
            when (row) {
                is Row.TextRow -> {
                    val htmlText = Html.toHtml(
                        row.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
                    )
                    sb.append("<div class='text'>").append(htmlText).append("</div>")
                }
                is Row.ImageRow -> {
                    sb.append("<div class='img-row'>")
                    var buffer = mutableListOf<ImageItem>()
                    fun flush() {
                        if (buffer.isEmpty()) return
                        sb.append("<div class='sub'>")
                        buffer.forEach { item ->
                            sb.append("<img src='").append(item.uri).append("'/>")
                        }
                        sb.append("</div>")
                        buffer = mutableListOf()
                    }
                    row.items.forEach { item ->
                        val cap = if (item.isPortrait) 2 else 1
                        buffer.add(item)
                        if (buffer.size == cap) flush()
                    }
                    flush()
                    sb.append("</div>")
                }
                is Row.VideoRow -> {
                    sb.append("<div class='video' data-uri='")
                        .append(row.uri).append("'></div>")
                }
                is Row.YoutubeRow -> {
                    sb.append("<div class='youtube' data-url='")
                        .append(escapeHtml(row.url)).append("'></div>")
                }
            }
        }
        sb.append("</div>")
        return sb.toString()
    }

    fun fromHtml(context: Context, html: String): List<Row> {
        val rows = mutableListOf<Row>()
        var i = 0
        while (i < html.length) {
            when {
                html.regionMatches(i, "<div class='text'>", 0, "<div class='text'>".length, true) -> {
                    val end = html.indexOf("</div>", i, true)
                    val inner = if (end > i) html.substring(i + "<div class='text'>".length, end) else ""
                    val spanned: Spanned = Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY)
                    rows.add(Row.TextRow(SpannableStringBuilder(spanned)))
                    i = if (end > 0) end + 6 else html.length
                }
                html.regionMatches(i, "<p>", 0, 3, true) -> { // 구버전 폴백
                    val end = html.indexOf("</p>", i, true)
                    val inner = if (end > i) html.substring(i, end + 4) else ""
                    val spanned: Spanned = Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY)
                    rows.add(Row.TextRow(SpannableStringBuilder(spanned)))
                    i = if (end > 0) end + 4 else html.length
                }
                html.regionMatches(i, "<div class='img-row'>", 0, "<div class='img-row'>".length, true) -> {
                    val endStart = findMatchingDiv(html, i)
                    val end = if (endStart >= 0) endStart + 6 else html.length
                    val block = html.substring(i, end)

                    val imgUris = Regex("<img\\s+src='(.*?)'/?>(?s)", RegexOption.IGNORE_CASE)
                        .findAll(block).map { Uri.parse(it.groupValues[1]) }.toList()

                    val items = imgUris.map { uri -> ImageItem(uri, isPortrait(context, uri)) }
                    rows.add(Row.ImageRow(items))

                    i = end
                }
                html.regionMatches(i, "<div class='video'", 0, "<div class='video'".length, true) -> {
                    val m = Regex("data-uri='(.*?)'", RegexOption.IGNORE_CASE).find(html, i)
                    val uri = m?.groupValues?.getOrNull(1)?.let(Uri::parse)
                    if (uri != null) rows.add(Row.VideoRow(uri))
                    val end = html.indexOf("</div>", i, true)
                    i = if (end > 0) end + 6 else html.length
                }
                html.regionMatches(i, "<div class='youtube'", 0, "<div class='youtube'".length, true) -> {
                    val m = Regex("data-url='(.*?)'", RegexOption.IGNORE_CASE).find(html, i)
                    val url = m?.groupValues?.getOrNull(1)
                    if (!url.isNullOrBlank()) rows.add(Row.YoutubeRow(unescapeHtml(url)))
                    val end = html.indexOf("</div>", i, true)
                    i = if (end > 0) end + 6 else html.length
                }
                else -> i++
            }
        }
        if (rows.none { it is Row.TextRow }) rows.add(Row.TextRow())
        return rows
    }

    // ---------- JSON (Undo/Redo 스냅샷) ----------
    fun toJson(rows: List<Row>): String {
        val root = org.json.JSONObject()
        val arr = org.json.JSONArray()
        rows.forEach { row ->
            val obj = org.json.JSONObject()
            when (row) {
                is Row.TextRow -> {
                    obj.put("type", "text")
                    obj.put("text", row.text.toString())
                    val spansArr = org.json.JSONArray()
                    val styleSpans = row.text.getSpans(0, row.text.length, StyleSpan::class.java)
                    for (sp in styleSpans) {
                        val st = row.text.getSpanStart(sp)
                        val en = row.text.getSpanEnd(sp)
                        if (st >= 0 && en > st) {
                            when (sp.style) {
                                Typeface.BOLD -> spansArr.put(org.json.JSONObject().apply {
                                    put("t", "b"); put("s", st); put("e", en)
                                })
                                Typeface.ITALIC -> spansArr.put(org.json.JSONObject().apply {
                                    put("t", "i"); put("s", st); put("e", en)
                                })
                            }
                        }
                    }
                    val ulSpans = row.text.getSpans(0, row.text.length, UnderlineSpan::class.java)
                    for (sp in ulSpans) {
                        val st = row.text.getSpanStart(sp)
                        val en = row.text.getSpanEnd(sp)
                        if (st >= 0 && en > st) {
                            spansArr.put(org.json.JSONObject().apply {
                                put("t", "u"); put("s", st); put("e", en)
                            })
                        }
                    }
                    obj.put("spans", spansArr)
                }
                is Row.ImageRow -> {
                    obj.put("type", "image")
                    val items = org.json.JSONArray()
                    row.items.forEach {
                        items.put(org.json.JSONObject().apply {
                            put("uri", it.uri.toString())
                            put("portrait", it.isPortrait)
                        })
                    }
                    obj.put("items", items)
                }
                is Row.VideoRow -> {
                    obj.put("type", "video")
                    obj.put("uri", row.uri.toString())
                }
                is Row.YoutubeRow -> {
                    obj.put("type", "youtube")
                    obj.put("url", row.url)
                }
            }
            arr.put(obj)
        }
        root.put("rows", arr)
        return root.toString()
    }

    fun fromJson(json: String): List<Row> {
        val rows = mutableListOf<Row>()
        try {
            val root = org.json.JSONObject(json)
            val arr = root.getJSONArray("rows")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                when (obj.getString("type")) {
                    "text" -> {
                        val text = obj.optString("text", "")
                        val s = SpannableStringBuilder(text)
                        val spansArr = obj.optJSONArray("spans") ?: org.json.JSONArray()
                        for (j in 0 until spansArr.length()) {
                            val sp = spansArr.getJSONObject(j)
                            val t = sp.getString("t")
                            val st = sp.getInt("s")
                            val en = sp.getInt("e")
                            if (st in 0..s.length && en in 0..s.length && st < en) {
                                when (t) {
                                    "b" -> s.setSpan(StyleSpan(Typeface.BOLD), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    "i" -> s.setSpan(StyleSpan(Typeface.ITALIC), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    "u" -> s.setSpan(UnderlineSpan(), st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                            }
                        }
                        rows.add(Row.TextRow(s))
                    }
                    "image" -> {
                        val itemsArr = obj.getJSONArray("items")
                        val list = mutableListOf<ImageItem>()
                        for (j in 0 until itemsArr.length()) {
                            val it = itemsArr.getJSONObject(j)
                            val uri = Uri.parse(it.getString("uri"))
                            val portrait = it.getBoolean("portrait")
                            list.add(ImageItem(uri, portrait))
                        }
                        rows.add(Row.ImageRow(list))
                    }
                    "video" -> rows.add(Row.VideoRow(Uri.parse(obj.getString("uri"))))
                    "youtube" -> rows.add(Row.YoutubeRow(obj.getString("url")))
                }
            }
        } catch (_: Exception) {
            rows.clear()
            rows.add(Row.TextRow())
        }
        return rows
    }

    // ---------- 내부 헬퍼 ----------
    // 시작 인덱스(start)는 "<div"의 '<' 위치여야 함
    private fun findMatchingDiv(html: String, start: Int): Int {
        var i = start
        var depth = 0
        while (i < html.length) {
            if (html.regionMatches(i, "<div", 0, 4, true)) {
                depth++; i += 4; continue
            }
            if (html.regionMatches(i, "</div>", 0, 6, true)) {
                depth--; if (depth == 0) return i; i += 6; continue
            }
            i++
        }
        return -1
    }
}
