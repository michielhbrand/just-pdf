package com.justpdf

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * A FrameLayout that supports pinch-to-zoom and pan gestures for its single child view.
 * Zoom range: 1× – 5×. Double-tap resets to 1×.
 */
class ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private val minScale = 1f
    private val maxScale = 5f

    // ── Scale gesture ──────────────────────────────────────────────────────────
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)

                // Pivot around the focal point
                val focusX = detector.focusX
                val focusY = detector.focusY
                translateX = focusX - (focusX - translateX) * (scaleFactor / prevScale)
                translateY = focusY - (focusY - translateY) * (scaleFactor / prevScale)

                clampTranslation()
                applyTransform()
                return true
            }
        })

    // ── Pan gesture ────────────────────────────────────────────────────────────
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = INVALID_POINTER_ID

    // ── Double-tap to reset ────────────────────────────────────────────────────
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom()
                return true
            }
        })

    companion object {
        private const val INVALID_POINTER_ID = -1
    }

    // ──────────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && scaleFactor > 1f) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        translateX += event.getX(idx) - lastTouchX
                        translateY += event.getY(idx) - lastTouchY
                        lastTouchX = event.getX(idx)
                        lastTouchY = event.getY(idx)
                        clampTranslation()
                        applyTransform()
                    }
                } else {
                    // Keep last position updated even during scale so pan doesn't jump
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        lastTouchX = event.getX(idx)
                        lastTouchY = event.getY(idx)
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIdx = event.actionIndex
                val pointerId = event.getPointerId(pointerIdx)
                if (pointerId == activePointerId) {
                    // Primary pointer lifted — pick a new one
                    val newIdx = if (pointerIdx == 0) 1 else 0
                    activePointerId = event.getPointerId(newIdx)
                    lastTouchX = event.getX(newIdx)
                    lastTouchY = event.getY(newIdx)
                }
            }
        }
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Let the ZoomLayout handle all touch events when zoomed in so the
        // RecyclerView doesn't steal horizontal/diagonal panning.
        return scaleFactor > 1f
    }

    // ──────────────────────────────────────────────────────────────────────────

    private fun clampTranslation() {
        if (width == 0 || height == 0) return
        val maxTransX = (width * (scaleFactor - 1f)) / 2f
        val maxTransY = (height * (scaleFactor - 1f)) / 2f
        translateX = translateX.coerceIn(-maxTransX, maxTransX)
        translateY = translateY.coerceIn(-maxTransY, maxTransY)
    }

    private fun applyTransform() {
        val child: View = getChildAt(0) ?: return
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translateX
        child.translationY = translateY
    }

    private fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        applyTransform()
    }
}
