package com.francescozoccheddu.knob

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.Dimension
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import java.lang.Math.toRadians
import kotlin.math.*
import kotlin.reflect.KMutableProperty0


class KnobView : View {

    private inner class ColorF {

        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f

        private val Float.b8 get() = this.roundToInt()

        var int
            get() = Color.argb(a.b8, r.b8, g.b8, b.b8)
            set(value) {
                r = Color.red(value).f
                g = Color.green(value).f
                b = Color.blue(value).f
                a = Color.alpha(value).f
            }

        fun smooth(target: Int, smoothness: Float, elapsed: Float) {
            val old = int
            val tr = Color.red(target).f
            val tg = Color.green(target).f
            val tb = Color.blue(target).f
            val ta = Color.alpha(target).f
            val s = smoothness * GLOBAL_SMOOTHNESS_FACTOR
            r = smooth(r, tr, s, elapsed).snap(tr, COLOR_SNAP_THRESHOLD)
            g = smooth(g, tg, s, elapsed).snap(tg, COLOR_SNAP_THRESHOLD)
            b = smooth(b, tb, s, elapsed).snap(tb, COLOR_SNAP_THRESHOLD)
            a = smooth(a, ta, s, elapsed).snap(ta, COLOR_SNAP_THRESHOLD)
            if (old != int) invalidate()
        }

    }

    private fun getLengthByValue(value: Float) = (value - startValue) / revolutionValue

    private fun getValueByLength(length: Float) = length * revolutionValue + startValue

    private fun KMutableProperty0<Float>.smooth(target: Float, smoothness: Float, elapsed: Float, snapThreshold: Float, min: Float, max: Float) {
        val before = get()
        set(smooth(before, target, smoothness * GLOBAL_SMOOTHNESS_FACTOR, elapsed).snap(target, snapThreshold).clamp(min, max))
        if (before != get()) invalidate()
    }

    private val tempRect = RectF()

    private inner class Revolution(val index: Int) {

        private var thicknessFactor = 0f
        private var radiusFactor = 1f
        private val backgroundColor = ColorF()
        private val foregroundColor = ColorF()
        // TODO Add labels

        fun update(elapsed: Float) {
            val order = (progressLength - index).previous
            val positiveOrder = max(order, 0)
            run {
                val target = if (order < 0 && trackLength - index < MIN_COLLAPSING_TRACK_LENGTH) 0f
                else Math.pow(revolutionThicknessBackoff.d, positiveOrder.d).f
                ::thicknessFactor.smooth(target, trackLayoutSmoothness, elapsed, THICKNESS_FACTOR_SNAP_THRESHOLD, 0f, 1f)
            }
            run {
                val target = Math.pow(revolutionRadiusBackoff.d, positiveOrder.d).f
                ::radiusFactor.smooth(target, trackLayoutSmoothness, elapsed, RADIUS_FACTOR_SNAP_THRESHOLD, 0f, 1f)
            }
            run {
                backgroundColor.smooth(trackColors[index], trackLayoutSmoothness, elapsed)
                foregroundColor.smooth(progressColors[positiveOrder], trackLayoutSmoothness, elapsed)
            }
        }

        fun finishAnimation() {

        }

        fun draw(canvas: Canvas) {
            trackPaint.strokeWidth = thickness * thicknessFactor
            val cx = contentRect.centerX()
            val cy = contentRect.centerY()
            val r = outerTrackRadius * radiusFactor
            tempRect.set(cx - r, cy - r, cx + r, cy + r)

            val minValueLength = getLengthByValue(minValue)
            fun getSweep(length: Float) = (if (index == 0) min(length, 1f) - minValueLength else min(length - index, 1f)) * 360f

            val arcStart = (if (index == 0) minValueLength * 360f else 0f) - startAngle
            fun draw(sweep: Float, color: Int) {
                if (Color.alpha(color) > 0 && thicknessFactor > 0f) {
                    trackPaint.color = color
                    // TODO Support for counter-clockwise drawing
                    if (sweep > 0f) {
                        canvas.drawArc(tempRect, arcStart, sweep, false, trackPaint)
                    } else {
                        val arcStartRad = toRadians(-arcStart.d).f
                        val x = cos(arcStartRad) * r
                        val y = -sin(arcStartRad) * r
                        canvas.drawPoint(cx + x, cy + y, trackPaint)
                    }
                }
            }

            draw(getSweep(trackLength), backgroundColor.int)
            draw(getSweep(progressLength), foregroundColor.int)
        }

    }

