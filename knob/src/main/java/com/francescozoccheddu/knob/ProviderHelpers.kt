package com.francescozoccheddu.knob

import android.graphics.Color
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.Size
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt


// Factor

enum class ProviderIndexingMode {
    BY_TRACK, BY_ORDER
}

private fun ProviderIndexingMode.provide(track: Int, order: Int) = when (this) {
    ProviderIndexingMode.BY_ORDER -> order
    ProviderIndexingMode.BY_TRACK -> track
}

private fun <Type> ProviderIndexingMode.fromListClamped(track: Int, order: Int, list: List<Type>) =
    list[provide(track, order).clamp(0, list.lastIndex)]

class ConstantFactorProvider : KnobView.FactorProvider {

    @FloatRange(from = 0.0, to = 1.0)
    var factor = 0.5f

    override fun provide(view: KnobView, track: Int, order: Int) = factor

}

class ListFactorProvider : KnobView.FactorProvider {

    @Size(min = 1)
    val factors = mutableListOf<Float>()
    var indexingMode = ProviderIndexingMode.BY_ORDER

    override fun provide(view: KnobView, track: Int, order: Int) = indexingMode.fromListClamped(track, order, factors)

}

class BackoffFactorProvider : KnobView.FactorProvider {

    @FloatRange(from = 0.0, to = 1.0)
    var backoff = 0.9f
    var indexingMode = ProviderIndexingMode.BY_ORDER

    override fun provide(view: KnobView, track: Int, order: Int): Float = backoff.pow(indexingMode.provide(track, order).f)

}

class CurveFactorProvider : KnobView.FactorProvider {

    @FloatRange(from = 0.0, to = 1.0)
    var from = 0f
    @FloatRange(from = 0.0, to = 1.0)
    var to = 1f
    var interpolator = LinearInterpolator()
    var indexingMode = ProviderIndexingMode.BY_ORDER

    override fun provide(view: KnobView, track: Int, order: Int) =
        lerp(from, to, interpolator.getInterpolation(indexingMode.provide(track, order).f / max(view.trackCount - 1, 1)))

}


// Color

class ConstantColorProvider : KnobView.ColorProvider {

    @ColorInt
    var color = Color.BLACK

    override fun provide(view: KnobView, track: Int, order: Int) = color

}

class ListColorProvider : KnobView.ColorProvider {

    @Size(min = 1)
    val colors = mutableListOf<Int>()
    var indexingMode = ProviderIndexingMode.BY_ORDER

    override fun provide(view: KnobView, track: Int, order: Int) = indexingMode.fromListClamped(track, order, colors)
}

class RGBCurveColorProvider : KnobView.ColorProvider {

    @ColorInt
    var from = Color.BLACK
    @ColorInt
    var to = Color.WHITE
    var interpolator = LinearInterpolator()
    var indexingMode = ProviderIndexingMode.BY_ORDER

    override fun provide(view: KnobView, track: Int, order: Int) =
        lerpColor(from, to, interpolator.getInterpolation(indexingMode.provide(track, order).f / max(view.trackCount - 1, 1)))

}

class HSVCurveColorProvider : KnobView.ColorProvider {

    @FloatRange(from = 0.0, to = 360.0)
    var fromHue = 0f
    @FloatRange(from = 0.0, to = 1.0)
    var fromSaturation = 0f
    @FloatRange(from = 0.0, to = 1.0)
    var fromValue = 0f
    @FloatRange(from = 0.0, to = 1.0)
    var fromAlpha = 1f
    @FloatRange(from = 0.0, to = 360.0)
    var toHue = 0f
    @FloatRange(from = 0.0, to = 1.0)
    var toSaturation = 0f
    @FloatRange(from = 0.0, to = 1.0)
    var toValue = 1f
    @FloatRange(from = 0.0, to = 1.0)
    var toAlpha = 1f
    var interpolator = LinearInterpolator()
    var indexingMode = ProviderIndexingMode.BY_ORDER

    override fun provide(view: KnobView, track: Int, order: Int): Int {
        val progress = interpolator.getInterpolation(indexingMode.provide(track, order).f / max(view.trackCount - 1, 1))
        val h = lerp(fromHue, toHue, progress)
        val s = lerp(fromSaturation, toSaturation, progress)
        val v = lerp(fromValue, toValue, progress)
        val a = lerp(fromAlpha, toAlpha, progress)
        return hsv(h, s, v, a)
    }

}


// Thicks

class ValueThickTextProvider : KnobView.ThickTextProvider {

    @IntRange(from = 0L, to = 4L)
    var decimalPlaces = 0
    @Size(min = 0, max = 3)
    var prefix = ""
    @Size(min = 0, max = 3)
    var suffix = ""

    override fun provide(view: KnobView, track: Int, thick: Int, value: Float): String {
        val rounded = BigDecimal(value.d).setScale(decimalPlaces, RoundingMode.HALF_UP)
        return "$prefix$rounded$suffix"
    }

}

class PercentageThickTextProvider : KnobView.ThickTextProvider {

    override fun provide(view: KnobView, track: Int, thick: Int, value: Float) =
        "${((value - view.startValue) / (view.maxValue - view.startValue) * 100f).roundToInt()}%"

}

class ThickListTextProvider : KnobView.ThickTextProvider {

    val thicks = mutableListOf<String>()
    var restartOnTrack = true

    override fun provide(view: KnobView, track: Int, thick: Int, value: Float) =
        if (restartOnTrack) thicks[thick + view.thicks * track]
        else thicks[thick]

}


// Label

class ValueLabelProvider : KnobView.LabelProvider {

    @IntRange(from = 0L, to = 4L)
    var decimalPlaces = 0
    @Size(min = 0, max = 3)
    var prefix = ""
    @Size(min = 0, max = 3)
    var suffix = ""

    override fun provide(view: KnobView, value: Float): String {
        val rounded = BigDecimal(value.d).setScale(decimalPlaces, RoundingMode.HALF_UP)
        return "$prefix$rounded$suffix"
    }

}

class PercentageLabelProvider : KnobView.LabelProvider {

    override fun provide(view: KnobView, value: Float) =
        "${((value - view.startValue) / (view.maxValue - view.startValue) * 100f).roundToInt()}%"

}