package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
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

/**
 * RecyclerView.Adapter를 상속받아
 * 텍스트, 이미지, 비디오, 유튜브 링크 등을
 * Row 단위로 표시/편집할 수 있는 에디터용 어댑터 클래스
 */
class EditorAdapter(
    private val context: Context,
    /** 외부에서 데이터 변경을 감지할 수 있도록 콜백 제공 (Undo/Redo 등) */
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** 실제 에디터에 표시될 Row 데이터 리스트 */
    private val rows = mutableListOf<Row>()

    /** 현재 포커스를 가진 TextViewHolder (글자 입력 중인 에디트텍스트 추적용) */
    var currentFocusedTextHolder: TextVH? = null
        internal set

    // ---------------- RecyclerView 필수 구현 ----------------
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
        // ViewHolder 타입별로 Row 데이터와 바인딩
        when (holder) {
            is TextVH -> holder.bind(rows[position] as Row.TextRow)
            is ImageVH -> holder.bind(rows[position] as Row.ImageRow)
            is VideoVH -> holder.bind(rows[position] as Row.VideoRow)
            is YoutubeVH -> holder.bind(rows[position] as Row.YoutubeRow)
        }
    }

    override fun getItemCount(): Int = rows.size

    // ---------------- 외부 API (Row 추가/이동) ----------------
    fun addTextRow(afterLast: Boolean = false) {
        val row = Row.TextRow()
        if (afterLast) {
            rows.add(row)
            notifyItemInserted(rows.lastIndex)
        } else {
            rows.add(0, row)
            notifyItemInserted(0)
        }
        onChanged()
    }

    fun addImageRow(uris: List<Uri>) {
        val items = uris.map { uri -> ImageItem(uri, isPortrait(context, uri)) }
        rows.add(Row.ImageRow(items))
        notifyItemInserted(rows.lastIndex)
        onChanged()
    }

    fun addVideoRow(uri: Uri) {
        rows.add(Row.VideoRow(uri))
        notifyItemInserted(rows.lastIndex)
        onChanged()
    }

    fun addYoutubeRow(url: String) {
        rows.add(Row.YoutubeRow(url))
        notifyItemInserted(rows.lastIndex)
        onChanged()
    }

    /** Row 순서 이동 */
    fun move(from: Int, to: Int) {
        if (from == to) return
        val row = rows.removeAt(from)
        rows.add(to, row)
        notifyItemMoved(from, to)
        onChanged()
    }

    // ---------------- 직렬화/역직렬화 ----------------
    fun toHtml(): String = EditorSerializer.toHtml(rows)

    fun fromHtml(html: String) {
        replaceAll(EditorSerializer.fromHtml(context, html))
        notifyDataSetChanged()
        onChanged()
    }

    fun toJson(): String = EditorSerializer.toJson(rows)

    fun fromJson(json: String) {
        replaceAll(EditorSerializer.fromJson(json))
        notifyDataSetChanged()
        onChanged()
    }

    private fun replaceAll(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
    }

    // ---------------- ViewHolder 정의 ----------------
    /** 텍스트 입력 Row */
    inner class TextVH(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val editText = (container.getChildAt(0) as EditText)

        private var boundRow: Row.TextRow? = null
        private var localBold = false
        private var localItalic = false
        private var localUnderline = false

        private var lastStart: Int = -1
        private var lastCount: Int = 0
        private var suppressWatcher = false

        /** 텍스트 변경 감지 리스너 */
        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lastStart = start
                lastCount = count
            }

            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return

                // 입력된 텍스트에 스타일 적용
                if (lastStart >= 0 && lastCount > 0 && s != null) {
                    applyStyleSpans(
                        s,
                        lastStart,
                        lastStart + lastCount,
                        localBold,
                        localItalic,
                        localUnderline
                    )
                }
                boundRow?.text = SpannableStringBuilder(s ?: "")
                onChanged()
            }
        }

        init {
            // 에디트텍스트에 리스너 부착
            editText.addTextChangedListener(watcher)
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    currentFocusedTextHolder = this
                } else if (currentFocusedTextHolder == this) {
                    currentFocusedTextHolder = null
                }
            }
        }

        /** 데이터 바인딩 */
        fun bind(row: Row.TextRow) {
            boundRow = row
            suppressWatcher = true
            editText.text = SpannableStringBuilder(row.text)
            suppressWatcher = false
        }

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
            if (bold) e.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (italic) e.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (underline) e.setSpan(
                UnderlineSpan(),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** 이미지 Row */
    inner class ImageVH(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        fun bind(row: Row.ImageRow) {
            container.removeAllViews()
            var buffer = mutableListOf<ImageItem>()

            // 이미지들을 가로 레이아웃에 일정 개수만큼 묶어서 표시
            fun flush() {
                if (buffer.isEmpty()) return
                val sub = LinearLayout(container.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = Gravity.CENTER
                }
                buffer.forEach { item ->
                    val iv = ImageView(container.context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, container.context.dp(160), 1f).also {
                                it.setMargins(
                                    container.context.dp(4),
                                    container.context.dp(4),
                                    container.context.dp(4),
                                    container.context.dp(4)
                                )
                            }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    Glide.with(iv).load(item.uri).into(iv)
                    sub.addView(iv)
                }
                container.addView(sub)
                buffer = mutableListOf()
            }

            // 세로/가로 이미지 배치 규칙 적용
            row.items.forEach { item ->
                val cap = if (item.isPortrait) 2 else 1
                buffer.add(item)
                if (buffer.size == cap) flush()
            }
            flush()
        }
    }

    /** 비디오 Row (썸네일 + 재생버튼 표시) */
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
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
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

    /** 유튜브 Row (썸네일 + URL 표시) */
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
            setPadding(
                container.context.dp(8),
                container.context.dp(4),
                container.context.dp(8),
                container.context.dp(12)
            )
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

    // ---------------- Row별 뷰 생성 함수 ----------------
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
