package kr.dot.wherepark.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.data.Prefs

/**
 * The speak/save bottom sheet over whatever is on screen (widget, notification, tile, app, shortcut, onboarding practice).
 * Extras: car_id, bt_car_id, from, mode=manual (contracts-v2 section 2). Recognition is on-device only; the heard text never leaves the phone.
 */
class ListenActivity : ComponentActivity() {
    private lateinit var model: ListenModel
    private var rec: SpeechRecognizer? = null
    private var shot: Pair<Park, File>? = null

    private val micAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Prefs.put(this, DENIALS, null); kr.dot.wherepark.data.WidgetRefresh.request(applicationContext); checkPack() } else {
            val n = denials() + 1
            Prefs.put(this, DENIALS, n)
            model.micDenied(n)
        }
    }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (p, f) = shot ?: return@registerForActivityResult
        if (ok) Thread { model.photoTaken(p, f) }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ListenModel(applicationContext, intent.getStringExtra("car_id"), intent.getStringExtra("bt_car_id"), intent.getStringExtra("from"))
        model.relisten = ::listen
        model.close = {
            if (model.practice) setResult(RESULT_OK, Intent().putExtra("understood", (model.state as? Sheet.Practice)?.result?.spot != null))
            finish()
        }
        setContent {
            ListenSheet(model, SheetActions(
                askMic = { micAsk.launch(Manifest.permission.RECORD_AUDIO) },
                openSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
                downloadPack = ::downloadPack,
                photo = ::takePhoto,
            ))
        }
        val restored = savedInstanceState?.getString(SHOT_CAR)?.let { c ->
            val id = savedInstanceState.getLong(SHOT_PARK)
            kr.dot.wherepark.data.ParkStore.history(this, c).firstOrNull { it.id == id }?.let { p -> p to File(savedInstanceState.getString(SHOT_FILE)!!) }
        }
        if (restored != null) { shot = restored; model.restore(restored.first) } else if (intent.getStringExtra("mode") == "manual") model.manual() else begin()
    }

    /** Keeps the pending camera shot across a process death while the camera app is in front. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shot?.let { (p, f) -> outState.putString(SHOT_CAR, p.carId); outState.putLong(SHOT_PARK, p.id); outState.putString(SHOT_FILE, f.path) }
    }

    private fun denials(): Int = Prefs.sp(this).getString(DENIALS, null)?.toIntOrNull() ?: 0
    private fun micOk() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun begin() = when {
        micOk() -> checkPack()
        denials() >= 2 -> model.micDenied(2)
        else -> { model.micDenied(denials()); micAsk.launch(Manifest.permission.RECORD_AUDIO) }
    }

    /** Back from system settings with the mic now on: start over. */
    override fun onResume() {
        super.onResume()
        if ((model.state as? Sheet.Manual)?.why == ManualWhy.NO_MIC && micOk()) { Prefs.put(this, DENIALS, null); begin() }
    }

    private fun recIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

    private fun recognizer(): SpeechRecognizer =
        rec ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { it.setRecognitionListener(listener); rec = it }

    private fun checkPack() {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return model.noRecognizer()
        recognizer().checkRecognitionSupport(recIntent(), mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                val ok = s.installedOnDeviceLanguages.any(::isKo)
                packKnown(ok)
                if (ok) listen() else model.packMissing()
            }
            override fun onError(error: Int) { packKnown(false); model.packMissing() }
        })
    }

    /** Widget mics show "음성팩 필요" while the Korean pack is known missing (spec 8.3). */
    private fun packKnown(ok: Boolean) {
        val was = Prefs.sp(this).getString(NO_PACK, null) == null
        if (was != ok) { Prefs.put(this, NO_PACK, if (ok) null else 1); kr.dot.wherepark.data.WidgetRefresh.request(applicationContext) }
    }

    private fun isKo(tag: String) = tag.replace('_', '-').lowercase().startsWith("ko")

    private fun listen() {
        if (isFinishing) return
        model.listening()
        recognizer().startListening(recIntent())
    }

    private fun downloadPack() {
        recognizer().triggerModelDownload(recIntent())
        model.downloading()
    }

    private fun takePhoto() {
        val p = (model.state as? Sheet.Saved)?.park ?: return
        val f = model.photoFile(p)
        shot = p to f
        try {
            camera.launch(FileProvider.getUriForFile(this, "$packageName.files", f))
        } catch (_: ActivityNotFoundException) {
            shot = null
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = model.rms(rmsdB)
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) = model.error(error)
        override fun onResults(results: Bundle?) = model.result(best(results))
        override fun onPartialResults(partialResults: Bundle?) = model.partial(best(partialResults))
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun best(b: Bundle?): String = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    /** Leaving mid-listen (home, lock) drops the sheet; the camera and settings trips keep it. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && model.state is Sheet.Listening) { rec?.cancel(); finish() }
    }

    override fun onDestroy() {
        rec?.destroy()
        rec = null
        super.onDestroy()
    }

    private companion object {
        const val LANG = "ko-KR"
        const val DENIALS = "wp_mic_denials"
        const val NO_PACK = "wp_no_pack"
        const val SHOT_CAR = "shot_car"
        const val SHOT_PARK = "shot_park"
        const val SHOT_FILE = "shot_file"
    }
}
