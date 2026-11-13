package kr.dot.catchpet.seg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds real people / dogs / cats (research/seg-compare.md): DeepLab v3 confidence masks at 0.3 on the 90x160 grid;
 * when a kind's mask is empty, an ellipse inside the EfficientDet box stands in; a Tracker holds each kind's last
 * mask for 4 missed frames. A person counts only with a face inside it (BlazeFace short range, operator 11-13: legs
 * alone were caught as a person; faces farther than ~2 m are missed). Everything model-side runs on ONE thread
 * (plan R16): the 300 ms loop and [onThread] jobs. The loop runs the box detector only every other pass (battery).
 * Small pets no detector class covers (hamster, rabbit, turtle, fish, guinea pig, lizard; research/small-animals.md):
 * every 4th pass with no other animal, the detector's boxes of any class are cropped and classified (EfficientNet-Lite0
 * int8, group score >= 0.15), and magic touch at the best box's centre, clipped to the box, gives the mask.
 */
class Segmenter(private val ctx: Context) {
    private val exec = Executors.newSingleThreadScheduledExecutor { Thread(it, "segmenter") }
    private var seg: ImageSegmenter? = null
    private var det: ObjectDetector? = null
    private var face: FaceDetector? = null
    private var cls: ImageClassifier? = null
    private var touch: InteractiveSegmenter? = null
    private var pass = 0
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
                val n = pass++
                val raw = run(f, full = n % 2 == 0, pets = n % 4 == 0)
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
     * Raw masks of one upright frame (no tracking): at most one PERSON and one animal (DOG|CAT|ANIMAL). Model thread only.
     * The frame is scaled to long side 320 first (DeepLab works at 257 px anyway; the grid is 90x160).
     * full = false skips the box detector fallback (the loop's odd passes; the Tracker bridges them);
     * pets = true also looks for small pets when no other animal was found.
     */
    fun run(frame: Bitmap, full: Boolean = true, pets: Boolean = full): List<Mask> {
        val s = 320f / max(frame.width, frame.height)
        val small = if (s < 1f) Bitmap.createScaledBitmap(frame, (frame.width * s).roundToInt(), (frame.height * s).roundToInt(), true) else frame
        val img = BitmapImageBuilder(small).build()
        val gw = ScreenMap.GRID_W; val gh = ScreenMap.GRID_H
        val person = BooleanArray(gw * gh); val animal = BooleanArray(gw * gh)
        val sums = FloatArray(ANIMALS.size)
        val res = segmenter().segment(img)
        val conf = res.confidenceMasks().orElse(null)
        if (conf != null) {
            val w = conf[PERSON].width; val h = conf[PERSON].height
            val per = floats(conf[PERSON])
            val ch = ANIMALS.map { floats(conf[it.first]) }
            for (gy in 0 until gh) for (gx in 0 until gw) {
                val i = ((gy + 0.5f) * h / gh).toInt() * w + ((gx + 0.5f) * w / gw).toInt()
                val k = gy * gw + gx
                if (per[i] > THRESHOLD) person[k] = true
                var sum = 0f
                for (c in ch.indices) sum += ch[c][i]
                if (sum > THRESHOLD) { animal[k] = true; for (c in ch.indices) sums[c] += ch[c][i] }
            }
            conf.forEach { it.close() }
        }
        val out = mutableListOf<Mask>()
        val p = Mask(gw, gh, person, Mask.Kind.PERSON)
        val a = animalMask(gw, gh, animal, ANIMALS[sums.indices.maxBy { sums[it] }].second)
        if (p.area > 0) out += p
        if (a.area > 0) out += a
        var detsCache: List<Detection>? = null
        fun dets() = detsCache ?: detector().detect(img).detections().also { detsCache = it }
        if (full && (p.area == 0 || a.area == 0)) {
            // the detector fills in only the kinds DeepLab missed this frame
            fun best(vararg names: String) = dets().filter { d ->
                d.categories().firstOrNull()?.let { it.categoryName() in names && it.score() >= DET_SCORE } == true
            }.maxByOrNull { it.categories()[0].score() }
            fun box(d: com.google.mediapipe.tasks.components.containers.Detection): Box {
                val r = d.boundingBox()
                return Box(
                    (r.left / small.width * gw).toInt(), (r.top / small.height * gh).toInt(),
                    (r.right / small.width * gw).toInt(), (r.bottom / small.height * gh).toInt(),
                )
            }
            if (p.area == 0) best("person")?.let { out += Mask.ellipseIn(gw, gh, box(it), Mask.Kind.PERSON) }
            if (a.area == 0) best(*DET_ANIMALS)?.let {
                val e = Mask.ellipseIn(gw, gh, box(it), Mask.Kind.ANIMAL)
                out += animalMask(gw, gh, e.bits, it.categories()[0].categoryName())
            }
        }
        if (pets && out.none { it.kind != Mask.Kind.PERSON }) smallPet(small, img, dets())?.let { out += it }
        out.firstOrNull { it.kind == Mask.Kind.PERSON }?.let { pm ->
            val b = pm.box
            val faces = faceDetector().detect(img).detections()
            val hasFace = b != null && faces.any { d ->
                val r = d.boundingBox()
                val fx = (r.centerX() / small.width * gw).toInt(); val fy = (r.centerY() / small.height * gh).toInt()
                fx in b.x0 - 3..b.x1 + 3 && fy in b.y0 - 3..b.y1 + 3
            }
            if (!hasFace) out.remove(pm)
        }
        img.close()
        if (small !== frame) small.recycle()
        return out.filter { it.area > 0 }
    }

    /** A small pet in one of the detector's boxes (any class), or null. */
    private fun smallPet(bmp: Bitmap, img: MPImage, dets: List<Detection>): Mask? {
        val gw = ScreenMap.GRID_W; val gh = ScreenMap.GRID_H
        val cands = dets.filter { d ->
            val c = d.categories()[0]
            !(c.score() >= DET_SCORE && c.categoryName() in listOf("person", "cat", "dog"))
        }.sortedByDescending { it.categories()[0].score() }.take(2)
        var best: Triple<RectF, String, Float>? = null
        for (d in cands) {
            val r = d.boundingBox()
            val l = max(0f, r.left - r.width() * 0.1f).toInt(); val t = max(0f, r.top - r.height() * 0.1f).toInt()
            val rr = min(bmp.width.toFloat(), r.right + r.width() * 0.1f).toInt(); val bb = min(bmp.height.toFloat(), r.bottom + r.height() * 0.1f).toInt()
            if (rr - l < 12 || bb - t < 12) continue
            val crop = Bitmap.createBitmap(bmp, l, t, rr - l, bb - t)
            val ci = BitmapImageBuilder(crop).build()
            val res = classifier().classify(ci)
            ci.close(); crop.recycle()
            val scores = HashMap<String, Float>()
            for (c in res.classificationResult().classifications().firstOrNull()?.categories().orEmpty()) {
                SMALL_LABELS[c.categoryName()]?.let { scores[it] = (scores[it] ?: 0f) + c.score() }
            }
            val top = scores.maxByOrNull { it.value } ?: continue
            if (top.value >= SMALL_SCORE && (best == null || top.value > best.third)) best = Triple(r, top.key, top.value)
        }
        val (r, species, _) = best ?: return null
        val box = Box(
            max(0, ((r.left - r.width() * 0.1f) / bmp.width * gw).toInt()), max(0, ((r.top - r.height() * 0.1f) / bmp.height * gh).toInt()),
            min(gw - 1, ((r.right + r.width() * 0.1f) / bmp.width * gw).toInt()), min(gh - 1, ((r.bottom + r.height() * 0.1f) / bmp.height * gh).toInt()),
        )
        val roi = InteractiveSegmenter.RegionOfInterest.create(NormalizedKeypoint.create(r.centerX() / bmp.width, r.centerY() / bmp.height))
        val bits = BooleanArray(gw * gh)
        touch().segment(img, roi).categoryMask().orElse(null)?.let { m ->
            val w = m.width; val h = m.height
            val buf = ByteBufferExtractor.extract(m)
            // magic touch: category 0 = the touched thing; keep it inside the (padded) box so it cannot leak into weeds
            for (gy in box.y0..box.y1) for (gx in box.x0..box.x1) {
                val i = ((gy + 0.5f) * h / gh).toInt() * w + ((gx + 0.5f) * w / gw).toInt()
                if (buf.get(i).toInt() == 0) bits[gy * gw + gx] = true
            }
            m.close()
        }
        val area = bits.count { it }
        // an empty or box-filling touch is no shape at all: the ellipse in the box stands in
        val shaped = if (area < 6 || area > box.w * box.h * 0.95f) Mask.ellipseIn(gw, gh, box, Mask.Kind.ANIMAL).bits else bits
        return Mask(gw, gh, shaped, Mask.Kind.ANIMAL, species = species)
    }

    /** Stops the loop and frees the models (on their own thread). */
    fun close() {
        exec.execute {
            seg?.close(); det?.close(); face?.close(); cls?.close(); touch?.close()
            seg = null; det = null; face = null; cls = null; touch = null
        }
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
            .setScoreThreshold(0.1f)   // weak boxes of any class are small-pet candidates; kinds need DET_SCORE
            .setMaxResults(5)
            .build(),
    ).also { det = it }

    private fun classifier(): ImageClassifier = cls ?: ImageClassifier.createFromOptions(
        ctx,
        ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/efficientnet_lite0_int8.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(10)
            .build(),
    ).also { cls = it }

    private fun touch(): InteractiveSegmenter = touch ?: InteractiveSegmenter.createFromOptions(
        ctx,
        InteractiveSegmenter.InteractiveSegmenterOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/magic_touch.tflite").build())
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build(),
    ).also { touch = it }

    private fun faceDetector(): FaceDetector = face ?: FaceDetector.createFromOptions(
        ctx,
        FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/blaze_face_short_range.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.5f)
            .build(),
    ).also { face = it }

