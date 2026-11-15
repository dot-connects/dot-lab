package kr.dot.catchpet.seg

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The high-res person model for cut-outs (MediaPipe selfie_multiclass_256x256, 16.4 MB) is not in the APK
 * (operator 11-15: keep the app small): it is fetched from Google's model storage the first time a person is grabbed
 * and used from the next grab on. Until then the 0.25 MB selfie segmenter in the APK cuts people.
 */
object HiRes {
    private const val URL_ = "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/1/selfie_multiclass_256x256.tflite"
    private const val SIZE = 16_371_837L
    private val busy = AtomicBoolean(false)

    fun file(ctx: Context) = File(ctx.filesDir, "models/selfie_multiclass_256x256.tflite")

    /** Starts the download in the background unless the model is here or already coming. */
    fun fetch(ctx: Context) {
        val f = file(ctx)
        if (f.isFile || !busy.compareAndSet(false, true)) return
        thread(name = "hires-model", isDaemon = true) {
            try {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".part")
                val c = URL(URL_).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                try { c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } } finally { c.disconnect() }
                if (tmp.length() == SIZE && tmp.renameTo(f)) Log.i("HiRes", "model ready") else tmp.delete()
            } catch (e: Exception) {
                Log.w("HiRes", "download failed: ${e.message}")
            } finally { busy.set(false) }
        }
    }
}