    private val tracks = TRACK_INDICES.map { Revolution(it) }.toTypedArray()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    companion object {
        const val MAX_REVOLUTION_COUNT = 3
        const val GLOBAL_SMOOTHNESS_FACTOR = 1f / 4f
        private val INPUT_REVOLUTION_ARC_SNAP_THRESHOLD = 30f.dp
        private val INPUT_DRAG_ARC_SNAP_THRESHOLD = 60f.dp
        private const val LENGTH_SNAP_THRESHOLD = 1f / 500f
        private const val THICKNESS_FACTOR_SNAP_THRESHOLD = 1f / 1000f
        private const val RADIUS_FACTOR_SNAP_THRESHOLD = 1f / 1000f
        private const val COLOR_SNAP_THRESHOLD = 2f
        private const val MIN_COLLAPSING_TRACK_LENGTH = 1f / 4f
        private const val SCROLL_HOLD_RADIUS_FACTOR = 1f / 2f
        private const val SCROLL_FACTOR = 1f / 2f
        private val TRACK_INDICES = 0..(MAX_REVOLUTION_COUNT - 1)
    }

    var minValue = 0f
        set(value) {
            field = value
            invalidate()
        }
    var maxValue = 300f
        set(value) {
            field = value
            invalidate()
        }
    var startValue = 0f
        set(value) {
            field = value
            invalidate()
        }
    var value: Float
        get() {
            if (snap > 0f) {
                val ticks = ((unsnappedValue - startValue) / snap).roundToInt()
                return (ticks * snap + startValue).clamp(minValue, maxValue)
            } else
                return unsnappedValue
        }
        set(value) {
            unsnappedValue = value
        }
    private var unsnappedValue = 50f
    var revolutionValue = 100f
        set(value) {
            field = value
            invalidate()
        }
    @Dimension
    var thickness = 20f.dp
    val progressColors = TRACK_INDICES.map {
        val a = it / max(MAX_REVOLUTION_COUNT - 1, 1).f
        hsv(lerp(180f, 190f, a), lerp(0.75f, 0.5f, a), lerp(0.75f, 0.5f, a))
    }
    val trackColors = IntArray(MAX_REVOLUTION_COUNT).apply {
        fill(hsv(0f, 0f, 0.1f))
    }
    var trackColor
        get() = trackColors[0]
        set(value) = trackColors.fill(value)
    var progressColor
        get() = progressColors[0]
        set(value) = trackColors.fill(value)
    // degrees, counter-clockwise
    var startAngle = -90f
        set(value) {
            field = value
            invalidate()
        }
    @FloatRange(from = 0.7, to = 1.0)
    var revolutionRadiusBackoff = 0.9f
    @FloatRange(from = 0.7, to = 1.0)
    var revolutionThicknessBackoff = 0.9f
    @FloatRange(from = 1.0, to = 3.0)
    var inputThicknessFactor = 3f
    var scrollable = true
    var tappable = true
    var draggable = true
    @FloatRange(from = 0.0, to = 1.0)
    var progressSmoothness = 0.4f
    @FloatRange(from = 0.0, to = 1.0)
    var trackLayoutSmoothness = 0.4f
    @FloatRange(from = 0.0, to = 1.0)
    var trackLengthSmoothness = 0.4f
    var clockwise = true
    @FloatRange(from = 0.0)
    var snap = 0f
    @IntRange(from = 0, to = 20)
    var labelThicks = 0

    // TODO Add thumb (with thickness factor)
    // TODO Add trackThicknessFactor
    // TODO Use color function (with helpers 'per order', 'per index')
    // TODO Add default attributeset

    private fun updateValue(elapsed: Float) {
        unsnappedValue = unsnappedValue.clamp(minValue, maxValue)
        val valueLength = getLengthByValue(unsnappedValue)
        val minValueLength = getLengthByValue(minValue)
        val maxValueLength = getLengthByValue(maxValue)
        ::progressLength.smooth(valueLength, progressSmoothness, elapsed, LENGTH_SNAP_THRESHOLD, minValueLength, maxValueLength)
        run {
            val targetTrackLength = max(ceil(valueLength), 1f).clamp(progressLength, maxValueLength)
            ::trackLength.smooth(targetTrackLength, trackLengthSmoothness, elapsed, LENGTH_SNAP_THRESHOLD, progressLength, maxValueLength)
        }
    }

