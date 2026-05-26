package com.justpdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * A FrameLayout that applies a pinch-to-zoom + pan Matrix transform to ALL of
 * its children as a single unit — so the entire RecyclerView (all PDF pages)
 * zooms together, pivoting exactly at the pinch focal point.
 *
 * Zoom range : 1× – 5×
 * Double-tap : resets to 1×
 */
class ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val drawMatrix = Matrix()
    private val invertMatrix = Matrix()

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

                // Pivot the zoom at the focal point
                val fx = detector.focusX
                val fy = detector.focusY
                translateX = fx - (fx - translateX) * (scaleFactor / prevScale)
                translateY = fy - (fy - translateY) * (scaleFactor / prevScale)

                clampTranslation()
                updateMatrix()
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
    // Drawing — apply the matrix to the whole canvas so every child is affected
    // ──────────────────────────────────────────────────────────────────────────

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.concat(drawMatrix)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Touch — map raw touch coords through the inverse matrix before dispatching
    // so child views (RecyclerView scroll) still receive correct coordinates.
    // ──────────────────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept all events when zoomed so the RecyclerView doesn't steal
        // horizontal/diagonal panning gestures.
        return scaleFactor > 1f
    }

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
                        updateMatrix()
                    }
                } else {
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
                // When back at 1× let the RecyclerView handle scrolling again
                if (scaleFactor <= 1f) resetZoom()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIdx = event.actionIndex
                val pointerId = event.getPointerId(pointerIdx)
                if (pointerId == activePointerId) {
                    val newIdx = if (pointerIdx == 0) 1 else 0
                    activePointerId = event.getPointerId(newIdx)
                    lastTouchX = event.getX(newIdx)
                    lastTouchY = event.getY(newIdx)
                }
            }
        }

        // When not zoomed, pass events to children (RecyclerView) for scrolling
        if (scaleFactor <= 1f) {
            transformAndDispatchToChild(event)
        }
        return true
    }

    /**
     * Map touch coordinates through the inverse matrix and re-dispatch to
     * children so the RecyclerView receives correctly-scaled coordinates.
     */
    private fun transformAndDispatchToChild(event: MotionEvent) {
        drawMatrix.invert(invertMatrix)
        val transformed = MotionEvent.obtain(event)
        transformed.transform(invertMatrix)
        super.dispatchTouchEvent(transformed)
        transformed.recycle()
    }

    // ──────────────────────────────────────────────────────────────────────────

    private fun clampTranslation() {
        if (width == 0 || height == 0) return
        val maxTx = width  * (scaleFactor - 1f) / 2f
        val maxTy = height * (scaleFactor - 1f) / 2f
        // Allow panning up to the scaled edges
        translateX = translateX.coerceIn(-maxTx * 2f, maxTx * 2f)
        translateY = translateY.coerceIn(-maxTy * 2f, maxTy * 2f)
    }

    private fun updateMatrix() {
        drawMatrix.reset()
        // Translate to pivot, scale, translate back, then apply pan offset
        drawMatrix.postTranslate(translateX, translateY)
        drawMatrix.postScale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        // Correct: build matrix as scale-around-focal then translate
        drawMatrix.reset()
        drawMatrix.setScale(scaleFactor, scaleFactor)
        drawMatrix.postTranslate(translateX, translateY)
        invalidate()
    }

    fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        updateMatrix()
    }
}
