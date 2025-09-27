package com.purestation.androidexample.editor

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ToggleButton
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.max

/**
 * Redo가 끊기지 않도록 suppressSnapshot 가드 적용 버전
 * - 툴바 HorizontalScrollView
 * - B/I/U 즉시 적용 + 선택영역 반영
 * - Undo/Redo: JSON 스냅샷
 * - 저장/불러오기: Html(어댑터 위임)
 * - 이미지/비디오/유튜브 행 규칙
 * - RecyclerView + Drag&Drop
 * - Glide 사용 (EditorAdapter 내부)
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

    // Undo/Redo 스택
    private val undoStack: Deque<String> = ArrayDeque()
    private val redoStack: Deque<String> = ArrayDeque()

    // Undo/Redo/불러오기 중 어댑터 onChanged로 인한 스냅샷 생성 차단
    private var suppressSnapshot = false

    private val saveFileName = "editor.html"

    private val recyclerView = RecyclerView(context)
    private val adapter = EditorAdapter(context) {
        if (!suppressSnapshot) snapshot()
    }

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
        snapshot() // 초기 상태 저장
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
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(48))
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
                    ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(36)
                )
                minWidth = context.dp(44)
                isAllCaps = false
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            }

        fun makeBtn(label: String, onClick: () -> Unit): Button =
            Button(context).apply {
                text = label
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(36)
                )
                minWidth = context.dp(44)
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
        // 토글 버튼 시각 상태 동기화
        boldBtn.isChecked = boldOn
        italicBtn.isChecked = italicOn
        underlineBtn.isChecked = underlineOn
    }

    private fun scrollToBottom() {
        recyclerView.post {
            recyclerView.scrollToPosition(max(0, adapter.itemCount - 1))
        }
    }

    // ---------------- 스냅샷/Undo/Redo ----------------
    private fun snapshot() {
        val json = adapter.toJson()
        val willPush = undoStack.isEmpty() || undoStack.peek() != json
        if (willPush) {
            undoStack.push(json)
            // 새 사용자 변경이 발생했을 때만 redo 폐기
            redoStack.clear()
        }
    }

    private fun undo() {
        if (undoStack.size <= 1) return
        val current = undoStack.pop()
        redoStack.push(current)
        val prev = undoStack.peek()

        suppressSnapshot = true
        try {
            adapter.fromJson(prev) // onChanged()가 와도 snapshot() 차단됨
        } finally {
            suppressSnapshot = false
        }
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.pop()
        undoStack.push(next) // redo한 상태를 undo 스택에 쌓음

        suppressSnapshot = true
        try {
            adapter.fromJson(next) // onChanged()가 와도 snapshot() 차단됨
        } finally {
            suppressSnapshot = false
        }
    }

    // ---------------- 저장/불러오기 ----------------
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

            suppressSnapshot = true
            try {
                adapter.fromHtml(html) // 불러올 때 스냅샷 생성 차단
            } finally {
                suppressSnapshot = false
            }

            // 스택 초기화 및 현재 상태를 기준점으로 설정
            undoStack.clear()
            redoStack.clear()
            undoStack.push(adapter.toJson())

            Toast.makeText(context, "불러오기 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
