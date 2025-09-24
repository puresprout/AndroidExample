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

    private val workMatrix = Matrix()
    private val values = FloatArray(9)
    private val viewRect = RectF()
    private val imgRect = RectF()

    // 스케일 한계(필요에 따라 조정)
    private var minScale = 1f
    private var maxScale = 5f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cur = currentScale()
                val target = (cur * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = target / cur
                workMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                fixBounds()
                imageMatrix = workMatrix
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                // 스크롤은 화면을 움직이는 제스처이므로 이미지 이동은 반대로 적용
                workMatrix.postTranslate(-dx, -dy)
                fixBounds()
                imageMatrix = workMatrix
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetToFit()
                return true
            }
        })

    private var lastAngle: Float? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) resetToFit()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetToFit() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 멀티터치 회전
        if (event.pointerCount >= 2) {
            val cx = (event.getX(0) + event.getX(1)) / 2f
            val cy = (event.getY(0) + event.getY(1)) / 2f
            val angle = angleBetween(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> lastAngle = angle
                MotionEvent.ACTION_MOVE -> {
                    lastAngle?.let { prev ->
                        val delta = angle - prev
                        workMatrix.postRotate(delta, cx, cy)
                        fixBounds()
                        imageMatrix = workMatrix
                    }
                    lastAngle = angle
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> lastAngle = null
            }
        } else {
            lastAngle = null
        }

        // 확대/이동 제스처
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun resetToFit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return

        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat().takeIf { it > 0 } ?: return
        val dh = d.intrinsicHeight.toFloat().takeIf { it > 0 } ?: return

        workMatrix.reset()
        val scale = minOf(vw / dw, vh / dh)
        val dx = (vw - dw * scale) / 2f
        val dy = (vh - dh * scale) / 2f
        workMatrix.postScale(scale, scale)
        workMatrix.postTranslate(dx, dy)
        imageMatrix = workMatrix

        // 현재 배율을 최소 배율로, 최대 배율은 임의 가중치
        minScale = currentScale()
        maxScale = (minScale * 4f).coerceAtLeast(minScale + 0.5f)
    }

    private fun fixBounds() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()

        viewRect.set(0f, 0f, vw, vh)
        imgRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        workMatrix.mapRect(imgRect)

        var dx = 0f
        var dy = 0f

        // 가로 방향 보정(이미지가 화면보다 넓으면 안쪽에 빈틈 없게, 좁으면 가운데 정렬)
        if (imgRect.width() <= vw) {
            dx = vw / 2f - imgRect.centerX()
        } else {
            if (imgRect.left > 0f) dx = -imgRect.left
            if (imgRect.right < vw) dx = vw - imgRect.right
        }

        // 세로 방향 보정
        if (imgRect.height() <= vh) {
            dy = vh / 2f - imgRect.centerY()
        } else {
            if (imgRect.top > 0f) dy = -imgRect.top
            if (imgRect.bottom < vh) dy = vh - imgRect.bottom
        }

        if (dx != 0f || dy != 0f) workMatrix.postTranslate(dx, dy)
    }

    private fun currentScale(): Float {
        // 회전이 섞여도 평균적인 스케일을 구하려면 (a,c) 혹은 (b,d)로 길이를 계산
        workMatrix.getValues(values)
        val a = values[Matrix.MSCALE_X]
        val c = values[Matrix.MSKEW_X]
        return sqrt(a * a + c * c)
    }

    private fun angleBetween(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(1) - e.getX(0)
        val dy = e.getY(1) - e.getY(0)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }
}
