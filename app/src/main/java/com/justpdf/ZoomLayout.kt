package com.justpdf

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout

/**
 * A FrameLayout that provides pinch-to-zoom and pan for its single child.
 * Zoom is applied via the child's scaleX/scaleY/pivotX/pivotY/translationX/Y
 * properties — no canvas matrix needed, so touch coordinates stay correct.
 *
 * Zoom range : 1× – 5×
 * Double-tap : resets to 1×
 * Pan        : drag while zoomed in
 */
class ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

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

                // Keep the focal point stationary in the layout
                val fx = detector.focusX
                val fy = detector.focusY
                translateX = fx - (fx - translateX) * (scaleFactor / prevScale)
                translateY = fy - (fy - translateY) * (scaleFactor / prevScale)

                clampTranslation()
                applyTransform()
                return true
            }
        })

    // ── Pan ────────────────────────────────────────────────────────────────────
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = INVALID_POINTER_ID

    // ── Double-tap reset ───────────────────────────────────────────────────────
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
    // Touch handling
    // ──────────────────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept multi-touch (pinch) always, and single-touch pan when zoomed
        return ev.pointerCount > 1 || scaleFactor > 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                lastTouchX = event.getX(idx)
                lastTouchY = event.getY(idx)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0 && scaleFactor > 1f) {
                        translateX += event.getX(idx) - lastTouchX
                        translateY += event.getY(idx) - lastTouchY
                        clampTranslation()
                        applyTransform()
                    }
                    val idx2 = event.findPointerIndex(activePointerId)
                    if (idx2 >= 0) {
                        lastTouchX = event.getX(idx2)
                        lastTouchY = event.getY(idx2)
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIdx = event.actionIndex
                if (event.getPointerId(pointerIdx) == activePointerId) {
                    val newIdx = if (pointerIdx == 0) 1 else 0
                    activePointerId = event.getPointerId(newIdx)
                    lastTouchX = event.getX(newIdx)
                    lastTouchY = event.getY(newIdx)
                }
            }
        }
        return true
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Transform helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun clampTranslation() {
        if (width == 0 || height == 0) return
        val maxTx = width  * (scaleFactor - 1f) / 2f
        val maxTy = height * (scaleFactor - 1f) / 2f
        translateX = translateX.coerceIn(-maxTx, maxTx)
        translateY = translateY.coerceIn(-maxTy, maxTy)
    }

    private fun applyTransform() {
        val child: View = getChildAt(0) ?: return
        // Pivot at the top-left so our manual translation is the sole offset
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translateX
        child.translationY = translateY
    }

    fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        applyTransform()
    }
}
