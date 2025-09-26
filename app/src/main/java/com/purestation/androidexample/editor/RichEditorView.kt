package com.purestation.androidexample.editor

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.purestation.androidexample.R
import kotlin.math.max

class RichEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val recycler = RecyclerView(context)
    private val adapter = BlocksAdapter()
    private val undoRedo = UndoRedo()

    private fun dpInt(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    init {
        // 기존 코드들(recycler, layoutManager 등) 위/아래 아무 곳에 배치 가능
        background = GradientDrawable().apply {
//            setColor(Color.WHITE) // 안쪽 배경
            cornerRadius = dp(8f)
            setStroke(dpInt(1), Color.parseColor("#000000"))
        }
        setPadding(dpInt(8), dpInt(8), dpInt(8), dpInt(8)) // 테두리 안쪽 여백

        // 레이아웃 매니저 필수
        recycler.layoutManager = LinearLayoutManager(context)

        recycler.adapter = adapter
        recycler.itemAnimator = null
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val ith = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val blocks = adapter.blocks
                if (from in blocks.indices && to in blocks.indices) {
                    val item = blocks.removeAt(from)
                    blocks.add(to, item)
                    adapter.notifyItemMoved(from, to)
                    pushState()
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        })
        ith.attachToRecyclerView(recycler)

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = sys.bottom, left = sys.left, right = sys.right, top = sys.top)
            insets
        }

        ViewCompat.setOnReceiveContentListener(recycler,
            arrayOf("image/*", "video/*", "text/uri-list", "text/plain")) { _, payload ->
            val clip = payload.clip
            handleClipData(clip)
            null
        }

        if (adapter.blocks.isEmpty()) adapter.blocks.add(Block.Paragraph())
        adapter.notifyDataSetChanged()
        pushState()
    }

    fun getState(): EditorState = EditorState(adapter.blocks.toList())
    fun setState(state: EditorState) {
        adapter.blocks.clear()
        adapter.blocks.addAll(state.blocks.map {
            when (it) {
                is Block.Paragraph -> Block.Paragraph(it.text.deepCopy(), it.isH1)
                is Block.ImageGrid -> Block.ImageGrid(it.uris.toMutableList(), it.columns)
                is Block.Video -> Block.Video(it.uri)
                is Block.YouTube -> Block.YouTube(it.videoId)
            }
        })
        adapter.notifyDataSetChanged()
        pushState()
    }

    fun toHtml(): String = HtmlIO.toHtml(getState())
    fun fromHtml(html: String) = setState(HtmlIO.fromHtml(html))

    fun addParagraph() {
        adapter.blocks.add(Block.Paragraph())
        adapter.notifyItemInserted(adapter.blocks.lastIndex)
        recycler.scrollToPosition(adapter.blocks.lastIndex)
        pushState()
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val cols = max(1, uris.size.coerceAtMost(3))
        adapter.blocks.add(Block.ImageGrid(uris.toMutableList(), cols))
        adapter.notifyItemInserted(adapter.blocks.lastIndex)
        recycler.scrollToPosition(adapter.blocks.lastIndex)
        pushState()
    }

    fun addVideo(uri: Uri) {
        adapter.blocks.add(Block.Video(uri))
        adapter.notifyItemInserted(adapter.blocks.lastIndex)
        recycler.scrollToPosition(adapter.blocks.lastIndex)
        pushState()
    }

    fun addYouTube(url: String) {
        val id = extractYouTubeId(url) ?: return
        adapter.blocks.add(Block.YouTube(id))
        adapter.notifyItemInserted(adapter.blocks.lastIndex)
        recycler.scrollToPosition(adapter.blocks.lastIndex)
        pushState()
    }

    fun toggleBold() = withFocusedEditText { et ->
        applySpanToggle<StyleSpan>(et) { StyleSpan(Typeface.BOLD) }
    }

    fun toggleItalic() = withFocusedEditText { et ->
        applySpanToggle<StyleSpan>(et) { StyleSpan(Typeface.ITALIC) }
    }

    fun toggleUnderline() = withFocusedEditText { et ->
        applySpanToggle<UnderlineSpan>(et) { UnderlineSpan() }
    }

    fun toggleH1() = withFocusedParagraph { p, pos ->
        p.isH1 = !p.isH1
        adapter.notifyItemChanged(pos)
        pushState()
    }

    fun cycleImageGridColumns() {
        val pos = adapter.focusedAdapterPos ?: return
        val b = adapter.blocks.getOrNull(pos)
        if (b is Block.ImageGrid) {
            b.columns = when (b.columns) { 1 -> 2; 2 -> 3; else -> 1 }
            adapter.notifyItemChanged(pos)
            pushState()
        }
    }

    fun undo() {
        val prev = undoRedo.popUndo(getState()) ?: return
        setState(prev)
    }

    fun redo() {
        val next = undoRedo.popRedo(getState()) ?: return
        setState(next)
    }

    private fun pushState() = undoRedo.push(getState())

    private fun withFocusedParagraph(block: (Block.Paragraph, Int) -> Unit) {
        val pos = adapter.focusedAdapterPos ?: return
        val b = adapter.blocks.getOrNull(pos)
        if (b is Block.Paragraph) block(b, pos)
    }

    private fun withFocusedEditText(block: (EditText) -> Unit) {
        val et = adapter.focusedEditText ?: return
        block(et)
        pushState()
    }

    private inline fun <reified T> applySpanToggle(et: EditText, newSpan: () -> Any) {
        val s = et.text
        val start = et.selectionStart
        val end = et.selectionEnd
        if (start == end) return
        val spans = s.getSpans(start, end, T::class.java)
        if (spans.isNotEmpty()) {
            spans.forEach { s.removeSpan(it) }
        } else {
            s.setSpan(newSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        et.text = s // refresh
        et.setSelection(start, end)
    }

    private fun handleClipData(clip: ClipData) {
        val uris = mutableListOf<Uri>()
        var youtube: String? = null
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            item.uri?.let { uris.add(it) }
            item.text?.toString()?.let { t ->
                if (t.startsWith("http")) {
                    if (isYouTubeUrl(t)) youtube = t
                }
            }
        }
        val imageUris = uris.filter { it.toString().contains("image") }
        val videoUris = uris.filter { it.toString().contains("video") }
        if (imageUris.isNotEmpty()) addImages(imageUris)
        videoUris.forEach { addVideo(it) }
        youtube?.let { addYouTube(it) }
    }

    private fun isYouTubeUrl(url: String) =
        url.contains("youtube.com") || url.contains("youtu.be")

    private fun extractYouTubeId(url: String): String? {
        val y1 = Regex("v=([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
        val y2 = Regex("youtu\\.be/([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
        return y1 ?: y2
    }

    inner class BlocksAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val blocks: MutableList<Block> = mutableListOf()
        var focusedEditText: EditText? = null
        var focusedAdapterPos: Int? = null

        override fun getItemViewType(position: Int): Int = when (blocks[position]) {
            is Block.Paragraph -> 0
            is Block.ImageGrid -> 1
            is Block.Video -> 2
            is Block.YouTube -> 3
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> ParagraphVH(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_paragraph, parent, false)
                )
                1 -> ImageGridVH(FrameLayout(parent.context))
                2 -> VideoVH(FrameLayout(parent.context))
                else -> YoutubeVH(FrameLayout(parent.context))
            }
        }

        override fun getItemCount(): Int = blocks.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is ParagraphVH -> holder.bind(blocks[position] as Block.Paragraph, position)
                is ImageGridVH -> holder.bind(blocks[position] as Block.ImageGrid, position)
                is VideoVH -> holder.bind(blocks[position] as Block.Video, position)
                is YoutubeVH -> holder.bind(blocks[position] as Block.YouTube, position)
            }
        }

        inner class ParagraphVH(view: View) : RecyclerView.ViewHolder(view) {
            private val et: EditText = view.findViewById(R.id.etParagraph)
            private val tvH1: TextView = view.findViewById(R.id.tvH1)

            fun bind(p: Block.Paragraph, pos: Int) {
                et.setText(p.text)
                et.text?.let { Selection.setSelection(it, it.length) }
                et.movementMethod = LinkMovementMethod.getInstance()
                et.textSize = if (p.isH1) 24f else 16f
                tvH1.visibility = if (p.isH1) View.VISIBLE else View.GONE

                et.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        focusedEditText = et
                        focusedAdapterPos = bindingAdapterPosition
                    } else if (focusedEditText == et) {
                        focusedEditText = null
                        focusedAdapterPos = null
                    }
                }
                et.addTextChangedListener(SimpleTextWatcher {
                    p.text = SpannableStringBuilder(et.text)
                })
            }
        }

        inner class ImageGridVH(container: FrameLayout) : RecyclerView.ViewHolder(container) {
            private val grid = GridLayout(container.context).apply {
                columnCount = 1
            }
            init {
                container.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                container.setPadding(16, 16, 16, 16)
                container.setBackgroundColor(Color.parseColor("#10101010"))
                container.setOnClickListener {
                    focusedAdapterPos = bindingAdapterPosition
                }
            }
            fun bind(g: Block.ImageGrid, pos: Int) {
                grid.removeAllViews()
                grid.columnCount = g.columns.coerceIn(1, 3)
                val size = resources.displayMetrics.widthPixels / grid.columnCount - 24
                g.uris.forEach { uri ->
                    val iv = ImageView(grid.context).apply {
                        layoutParams = ViewGroup.LayoutParams(size, size)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    Glide.with(iv).load(uri)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(iv)
                    grid.addView(iv)
                }
            }
        }

        inner class VideoVH(container: FrameLayout) : RecyclerView.ViewHolder(container) {
            private val iv = ImageView(container.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            private val play = TextView(container.context).apply {
                text = "▶"
                textSize = 32f
                setTextColor(Color.WHITE)
                setShadowLayer(8f, 0f, 0f, Color.BLACK)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }
            init {
                container.addView(iv)
                container.addView(play)
                container.setPadding(0, 8, 0, 8)
                container.setOnClickListener { focusedAdapterPos = bindingAdapterPosition }
            }
            fun bind(v: Block.Video, pos: Int) {
                val bmp = extractVideoFrame(itemView.context, v.uri)
                if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageResource(android.R.color.darker_gray)
            }
        }

        inner class YoutubeVH(container: FrameLayout) : RecyclerView.ViewHolder(container) {
            private val iv = ImageView(container.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            private val badge = TextView(container.context).apply {
                text = "YouTube"
                setBackgroundColor(Color.RED)
                setTextColor(Color.WHITE)
                setPadding(12, 6, 12, 6)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
            }
            init {
                container.addView(iv)
                container.addView(badge)
                container.setPadding(0, 8, 0, 8)
                container.setOnClickListener { focusedAdapterPos = bindingAdapterPosition }
            }
            fun bind(y: Block.YouTube, pos: Int) {
                val url = "https://img.youtube.com/vi/${y.videoId}/hqdefault.jpg"
                Glide.with(iv).load(url).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(iv)
            }
        }
    }
}

private fun extractVideoFrame(context: Context, uri: Uri): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(context, uri)
        mmr.frameAtTime?.let { bmp ->
            if (bmp.width > 1280) {
                val ratio = 1280f / bmp.width
                Bitmap.createScaledBitmap(bmp, 1280, (bmp.height * ratio).toInt(), true)
            } else bmp
        }
    } catch (_: Exception) { null } finally { mmr.release() }
}

private class SimpleTextWatcher(val onChange: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) { onChange() }
}
