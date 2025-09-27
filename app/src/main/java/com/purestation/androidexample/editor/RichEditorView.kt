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
 * 리치 텍스트 에디터의 루트 커스텀 View.
 *
 * 특징
 * - 상단 툴바(HorizontalScrollView) + B/I/U 토글 → 선택 영역 즉시 스타일 적용
 * - 이미지/비디오/유튜브 행 추가 시 규칙적으로 TextRow 자동 추가
 * - RecyclerView 기반 Row 리스트 + Drag&Drop 정렬
 * - Undo/Redo: JSON 스냅샷 스택 (redo가 끊기지 않도록 suppressSnapshot 가드 적용)
 * - 저장/불러오기: HTML (실제 직렬화/역직렬화는 EditorAdapter/EditorSerializer가 담당)
 */
class RichEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // Compose 등 외부에서 전달되는 파일/링크 선택기 콜백
    private var onPickImages: (() -> Unit)? = null
    private var onPickVideo: (() -> Unit)? = null
    private var onPickYoutube: (() -> Unit)? = null

    // 툴바 토글 상태
    private var boldOn = false
    private var italicOn = false
    private var underlineOn = false

    // Undo/Redo 스택 (최상단이 최신 상태)
    private val undoStack: Deque<String> = ArrayDeque()
    private val redoStack: Deque<String> = ArrayDeque()

    /**
     * 스냅샷 억제 플래그.
     * - Undo/Redo/불러오기 등으로 어댑터가 갱신될 때 onChanged()가 호출되어도
     *   snapshot()이 다시 호출되지 않도록 막아 Redo 단절을 방지.
     */
    private var suppressSnapshot = false

    // 내부 저장소 파일명 (HTML)
    private val saveFileName = "editor.html"

    // 본문 RecyclerView + 어댑터
    private val recyclerView = RecyclerView(context)
    private val adapter = EditorAdapter(context) {
        // 어댑터의 데이터가 바뀔 때(입력/행 추가/이동 등) 스냅샷 저장
        if (!suppressSnapshot) snapshot()
    }

    // 툴바 토글 버튼 참조
    private lateinit var boldBtn: ToggleButton
    private lateinit var italicBtn: ToggleButton
    private lateinit var underlineBtn: ToggleButton

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // 상단 툴바 추가
        addView(buildToolbar())

        // 본문 RecyclerView 설정
        recyclerView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        addView(recyclerView)

        // Drag&Drop 정렬 지원
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
                adapter.move(from, to) // 이동 시 어댑터가 onChanged() 호출 → 스냅샷 생성
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) { /* 스와이프 미사용 */ }
        })
        helper.attachToRecyclerView(recyclerView)

        // 최초 TextRow 하나 생성 + 초기 스냅샷
        adapter.addTextRow()
        snapshot() // 초기 상태 저장
    }

    /**
     * 외부(Compose 등)에서 이미지/비디오/유튜브 선택기를 호출할 수 있도록 콜백을 연결.
     */
    fun setExternalPickers(
        onPickImages: () -> Unit,
        onPickVideo: () -> Unit,
        onPickYoutube: () -> Unit
    ) {
        this.onPickImages = onPickImages
        this.onPickVideo = onPickVideo
        this.onPickYoutube = onPickYoutube
    }

    /**
     * 이미지 행 추가 후, 이어서 텍스트 입력을 할 수 있도록 TextRow를 추가.
     * - 사용자 변화이므로 snapshot() 호출.
     * - 마지막으로 스크롤 이동.
     */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        adapter.addImageRow(uris)
        adapter.addTextRow(afterLast = true)
        snapshot()
        scrollToBottom()
    }

    /** 비디오 행 추가 + 후속 TextRow 추가 */
    fun addVideo(uri: Uri) {
        adapter.addVideoRow(uri)
        adapter.addTextRow(afterLast = true)
        snapshot()
        scrollToBottom()
    }

    /** 유튜브 행 추가 + 후속 TextRow 추가 */
    fun addYoutube(url: String) {
        adapter.addYoutubeRow(url)
        adapter.addTextRow(afterLast = true)
        snapshot()
        scrollToBottom()
    }

    /**
     * 상단 툴바 구성: B/I/U 토글 + IMG/VID/YTB + Undo/Redo + Save/Load
     */
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

        // 토글 버튼 생성 헬퍼
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

        // 일반 버튼 생성 헬퍼
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

        // 스타일 토글: 현재 포커스된 TextRow에 즉시 적용
        boldBtn = makeToggle("B") { boldOn = it; updateFocusTextBoldItalicUnderline() }
        italicBtn = makeToggle("I") { italicOn = it; updateFocusTextBoldItalicUnderline() }
        underlineBtn = makeToggle("U") { underlineOn = it; updateFocusTextBoldItalicUnderline() }

        // 미디어/링크 선택
        val imgBtn  = makeBtn("IMG") { onPickImages?.invoke() }
        val vidBtn  = makeBtn("VID") { onPickVideo?.invoke() }
        val ytBtn   = makeBtn("YTB") { onPickYoutube?.invoke() }

        // 편집 이력 & 저장/불러오기
        val undoBtn = makeBtn("Undo") { undo() }
        val redoBtn = makeBtn("Redo") { redo() }
        val saveBtn = makeBtn("Save") { saveToFile() }
        val loadBtn = makeBtn("Load") { loadFromFile() }

        // 툴바 버튼 나열
        bar.addView(boldBtn); bar.addView(italicBtn); bar.addView(underlineBtn)
        bar.addView(imgBtn);  bar.addView(vidBtn);    bar.addView(ytBtn)
        bar.addView(undoBtn); bar.addView(redoBtn);   bar.addView(saveBtn); bar.addView(loadBtn)

        return scroll
    }

    /**
     * 툴바의 B/I/U 토글 상태를 현재 포커스된 EditText에 반영.
     * - 선택 영역 존재 시 해당 범위에 스타일 span 적용 (EditorAdapter.TextVH.applyTypingFlags)
     * - 버튼의 체크 상태를 상태값과 동기화
     */
    private fun updateFocusTextBoldItalicUnderline() {
        val holder = adapter.currentFocusedTextHolder ?: return
        holder.applyTypingFlags(boldOn, italicOn, underlineOn)
        boldBtn.isChecked = boldOn
        italicBtn.isChecked = italicOn
        underlineBtn.isChecked = underlineOn
    }

    /** 리스트의 마지막 아이템으로 스크롤 이동 */
    private fun scrollToBottom() {
        recyclerView.post {
            recyclerView.scrollToPosition(max(0, adapter.itemCount - 1))
        }
    }

    // ---------------- 스냅샷/Undo/Redo ----------------

    /**
     * 현재 편집 상태(JSON) 스냅샷을 undoStack에 저장.
     * - 동일 상태 반복 저장 방지
     * - 사용자 변화가 있을 때만 redoStack 초기화(끊김 방지)
     */
    private fun snapshot() {
        val json = adapter.toJson()
        val willPush = undoStack.isEmpty() || undoStack.peek() != json
        if (willPush) {
            undoStack.push(json)
            // 새 사용자 변경 발생 시에만 redo 폐기 (undo/redo 중에는 suppressSnapshot으로 들어오지 않음)
            redoStack.clear()
        }
    }

    /** 한 단계 되돌리기 */
    private fun undo() {
        if (undoStack.size <= 1) return
        val current = undoStack.pop()
        redoStack.push(current)
        val prev = undoStack.peek()

        suppressSnapshot = true
        try {
            adapter.fromJson(prev) // 어댑터 갱신 중 onChanged() → snapshot() 차단
        } finally {
            suppressSnapshot = false
        }
    }

    /** 한 단계 다시 실행 */
    private fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.pop()
        undoStack.push(next) // redo 결과를 현재 상태로 반영

        suppressSnapshot = true
        try {
            adapter.fromJson(next) // onChanged() → snapshot() 차단
        } finally {
            suppressSnapshot = false
        }
    }

    // ---------------- 저장/불러오기 ----------------

    /**
     * 현재 에디터 상태를 HTML로 직렬화하여 내부 저장소에 저장.
     * - 직렬화 로직은 EditorAdapter → EditorSerializer 위임
     */
    private fun saveToFile() {
        val html = adapter.toHtml()
        context.openFileOutput(saveFileName, Context.MODE_PRIVATE).use {
            it.write(html.toByteArray())
        }
        Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
    }

    /**
     * 내부 저장소에서 HTML을 읽어와 에디터 상태 복원.
     * - 복원 중에는 suppressSnapshot=true로 설정하여 스냅샷 생성 방지
     * - 복원 후 Undo/Redo 스택을 현재 상태 기준으로 재초기화
     */
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
                adapter.fromHtml(html) // 불러오는 동안 onChanged()로 인한 스냅샷 생성 차단
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
