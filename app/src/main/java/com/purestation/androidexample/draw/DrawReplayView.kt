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

class DrawReplayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    data class Pt(val x: Float, val y: Float, val t: Long) // t: 스트로크 시작 대비 ms
    data class Stroke(
        val startAt: Long,                        // 세션 시작 대비 ms
        val pts: MutableList<Pt> = mutableListOf()
    )

    private val strokes = mutableListOf<Stroke>() // 완료된 스트로크 기록
    private val finishedPaths = mutableListOf<Path>() // 화면 표시용 캐시
    private val currentPath = Path()

    private var sessionStart = 0L
    private var currentStroke: Stroke? = null
    private var currentDownUptime = 0L

    // 재생 관련
    private var isReplaying = false
    private var replayAnimator: ValueAnimator? = null
    private var replayPath: Path? = null
    private var replayProgressMs: Long = 0L
    private var rpStrokeIndex = 0
    private var rpPointIndex = 0

    init {
        isClickable = true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (isReplaying) return true // 재생 중엔 입력 무시(원한다면 허용 가능)
        if (sessionStart == 0L) sessionStart = SystemClock.uptimeMillis()

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                currentDownUptime = e.eventTime
                currentPath.reset()
                currentPath.moveTo(e.x, e.y)

                currentStroke = Stroke(startAt = currentDownUptime - sessionStart).also {
                    it.pts += Pt(e.x, e.y, 0L)
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val s = currentStroke ?: return true

                // 히스토리 포인트 먼저 추가(더 부드러운 기록)
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
                        currentPath.quadTo(lastX, lastY, mx, my)
                        s.pts += Pt(hx, hy, ht)
                        lastX = hx; lastY = hy
                    }
                }

                // 현재 포인트 추가
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
                    // 마지막 선분을 확정하려면 lineTo(e.x, e.y) 등 추가 가능
                    finishedPaths += Path(currentPath) // 표시용 캐시
                    strokes += s                       // 기록 저장
                    currentStroke = null
                }
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isReplaying) {
            replayPath?.let { canvas.drawPath(it, paint) }
        } else {
            finishedPaths.forEach { canvas.drawPath(it, paint) }
            canvas.drawPath(currentPath, paint)
        }
    }

    private val totalDurationMs: Long
        get() {
            val lastStroke = strokes.lastOrNull() ?: return 0L
            val lastPoint = lastStroke.pts.lastOrNull() ?: return 0L
            return lastStroke.startAt + lastPoint.t
        }

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

            // 아직 이 스트로크의 남은 포인트가 있다면 다음 프레임에 이어서 그림
            if (rpPointIndex < s.pts.size) break

            // 스트로크 완료 → 다음 스트로크로
            rpStrokeIndex++
            rpPointIndex = 0
        }
    }

    fun clearAll() {
        stopReplay()
        strokes.clear()
        finishedPaths.clear()
        currentPath.reset()
        sessionStart = 0L
        invalidate()
    }

    // 옵션: 외부에서 선 색/두께 변경
    fun setStrokeColor(color: Int) { paint.color = color; invalidate() }
    fun setStrokeWidth(px: Float) { paint.strokeWidth = px; invalidate() }
}