    private val animator = TimeAnimator().apply {
        setTimeListener { _, _, elapsedMillis ->
            // Validation
            if (minValue > maxValue) throw IllegalStateException("'${::minValue.name}' cannot be greater than '${::maxValue.name}'")
            if (startValue > minValue) throw IllegalStateException("'${::startValue.name}' cannot be greater than '${::minValue.name}'")
            if (revolutionValue <= 0) throw IllegalStateException("'${::revolutionValue.name}' must be positive")
            if ((maxValue - startValue) / revolutionValue > MAX_REVOLUTION_COUNT) throw IllegalStateException("Revolution count cannot be greater than $MAX_REVOLUTION_COUNT")
            if (getLengthByValue(minValue) >= 1f) throw IllegalStateException("'${::minValue.name}' does not fall inside the first revolution")
            // Update
            val elapsed = elapsedMillis / 1000f
            updateValue(elapsed)
            tracks.forEach { it.update(elapsed) }
        }
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    private val contentRect = RectF()

    private var progressLength = unsnappedValue
    private var trackLength = 0f

    fun finishLayoutSmoothing() {

    }

    fun finishValueSmoothing() {
        progressLength = unsnappedValue
    }

    fun finishTrackSmoothing() {

    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) animator.start()
        else animator.cancel()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.apply {
            tracks.forEach { it.draw(this) }
            // TODO Draw label
        }

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val paddingX = paddingLeft + paddingRight
        val paddingY = paddingTop + paddingBottom
        val contentW = w - paddingX
        val contentH = h - paddingY
        val radius = min(contentW, contentH) / 2f
        val centerX = paddingLeft + contentW / 2f
        val centerY = paddingTop + contentH / 2f
        contentRect.left = centerX - radius
        contentRect.right = centerX + radius
        contentRect.top = centerY - radius
        contentRect.bottom = centerY + radius
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // TODO Implement
    }

    private fun getLengthAt(distance: Float, angle: Float, @FloatRange(from = 0.0) thicknessFactor: Float): Float? {
        val d = abs(outerTrackRadius - distance)
        val maxD = thickness / 2f * thicknessFactor
        if (d <= maxD) {
            val ua = (if (clockwise) 360f - angle else angle) + startAngle
            val frl = normalizeAngle(ua) / 360f
            var lrl = frl + max(getLengthByValue(unsnappedValue).previous, 0)
            if (lrl > getLengthByValue(maxValue)) lrl -= 1f
            if (lrl >= getLengthByValue(minValue)) return lrl
        }
        return null
    }

    private fun getLengthAt(distance: Float, angle: Float,
                            @FloatRange(from = 0.0) thicknessFactor: Float,
                            @FloatRange(from = 0.0, to = 1.0) revolutionArcSnap: Float): Float? {
        val length = getLengthAt(distance, angle, thicknessFactor)
        if (length != null && revolutionArcSnap > 0f) {
            // TODO Implement
        }
        return length
    }

    private val contentRadius get() = contentRect.width() / 2f
    private val outerTrackRadius get() = (contentRect.width() - thickness) / 2f

    val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        private val MotionEvent.offX get() = getX(actionIndex) - contentRect.centerX()
        private val MotionEvent.offY get() = getY(actionIndex) - contentRect.centerY()
        private val MotionEvent.angle get() = angle(offX, -offY)
        private val MotionEvent.distance get() = length(offX, offY)

        private fun getLengthAt(event: MotionEvent, thicknessFactor: Float, revolutionSnap: Float): Float? {
            val nx = event.offX
            val ny = event.offY
            return getLengthAt(length(nx, ny), angle(nx, -ny), thicknessFactor, revolutionSnap)
        }


        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            if (e1 != null) {
                if (draggable && e2 != null && getLengthAt(e1, inputThicknessFactor, 0f) != null) {
                    val length = getLengthAt(e2, inputThicknessFactor, 0f)
                    if (length != null) {
                        val unsnappedLength = getLengthByValue(unsnappedValue)
                        val snappedLength = getLengthByValue(value)
                        val minLength = getLengthByValue(minValue)
                        val maxLength = getLengthByValue(maxValue)
                        fun trySet(length: Float): Boolean {
                            if (length >= minLength && length <= maxLength) {
                                val minDiff = absMin(length - unsnappedLength, length - snappedLength, length - progressLength)
                                if (minDiff * Math.PI * 2 * outerTrackRadius <= INPUT_DRAG_ARC_SNAP_THRESHOLD) {
                                    unsnappedValue = getValueByLength(length)
                                    return true
                                }
                            }
                            return false
                        }
                        return trySet(length) || trySet(length + 1f) || trySet(length - 1f)
                    }
                } else if (scrollable) {
                    val r = contentRadius
                    if (r > 0 && e1.distance <= r * SCROLL_HOLD_RADIUS_FACTOR) {
                        unsnappedValue += distanceY / r * SCROLL_FACTOR * revolutionValue
                        return true
                    }
                }
            }
            return false
        }

        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            if (tappable && e != null) {
                val length = getLengthAt(e, inputThicknessFactor, INPUT_REVOLUTION_ARC_SNAP_THRESHOLD)
                if (length != null) {
                    unsnappedValue = getValueByLength(length)
                    return true
                }
            }
            return false
        }

    }).apply {
        setIsLongpressEnabled(false)
        setOnDoubleTapListener(null)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return gestureDetector.onTouchEvent(event)
        // TODO Scroll wheel
        // TODO Keyboard arrows
    }
}