    private fun floats(m: MPImage): FloatArray {
        val fb = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }

    companion object {
        private const val TAG = "Segmenter"
        const val PERSON = 15
        const val THRESHOLD = 0.3f
        const val DET_SCORE = 0.3f     // a detector box names a kind only from this score
        const val SMALL_SCORE = 0.15f  // int8 classifier group score (negatives stayed <= 0.07, research/small-animals.md)
        /** ImageNet labels -> the small pet species word (research/small-animals.md). */
        val SMALL_LABELS: Map<String, String> = buildMap {
            put("hamster", "hamster"); put("guinea pig", "guinea pig")
            for (l in listOf("wood rabbit", "hare", "Angora")) put(l, "rabbit")
            for (l in listOf("box turtle", "mud turtle", "terrapin", "loggerhead", "leatherback turtle")) put(l, "turtle")
            for (l in listOf("goldfish", "tench", "anemone fish", "rock beauty", "puffer", "lionfish", "eel", "coho", "sturgeon", "gar", "barracouta")) put(l, "fish")
            for (l in listOf("common iguana", "American chameleon", "whiptail", "agama", "frilled lizard", "alligator lizard",
                "Gila monster", "green lizard", "African chameleon", "Komodo dragon", "banded gecko")) put(l, "lizard")
        }
        /** DeepLab v3 (PASCAL VOC) animal channels and their species words. */
        val ANIMALS = listOf(8 to "cat", 12 to "dog", 3 to "bird", 10 to "cow", 13 to "horse", 17 to "sheep")
        /** EfficientDet-Lite0 (COCO) animal labels; "bear" is the animal, "teddy bear" is not on the list. */
        val DET_ANIMALS = arrayOf("cat", "dog", "bird", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe")

        fun animalMask(gw: Int, gh: Int, bits: BooleanArray, species: String): Mask = when (species) {
            "cat" -> Mask(gw, gh, bits, Mask.Kind.CAT)
            "dog" -> Mask(gw, gh, bits, Mask.Kind.DOG)
            else -> Mask(gw, gh, bits, Mask.Kind.ANIMAL, species = species)
        }
    }
}
