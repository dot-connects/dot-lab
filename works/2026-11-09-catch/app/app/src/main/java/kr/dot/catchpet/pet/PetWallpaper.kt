package kr.dot.catchpet.pet

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "아이콘 뒤": a live wallpaper with the pet walking behind the home-screen icons (PetBrain zone BEHIND).
 * Background = the photo the user picked (filesDir/wallpaper.jpg, plan R9) or a soft gradient. A tap on the
 * empty home screen near the pet makes it smile.
 */
class PetWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = PetEngine()

    private inner class PetEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val density get() = resources.displayMetrics.density
        private var look: PetLook? = null
        private var brain: PetBrain? = null
        private var photo: Bitmap? = null
        private var visible = false
        private var lastMs = 0L
        private var touchedAt = SystemClock.uptimeMillis()
        private val bg = Paint()
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        private val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                brain?.step(min(now - lastMs, 100L).toFloat(), (now - touchedAt).toFloat(), PetPrefs.night(), false)
                lastMs = now
                draw()
                if (visible) handler.postDelayed(this, 50)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            reload()
            val w = width / density; val h = height / density
            brain = PetBrain(PetBrain.Zone.BEHIND, w, h, PetPrefs.floorY(h), PetService.SIZE) { Math.random().toFloat() }
            bg.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFC9D8F5.toInt(), 0xFFE9CFE0.toInt(), 0xFFF6E2C8.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            draw()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(tick)
            if (v) {
                reload()
                lastMs = SystemClock.uptimeMillis()
                handler.post(tick)
            }
        }

        private var petKey: String? = null
        private var photoKey = 0L

        /** The pet or the photo may have changed in the app; decode again only then (this runs on every home visit). */
        private fun reload() {
            val key = PetPrefs.prefs(this@PetWallpaper).getString("pet", null)?.let { id ->
                id + (kr.dot.catchpet.dex.Dex.get(this@PetWallpaper, id)?.ai?.sheet ?: "")
            }
            if (key != petKey || look == null) { petKey = key; look = Frames.chosen(this@PetWallpaper)?.let { PetLook(it) } }
            val f = File(filesDir, "wallpaper.jpg")
            val mod = if (f.isFile) f.lastModified() else 0L
            if (mod != photoKey) { photoKey = mod; photo = if (mod == 0L) null else BitmapFactory.decodeFile(f.path) }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) {
                val b = brain
                if (b != null && near(b, x / density, y / density)) { touchedAt = SystemClock.uptimeMillis(); b.tap() }
            }
            return null
        }

        private fun near(b: PetBrain, x: Float, y: Float) =
            x in b.x - b.size..b.x + b.size && y in b.y - b.size * 1.6f..b.y + b.size * 0.6f

        private fun draw() {
            val holder = surfaceHolder
            val c = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val w = c.width; val h = c.height
                val p = photo
                if (p != null) c.drawBitmap(p, centerCrop(p, w, h), Rect(0, 0, w, h), bmpPaint)
                else c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
                val b = brain; val l = look
                if (b != null && l != null) {
                    val t = animClock()
                    l.draw(c, b, t, b.x * density, b.y * density, b.size * density, l.lift(b, t))
                }
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }

        private fun centerCrop(p: Bitmap, w: Int, h: Int): Rect {
            val s = max(w.toFloat() / p.width, h.toFloat() / p.height)
            val cw = (w / s).roundToInt(); val ch = (h / s).roundToInt()
            val l = (p.width - cw) / 2; val t = (p.height - ch) / 2
            return Rect(l, t, l + cw, t + ch)
        }
    }
}
