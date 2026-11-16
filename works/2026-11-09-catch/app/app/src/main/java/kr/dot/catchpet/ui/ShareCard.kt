package kr.dot.catchpet.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.pet.Frames
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** What a 자랑하기 card shows: kept from the moment of the catch, or read from a dex entry. */
class Brag(val name: String, val kind: String, val pic: Bitmap?, val critterId: String?, val cap: Int, val at: Long = System.currentTimeMillis()) {
    val person get() = Dex.hasPerson(kind)

    /** A critter's catch carries no picture: read its front cell (off the main thread). */
    fun ready(ctx: Context): Brag = if (pic == null && critterId != null) Brag(name, kind, Frames.asset(ctx, critterId, "front"), critterId, cap, at) else this

    companion object {
        /** A dex entry: a critter's front picture, a real thing's cut-out photo (its first pose). */
        fun of(ctx: Context, e: Dex.Entry): Brag {
            val pic = if (e.critterId != null) Frames.asset(ctx, e.critterId, "front")
            else e.poses.firstOrNull()?.let { BitmapFactory.decodeFile(Dex.file(ctx, e.id, "$it.png").path) }
            return Brag(e.name, e.kind, pic, e.critterId, e.cap ?: CapColors.of(e.no), e.caughtAt)
        }
    }
}

/** Old entries have no stored capsule colour: one from the same set, by number. */
object CapColors {
    fun random() = CAP_COLORS.random()
    fun of(no: Int) = CAP_COLORS[Math.floorMod(no, CAP_COLORS.size)]

    /** Two-tone capsule colours, one picked at random per catch (operator 11-15 "다양하게"). */
    val CAP_COLORS = intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFB938.toInt(), 0xFF4DA8FF.toInt(), 0xFF4CCB8C.toInt(), 0xFFA98BFF.toInt(),
        0xFFFF86C2.toInt(), 0xFFFF8F3D.toInt(), 0xFF2EC2CC.toInt(), 0xFF7E9CFF.toInt(), 0xFFE5484D.toInt())
}

/**
 * The 4:5 자랑하기 card (spec-extras 3, mockup features 3A): sky over grass, the title, "date · kind", the capsule
 * holding the catch, and a paper footer with the app's name. No link before the Play listing (operator 11-16).
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350
    private val INK = 0xFF24331F.toInt()

    fun render(b: Brag): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val horizon = H * 0.6f
        p.shader = LinearGradient(0f, 0f, 0f, horizon, 0xFF9FD8FF.toInt(), 0xFFBFE6FF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), horizon, p)
        p.shader = LinearGradient(0f, horizon, 0f, H.toFloat(), 0xFFD9F2C9.toInt(), 0xFFC4E8AE.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, W.toFloat(), H.toFloat(), p)
        p.shader = null

        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val title = if (b.critterId != null) "새 친구! ${b.name}" else Dex.withObject("${Dex.kindName(b.kind)} ${b.name}") + " 뽑았다!"
        t.textSize = 82f
        while (t.measureText(title) > W - 120f && t.textSize > 40f) t.textSize -= 4f
        // white lift under the title, like the app's headings
        t.color = Color.WHITE; c.drawText(title, W / 2f, 190f, t)
        t.color = INK; c.drawText(title, W / 2f, 182f, t)
        t.typeface = Typeface.DEFAULT; t.textSize = 40f; t.color = 0xFF5D6B57.toInt()
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(b.at))
        c.drawText("$date · ${if (b.critterId != null) "털뭉치" else Dex.kindName(b.kind)}", W / 2f, 252f, t)

        // a soft shadow on the grass, then the capsule with the catch inside
        val r = 330f; val cx = W / 2f; val cy = 690f
        p.color = 0x33243319; c.drawOval(cx - r * 0.8f, cy + r * 0.86f, cx + r * 0.8f, cy + r * 1.06f, p)
        p.color = Color.BLACK   // opaque again: the shadow left the paint at 20 % alpha (operator 11-16)
        CapsuleArt.draw(c, cx, cy, r, -8f, b.cap) { cv ->
            val pic = b.pic ?: return@draw
            val k = r * 1.6f / max(pic.width, pic.height)
            val bw = pic.width * k; val bh = pic.height * k
            cv.drawBitmap(pic, null, RectF(-bw / 2, -0.2f * r - bh / 2, bw / 2, -0.2f * r + bh / 2), p)
        }

        // the footer: paper, an ink line, the app's name
        val top = H - 170f
        p.color = 0xFFFFFDF6.toInt(); c.drawRect(0f, top, W.toFloat(), H.toFloat(), p)
        p.color = INK; c.drawRect(0f, top, W.toFloat(), top + 6f, p)
        t.textAlign = Paint.Align.LEFT; t.color = INK; t.typeface = Typeface.DEFAULT_BOLD; t.textSize = 54f
        c.drawText("리얼뽑기", 64f, top + 82f, t)
        t.typeface = Typeface.DEFAULT; t.textSize = 36f; t.color = 0xFF5D6B57.toInt()
        c.drawText("찍으면 캡슐에 담겨요", 64f, top + 132f, t)
        return bmp
    }

    private val work = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** A dex entry's card (its picture is read off the main thread). */
    fun share(ctx: Context, e: Dex.Entry) = shareLater(ctx) { Brag.of(ctx, e) }

    fun share(ctx: Context, b: Brag) = shareLater(ctx) { b }

    /** Render and save off the main thread (a 1080 x 1350 PNG takes a few hundred ms, review 11-16), then the sheet. */
    private fun shareLater(ctx: Context, brag: () -> Brag) {
        val app = ctx.applicationContext
        work.execute {
            runCatching { make(app, brag()) }
                .onSuccess { i -> main.post { runCatching { ctx.startActivity(i) } } }
                .onFailure { android.util.Log.w("ShareCard", "share", it) }
        }
    }

    /** Render, save under cache/share/ (a new name each time), and the share-sheet intent. */
    private fun make(ctx: Context, b0: Brag): Intent {
        val b = b0.ready(ctx)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000L }?.forEach { it.delete() }
        val f = File(dir, "brag-${System.currentTimeMillis()}.png")
        val bmp = render(b)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "리얼뽑기에서 ${Dex.withObject(b.name)} 뽑았어!")
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "자랑하기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

}
