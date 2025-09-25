package com.purestation.androidexample.gestures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.atan2
import kotlin.math.sqrt

class TransformCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // 표시할 비트맵 (외부에서 setBitmap으로 주입)
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true       // 축소 시 계단 현상 줄이도록 설정
    }

    // 변환 행렬 (이동/회전/확대 정보를 누적)
    private val matrix = Matrix()
    // 임시 사각형 (경계 계산용)
    private val tmpRect = RectF()
    // 행렬 값 읽기용 배열
    private val tmpValues = FloatArray(9)

    // 최소/최대 배율 제한
    private var minScale = 1f
    private var maxScale = 5f

    // 핀치 줌(확대/축소) 제스처 감지기
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cur = currentScale() // 현재 스케일
                val target = (cur * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = target / cur
                // 손가락 중심을 기준으로 스케일링
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                fixBounds()   // 화면 밖으로 나가지 않게 보정
                invalidate()  // 다시 그리기 요청
                return true
            }
        })

    // 드래그, 더블탭 제스처 감지기
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            // 손가락 드래그(스크롤)
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                matrix.postTranslate(-dx, -dy) // 스크롤 방향 반대로 이미지 이동
                fixBounds()
                invalidate()
                return true
            }

            // 더블탭 → 화면에 맞게 초기화
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetToFit()
                invalidate()
                return true
            }
        })

    // 회전을 위한 직전 각도
    private var lastAngle: Float? = null

    // 외부에서 비트맵 설정
    fun setBitmap(bm: Bitmap) {
        bitmap = bm
        resetToFit()   // 화면에 맞게 초기 배치
        invalidate()
    }

    // 뷰 크기 변경 시 (예: 화면 회전, 레이아웃 변화)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null) resetToFit()
    }

    // 실제 그리기
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.save()
        canvas.concat(matrix)               // 변환 행렬 적용
        canvas.drawBitmap(bmp, 0f, 0f, paint) // 원점에 비트맵 그리기
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 두 손가락 이상일 때 회전 처리
        if (event.pointerCount >= 2) {
            val cx = (event.getX(0) + event.getX(1)) / 2f
            val cy = (event.getY(0) + event.getY(1)) / 2f
            val angle = angleBetween(event) // 두 손가락 벡터의 각도
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> lastAngle = angle
                MotionEvent.ACTION_MOVE -> {
                    lastAngle?.let { prev ->
                        val delta = angle - prev
                        // 두 손가락 중점을 기준으로 회전
                        matrix.postRotate(delta, cx, cy)
                        fixBounds()
                        invalidate()
                    }
                    lastAngle = angle
                }
                // 포인터 빠지거나 취소되면 회전 초기화
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> lastAngle = null
            }
        } else {
            lastAngle = null
        }

        // 확대/이동 제스처 전달
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // 이미지를 화면에 맞게 초기 배치
    private fun resetToFit() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return

        matrix.reset()

        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = bmp.width.toFloat()
        val dh = bmp.height.toFloat()

        // 화면에 맞추기 위한 스케일 계산
        val scale = minOf(vw / dw, vh / dh)
        val dx = (vw - dw * scale) / 2f
        val dy = (vh - dh * scale) / 2f

        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        // 최소/최대 스케일 갱신
        minScale = currentScale()
        maxScale = (minScale * 4f).coerceAtLeast(minScale + 0.5f)
    }

    // 이미지가 화면 밖으로 나가지 않도록 위치 보정
    private fun fixBounds() {
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()

        // 현재 행렬이 적용된 이미지 영역 계산
        tmpRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        matrix.mapRect(tmpRect)

        var dx = 0f
        var dy = 0f

        // 가로 보정
        if (tmpRect.width() <= vw) {
            // 이미지가 더 작으면 중앙 정렬
            dx = vw / 2f - tmpRect.centerX()
        } else {
            // 좌우 여백 방지
            if (tmpRect.left > 0) dx = -tmpRect.left
            if (tmpRect.right < vw) dx = vw - tmpRect.right
        }

        // 세로 보정
        if (tmpRect.height() <= vh) {
            dy = vh / 2f - tmpRect.centerY()
        } else {
            if (tmpRect.top > 0) dy = -tmpRect.top
            if (tmpRect.bottom < vh) dy = vh - tmpRect.bottom
        }

        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }

    // 현재 스케일 계산 (회전 포함)
    private fun currentScale(): Float {
        matrix.getValues(tmpValues)
        val a = tmpValues[Matrix.MSCALE_X]
        val c = tmpValues[Matrix.MSKEW_X]
        return sqrt(a * a + c * c) // 첫 번째 열 벡터의 길이로 스케일 추출
    }

    // 두 손가락 벡터의 각도 계산 (라디안 → 도 단위)
    private fun angleBetween(e: MotionEvent): Float {
        val dx = e.getX(1) - e.getX(0)
        val dy = e.getY(1) - e.getY(0)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }
}
