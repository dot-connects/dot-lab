package kr.dot.tripbook

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var making: MethodChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Making.launchBook = intent?.getStringExtra(Making.EXTRA_BOOK) // opened from "책이 다 됐어요"
        super.onCreate(savedInstanceState)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "kr.dot.tripbook/media").setMethodCallHandler(MediaChannel(this))
        // the phone's ANDROID_ID (same across reinstalls and cleared data; per app signing key): free books are counted by it
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "kr.dot.tripbook/device").setMethodCallHandler { call, result ->
            if (call.method == "id") result.success(Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)) else result.notImplemented()
        }
        making = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "kr.dot.tripbook/making").also {
            it.setMethodCallHandler(Making.handler(this))
        }
    }

    // a "책이 다 됐어요" tap while the app is still open: tell Dart which book to show
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val book = intent.getStringExtra(Making.EXTRA_BOOK) ?: return
        making?.invokeMethod("open", book)
    }
}
