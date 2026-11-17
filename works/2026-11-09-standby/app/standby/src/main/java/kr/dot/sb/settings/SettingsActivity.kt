package kr.dot.sb.settings

import kr.dot.sb.ui.theme.clockStyle
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId

/**
 * Launcher activity: one activity, in-app navigation by route string ([RouteHost]).
 * Config lives in [ConfigStore]; permission state is re-read every time the user comes back from a system page.
 * StandBy opens it at "apps:<board>:<slot>:<index>" for an empty 앱 바로가기 card: 앱 고르기 alone, and leaving it closes
 * back to StandBy.
 */
class SettingsActivity : ComponentActivity() {
    private val granted = mutableStateOf<Map<Perm, Boolean>>(emptyMap())
    private val lux = mutableStateOf<Float?>(null)
    private var pending: Perm? = null

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted.value = Perms.granted(this)
        val p = pending ?: return@registerForActivityResult
        pending = null
        // denied for good = the system no longer asks; the app's own permission page instead
        if (granted.value[p] != true && Perms.runtimePermissions(p).none { shouldShowRequestPermissionRationale(it) }) openScreen(p)
        else if (walk.value?.current == p) step(p)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        granted.value = Perms.granted(this)
        val fresh = runCatching { packageManager.getPackageInfo(packageName, 0).let { it.firstInstallTime == it.lastUpdateTime } }.getOrDefault(false)
        walkOffer.value = PermWalk.starts(fresh, walkDoneFile().exists(), ConfigStore.storedVersion(this))
        kr.dot.sb.platform.clearOldWidgetHost(this)
        kr.dot.sb.platform.PostureProbe.attach(this, "설정") // plan-r15 measurement (personal build, foldables)
        setContent {
            val config by ConfigStore.flow(this).collectAsState()
            // app start and every toggle: the job exists iff the option is on
            androidx.compose.runtime.LaunchedEffect(config.startOnCharge) { kr.dot.sb.platform.ChargeStart.sync(this@SettingsActivity) }
            // StandBy opens settings at a section: "board:<id>", "clock:<FACE>", or a permission need ("photos")
            val section = intent.getStringExtra("section")
            var stack by rememberSaveable {
                mutableStateOf(
                    when {
                        section == null -> listOf(Routes.HOME)
                        // the card's own settings task: 앱 고르기 is the bottom, so Back leaves to StandBy
                        section.startsWith("apps:") -> listOf(section)
                        section == "photos" || section == "calendar" || section == "notifications" -> listOf(Routes.HOME, Routes.PERMS)
                        else -> listOf(Routes.HOME, section)
                    }.let { if (section == null && walkOffer.value) it + Routes.PERMS else it },
                )
            }
            BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
            val nav = remember {
                object : Nav {
                    override fun push(route: String) { stack = stack + route }
                    override fun back() { if (stack.size > 1) stack = stack.dropLast(1) else finish() }
                    override fun replace(route: String) { stack = stack.dropLast(1) + route }
                }
            }
            val route = stack.last()
            // the walk is offered once: shown = done, even when the user backs out of 권한 (review r13 M2)
            if (route == Routes.PERMS && walkOffer.value) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { walkDoneFile().createNewFile() } }
            LiveLux(route == Routes.NIGHT)
            val env = SettingsEnv(
                foldable = Perms.isFoldable(this),
                phone = Perms.phone(this),
                granted = granted.value,
                onPerm = ::ask,
                lux = lux.value,
                onGlowTest = ::testGlow,
                licenseNames = assets.list("licenses")?.sorted().orEmpty(),
                licenseText = { f -> runCatching { assets.open("licenses/$f").bufferedReader().use { it.readText() } }.getOrDefault("") },
                versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "",
                launcherApps = { kr.dot.sb.ui.cards.launcherApps(this) },
                permWalkOffer = walkOffer.value,
                onPermWalk = ::startWalk,
                onPermLater = { endWalk(); nav.back() },
                calendars = { kr.dot.sb.data.DeviceSources.calendars(this) },
                photoFolders = { kr.dot.sb.data.DeviceSources.folders(this) },
                playSample = ::playSample,
                chargeTrail = { kr.dot.sb.platform.ChargeStart.trail(this) },
                measureLog = { kr.dot.sb.platform.Measure.lines(this) },
            )
            SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) {
                // plan-r13 C1: bars and cutouts padded once here; the keyboard only by the page scroll and the sheets
                Box(Modifier.fillMaxSize().background(SK.bg).windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))) {
                    RouteHost(route, config, ::update, nav, env)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted.value = Perms.granted(this)
        // back from the system page the walk opened: on to the next one
        if (opened && away) { opened = false; away = false; walk.value?.current?.let(::step) }
    }

    override fun onPause() {
        super.onPause()
        if (opened) away = true
    }

    private fun update(f: (AppConfig) -> AppConfig) = ConfigStore.update(this, f)

    // ---- permissions: one at a time, or [차례로 켜기] (plan-r13 B1) ----------------------------------------------------
    // ponytail: the walk lives in fields; a rotation mid-walk ends it quietly (the next launch offers it again).

    private val walk = mutableStateOf<PermWalk?>(null)
    private val walkOffer = mutableStateOf(false)
    /** The walk opened a system page for its current step; [away] = this activity was paused since (the user left). */
    private var opened = false
    private var away = false

    /** No-backup: a phone restored from a backup, with none of the permissions, still gets the walk. */
    private fun walkDoneFile() = java.io.File(noBackupFilesDir, "perm_walk_done")

    private fun ask(p: Perm) {
        val names = Perms.runtimePermissions(p)
        if (names.isNotEmpty() && granted.value[p] != true) { pending = p; requestPermissions.launch(names.toTypedArray()) } else openScreen(p)
    }

    private fun openScreen(p: Perm) {
        val step = walk.value?.current == p
        if (Perms.open(this, p)) { if (step) { opened = true; away = false } } else if (step) step(p)
    }

    private fun startWalk() {
        walk.value = PermWalk()
        step(null)
    }

    /** Step [from] finished (null = start): ask the next missing permission, or end. A repeated report is ignored. */
    private fun step(from: Perm?) {
        val w = walk.value ?: return
        granted.value = Perms.granted(this)
        val next = w.advance(from, Perm.entries.filter { granted.value[it] != true })
        if (next == w) return
        walk.value = next
        val p = next.current
        if (p == null) endWalk() else ask(p)
    }

    private fun endWalk() {
        walk.value = null
        walkOffer.value = false
        runCatching { walkDoneFile().createNewFile() }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopSample = Runnable { kr.dot.sb.platform.SleepService.stop(this) }

    /** 수면 소리: 3 s of [s] through the real service (a new tap restarts the 3 s). */
    private fun playSample(s: kr.dot.sb.model.SleepSound) {
        main.removeCallbacks(stopSample)
        runCatching { kr.dot.sb.platform.SleepService.start(this, s, 1) }
        main.postDelayed(stopSample, 3_000)
    }

    private fun testGlow() {
        val i = Intent().setClassName(packageName, "kr.dot.sb.platform.GlowActivity").putExtra("source", "TEST").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(i) }.isFailure) Toast.makeText(this, "무드등을 열 수 없어요", Toast.LENGTH_SHORT).show()
    }

    /** Ambient light only while the night screen is open (the sensor costs battery). */
    @androidx.compose.runtime.Composable
    private fun LiveLux(active: Boolean) {
        DisposableEffect(active) {
            val sm = getSystemService(SensorManager::class.java)
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { lux.value = e.values[0] }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            if (active && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sm?.unregisterListener(l) }
        }
    }
}
