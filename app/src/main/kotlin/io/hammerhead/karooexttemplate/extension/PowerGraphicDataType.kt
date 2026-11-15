package io.hammerhead.karooexttemplate.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.widget.RemoteViews
import io.hammerhead.karooexttemplate.R
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Power counterpart of [HeartRateGraphicDataType]: same layout and zone bar,
 * showing 3s power in the middle, average power on the left and normalized
 * power on the right, with the bar driven by the Karoo profile's power zones.
 */
class PowerGraphicDataType(extension: String) :
    DataTypeImpl(extension, "power-graphic") {

    private var power: Double? = null
    private var averagePower: Double? = null
    private var normalizedPower: Double? = null

    private var powerZones: List<UserProfile.Zone> = emptyList()

    private var viewContext: Context? = null
    private var viewEmitter: ViewEmitter? = null

    // Seven Coggan-style zones, reusing the HR field's palette for Z2-Z6 so
    // the two fields read the same at a glance.
    private val zoneColors = listOf(
        Color.rgb(158, 158, 158), // Z1 active recovery
        Color.rgb(33, 150, 243),  // Z2 endurance
        Color.rgb(0, 200, 83),    // Z3 tempo
        Color.rgb(255, 234, 0),   // Z4 threshold
        Color.rgb(251, 100, 85),  // Z5 VO2max
        Color.rgb(255, 23, 68),   // Z6 anaerobic
        Color.rgb(170, 0, 255)    // Z7 neuromuscular
    )

    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter
    ) {
        viewContext = context
        viewEmitter = emitter

        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        updateView()
    }

    fun updatePower(value: Double) {
        power = value
        updateView()
    }

    fun updatePowerStats(average: Double?, normalized: Double?) {
        averagePower = average
        normalizedPower = normalized
        updateView()
    }

    fun updateZones(zones: List<UserProfile.Zone>) {
        powerZones = zones
        updateView()
    }

    private fun updateView() {
        val context = viewContext ?: return
        val emitter = viewEmitter ?: return

        val views = RemoteViews(
            context.packageName,
            R.layout.power_graphic_view
        )

        val currentText = power?.roundToInt()?.toString() ?: "--"

        // CURRENT POWER (shrinks for 4-digit sprint numbers so it still fits)
        views.setImageViewBitmap(
            R.id.power_value,
            createTextBitmap(
                context = context,
                text = currentText,
                textSizeSp = if (currentText.length > 3) 96f else 122f,
                textColor = Color.WHITE,
                bold = false,
                useMonospace = true,
                tightReference = "0123456789"
            )
        )

        // AVG LABEL
        views.setImageViewBitmap(
            R.id.power_average_label,
            createTextBitmap(
                context = context,
                text = "AVG",
                textSizeSp = 15f,
                textColor = Color.WHITE,
                bold = true,
                useMonospace = false,
                tightReference = null
            )
        )

        // AVG VALUE
        views.setImageViewBitmap(
            R.id.power_average,
            createTextBitmap(
                context = context,
                text = averagePower?.roundToInt()?.toString() ?: "--",
                textSizeSp = 36f,
                textColor = Color.WHITE,
                bold = false,
                useMonospace = true,
                tightReference = "0123456789"
            )
        )

        // NP LABEL
        views.setImageViewBitmap(
            R.id.power_normalized_label,
            createTextBitmap(
                context = context,
                text = "NP",
                textSizeSp = 15f,
                textColor = Color.WHITE,
                bold = true,
                useMonospace = false,
                tightReference = null
            )
        )

        // NP VALUE
        views.setImageViewBitmap(
            R.id.power_normalized,
            createTextBitmap(
                context = context,
                text = normalizedPower?.roundToInt()?.toString() ?: "--",
                textSizeSp = 36f,
                textColor = Color.WHITE,
                bold = false,
                useMonospace = true,
                tightReference = "0123456789"
            )
        )

        views.setImageViewBitmap(
            R.id.power_zone_bar,
            createZoneBar(
                width = 600,
                height = 20,
                currentPower = power,
                zones = powerZones
            )
        )

        emitter.updateView(views)
    }

    private fun createTextBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        textColor: Int,
        bold: Boolean = false,
        useMonospace: Boolean = true,
        tightReference: String? = null
    ): Bitmap {

        val density = context.resources.displayMetrics.density
        val scaledSize = textSizeSp * density

        val typeface: Typeface = Typeface.create(
            if (useMonospace) "monospace" else "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = scaledSize
            this.typeface = typeface

            if (bold && useMonospace) {
                isFakeBoldText = true
            }

            textAlign = Paint.Align.LEFT
        }

        val fontMetrics = paint.fontMetrics
        val padding = 2f * density

        val top: Float
        val bottom: Float

        if (tightReference != null) {
            val referenceBounds = Rect()

            paint.getTextBounds(
                tightReference,
                0,
                tightReference.length,
                referenceBounds
            )

            top = referenceBounds.top.toFloat()
            bottom = referenceBounds.bottom.toFloat()
        } else {
            top = fontMetrics.ascent
            bottom = fontMetrics.descent
        }

        val bitmapWidth = ceil(
            paint.measureText(text) + padding * 2
        ).toInt().coerceAtLeast(1)

        val bitmapHeight = ceil(
            bottom - top + padding * 2
        ).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(
            bitmapWidth,
            bitmapHeight,
            Bitmap.Config.ARGB_8888
        )

        Canvas(bitmap).drawText(
            text,
            padding,
            padding - top,
            paint
        )

        return bitmap
    }

    private fun createZoneBar(
        width: Int,
        height: Int,
        currentPower: Double?,
        zones: List<UserProfile.Zone>
    ): Bitmap {

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        if (zones.isEmpty()) {
            return bitmap
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Active zone = the highest zone whose lower bound we've reached.
        // (Power is fractional, so an exact min..max match can fall in the
        // 1 W gap between integer zone bounds.)
        val activeZoneIndex = currentPower?.let { value ->
            zones.indexOfLast { zone -> value >= zone.min }
                .coerceAtLeast(0)
        } ?: -1

        val inactiveWeight = 1f
        val activeWeight = 1.9f

        val totalWeight = zones.indices.sumOf { index ->
            if (index == activeZoneIndex) {
                activeWeight.toDouble()
            } else {
                inactiveWeight.toDouble()
            }
        }.toFloat()

        val gap = 5f
        val availableWidth = width.toFloat() - gap * (zones.size - 1)
        val unitWidth = availableWidth / totalWeight

        var left = 0f

        zones.forEachIndexed { index, zone ->

            val isActive = index == activeZoneIndex
            val weight = if (isActive) activeWeight else inactiveWeight

            val segmentWidth = unitWidth * weight
            val right = left + segmentWidth

            val barTop = if (isActive) 1.5f else 6f
            val barBottom = if (isActive) 16f else 13f

            paint.color = zoneColors.getOrElse(index) { Color.GRAY }

            canvas.drawRect(left, barTop, right, barBottom, paint)

            if (isActive) {

                val watts = currentPower!!.toFloat()

                // The top zone is open-ended (its max is often a huge
                // sentinel), so give it a span equal to its lower bound's
                // distance above the previous zone's floor, or 50% of min.
                val span = if (index == zones.lastIndex) {
                    val prevMin = zones.getOrNull(index - 1)?.min
                    val fallback = zone.min * 0.5f
                    (if (prevMin != null) (zone.min - prevMin) * 2f else fallback)
                        .coerceAtLeast(fallback)
                } else {
                    (zone.max - zone.min + 1).toFloat()
                }.coerceAtLeast(1f)

                val positionInZone = ((watts - zone.min) / span)
                    .coerceIn(0f, 1f)

                val markerCenter = left + positionInZone * segmentWidth

                val markerTop = 0f
                val markerBottom = 17.8f

                val outerMarker = Path().apply {
                    moveTo(markerCenter - 8.4f, markerTop)
                    lineTo(markerCenter + 8.4f, markerTop)
                    lineTo(markerCenter + 13.2f, markerBottom)
                    lineTo(markerCenter - 13.2f, markerBottom)
                    close()
                }

                paint.color = Color.WHITE
                canvas.drawPath(outerMarker, paint)

                val innerMarker = Path().apply {
                    moveTo(markerCenter - 4.4f, barTop)
                    lineTo(markerCenter + 4.4f, barTop)
                    lineTo(markerCenter + 10.8f, barBottom)
                    lineTo(markerCenter - 10.8f, barBottom)
                    close()
                }

                canvas.save()
                canvas.clipRect(left, barTop, right, barBottom)
                paint.color = Color.BLACK
                canvas.drawPath(innerMarker, paint)
                canvas.restore()
            }

            left = right + gap
        }

        return bitmap
    }
}
