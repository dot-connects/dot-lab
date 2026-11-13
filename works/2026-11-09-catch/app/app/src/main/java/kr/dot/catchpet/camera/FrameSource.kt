package kr.dot.catchpet.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import kr.dot.catchpet.seg.ScreenMap
import java.util.concurrent.Executors

/** A camera: the live preview, the latest upright frame (matching what the preview shows) and the map onto the view. */
interface FrameSource {
    /** Latest upright frame, cropped to the field of view the preview shows; null before the first frame. */
    fun latest(): Bitmap?

    /** View <-> frame <-> mask grid for a view of this size (px); null before the first frame. */
    fun map(viewW: Float, viewH: Float): ScreenMap?

    @Composable fun Preview(modifier: Modifier)

    fun close()
}

/**
 * CameraX back camera. Preview + ImageAnalysis (RGBA_8888, ~1280 long side) bound in one UseCaseGroup with the
 * PreviewView's ViewPort, so the analysis crop rect is exactly the field of view the preview shows (FILL_CENTER).
 */
class CameraXSource(private val ctx: Context, private val owner: LifecycleOwner) : FrameSource {
    private val exec = Executors.newSingleThreadExecutor { Thread(it, "camera-frames") }
    @Volatile private var frame: Bitmap? = null
    @Volatile private var buf = Triple(0, 0, 0)  // crop w, crop h, rotation
    @Volatile private var lastMs = 0L
    private var provider: ProcessCameraProvider? = null
    private var closed = false

    override fun latest(): Bitmap? = frame

    override fun map(viewW: Float, viewH: Float): ScreenMap? {
        val (w, h, r) = buf
        return if (w == 0 || viewW <= 0f) null else ScreenMap(w, h, r, viewW, viewH)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        AndroidView(modifier = modifier, factory = { c ->
            PreviewView(c).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // bind once the view has a size: its ViewPort needs one
                addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                        if (r - l <= 0) return
                        removeOnLayoutChangeListener(this)
                        bind(this@apply)
                    }
                })
            }
        })
    }

    private fun bind(view: PreviewView) {
        val f = ProcessCameraProvider.getInstance(ctx)
        f.addListener({
            if (closed) return@addListener
            val p = f.get().also { provider = it }
            val port = view.viewPort ?: return@addListener
            val sel = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
            val preview = Preview.Builder().setResolutionSelector(sel).build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(sel)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(exec, ::onFrame)
            val group = UseCaseGroup.Builder().setViewPort(port).addUseCase(preview).addUseCase(analysis).build()
            try {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
            } catch (e: Exception) {
                Log.e(TAG, "bind failed", e)
            }
        }, ctx.mainExecutor)
    }

    private fun onFrame(img: ImageProxy) {
        img.use {
            // converts at most every 250 ms (segment loop is 300 ms): each conversion is two 720p copies (battery review 11-13)
            val now = System.currentTimeMillis()
            if (now - lastMs < 250) return
            lastMs = now
            val rot = it.imageInfo.rotationDegrees
            val crop = it.cropRect
            val full = it.toBitmap()
            // toBitmap may or may not apply the crop rect: crop only when it did not
            val r = if (full.width == crop.width() && full.height == crop.height()) android.graphics.Rect(0, 0, full.width, full.height) else crop
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            frame = Bitmap.createBitmap(full, r.left, r.top, r.width(), r.height(), m, true)
            buf = Triple(r.width(), r.height(), rot)
            if (frame !== full) full.recycle()
        }
    }

    override fun close() {
        closed = true
        provider?.unbindAll()
        exec.shutdown()
    }

    private companion object { const val TAG = "CameraXSource" }
}
