package com.purestation.androidexample.gestures

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.atan2
import kotlin.math.sqrt

class TransformImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    // 변환을 누적할 행렬 (이동/회전/확대)
    private val workMatrix = Matrix()
    // 행렬 값 읽을 때 사용할 배열
    private val values = FloatArray(9)
    // workMatrix.mapRect로 갱신되는 이미지의 화면상 경계(계산용), 화면 반영 X
    private val imgRect = RectF()

    // 최소/최대 스케일 제한
    private var minScale = 1f
    private var maxScale = 5f

    // 핀치 줌(확대/축소) 제스처 감지기
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cur = currentScale() // 현재 배율
                // 새로운 목표 배율 (최소~최대 범위로 제한)
                val target = (cur * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = target / cur
                // 손가락 중심을 기준으로 스케일 적용
                workMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                fixBounds() // 경계 보정
                imageMatrix = workMatrix
                return true
            }
        })

    // 드래그/더블탭 제스처 감지기
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            // 스크롤(이동) 처리
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                // 스크롤은 화면 움직임 → 이미지 이동은 반대 방향
                workMatrix.postTranslate(-dx, -dy)
                fixBounds()
                imageMatrix = workMatrix
                return true
            }

            // 더블탭 시 초기화 (화면에 맞게 리셋)
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetToFit()
                return true
            }
        })

    // 회전 처리에 사용할 마지막 각도
    private var lastAngle: Float? = null

    init {
        // Matrix 스케일 타입 강제 (행렬 직접 제어)
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) resetToFit() // 뷰 크기 바뀌면 이미지 다시 맞춤
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetToFit() } // 드로어블 변경 시 화면에 맞게 배치
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 두 손가락 이상일 때 회전 처리
        if (event.pointerCount >= 2) {
            val cx = (event.getX(0) + event.getX(1)) / 2f
            val cy = (event.getY(0) + event.getY(1)) / 2f
            val angle = angleBetween(event) // 두 손가락 사이 각도
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> lastAngle = angle
                MotionEvent.ACTION_MOVE -> {
                    lastAngle?.let { prev ->
                        val delta = angle - prev
                        // 중점을 기준으로 회전
                        workMatrix.postRotate(delta, cx, cy)
                        fixBounds()
                        imageMatrix = workMatrix
                    }
                    lastAngle = angle
                }
                // 손가락 뗄 때 회전 초기화
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

    // 이미지 화면에 맞게 초기화
    private fun resetToFit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return

        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat().takeIf { it > 0 } ?: return
        val dh = d.intrinsicHeight.toFloat().takeIf { it > 0 } ?: return

        workMatrix.reset()
        // 뷰 크기에 맞추기 위해 최소 축척 선택
        val scale = minOf(vw / dw, vh / dh)
        val dx = (vw - dw * scale) / 2f
        val dy = (vh - dh * scale) / 2f
        workMatrix.postScale(scale, scale)
        workMatrix.postTranslate(dx, dy)
        imageMatrix = workMatrix

        // 현재 스케일을 최소 스케일로 설정
        minScale = currentScale()
        // 최대 스케일은 최소 스케일의 4배
        maxScale = (minScale * 4f).coerceAtLeast(minScale + 0.5f)
    }

    // 이미지가 화면 밖으로 나가지 않게 보정
    private fun fixBounds() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()

        imgRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        workMatrix.mapRect(imgRect)

        var dx = 0f
        var dy = 0f

        // 가로 보정
        if (imgRect.width() <= vw) {
            // 이미지가 더 작으면 중앙 정렬
            dx = vw / 2f - imgRect.centerX()
        } else {
            // 좌우 빈 공간 방지
            if (imgRect.left > 0f) dx = -imgRect.left
            if (imgRect.right < vw) dx = vw - imgRect.right
        }

        // 세로 보정
        if (imgRect.height() <= vh) {
            dy = vh / 2f - imgRect.centerY()
        } else {
            if (imgRect.top > 0f) dy = -imgRect.top
            if (imgRect.bottom < vh) dy = vh - imgRect.bottom
        }

        if (dx != 0f || dy != 0f) workMatrix.postTranslate(dx, dy)
    }

    // 현재 배율 계산
    private fun currentScale(): Float {
        workMatrix.getValues(values)
        val a = values[Matrix.MSCALE_X]
        val c = values[Matrix.MSKEW_X]
        // 회전이 포함되어도 (a,c) 벡터 길이로 스케일 계산
        return sqrt(a * a + c * c)
    }

    // 두 손가락 사이 각도 계산 (라디안 → 도)
    private fun angleBetween(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(1) - e.getX(0)
        val dy = e.getY(1) - e.getY(0)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }
}
