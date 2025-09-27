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
 * Editor 행(List<Row>) ↔ HTML/JSON 직렬화/역직렬화 담당 유틸
 * - HTML: 에디터 저장/불러오기용 (파일, DB 등)
 * - JSON: Undo/Redo 스냅샷용 (히스토리 기록)
 */
object EditorSerializer {

    // ---------- HTML 직렬화 ----------
    /** Row 리스트 → HTML 문자열로 변환 */
    fun toHtml(rows: List<Row>): String {
        val sb = StringBuilder()
        sb.append("<div class='editor'>") // 전체 wrapper

        rows.forEach { row ->
            when (row) {
                is Row.TextRow -> {
                    // Spannable → HTML 변환
                    val htmlText = Html.toHtml(
                        row.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE
                    )
                    sb.append("<div class='text'>").append(htmlText).append("</div>")
                }

                is Row.ImageRow -> {
                    // 이미지들을 sub-block 단위로 묶어서 HTML 변환
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

                    // 세로사진은 2개씩, 가로사진은 1개씩 묶어서 flush
                    row.items.forEach { item ->
                        val cap = if (item.isPortrait) 2 else 1
                        buffer.add(item)
                        if (buffer.size == cap) flush()
                    }
                    flush()
                    sb.append("</div>")
                }

                is Row.VideoRow -> {
                    // 동영상 URI를 data 속성에 저장
                    sb.append("<div class='video' data-uri='")
                        .append(row.uri).append("'></div>")
                }

                is Row.YoutubeRow -> {
                    // 유튜브 URL을 escape 처리 후 data 속성에 저장
                    sb.append("<div class='youtube' data-url='")
                        .append(escapeHtml(row.url)).append("'></div>")
                }
            }
        }

        sb.append("</div>")
        return sb.toString()
    }

    /** HTML 문자열 → Row 리스트로 변환 */
    fun fromHtml(context: Context, html: String): List<Row> {
        val rows = mutableListOf<Row>()
        var i = 0

        while (i < html.length) {
            when {
                // --- 텍스트 Row ---
                html.regionMatches(
                    i,
                    "<div class='text'>",
                    0,
                    "<div class='text'>".length,
                    true
                ) -> {
                    val end = html.indexOf("</div>", i, true)
                    val inner =
                        if (end > i) html.substring(i + "<div class='text'>".length, end) else ""
                    val spanned: Spanned = Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY)
                    rows.add(Row.TextRow(SpannableStringBuilder(spanned)))
                    i = if (end > 0) end + 6 else html.length
                }
                // --- 구버전 <p> 태그 대응 ---
                html.regionMatches(i, "<p>", 0, 3, true) -> {
                    val end = html.indexOf("</p>", i, true)
                    val inner = if (end > i) html.substring(i, end + 4) else ""
                    val spanned: Spanned = Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY)
                    rows.add(Row.TextRow(SpannableStringBuilder(spanned)))
                    i = if (end > 0) end + 4 else html.length
                }
                // --- 이미지 Row ---
                html.regionMatches(
                    i,
                    "<div class='img-row'>",
                    0,
                    "<div class='img-row'>".length,
                    true
                ) -> {
                    val endStart = findMatchingDiv(html, i) // 중첩 div 닫기 찾기
                    val end = if (endStart >= 0) endStart + 6 else html.length
                    val block = html.substring(i, end)

                    // <img src='...'> 정규식으로 추출
                    val imgUris = Regex("<img\\s+src='(.*?)'/?>(?s)", RegexOption.IGNORE_CASE)
                        .findAll(block).map { Uri.parse(it.groupValues[1]) }.toList()

                    val items = imgUris.map { uri -> ImageItem(uri, isPortrait(context, uri)) }
                    rows.add(Row.ImageRow(items))

                    i = end
                }
                // --- 비디오 Row ---
                html.regionMatches(
                    i,
                    "<div class='video'",
                    0,
                    "<div class='video'".length,
                    true
                ) -> {
                    val m = Regex("data-uri='(.*?)'", RegexOption.IGNORE_CASE).find(html, i)
                    val uri = m?.groupValues?.getOrNull(1)?.let(Uri::parse)
                    if (uri != null) rows.add(Row.VideoRow(uri))
                    val end = html.indexOf("</div>", i, true)
                    i = if (end > 0) end + 6 else html.length
                }
                // --- 유튜브 Row ---
                html.regionMatches(
                    i,
                    "<div class='youtube'",
                    0,
                    "<div class='youtube'".length,
                    true
                ) -> {
                    val m = Regex("data-url='(.*?)'", RegexOption.IGNORE_CASE).find(html, i)
                    val url = m?.groupValues?.getOrNull(1)
                    if (!url.isNullOrBlank()) rows.add(Row.YoutubeRow(unescapeHtml(url)))
                    val end = html.indexOf("</div>", i, true)
                    i = if (end > 0) end + 6 else html.length
                }

                else -> i++ // 다음 문자로 이동
            }
        }

        // 최소 하나의 TextRow 보장
        if (rows.none { it is Row.TextRow }) rows.add(Row.TextRow())
        return rows
    }

    // ---------- JSON 직렬화 (Undo/Redo) ----------
    /** Row 리스트 → JSON 문자열 변환 */
    fun toJson(rows: List<Row>): String {
        val root = org.json.JSONObject()
        val arr = org.json.JSONArray()

        rows.forEach { row ->
            val obj = org.json.JSONObject()
            when (row) {
                is Row.TextRow -> {
                    obj.put("type", "text")
                    obj.put("text", row.text.toString())

                    // 스타일(볼드/이탤릭/언더라인) 정보 직렬화
                    val spansArr = org.json.JSONArray()

                    // Bold/Italic 스타일 추출
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
                    // Underline 스타일 추출
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

    /** JSON 문자열 → Row 리스트 복원 */
    fun fromJson(json: String): List<Row> {
        val rows = mutableListOf<Row>()
        try {
            val root = org.json.JSONObject(json)
            val arr = root.getJSONArray("rows")

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                when (obj.getString("type")) {
                    "text" -> {
                        // 텍스트 복원
                        val text = obj.optString("text", "")
                        val s = SpannableStringBuilder(text)

                        // 스타일 span 복원
                        val spansArr = obj.optJSONArray("spans") ?: org.json.JSONArray()
                        for (j in 0 until spansArr.length()) {
                            val sp = spansArr.getJSONObject(j)
                            val t = sp.getString("t")
                            val st = sp.getInt("s")
                            val en = sp.getInt("e")

                            if (st in 0..s.length && en in 0..s.length && st < en) {
                                when (t) {
                                    "b" -> s.setSpan(
                                        StyleSpan(Typeface.BOLD),
                                        st,
                                        en,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    "i" -> s.setSpan(
                                        StyleSpan(Typeface.ITALIC),
                                        st,
                                        en,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    "u" -> s.setSpan(
                                        UnderlineSpan(),
                                        st,
                                        en,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                }
                            }
                        }
                        rows.add(Row.TextRow(s))
                    }

                    "image" -> {
                        // 이미지 복원
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
            // 실패 시 기본 TextRow 하나 생성
            rows.clear()
            rows.add(Row.TextRow())
        }
        return rows
    }

    // ---------- 내부 헬퍼 ----------
    /**
     * <div> 태그의 중첩 구조에서 매칭되는 </div> 닫기 위치 찾기
     * - depth 카운트를 이용해 중첩 div 처리
     */
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
