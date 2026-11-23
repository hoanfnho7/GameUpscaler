package com.nho.upscaler

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Modern tactical pill toggle switch matching the Cyber-Utility Volt Lime design.
 * Track: 48dp x 26dp capsule
 * Thumb: 20dp smooth sliding circular dot
 */
class ModernToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackRect = RectF()

    var isChecked: Boolean = false
        private set

    private var progress: Float = 0f // 0f = off, 1f = on
    private var animator: ValueAnimator? = null

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            setChecked(!isChecked, animate = true, notify = true)
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean = true, notify: Boolean = true) {
        if (isChecked == checked && (progress == 0f || progress == 1f)) return
        isChecked = checked

        animator?.cancel()
        if (animate && isAttachedToWindow) {
            val target = if (checked) 1f else 0f
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = 160L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = if (checked) 1f else 0f
            invalidate()
        }

        if (notify) {
            onCheckedChangeListener?.invoke(checked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val defaultW = (48 * density + 0.5f).toInt()
        val defaultH = (26 * density + 0.5f).toInt()
        val w = resolveSize(defaultW, widthMeasureSpec)
        val h = resolveSize(defaultH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cornerRadius = h / 2f
        val isDark = UiTheme.isDark(context)

        // Interpolate track color
        val offTrack = if (isDark) Color.parseColor("#333538") else Color.parseColor("#D1D5DB")
        val onTrack = UiTheme.accent(context)
        trackPaint.color = blendColors(offTrack, onTrack, progress)

        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        // Interpolate thumb color & position
        val offThumb = if (isDark) Color.parseColor("#8F937E") else Color.WHITE
        val onThumb = UiTheme.textOnAccent(context)
        thumbPaint.color = blendColors(offThumb, onThumb, progress)

        val padding = h * 0.12f
        val thumbRadius = (h - 2f * padding) / 2f
        val startX = padding + thumbRadius
        val endX = w - padding - thumbRadius
        val currentX = startX + (endX - startX) * progress
        val centerY = h / 2f

        canvas.drawCircle(currentX, centerY, thumbRadius, thumbPaint)
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio
        val r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio
        val g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio
        val b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }
}

/**
 * Modern neon radio button indicator matching the SSAA resolution preset cards in Stitch.
 * Unselected: dark circle with subtle border
 * Selected: filled Volt Lime outer ring with dark center dot
 */
class ModernRadioIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    var isRadioSelected: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val size = (24 * density + 0.5f).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(cx, cy) - 2f
        if (radius <= 0f) return
        val isDark = UiTheme.isDark(context)

        if (isRadioSelected) {
            // Active neon Volt Lime fill + dark center dot
            fillPaint.color = UiTheme.accent(context)
            canvas.drawCircle(cx, cy, radius, fillPaint)

            dotPaint.color = UiTheme.textOnAccent(context)
            canvas.drawCircle(cx, cy, radius * 0.42f, dotPaint)
        } else {
            // Inactive subtle outline ring
            fillPaint.color = if (isDark) Color.parseColor("#1E2023") else Color.parseColor("#E5E7EB")
            canvas.drawCircle(cx, cy, radius, fillPaint)

            ringPaint.color = if (isDark) Color.parseColor("#383B40") else Color.parseColor("#CBD5E1")
            ringPaint.strokeWidth = (1.5f * resources.displayMetrics.density).coerceAtLeast(1f)
            canvas.drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)
        }
    }
}
