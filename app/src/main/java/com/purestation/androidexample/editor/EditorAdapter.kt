package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class EditorAdapter(
    private val context: Context,
    /** 데이터 변경(Undo 스냅샷 등) 시 알림 */
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<Row>()
    var currentFocusedTextHolder: TextVH? = null
        internal set

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.TextRow -> 0
        is Row.ImageRow -> 1
        is Row.VideoRow -> 2
        is Row.YoutubeRow -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            0 -> TextVH(makeTextRowView(parent))
            1 -> ImageVH(makeImageRowView(parent))
            2 -> VideoVH(makeVideoRowView(parent))
            else -> YoutubeVH(makeYoutubeRowView(parent))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TextVH -> holder.bind(rows[position] as Row.TextRow)
            is ImageVH -> holder.bind(rows[position] as Row.ImageRow)
            is VideoVH -> holder.bind(rows[position] as Row.VideoRow)
            is YoutubeVH -> holder.bind(rows[position] as Row.YoutubeRow)
        }
    }

    override fun getItemCount(): Int = rows.size

    // ---------- 외부에서 사용하는 API ----------
    fun addTextRow(afterLast: Boolean = false) {
        val row = Row.TextRow()
        if (afterLast) {
            rows.add(row)
            notifyItemInserted(rows.lastIndex)
        } else {
            rows.add(0, row)
            notifyItemInserted(0)
        }
    }

    fun addImageRow(uris: List<Uri>) {
        val items = uris.map { uri -> ImageItem(uri, isPortrait(context, uri)) }
        rows.add(Row.ImageRow(items))
        notifyItemInserted(rows.lastIndex)
    }

    fun addVideoRow(uri: Uri) {
        rows.add(Row.VideoRow(uri))
        notifyItemInserted(rows.lastIndex)
    }

    fun addYoutubeRow(url: String) {
        rows.add(Row.YoutubeRow(url))
        notifyItemInserted(rows.lastIndex)
    }

    fun move(from: Int, to: Int) {
        if (from == to) return
        val row = rows.removeAt(from)
        rows.add(to, row)
        notifyItemMoved(from, to)
        onChanged()
    }

    // ---------- HTML (저장/불러오기) ----------
    fun toHtml(): String {
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

    // 시작 인덱스(start)는 "<div" 의 '<' 에 위치해야 함
    private fun findMatchingDiv(html: String, start: Int): Int {
        var i = start
        var depth = 0
        while (i < html.length) {
            if (html.regionMatches(i, "<div", 0, 4, ignoreCase = true)) {
                depth++
                i += 4
                continue
            }
            if (html.regionMatches(i, "</div>", 0, 6, ignoreCase = true)) {
                depth--
                if (depth == 0) return i
                i += 6
                continue
            }
            i++
        }
        return -1
    }

    fun fromHtml(html: String) {
        rows.clear()
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
                html.regionMatches(i, "<p>", 0, 3, true) -> {
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
        notifyDataSetChanged()
    }

    // ---------- JSON (Undo/Redo 스냅샷 전용) ----------
    fun toJson(): String {
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

    fun fromJson(json: String) {
        rows.clear()
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
        notifyDataSetChanged()
    }

    // ---------- ViewHolder들 ----------
    inner class TextVH(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val editText = (container.getChildAt(0) as EditText)

        private var boundRow: Row.TextRow? = null
        private var localBold = false
        private var localItalic = false
        private var localUnderline = false

        private var lastStart: Int = -1
        private var lastCount: Int = 0
        private var suppressWatcher = false

        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lastStart = start
                lastCount = count
            }
            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return

                if (lastStart >= 0 && lastCount > 0 && s != null) {
                    applyStyleSpans(s, lastStart, lastStart + lastCount, localBold, localItalic, localUnderline)
                }
                boundRow?.text = SpannableStringBuilder(s ?: "")
                onChanged()
            }
        }

        init {
            editText.addTextChangedListener(watcher)
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    currentFocusedTextHolder = this
                    // 바깥에서 토글 상태를 적용할 수 있게 그대로 대기
                } else if (currentFocusedTextHolder == this) {
                    currentFocusedTextHolder = null
                }
            }
        }

        fun bind(row: Row.TextRow) {
            boundRow = row
            suppressWatcher = true
            editText.text = SpannableStringBuilder(row.text)
            suppressWatcher = false
        }

        /** RichEditorView에서 호출됨 */
        fun applyTypingFlags(b: Boolean, i: Boolean, u: Boolean) {
            localBold = b; localItalic = i; localUnderline = u

            val selStart = editText.selectionStart
            val selEnd = editText.selectionEnd
            if (selStart in 0..selEnd && selStart != selEnd) {
                suppressWatcher = true
                applyStyleSpans(editText.editableText, selStart, selEnd, b, i, u)
                boundRow?.text = SpannableStringBuilder(editText.editableText)
                suppressWatcher = false
                onChanged()
            }
        }

        private fun applyStyleSpans(
            e: Editable, start: Int, end: Int,
            bold: Boolean, italic: Boolean, underline: Boolean
        ) {
            if (start >= end) return
            if (bold) e.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (italic) e.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (underline) e.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    inner class ImageVH(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        fun bind(row: Row.ImageRow) {
            container.removeAllViews()
            var buffer = mutableListOf<ImageItem>()
            fun flush() {
                if (buffer.isEmpty()) return
                val sub = LinearLayout(container.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = Gravity.CENTER
                }
                buffer.forEach { item ->
                    val iv = ImageView(container.context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, container.context.dp(160), 1f).also {
                            it.setMargins(container.context.dp(4), container.context.dp(4), container.context.dp(4), container.context.dp(4))
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    Glide.with(iv).load(item.uri).into(iv)
                    sub.addView(iv)
                }
                container.addView(sub)
                buffer = mutableListOf()
            }

            row.items.forEach { item ->
                val cap = if (item.isPortrait) 2 else 1
                buffer.add(item)
                if (buffer.size == cap) flush()
            }
            flush()
        }
    }

    inner class VideoVH(val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
        private val thumbnail = ImageView(frame.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, frame.context.dp(200)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        private val play = TextView(frame.context).apply {
            text = "▶"
            textSize = 48f
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
        }
        init {
            frame.removeAllViews()
            frame.addView(thumbnail)
            frame.addView(play)
        }

        fun bind(row: Row.VideoRow) {
            if (row.thumb == null) {
                row.thumb = createVideoThumbnail(frame.context, row.uri)
            }
            row.thumb?.let { thumbnail.setImageBitmap(it) }
        }
    }

    inner class YoutubeVH(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val thumb = ImageView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, container.context.dp(200)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        private val urlText = TextView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(container.context.dp(8), container.context.dp(4), container.context.dp(8), container.context.dp(12))
            setTextColor(Color.DKGRAY)
            textSize = 14f
        }
        init {
            container.removeAllViews()
            container.addView(thumb)
            container.addView(urlText)
        }
        fun bind(row: Row.YoutubeRow) {
            val id = extractYoutubeId(row.url)
            val thumbUrl = id?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            row.thumbUrl = thumbUrl
            Glide.with(thumb).load(thumbUrl).into(thumb)
            urlText.text = row.url
        }
    }

    // ---------- Row별 뷰 생성 ----------
    private fun makeTextRowView(parent: ViewGroup): LinearLayout =
        LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
            val et = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 1
            }
            addView(et)
        }

    private fun makeImageRowView(parent: ViewGroup): LinearLayout =
        LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

    private fun makeVideoRowView(parent: ViewGroup): FrameLayout =
        FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

    private fun makeYoutubeRowView(parent: ViewGroup): LinearLayout =
        LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
}
