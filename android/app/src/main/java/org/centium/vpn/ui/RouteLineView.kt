package org.centium.vpn.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import org.centium.vpn.R

/**
 * Vertical transit-map line drawn behind its station rows. Each child is one
 * station; its dot sits level with the child's first view (the title line).
 * [progress] runs 0..1 from the first station to the last.
 */
class RouteLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class Tone { ROUTE, DANGER }

    private val density = resources.displayMetrics.density
    private val lineX = 14f * density
    private val dotRadius = 6f * density
    private val terminusRadius = 8f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(2f * density, 5f * density), 0f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val terminusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var animator: ValueAnimator? = null

    var progress: Float = 0f
        private set

    /** Draws a soft halo at the line's leading point while a connection is being built. */
    var building: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var tone: Tone = Tone.ROUTE
        set(value) {
            field = value
            applyColors()
            invalidate()
        }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        applyColors()
    }

    private fun applyColors() {
        val accent = context.getColor(if (tone == Tone.DANGER) R.color.danger else R.color.route)
        trackPaint.color = context.getColor(R.color.faint)
        linePaint.color = accent
        fillPaint.color = accent
        ringPaint.color = context.getColor(R.color.faint)
        terminusPaint.color = context.getColor(R.color.ink)
        groundPaint.color = context.getColor(R.color.mist)
    }

    fun setProgress(target: Float, animate: Boolean) {
        val clamped = target.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate || clamped == progress) {
            progress = clamped
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, clamped).apply {
            // Slower when the line travels further, so each hop reads as a step
            duration = (350 + 900 * kotlin.math.abs(clamped - progress)).toLong()
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun stationY(index: Int): Float {
        val row = getChildAt(index)
        val anchor = (row as? ViewGroup)?.getChildAt(0) ?: row
        var top = anchor.top.toFloat()
        if (anchor !== row) top += row.top
        return top + anchor.height / 2f
    }

    override fun dispatchDraw(canvas: Canvas) {
        val count = childCount
        if (count >= 2) drawRoute(canvas, count)
        super.dispatchDraw(canvas)
    }

    private fun drawRoute(canvas: Canvas, count: Int) {
        val ys = FloatArray(count) { stationY(it) }
        val segments = count - 1

        canvas.drawLine(lineX, ys.first(), lineX, ys.last(), trackPaint)

        // Leading point: progress is spread evenly across segments, not pixels
        val scaled = progress * segments
        val seg = scaled.toInt().coerceAtMost(segments - 1)
        val frac = scaled - seg
        val headY = ys[seg] + (ys[seg + 1] - ys[seg]) * frac

        if (progress > 0f) {
            canvas.drawLine(lineX, ys.first(), lineX, headY, linePaint)
        }

        for (i in 0 until count) {
            val reached = progress > 0f && scaled >= i - 0.001f
            val terminus = i == 0 || i == count - 1
            val r = if (terminus) terminusRadius else dotRadius
            // Clear the track behind the station so dots read as stops, not beads
            canvas.drawCircle(lineX, ys[i], r, groundPaint)
            if (reached) {
                canvas.drawCircle(lineX, ys[i], r, fillPaint)
                if (terminus) canvas.drawCircle(lineX, ys[i], r * 0.4f, groundPaint)
            } else {
                // Termini always exist (your phone, the internet); relays are only potential stops
                val paint = if (terminus) terminusPaint else ringPaint
                canvas.drawCircle(lineX, ys[i], r - paint.strokeWidth / 2f, paint)
            }
        }

        if (building && progress < 1f) {
            fillPaint.alpha = 56
            canvas.drawCircle(lineX, headY, 12f * density, fillPaint)
            fillPaint.alpha = 255
            canvas.drawCircle(lineX, headY, 4f * density, fillPaint)
        }
    }
}
