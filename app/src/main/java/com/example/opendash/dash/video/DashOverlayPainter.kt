package com.example.opendash.dash.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import com.example.opendash.dash.DashMenu
import com.example.opendash.media.DashMessage
import com.example.opendash.util.WeatherNow
import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver

/**
 * Everything the app draws on top of the dash map: turn banner with arrow, lane strip,
 * joystick menu, message card, weather and speed warning. The Tripper screen is round,
 * so all of it stays inside the circle at the centre of the 526x300 frame.
 */
class DashOverlayPainter {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60B0D0E.toInt() }
    private val rowHi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2A93B.toInt() }
    private val white = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
    }
    private val dim = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9BEC4.toInt(); textSize = 11f }
    private val gold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2A93B.toInt(); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
    }
    private val banner = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
    }
    private val whiteTint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    private val plainBitmap = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val signRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD93025.toInt() }
    private val signFace = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val signText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    private fun fit(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    /** "350 m  Turn left" with an arrow, top-centre. Uses Google's arrow picture when it sent one. */
    fun drawTurnBanner(canvas: Canvas, width: Int, text: String, maneuver: Int, icon: Bitmap?) {
        val cx = width / 2f
        val label = fit(text, banner, 190f)
        val iconW = 24f
        val total = iconW + 8f + banner.measureText(label)
        val left = cx - total / 2f
        canvas.drawRoundRect(left - 12f, 28f, left + total + 12f, 60f, 15f, 15f, bg)
        val box = RectF(left, 32f, left + iconW, 56f)
        if (icon != null && !icon.isRecycled) canvas.drawBitmap(icon, null, box, whiteTint)
        else drawArrow(canvas, box, maneuver)
        canvas.drawText(label, left + iconW + 8f, 50f, banner)
    }

    /** Google's lane picture (which lanes lead to the next turn), just under the banner. */
    fun drawLanes(canvas: Canvas, width: Int, lanes: Bitmap?) {
        if (lanes == null || lanes.isRecycled || lanes.width == 0 || lanes.height == 0) return
        val h = 24f
        val w = (lanes.width * (h / lanes.height)).coerceAtMost(170f)
        val cx = width / 2f
        canvas.drawRoundRect(cx - w / 2f - 8f, 63f, cx + w / 2f + 8f, 63f + h + 8f, 10f, 10f, bg)
        canvas.drawBitmap(lanes, null, RectF(cx - w / 2f, 67f, cx + w / 2f, 67f + h), plainBitmap)
    }

    /** Simple drawn arrow for when no picture is available. */
    private fun drawArrow(canvas: Canvas, box: RectF, maneuver: Int) {
        val angle = turnAngle(maneuver)
        val cx = box.centerX(); val cy = box.centerY()
        val r = box.height() / 2f - 2f
        canvas.save()
        canvas.rotate(angle, cx, cy)
        val p = Path().apply {
            moveTo(cx, cy + r); lineTo(cx, cy - r)
            moveTo(cx - r * 0.6f, cy - r * 0.35f); lineTo(cx, cy - r); lineTo(cx + r * 0.6f, cy - r * 0.35f)
        }
        canvas.drawPath(p, arrow)
        canvas.restore()
    }

    /** Joystick menu in the middle of the screen. */
    fun drawMenu(canvas: Canvas, width: Int, height: Int, menu: DashMenu, nowMs: Long) {
        if (!menu.isOpen) return
        val cx = width / 2f
        val left = cx - 104f; val right = cx + 104f
        val top = height / 2f - 98f; val bottom = height / 2f + 98f
        canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, bg)
        white.textAlign = Paint.Align.CENTER
        canvas.drawText(fit(menu.title(), white, 190f), cx, top + 22f, white)
        white.textAlign = Paint.Align.LEFT

        val rows = menu.rows(nowMs)
        val visible = 5
        val rowH = 27f
        val first = (menu.index - visible + 1).coerceAtLeast(0).coerceAtMost((rows.size - visible).coerceAtLeast(0))
        var y = top + 32f
        for (i in first until minOf(rows.size, first + visible)) {
            val row = rows[i]
            val selected = i == menu.index && menu.page != DashMenu.Page.CONFIRM
            if (selected) canvas.drawRoundRect(left + 6f, y, right - 6f, y + rowH - 2f, 8f, 8f, rowHi)
            white.color = if (selected) Color.BLACK else Color.WHITE
            dim.color = if (selected) 0xFF2A2A2A.toInt() else 0xFFB9BEC4.toInt()
            val rightW = if (row.right.isBlank()) 0f else dim.measureText(row.right) + 8f
            canvas.drawText(fit(row.left, white, 184f - rightW), left + 14f, y + 18f, white)
            if (row.right.isNotBlank()) {
                dim.textAlign = Paint.Align.RIGHT
                canvas.drawText(row.right, right - 14f, y + 17f, dim)
                dim.textAlign = Paint.Align.LEFT
            }
            y += rowH
        }
        white.color = Color.WHITE
        dim.color = 0xFFB9BEC4.toInt()
        gold.textAlign = Paint.Align.CENTER
        canvas.drawText(fit(menu.hint(), gold, 196f), cx, bottom - 10f, gold)
    }

    /** New message: who it is from and the first two lines. */
    fun drawMessage(canvas: Canvas, width: Int, height: Int, message: DashMessage) {
        val cx = width / 2f
        val left = cx - 100f; val right = cx + 100f
        val top = height / 2f + 8f
        val lines = wrap(message.text, dim, 184f, 2)
        val bottom = top + 26f + lines.size * 14f + 6f
        canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, bg)
        canvas.drawText(fit(message.sender, white, 184f), left + 10f, top + 19f, white)
        lines.forEachIndexed { i, line -> canvas.drawText(line, left + 10f, top + 35f + i * 14f, dim) }
    }

    /** Temperature and one word of weather, left of centre. */
    fun drawWeather(canvas: Canvas, width: Int, height: Int, weather: WeatherNow) {
        val cx = width / 2f - 108f
        val cy = height / 2f
        val wide = weather.label.isNotBlank()
        canvas.drawRoundRect(cx - 26f, cy - 19f, cx + 26f, cy + (if (wide) 19f else 8f), 10f, 10f, bg)
        white.textAlign = Paint.Align.CENTER
        canvas.drawText("${weather.tempC}°", cx, cy - 1f, white)
        white.textAlign = Paint.Align.LEFT
        if (wide) {
            val p = if (weather.wet) gold else dim
            p.textAlign = Paint.Align.CENTER
            canvas.drawText(weather.label, cx, cy + 13f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    /** Speed-limit sign with Google's limit, shown only while the rider is over it. */
    fun drawSpeedLimit(canvas: Canvas, width: Int, height: Int, limitKmh: Int) {
        val cx = width / 2f + 108f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, 21f, signRing)
        canvas.drawCircle(cx, cy, 16f, signFace)
        canvas.drawText(limitKmh.toString(), cx, cy + 5.5f, signText)
    }

    /** Small reminder while a call is connected. */
    fun drawActiveCall(canvas: Canvas, width: Int, height: Int, caller: String) {
        val cx = width / 2f
        val text = fit("On call: $caller", dim, 150f)
        val top = height - 62f
        canvas.drawRoundRect(cx - 90f, top, cx + 90f, top + 34f, 10f, 10f, bg)
        dim.textAlign = Paint.Align.CENTER; gold.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, top + 14f, dim)
        canvas.drawText("DOWN to end", cx, top + 28f, gold)
        dim.textAlign = Paint.Align.LEFT
    }

    private fun wrap(text: String, paint: TextPaint, width: Float, maxLines: Int): List<String> {
        val out = ArrayList<String>()
        var rest = text.replace('\n', ' ').trim()
        while (rest.isNotEmpty() && out.size < maxLines) {
            if (out.size == maxLines - 1) { out += fit(rest, paint, width); break }
            var n = paint.breakText(rest, true, width, null)
            if (n <= 0) break
            if (n < rest.length) rest.lastIndexOf(' ', n).takeIf { it > 0 }?.let { n = it }
            out += rest.substring(0, n).trim()
            rest = rest.substring(n).trim()
        }
        return out
    }

    companion object {
        /** Degrees to rotate an up-arrow for a Navigation SDK maneuver. */
        fun turnAngle(maneuver: Int): Float = when (maneuver) {
            Maneuver.TURN_LEFT, Maneuver.ON_RAMP_LEFT, Maneuver.OFF_RAMP_LEFT,
            Maneuver.ROUNDABOUT_LEFT_CLOCKWISE, Maneuver.ROUNDABOUT_LEFT_COUNTERCLOCKWISE,
            Maneuver.DESTINATION_LEFT -> -90f
            Maneuver.TURN_RIGHT, Maneuver.ON_RAMP_RIGHT, Maneuver.OFF_RAMP_RIGHT,
            Maneuver.ROUNDABOUT_RIGHT_CLOCKWISE, Maneuver.ROUNDABOUT_RIGHT_COUNTERCLOCKWISE,
            Maneuver.DESTINATION_RIGHT -> 90f
            Maneuver.TURN_SLIGHT_LEFT, Maneuver.TURN_KEEP_LEFT, Maneuver.FORK_LEFT, Maneuver.MERGE_LEFT,
            Maneuver.ON_RAMP_SLIGHT_LEFT, Maneuver.ON_RAMP_KEEP_LEFT,
            Maneuver.OFF_RAMP_SLIGHT_LEFT, Maneuver.OFF_RAMP_KEEP_LEFT -> -45f
            Maneuver.TURN_SLIGHT_RIGHT, Maneuver.TURN_KEEP_RIGHT, Maneuver.FORK_RIGHT, Maneuver.MERGE_RIGHT,
            Maneuver.ON_RAMP_SLIGHT_RIGHT, Maneuver.ON_RAMP_KEEP_RIGHT,
            Maneuver.OFF_RAMP_SLIGHT_RIGHT, Maneuver.OFF_RAMP_KEEP_RIGHT -> 45f
            Maneuver.TURN_SHARP_LEFT, Maneuver.ON_RAMP_SHARP_LEFT, Maneuver.OFF_RAMP_SHARP_LEFT -> -135f
            Maneuver.TURN_SHARP_RIGHT, Maneuver.ON_RAMP_SHARP_RIGHT, Maneuver.OFF_RAMP_SHARP_RIGHT -> 135f
            Maneuver.TURN_U_TURN_CLOCKWISE, Maneuver.TURN_U_TURN_COUNTERCLOCKWISE,
            Maneuver.ON_RAMP_U_TURN_CLOCKWISE, Maneuver.ON_RAMP_U_TURN_COUNTERCLOCKWISE,
            Maneuver.OFF_RAMP_U_TURN_CLOCKWISE, Maneuver.OFF_RAMP_U_TURN_COUNTERCLOCKWISE,
            Maneuver.ROUNDABOUT_U_TURN_CLOCKWISE, Maneuver.ROUNDABOUT_U_TURN_COUNTERCLOCKWISE -> 180f
            else -> 0f
        }

        /** Google only reports "x% over the limit"; work the limit back out, to the nearest 5. */
        fun limitFromSpeeding(speedMps: Float, percentAbove: Float): Int? {
            if (percentAbove <= 0f || speedMps <= 1f) return null
            val limit = speedMps * 3.6f / (1f + percentAbove / 100f)
            val rounded = Math.round(limit / 5f) * 5
            return rounded.takeIf { it in 10..150 }
        }
    }
}
