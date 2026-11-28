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
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
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
            val ll = e.latLong // GPS from the same open: no second read of the file for the position
            return mapOf("wall" to wall, "offset" to e.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                "lat" to ll?.get(0)?.toString(), "lng" to ll?.get(1)?.toString())
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
        // The clip is re-encoded anyway (cuts rarely land on a keyframe): encode at 720p on the short side, not at the
        // camera's 4K. Smaller files, far less heat while making and while the book loops the clip.
        io.execute {
            val h = runCatching { outHeight(uri) }.getOrNull()
            val edited = EditedMediaItem.Builder(item)
                .apply { if (h != null) setEffects(Effects(listOf(), listOf(Presentation.createForHeight(h)))) }
                .build()
            main.post { start(edited, out, result) }
        }
    }

    /** Output height that puts the displayed short side at 720, or null when the video is already that small. */
    private fun outHeight(uri: Uri): Int? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(activity, uri)
            fun key(k: Int) = r.extractMetadata(k)?.toIntOrNull() ?: 0
            var w = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            var h = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0) w = h.also { h = w }
            if (w <= 0 || h <= 0 || minOf(w, h) <= 720) return null
            return if (h <= w) 720 else (720L * h / w).toInt() / 2 * 2
        } finally {
            r.release()
        }
    }

    private fun start(item: EditedMediaItem, out: String, result: MethodChannel.Result) {
        val t = Transformer.Builder(activity)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = result.success(out)
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                    result.error("trim", exportException.message, null)
            })
            .build()
        t.start(item, out)
    }
}
