package kr.dot.ttarogachi

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

// The whole app is site/ (assets/www, put there by ../assemble.mjs) served from https://appassets.androidplatform.net,
// a secure origin, so getUserMedia, WebCodecs and wasm work as on the web. js/platform.js (platform-play.js) talks to
// the Bridge below for what the web can't do well inside an app: save to the gallery, the share sheet, the back key.
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var pending: PermissionRequest? = null
    private val camera = registerForActivityResult(RequestPermission()) { ok ->
        pending?.let { if (ok) it.grant(it.resources) else it.deny() }
        pending = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assets = WebViewAssetLoader.Builder().addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                    assets.shouldInterceptRequest(r.url)

                // our pages stay inside; any other link (help pages, the web site) opens in the browser
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    if (r.url.host == HOST) return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE !in request.resources) return request.deny()
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) return request.grant(request.resources)
                    pending = request
                    camera.launch(Manifest.permission.CAMERA)
                }
            }
            addJavascriptInterface(Bridge(), "BoothApp")
        }
        setContentView(web)
        if (savedInstanceState == null) web.loadUrl("https://$HOST/www/index.html") else web.restoreState(savedInstanceState)

        // the page decides what back means (platform-play.js onBack); without a handler it is the browser's back, then exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.__boothBack ? (window.__boothBack(), 1) : 0") { r ->
                    if (r == "1") return@evaluateJavascript
                    if (web.canGoBack()) web.goBack() else finish()
                }
            }
        })
    }

    // in the background the page stops: no JS timers, no rendering (battery and heat, operator 11-27)
    override fun onPause() {
        super.onPause()
        web.onPause()
        web.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        web.resumeTimers()
        web.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    inner class Bridge {
        // base64 file -> Pictures/따로같이네컷 or Movies/따로같이네컷 (MediaStore: no storage permission on API 29+)
        @JavascriptInterface
        fun save(data: String, name: String, mime: String): Boolean = runCatching {
            val video = mime.startsWith("video/")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/$ALBUM")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val table = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri: Uri = contentResolver.insert(table, values) ?: error("insert failed")
            try {
                contentResolver.openOutputStream(uri)!!.use { it.write(Base64.decode(data, Base64.DEFAULT)) }
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
            true
        }.getOrElse { Log.w(TAG, "save $name", it); false }

        @JavascriptInterface
        fun share(text: String) = runOnUiThread {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(send, null))
        }

        @JavascriptInterface
        fun close() = runOnUiThread { finish() }
    }

    private companion object {
        const val HOST = "appassets.androidplatform.net"
        const val ALBUM = "따로같이네컷"
        const val TAG = "booth"
    }
}
