package kr.dot.tripbook

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * kr.dot.tripbook/media: what photo_manager does not give.
 *  exif(uri)                         -> {wall: "yyyy-MM-ddTHH:mm:ss"?, offset: "+09:00"?}  (camera clock, original file)
 *  videoFrames(uri)                  -> [jpeg bytes x4] at 15/40/65/90 % of the length, long side 512
 *  trim(uri, startMs, endMs, out)    -> out path (720p clip for the book's video slot, sound kept)
 *  keepScreenOn(on)                  -> keeps the screen awake while a book is being made
 */
class MediaChannel(private val activity: Activity) : MethodChannel.MethodCallHandler {
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "keepScreenOn" -> {
                val on = call.argument<Boolean>("on") == true
                if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                result.success(null)
            }
            "exif" -> background(result) { exif(Uri.parse(call.argument<String>("uri")!!)) }
            "videoFrames" -> background(result) { frames(Uri.parse(call.argument<String>("uri")!!)) }
            "trim" -> trim(
                Uri.parse(call.argument<String>("uri")!!), call.argument<Int>("startMs")!!.toLong(),
                call.argument<Int>("endMs")!!.toLong(), call.argument<String>("out")!!, result,
            )
            else -> result.notImplemented()
        }
    }

    private fun background(result: MethodChannel.Result, work: () -> Any?) = io.execute {
        val r = runCatching(work)
        main.post { r.fold({ result.success(it) }, { result.error("media", it.message, null) }) }
    }

    private fun exif(uri: Uri): Map<String, String?> {
        // the original (unredacted) file: without setRequireOriginal the location tags come back stripped
        val original = runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        activity.contentResolver.openInputStream(original).use { s ->
            val e = ExifInterface(s!!)
            val raw = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
            val wall = raw?.let { runCatching {
                val d = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)!!
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(d)
            }.getOrNull() }
            return mapOf("wall" to wall, "offset" to e.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        }
    }

    private fun frames(uri: Uri): List<ByteArray> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(activity, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            return listOf(0.15, 0.40, 0.65, 0.90).mapNotNull { f ->
                val us = (durMs * f * 1000).toLong()
                val bmp = r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512) ?: return@mapNotNull null
                ByteArrayOutputStream().use { o -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, o); bmp.recycle(); o.toByteArray() }
            }
        } finally {
            r.release()
        }
    }

    /** Media3 Transformer must be driven from a Looper thread: the main one. */
    private fun trim(uri: Uri, startMs: Long, endMs: Long, out: String, result: MethodChannel.Result) {
        val item = MediaItem.Builder().setUri(uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
            .build()
        File(out).parentFile?.mkdirs()
        val t = Transformer.Builder(activity)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = result.success(out)
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                    result.error("trim", exportException.message, null)
            })
            .build()
        main.post { t.start(item, out) }
    }
}
