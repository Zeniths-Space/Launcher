package com.elara.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable

/**
 * A live clock icon's foreground: the [face], then each hand turned about the centre to the time. The
 * hands come pointing at 12 without shadows; each gets a soft shadow drawn here, offset down and right
 * after turning, so the light stays at the top left whatever the time. Offsets are in the icon's
 * 108-unit grid, as in the app's own icon.
 */
class ClockHandsDrawable(
    private val face: Drawable,
    private val hour: Drawable,
    private val minute: Drawable,
    private val hourDegrees: Float,
    private val minuteDegrees: Float,
) : Drawable() {
    private val shadow = PorterDuffColorFilter(0x262B1766, PorterDuff.Mode.SRC_IN)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val unit = b.width() / 108f
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        face.bounds = b
        face.draw(canvas)
        for ((hand, degrees) in listOf(hour to hourDegrees, minute to minuteDegrees)) {
            hand.bounds = b
            canvas.save()
            canvas.translate(1.2f * unit, 2f * unit)
            canvas.rotate(degrees, cx, cy)
            hand.colorFilter = shadow
            hand.draw(canvas)
            canvas.restore()
            canvas.save()
            canvas.rotate(degrees, cx, cy)
            hand.colorFilter = null
            hand.draw(canvas)
            canvas.restore()
        }
    }

    override fun setAlpha(alpha: Int) {
        face.alpha = alpha; hour.alpha = alpha; minute.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        face.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
