package kr.dot.catchpet.seg

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kr.dot.catchpet.game.Box
import kr.dot.catchpet.game.Mask
import kr.dot.catchpet.game.Tracker
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds real people / dogs / cats (research/seg-compare.md): DeepLab v3 confidence masks at 0.3 on the 90x160 grid;
 * when a kind's mask is empty, an ellipse inside the EfficientDet box stands in; a Tracker holds each kind's last
 * mask for 4 missed frames. Everything model-side runs on ONE thread (plan R16): the 300 ms loop and [onThread] jobs.
 */
class Segmenter(private val ctx: Context) {
    private val exec = Executors.newSingleThreadScheduledExecutor { Thread(it, "segmenter") }
    private var seg: ImageSegmenter? = null
    private var det: ObjectDetector? = null
    // cat and dog share one tracker: DeepLab flips one animal between the two channels (seg-compare.md),
    // two trackers would hold a stale twin of the same animal
    private val people = Tracker(4)
    private val animals = Tracker(4)

    /** The tracked masks of the latest loop frame (grid ScreenMap.GRID_W x GRID_H, covering the upright frame). */
    @Volatile var masks: List<Mask> = emptyList(); private set

    /** While the app is in the background the loop skips (review M3): no model runs on a stale frame. */
    @Volatile var paused = false

    /** Segments [frames]() every 300 ms on the model thread. */
    fun start(frames: () -> Bitmap?) {
        exec.scheduleWithFixedDelay({
            try {
                if (paused) return@scheduleWithFixedDelay
                val f = frames() ?: return@scheduleWithFixedDelay
                val raw = run(f)
                masks = listOfNotNull(
                    people.update(raw.firstOrNull { it.kind == Mask.Kind.PERSON }),
                    animals.update(raw.firstOrNull { it.kind == Mask.Kind.DOG || it.kind == Mask.Kind.CAT }),
                )
            } catch (e: Exception) {
                Log.w(TAG, "segment failed", e)
            }
        }, 0, 300, TimeUnit.MILLISECONDS)
    }

    /** Runs [block] on the model thread, after any loop pass in progress. */
    fun <T> onThread(block: () -> T): Future<T> = exec.submit(Callable(block))

    /**
     * Raw masks of one upright frame (no tracking): at most one PERSON and one DOG|CAT. Model thread only.
     * The frame is scaled to long side 320 first (DeepLab works at 257 px anyway; the grid is 90x160).
     */
    fun run(frame: Bitmap): List<Mask> {
        val s = 320f / max(frame.width, frame.height)
        val small = if (s < 1f) Bitmap.createScaledBitmap(frame, (frame.width * s).roundToInt(), (frame.height * s).roundToInt(), true) else frame
        val img = BitmapImageBuilder(small).build()
        val gw = ScreenMap.GRID_W; val gh = ScreenMap.GRID_H
        val person = BooleanArray(gw * gh); val animal = BooleanArray(gw * gh)
        var catSum = 0f; var dogSum = 0f
        val res = segmenter().segment(img)
        val conf = res.confidenceMasks().orElse(null)
        if (conf != null) {
            val w = conf[CAT].width; val h = conf[CAT].height
            val cat = floats(conf[CAT]); val dog = floats(conf[DOG]); val per = floats(conf[PERSON])
            for (gy in 0 until gh) for (gx in 0 until gw) {
                val i = ((gy + 0.5f) * h / gh).toInt() * w + ((gx + 0.5f) * w / gw).toInt()
                val k = gy * gw + gx
                if (per[i] > THRESHOLD) person[k] = true
                if (cat[i] + dog[i] > THRESHOLD) { animal[k] = true; catSum += cat[i]; dogSum += dog[i] }
            }
            conf.forEach { it.close() }
        }
        val out = mutableListOf<Mask>()
        val p = Mask(gw, gh, person, Mask.Kind.PERSON)
        val a = Mask(gw, gh, animal, if (catSum >= dogSum) Mask.Kind.CAT else Mask.Kind.DOG)
        if (p.area > 0) out += p
        if (a.area > 0) out += a
        if (p.area == 0 || a.area == 0) {
            // the detector fills in only the kinds DeepLab missed this frame
            val dets = detector().detect(img).detections()
            fun best(vararg names: String) = dets.filter { it.categories().firstOrNull()?.categoryName() in names }
                .maxByOrNull { it.categories()[0].score() }
            fun ellipse(d: com.google.mediapipe.tasks.components.containers.Detection, kind: Mask.Kind): Mask {
                val r = d.boundingBox()
                val b = Box(
                    (r.left / small.width * gw).toInt(), (r.top / small.height * gh).toInt(),
                    (r.right / small.width * gw).toInt(), (r.bottom / small.height * gh).toInt(),
                )
                return Mask.ellipseIn(gw, gh, b, kind)
            }
            if (p.area == 0) best("person")?.let { out += ellipse(it, Mask.Kind.PERSON) }
            if (a.area == 0) best("cat", "dog")?.let {
                out += ellipse(it, if (it.categories()[0].categoryName() == "cat") Mask.Kind.CAT else Mask.Kind.DOG)
            }
        }
        img.close()
        if (small !== frame) small.recycle()
        return out.filter { it.area > 0 }
    }

    /** Stops the loop and frees the models (on their own thread). */
    fun close() {
        exec.execute { seg?.close(); det?.close(); seg = null; det = null }
        exec.shutdown()
    }

    private fun segmenter(): ImageSegmenter = seg ?: ImageSegmenter.createFromOptions(
        ctx,
        ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/deeplab_v3.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setOutputConfidenceMasks(true)
            .setOutputCategoryMask(false)
            .build(),
    ).also { seg = it }

    private fun detector(): ObjectDetector = det ?: ObjectDetector.createFromOptions(
        ctx,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/efficientdet_lite0.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.3f)
            .setMaxResults(5)
            .setCategoryAllowlist(listOf("person", "cat", "dog"))
            .build(),
    ).also { det = it }

    private fun floats(m: MPImage): FloatArray {
        val fb = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }

    companion object {
        private const val TAG = "Segmenter"
        const val CAT = 8
        const val DOG = 12
        const val PERSON = 15
        const val THRESHOLD = 0.3f
    }
}
