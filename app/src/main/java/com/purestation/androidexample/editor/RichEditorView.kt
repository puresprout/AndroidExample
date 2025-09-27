package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.max

/**
 * 수정본 RichEditorView
 * - 툴바 HorizontalScrollView 적용(모든 버튼 노출)
 * - B/I/U 입력 즉시 적용 + 선택 영역 즉시 반영
 * - Undo/Redo: JSON 스냅샷(서식 유지)
 * - 저장/불러오기: Html.toHtml/Html.fromHtml로 줄바꿈/서식 보존
 * - 이미지/비디오/유튜브 행 규칙 유지
 * - RecyclerView + LinearLayoutManager + Drag&Drop
 * - Glide 사용
 * - XML 미사용(전부 코드 생성)
 */
class RichEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var onPickImages: (() -> Unit)? = null
    private var onPickVideo: (() -> Unit)? = null
    private var onPickYoutube: (() -> Unit)? = null

    private var boldOn = false
    private var italicOn = false
    private var underlineOn = false

    // JSON 스냅샷 기반 Undo/Redo
    private val undoStack: Deque<String> = ArrayDeque()
    private val redoStack: Deque<String> = ArrayDeque()

    private val saveFileName = "editor.html"

    private val recyclerView = RecyclerView(context)
    private val adapter = EditorAdapter()

    private lateinit var boldBtn: ToggleButton
    private lateinit var italicBtn: ToggleButton
    private lateinit var underlineBtn: ToggleButton

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        addView(buildToolbar())

        recyclerView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        addView(recyclerView)

        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.move(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        })
        helper.attachToRecyclerView(recyclerView)

        adapter.addTextRow()
        snapshot()
    }

    fun setExternalPickers(
        onPickImages: () -> Unit,
        onPickVideo: () -> Unit,
        onPickYoutube: () -> Unit
    ) {
        this.onPickImages = onPickImages
        this.onPickVideo = onPickVideo
        this.onPickYoutube = onPickYoutube
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        adapter.addImageRow(uris)
        adapter.addTextRow(afterLast = true)
        snapshot()
        scrollToBottom()
    }

    fun addVideo(uri: Uri) {
        adapter.addVideoRow(uri)
        adapter.addTextRow(afterLast = true)
        snapshot()
        scrollToBottom()
    }

    fun addYoutube(url: String) {
        adapter.addYoutubeRow(url)
        adapter.addTextRow(afterLast = true)
        snapshot()
        scrollToBottom()
    }

    private fun buildToolbar(): View {
        val scroll = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
            isHorizontalScrollBarEnabled = true
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(bar)

        fun makeToggle(label: String, onChange: (Boolean) -> Unit): ToggleButton =
            ToggleButton(context).apply {
                textOn = label; textOff = label; text = label
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
                )
                minWidth = dp(44)
                isAllCaps = false
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            }

        fun makeBtn(label: String, onClick: () -> Unit): Button =
            Button(context).apply {
                text = label
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
                )
                minWidth = dp(44)
                isAllCaps = false
                setOnClickListener { onClick() }
            }

        boldBtn = makeToggle("B") { boldOn = it; updateFocusTextBoldItalicUnderline() }
        italicBtn = makeToggle("I") { italicOn = it; updateFocusTextBoldItalicUnderline() }
        underlineBtn = makeToggle("U") { underlineOn = it; updateFocusTextBoldItalicUnderline() }

        val imgBtn = makeBtn("IMG") { onPickImages?.invoke() }
        val vidBtn = makeBtn("VID") { onPickVideo?.invoke() }
        val ytBtn = makeBtn("YTB") { onPickYoutube?.invoke() }
        val undoBtn = makeBtn("Undo") { undo() }
        val redoBtn = makeBtn("Redo") { redo() }
        val saveBtn = makeBtn("Save") { saveToFile() }
        val loadBtn = makeBtn("Load") { loadFromFile() }

        bar.addView(boldBtn); bar.addView(italicBtn); bar.addView(underlineBtn)
        bar.addView(imgBtn); bar.addView(vidBtn); bar.addView(ytBtn)
        bar.addView(undoBtn); bar.addView(redoBtn); bar.addView(saveBtn); bar.addView(loadBtn)

        return scroll
    }

    private fun updateFocusTextBoldItalicUnderline() {
        val holder = adapter.currentFocusedTextHolder ?: return
        holder.applyTypingFlags(boldOn, italicOn, underlineOn)
    }

    private fun scrollToBottom() {
        recyclerView.post {
            recyclerView.scrollToPosition(max(0, adapter.itemCount - 1))
        }
    }

    // JSON 스냅샷
    private fun snapshot() {
        val json = adapter.toJson()
        if (undoStack.isEmpty() || undoStack.peek() != json) {
            undoStack.push(json)
        }
        redoStack.clear()
    }

    private fun undo() {
        if (undoStack.size <= 1) return
        val current = undoStack.pop()
        redoStack.push(current)
        val prev = undoStack.peek()
        adapter.fromJson(prev)
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.pop()
        undoStack.push(next)
        adapter.fromJson(next)
    }

    private fun saveToFile() {
        val html = adapter.toHtml()
        context.openFileOutput(saveFileName, Context.MODE_PRIVATE).use {
            it.write(html.toByteArray())
        }
        Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
    }

    private fun loadFromFile() {
        try {
            val sb = StringBuilder()
            context.openFileInput(saveFileName).use { fis ->
                BufferedReader(InputStreamReader(fis)).use { br ->
                    var line: String?
                    while (true) {
                        line = br.readLine() ?: break
                        sb.append(line)
                    }
                }
            }
            val html = sb.toString()
            adapter.fromHtml(html)

            undoStack.clear(); redoStack.clear()
            undoStack.push(adapter.toJson())

            Toast.makeText(context, "불러오기 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()

    // ---------------- 데이터 모델 ----------------

    private sealed class Row {
        data class TextRow(var text: SpannableStringBuilder = SpannableStringBuilder("")) : Row()
        data class ImageRow(val items: List<ImageItem>) : Row()
        data class VideoRow(val uri: Uri, var thumb: Bitmap? = null) : Row()
        data class YoutubeRow(val url: String, var thumbUrl: String? = null) : Row()
    }

    private data class ImageItem(val uri: Uri, val isPortrait: Boolean)

    // ---------------- 어댑터 ----------------

    private inner class EditorAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows = mutableListOf<Row>()
        var currentFocusedTextHolder: TextVH? = null

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.TextRow -> 0
            is Row.ImageRow -> 1
            is Row.VideoRow -> 2
            is Row.YoutubeRow -> 3
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> TextVH(makeTextRowView(parent))
                1 -> ImageVH(makeImageRowView(parent))
                2 -> VideoVH(makeVideoRowView(parent))
                else -> YoutubeVH(makeYoutubeRowView(parent))
            }
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
            val items = uris.map { uri -> ImageItem(uri, isPortrait(uri)) }
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
            snapshot()
        }

        // ---------- HTML (저장/불러오기) ----------
        fun toHtml(): String {
            val sb = StringBuilder()
            sb.append("<div class='editor'>")
            rows.forEach { row ->
                when (row) {
                    is Row.TextRow -> {
                        // 스팬/줄바꿈을 HTML로 보존
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

        fun fromHtml(html: String) {
            rows.clear()
            var i = 0
            while (i < html.length) {
                when {
                    // 새 포맷: <div class='text'> ... </div>
                    html.regionMatches(i, "<div class='text'>", 0, "<div class='text'>".length, ignoreCase = true) -> {
                        val end = html.indexOf("</div>", i, ignoreCase = true)
                        val inner = if (end > i) html.substring(i + "<div class='text'>".length, end) else ""
                        val spanned: Spanned = Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY)
                        rows.add(Row.TextRow(SpannableStringBuilder(spanned)))
                        i = if (end > 0) end + 6 else html.length
                    }
                    // 구버전 폴백: <p> ... </p>
                    html.regionMatches(i, "<p>", 0, 3, ignoreCase = true) -> {
                        val end = html.indexOf("</p>", i, ignoreCase = true)
                        val inner = if (end > i) html.substring(i, end + 4) else ""
                        val spanned: Spanned = Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY)
                        rows.add(Row.TextRow(SpannableStringBuilder(spanned)))
                        i = if (end > 0) end + 4 else html.length
                    }
                    html.regionMatches(i, "<div class='img-row'>", 0, "<div class='img-row'>".length, ignoreCase = true) -> {
                        val end = html.indexOf("</div>", i, ignoreCase = true)
                        val block = if (end > i) html.substring(i, end + 6) else ""
                        val imgUris = Regex("<img src='(.*?)'/?>(?s)", RegexOption.IGNORE_CASE)
                            .findAll(block).map { Uri.parse(it.groupValues[1]) }.toList()
                        val items = imgUris.map { uri -> ImageItem(uri, isPortrait(uri)) }
                        rows.add(Row.ImageRow(items))
                        i = if (end > 0) end + 6 else html.length
                    }
                    html.regionMatches(i, "<div class='video'", 0, "<div class='video'".length, ignoreCase = true) -> {
                        val m = Regex("data-uri='(.*?)'", RegexOption.IGNORE_CASE).find(html, i)
                        val uri = m?.groupValues?.getOrNull(1)?.let(Uri::parse)
                        if (uri != null) rows.add(Row.VideoRow(uri))
                        val end = html.indexOf("</div>", i, ignoreCase = true)
                        i = if (end > 0) end + 6 else html.length
                    }
                    html.regionMatches(i, "<div class='youtube'", 0, "<div class='youtube'".length, ignoreCase = true) -> {
                        val m = Regex("data-url='(.*?)'", RegexOption.IGNORE_CASE).find(html, i)
                        val url = m?.groupValues?.getOrNull(1)
                        if (!url.isNullOrBlank()) rows.add(Row.YoutubeRow(unescapeHtml(url)))
                        val end = html.indexOf("</div>", i, ignoreCase = true)
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
                        row.items.forEach { it ->
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
                        "video" -> {
                            rows.add(Row.VideoRow(Uri.parse(obj.getString("uri"))))
                        }
                        "youtube" -> {
                            rows.add(Row.YoutubeRow(obj.getString("url")))
                        }
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

                    snapshot()
                }
            }

            init {
                editText.addTextChangedListener(watcher)
                editText.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        currentFocusedTextHolder = this
                        applyTypingFlags(boldOn, italicOn, underlineOn)
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

            fun applyTypingFlags(b: Boolean, i: Boolean, u: Boolean) {
                localBold = b; localItalic = i; localUnderline = u
                boldBtn.isChecked = b
                italicBtn.isChecked = i
                underlineBtn.isChecked = u

                val selStart = editText.selectionStart
                val selEnd = editText.selectionEnd
                if (selStart in 0..selEnd && selStart != selEnd) {
                    suppressWatcher = true
                    applyStyleSpans(editText.editableText, selStart, selEnd, b, i, u)
                    boundRow?.text = SpannableStringBuilder(editText.editableText)
                    suppressWatcher = false
                    snapshot()
                }
            }

            private fun applyStyleSpans(
                e: Editable,
                start: Int,
                end: Int,
                bold: Boolean,
                italic: Boolean,
                underline: Boolean
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
                        orientation = HORIZONTAL
                        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                        gravity = Gravity.CENTER
                    }
                    buffer.forEach { item ->
                        val iv = ImageView(container.context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, dp(160), 1f).also {
                                it.setMargins(dp(4), dp(4), dp(4), dp(4))
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
                    LayoutParams.MATCH_PARENT, dp(200)
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            private val play = TextView(frame.context).apply {
                text = "▶"
                textSize = 48f
                setTextColor(Color.WHITE)
                setShadowLayer(8f, 0f, 0f, Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER
                )
            }
            init {
                frame.removeAllViews()
                frame.addView(thumbnail)
                frame.addView(play)
            }

            fun bind(row: Row.VideoRow) {
                if (row.thumb == null) {
                    row.thumb = createVideoThumbnail(row.uri)
                }
                row.thumb?.let { thumbnail.setImageBitmap(it) }
            }
        }

        inner class YoutubeVH(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
            private val thumb = ImageView(container.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(200))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            private val urlText = TextView(container.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setPadding(dp(8), dp(4), dp(8), dp(12))
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
                orientation = VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                )
                val et = EditText(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
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
                orientation = VERTICAL
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
                orientation = VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }

        private fun dp(v: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
            ).toInt()
    }

    // ---------------- 유틸 ----------------

    private fun isPortrait(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri).use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                val w = opts.outWidth; val h = opts.outHeight
                h >= w
            }
        } catch (_: Exception) { true }
    }

    private fun createVideoThumbnail(uri: Uri): Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bmp = retriever.frameAtTime
            retriever.release()
            bmp
        } catch (_: Exception) { null }
    }

    private fun extractYoutubeId(url: String): String? {
        val patterns = listOf(
            "v=([\\w-]{11})",
            "youtu\\.be/([\\w-]{11})",
            "youtube\\.com/embed/([\\w-]{11})"
        )
        for (p in patterns) {
            val m = Regex(p).find(url)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    private fun unescapeHtml(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&amp;", "&")
}
