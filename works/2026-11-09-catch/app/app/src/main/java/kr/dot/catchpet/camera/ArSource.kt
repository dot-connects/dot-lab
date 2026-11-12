package kr.dot.catchpet.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import kr.dot.catchpet.seg.ScreenMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * ARCore camera (developer switch "ar", plan Task 5b). A GLSurfaceView draws the camera background; every
 * ~150 ms the CPU image is turned into an upright Bitmap of exactly the field of view the view shows (view corners
 * mapped with frame.transformCoordinates2d), so the segmenter's masks line up like with CameraX.
 * [onFrame] lets the AR critters work on the GL thread, where ARCore objects must be touched.
 * Any failure calls [onFail] once (main thread) and the catch screen falls back to CameraX.
 */
class ArSource private constructor(
    private val session: Session,
    private val owner: LifecycleOwner,
    private val onFail: () -> Unit,
) : FrameSource, GLSurfaceView.Renderer {
    @Volatile private var frame: Bitmap? = null
    @Volatile private var out = 0 to 0
    @Volatile var onFrame: ((Session, Frame, Int, Int) -> Unit)? = null
    @Volatile private var closed = false
    @Volatile private var failed = false
    private var view: GLSurfaceView? = null
    private val exec = Executors.newSingleThreadExecutor { Thread(it, "ar-frames") }
    @Volatile private var converting = false
    private var lastMs = 0L
    private var viewW = 0
    private var viewH = 0
    private val main = Handler(Looper.getMainLooper())

    private val life = LifecycleEventObserver { _, e ->
        when (e) {
            Lifecycle.Event.ON_RESUME -> try {
                session.resume(); view?.onResume()
            } catch (t: Throwable) { fail("resume", t) }
            Lifecycle.Event.ON_PAUSE -> { view?.onPause(); runCatching { session.pause() } }
            else -> {}
        }
    }

    init { owner.lifecycle.addObserver(life) }

    override fun latest(): Bitmap? = frame

    override fun map(viewW: Float, viewH: Float): ScreenMap? {
        val (w, h) = out
        return if (w == 0 || viewW <= 0f) null else ScreenMap(w, h, 0, viewW, viewH)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        AndroidView(modifier = modifier, factory = { c ->
            GLSurfaceView(c).apply {
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(this@ArSource)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                view = this
            }
        })
    }

    override fun close() {
        if (closed) return
        closed = true
        owner.lifecycle.removeObserver(life)
        view?.onPause()   // waits for the GL thread to stop drawing
        runCatching { session.pause() }
        runCatching { session.close() }
        exec.shutdown()
    }

    private fun fail(where: String, t: Throwable) {
        Log.w(TAG, "AR failed in $where, back to CameraX", t)
        if (failed) return
        failed = true
        main.post(onFail)
    }

    // ---------- GL thread ----------
    private var tex = 0
    private var prog = 0
    private val quad = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val uv = FloatArray(8)
    private val uvBuf = floats(*uv)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            tex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
            for ((k, v) in listOf(GLES20.GL_TEXTURE_WRAP_S to GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_TEXTURE_WRAP_T to GLES20.GL_CLAMP_TO_EDGE,
                GLES20.GL_TEXTURE_MIN_FILTER to GLES20.GL_LINEAR, GLES20.GL_TEXTURE_MAG_FILTER to GLES20.GL_LINEAR))
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, k, v)
            prog = program(VERT, FRAG)
            session.setCameraTextureName(tex)
        } catch (t: Throwable) { fail("surface", t) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewW = width; viewH = height
        // the catch screen is portrait-locked
        runCatching { session.setDisplayGeometry(Surface.ROTATION_0, width, height) }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (closed || failed || tex == 0) return
        try {
            val f = session.update()
            if (f.hasDisplayGeometryChanged() || uv.all { it == 0f }) {
                f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, QUAD, Coordinates2d.TEXTURE_NORMALIZED, uv)
                uvBuf.position(0); uvBuf.put(uv); uvBuf.position(0)
            }
            if (f.timestamp == 0L) return   // no camera image yet
            drawBackground()
            grabCpuImage(f)
            onFrame?.invoke(session, f, viewW, viewH)
        } catch (e: CameraNotAvailableException) {
            fail("update", e)
        } catch (t: Throwable) {
            Log.w(TAG, "frame skipped", t)   // e.g. SessionPausedException while pausing
        }
    }

    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        val pos = GLES20.glGetAttribLocation(prog, "a_Pos")
        val tc = GLES20.glGetAttribLocation(prog, "a_Uv")
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(tc, 2, GLES20.GL_FLOAT, false, 0, uvBuf)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glEnableVertexAttribArray(tc)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(tc)
        GLES20.glDepthMask(true)
    }

    /** Copy the YUV planes here (the image is only valid now); convert and crop on [exec]. */
    private fun grabCpuImage(f: Frame) {
        val now = System.currentTimeMillis()
        // ponytail: one upright frame per 150 ms (segment loop is 300 ms); lower if GRIP frames look stale
        if (converting || now - lastMs < 150 || viewW == 0) return
        val img: Image = try { f.acquireCameraImage() } catch (t: Throwable) { return }   // NotYetAvailable early on
        lastMs = now
        val yuv = try { Yuv.copy(img) } finally { img.close() }
        // where the view's corners (TL, TR, BR, BL) fall in the CPU image
        val corners = floatArrayOf(0f, 0f, viewW.toFloat(), 0f, viewW.toFloat(), viewH.toFloat(), 0f, viewH.toFloat())
        val src = FloatArray(8)
        f.transformCoordinates2d(Coordinates2d.VIEW, corners, Coordinates2d.IMAGE_PIXELS, src)
        converting = true
        exec.execute {
            try {
                val full = yuv.toBitmap()
                val w = hypot(src[2] - src[0], src[3] - src[1]).roundToInt().coerceAtLeast(1)
                val h = hypot(src[6] - src[0], src[7] - src[1]).roundToInt().coerceAtLeast(1)
                val up = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val m = Matrix()
                m.setPolyToPoly(src, 0, floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()), 0, 4)
                Canvas(up).drawBitmap(full, m, Paint(Paint.FILTER_BITMAP_FLAG))
                full.recycle()
                frame = up
                out = w to h
            } catch (t: Throwable) {
                Log.w(TAG, "cpu image failed", t)
            } finally { converting = false }
        }
    }

    /** A YUV_420_888 image copied out of ARCore. */
    private class Yuv(val w: Int, val h: Int, val y: ByteArray, val u: ByteArray, val v: ByteArray,
                      val yRow: Int, val yPix: Int, val uvRow: Int, val uvPix: Int) {
        fun toBitmap(): Bitmap {
            val px = IntArray(w * h)
            for (j in 0 until h) {
                val yo = j * yRow; val co = (j shr 1) * uvRow
                for (i in 0 until w) {
                    val yy = (y[yo + i * yPix].toInt() and 0xff)
                    val ci = co + (i shr 1) * uvPix
                    val uu = (u[ci].toInt() and 0xff) - 128
                    val vv = (v[ci].toInt() and 0xff) - 128
                    val r = (yy + 1.402f * vv).toInt().coerceIn(0, 255)
                    val g = (yy - 0.344f * uu - 0.714f * vv).toInt().coerceIn(0, 255)
                    val b = (yy + 1.772f * uu).toInt().coerceIn(0, 255)
                    px[j * w + i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }

        companion object {
            fun copy(img: Image): Yuv {
                val p = img.planes
                fun bytes(b: ByteBuffer) = ByteArray(b.remaining()).also { b.get(it) }
                return Yuv(img.width, img.height, bytes(p[0].buffer), bytes(p[1].buffer), bytes(p[2].buffer),
                    p[0].rowStride, p[0].pixelStride, p[1].rowStride, p[1].pixelStride)
            }
        }
    }

    companion object {
        private const val TAG = "ArSource"
        private val QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private const val VERT = "attribute vec4 a_Pos; attribute vec2 a_Uv; varying vec2 v_Uv;" +
            "void main() { gl_Position = a_Pos; v_Uv = a_Uv; }"
        private const val FRAG = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float; varying vec2 v_Uv; uniform samplerExternalOES u_Tex;" +
            "void main() { gl_FragColor = texture2D(u_Tex, v_Uv); }"

        /**
         * An AR camera, or null when ARCore is not installed and ready (not supported, not installed, still
         * checking) or the session cannot be made. The caller already holds the camera permission.
         */
        fun open(ctx: Context, owner: LifecycleOwner, onFail: () -> Unit): ArSource? {
            var s: Session? = null
            return try {
                if (ArCoreApk.getInstance().checkAvailability(ctx) != ArCoreApk.Availability.SUPPORTED_INSTALLED) return null
                s = Session(ctx)
                // the biggest CPU image up to 1280 on the long side (GRIP cut-outs want detail; default is 640x480)
                s.getSupportedCameraConfigs(CameraConfigFilter(s))
                    .filter { maxOf(it.imageSize.width, it.imageSize.height) <= 1280 }
                    .maxByOrNull { it.imageSize.width * it.imageSize.height }
                    ?.let { s.cameraConfig = it }
                s.configure(Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    focusMode = Config.FocusMode.AUTO
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                })
                ArSource(s, owner, onFail)
            } catch (t: Throwable) {
                Log.w(TAG, "no AR session, using CameraX", t)
                runCatching { s?.close() }
                null
            }
        }

        private fun floats(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }

        private fun shader(type: Int, src: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
        }

        private fun program(v: String, f: String): Int = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, v))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, f))
            GLES20.glLinkProgram(it)
        }
    }
}
