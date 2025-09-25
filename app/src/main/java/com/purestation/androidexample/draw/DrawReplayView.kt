package com.purestation.androidexample.draw

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * DrawReplayView
 *
 * - 사용자가 손가락으로 그린 선(스트로크)을 기록하고 다시 재생할 수 있는 커스텀 뷰
 * - 기록: 터치 이벤트(MotionEvent)를 받아 Path와 시간 정보를 저장
 * - 재생: 저장된 시간 정보에 맞춰 ValueAnimator를 이용해 순차적으로 Path를 그려줌
 */
class DrawReplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // 선(스트로크)을 그리기 위한 Paint 객체
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // 그려진 점 하나의 데이터 클래스 (x,y 좌표와 시간)
    data class Pt(val x: Float, val y: Float, val t: Long) // t: 스트로크 시작 시점 대비 경과 ms

    // 스트로크 데이터 (한 번 터치로 이어서 그린 선 전체)
    data class Stroke(
        val startAt: Long,                        // 세션 시작 대비 스트로크 시작 시간(ms)
        val pts: MutableList<Pt> = mutableListOf() // 스트로크를 구성하는 점들의 목록
    )

    // 기록 관련
    private val strokes = mutableListOf<Stroke>()    // 완료된 스트로크들 저장
    private val finishedPaths = mutableListOf<Path>() // 화면 표시용 Path 캐시
    private val currentPath = Path()                  // 현재 그리고 있는 Path

    private var sessionStart = 0L          // 첫 터치 시작 시각
    private var currentStroke: Stroke? = null // 현재 진행 중인 스트로크
    private var currentDownUptime = 0L     // 현재 스트로크의 ACTION_DOWN 시각

    // 재생 관련 상태값
    private var isReplaying = false
    private var replayAnimator: ValueAnimator? = null
    private var replayPath: Path? = null         // 재생 중에 점점 그려질 Path
    private var replayProgressMs: Long = 0L      // 재생 시 현재까지 진행된 시간
    private var rpStrokeIndex = 0                // 현재 재생 중인 스트로크 인덱스
    private var rpPointIndex = 0                 // 현재 스트로크 내에서 재생 중인 점 인덱스

    init {
        // 클릭 가능 속성 (터치 이벤트를 받기 위함)
        isClickable = true
    }

    /**
     * 터치 이벤트 처리
     * - ACTION_DOWN: 스트로크 시작
     * - ACTION_MOVE: 점 추가 및 Path 갱신
     * - ACTION_UP / CANCEL: 스트로크 종료 및 기록 저장
     */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (isReplaying) return true // 재생 중이면 입력 무시
        if (sessionStart == 0L) sessionStart = SystemClock.uptimeMillis()

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                currentDownUptime = e.eventTime
                currentPath.reset()
                currentPath.moveTo(e.x, e.y)

                // 새로운 스트로크 시작
                currentStroke = Stroke(startAt = currentDownUptime - sessionStart).also {
                    it.pts += Pt(e.x, e.y, 0L)
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val s = currentStroke ?: return true

                // MotionEvent의 history 데이터 활용 (더 부드러운 선 기록)
                val hist = e.historySize
                if (hist > 0) {
                    var lastX = s.pts.last().x
                    var lastY = s.pts.last().y
                    for (i in 0 until hist) {
                        val hx = e.getHistoricalX(i)
                        val hy = e.getHistoricalY(i)
                        val ht = e.getHistoricalEventTime(i) - currentDownUptime
                        val mx = (lastX + hx) / 2f
                        val my = (lastY + hy) / 2f
                        currentPath.quadTo(lastX, lastY, mx, my) // 부드럽게 곡선 연결
                        s.pts += Pt(hx, hy, ht)
                        lastX = hx; lastY = hy
                    }
                }

                // 현재 좌표도 추가
                val dt = e.eventTime - currentDownUptime
                val lx = s.pts.last().x
                val ly = s.pts.last().y
                val mx = (lx + e.x) / 2f
                val my = (ly + e.y) / 2f
                currentPath.quadTo(lx, ly, mx, my)
                s.pts += Pt(e.x, e.y, dt)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke?.let { s ->
                    // Path 복사하여 화면 표시용 캐시에 저장
                    finishedPaths += Path(currentPath)
                    strokes += s // 스트로크 기록 저장
                    currentStroke = null
                }
                invalidate()
            }
        }
        return true
    }

    /**
     * 그리기
     * - 재생 중이면 replayPath만 그림
     * - 그 외에는 완료된 Path + 현재 진행 중인 Path 그림
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isReplaying) {
            replayPath?.let { canvas.drawPath(it, paint) }
        } else {
            finishedPaths.forEach { canvas.drawPath(it, paint) }
            canvas.drawPath(currentPath, paint)
        }
    }

    /**
     * 전체 기록의 총 시간(ms)
     */
    private val totalDurationMs: Long
        get() {
            val lastStroke = strokes.lastOrNull() ?: return 0L
            val lastPoint = lastStroke.pts.lastOrNull() ?: return 0L
            return lastStroke.startAt + lastPoint.t
        }

    /**
     * 저장된 기록을 기반으로 재생 시작
     */
    fun startReplay() {
        if (strokes.isEmpty()) return
        stopReplay()

        isReplaying = true
        replayPath = Path()
        replayProgressMs = 0L
        rpStrokeIndex = 0
        rpPointIndex = 0

        val total = totalDurationMs.coerceAtLeast(1L)
        replayAnimator = ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = total
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsed = (it.animatedValue as Float).toLong()
                buildReplayPathUntil(elapsed)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    stopReplay()
                }
            })
            start()
        }
    }

    /**
     * 재생 중지
     */
    fun stopReplay() {
        replayAnimator?.cancel()
        replayAnimator = null
        isReplaying = false
        replayPath = null
        replayProgressMs = 0L
        rpStrokeIndex = 0
        rpPointIndex = 0
        invalidate()
    }

    /**
     * 주어진 시간(elapsed)까지 Path를 그림
     */
    private fun buildReplayPathUntil(elapsed: Long) {
        val path = replayPath ?: return

        if (elapsed < replayProgressMs) {
            // 타임라인이 되돌아간 경우 초기화
            path.reset()
            replayProgressMs = 0L
            rpStrokeIndex = 0
            rpPointIndex = 0
        }
        replayProgressMs = elapsed

        while (rpStrokeIndex < strokes.size) {
            val s = strokes[rpStrokeIndex]
            val absStart = s.startAt
            if (absStart > elapsed) break

            // 스트로크 시작점
            if (rpPointIndex == 0 && s.pts.isNotEmpty()) {
                val p0 = s.pts[0]
                path.moveTo(p0.x, p0.y)
            }

            // 스트로크 내부 포인트 연결
            while (rpPointIndex < s.pts.size) {
                val p = s.pts[rpPointIndex]
                val absT = absStart + p.t
                if (absT > elapsed) break

                if (rpPointIndex > 0) {
                    val prev = s.pts[rpPointIndex - 1]
                    val mx = (prev.x + p.x) / 2f
                    val my = (prev.y + p.y) / 2f
                    path.quadTo(prev.x, prev.y, mx, my)
                }
                rpPointIndex++
            }

            // 아직 이 스트로크에 그려야 할 점이 남아있으면 break
            if (rpPointIndex < s.pts.size) break

            // 스트로크 완료 → 다음 스트로크로 이동
            rpStrokeIndex++
            rpPointIndex = 0
        }
    }

    /**
     * 전체 화면 초기화
     */
    fun clearAll() {
        stopReplay()
        strokes.clear()
        finishedPaths.clear()
        currentPath.reset()
        sessionStart = 0L
        invalidate()
    }

    // 옵션: 외부에서 선 색상 및 두께 변경 가능
    fun setStrokeColor(color: Int) { paint.color = color; invalidate() }
    fun setStrokeWidth(px: Float) { paint.strokeWidth = px; invalidate() }
}
