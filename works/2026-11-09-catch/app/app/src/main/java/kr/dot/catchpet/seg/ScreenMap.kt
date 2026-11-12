package kr.dot.catchpet.seg

import kotlin.math.floor
import kotlin.math.max

/**
 * Pure coordinate map between the view (px), the upright camera image (px) and the mask grid.
 * The camera buffer (bufW x bufH, sensor orientation) is turned upright by `rotation` (0/90/180/270), shown
 * FILL_CENTER in a viewW x viewH view, and the mask grid (gridW x gridH) covers the whole upright image.
 */
class ScreenMap(
    bufW: Int, bufH: Int, rotation: Int,
    val viewW: Float, val viewH: Float,
    val gridW: Int = GRID_W, val gridH: Int = GRID_H,
) {
    val imgW: Int = if (rotation % 180 == 0) bufW else bufH
    val imgH: Int = if (rotation % 180 == 0) bufH else bufW
    /** view px per image px, and where the image's top-left lands in the view (negative = cropped). */
    val scale: Float = max(viewW / imgW, viewH / imgH)
    val left: Float = (viewW - imgW * scale) / 2f
    val top: Float = (viewH - imgH * scale) / 2f

    /** Grid cell under view point (vx, vy); may fall outside the grid. */
    fun toGrid(vx: Float, vy: Float): Pair<Int, Int> =
        floor((vx - left) / scale * gridW / imgW).toInt() to floor((vy - top) / scale * gridH / imgH).toInt()

    /** View point of grid coordinate (gx, gy); pass cell + 0.5 for a cell's centre. */
    fun gridToViewX(gx: Float): Float = left + gx * imgW / gridW * scale
    fun gridToViewY(gy: Float): Float = top + gy * imgH / gridH * scale

    /** View point of an upright-image pixel. */
    fun imageToViewX(ix: Float): Float = left + ix * scale
    fun imageToViewY(iy: Float): Float = top + iy * scale

    companion object {
        const val GRID_W = 90
        const val GRID_H = 160
    }
}
