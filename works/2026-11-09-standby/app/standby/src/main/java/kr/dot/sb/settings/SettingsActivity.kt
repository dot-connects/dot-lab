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

/** The folder picker opens at DCIM (a hint: the picker may ignore it). */
private val DCIM = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:DCIM")

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
        firstRun = PermWalk.starts(fresh, walkDoneFile().exists(), ConfigStore.storedVersion(this), again = againFile().exists())
        restoreWalk(savedInstanceState)
        kr.dot.sb.platform.clearOldWidgetHost(this)
        kr.dot.sb.platform.PostureProbe.attach(this, "설정") // plan-r15 measurement (personal build, foldables)
        setContent {
            val config by ConfigStore.flow(this).collectAsState()
            // app start and every toggle: the job exists iff the option is on
            androidx.compose.runtime.LaunchedEffect(config.startOnCharge) { kr.dot.sb.platform.ChargeStart.sync(this@SettingsActivity) }
            // StandBy opens settings at a section: "board:<id>", "clock:<FACE>", "photos" (the folder list), or a permission need
            val section = intent.getStringExtra("section")
            var stack by rememberSaveable {
                mutableStateOf(
                    when {
                        // plan-r15 F3: a fresh install (or 처음 설정으로) opens the four first-run steps alone
                        section == null && firstRun -> listOf(Routes.FIRST_RUN)
                        section == null -> listOf(Routes.HOME)
                        // the card's own settings task: 앱 고르기 is the bottom, so Back leaves to StandBy
                        section.startsWith("apps:") -> listOf(section)
                        // a card asking for its grant opens where that grant's line is
                        section == "calendar" -> listOf(Routes.HOME, Routes.card(kr.dot.sb.model.CardType.CALENDAR))
                        section == "notifications" -> listOf(Routes.HOME, Routes.ALERTS)
                        else -> listOf(Routes.HOME, section)
                    },
                )
            }
            // the first run ends here for good; 처음 설정으로 brings it back ([reset])
            val endFirstRun = {
                runCatching { walkDoneFile().createNewFile(); againFile().delete() }
                firstRun = false
                stack = listOf(Routes.HOME)
            }
            restart = { stack = listOf(Routes.FIRST_RUN) }
            BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
            val nav = remember {
                object : Nav {
                    override fun push(route: String) { stack = stack + route }
                    override fun back() { if (stack.size > 1) stack = stack.dropLast(1) else finish() }
                    override fun replace(route: String) { stack = stack.dropLast(1) + route }
                }
            }
            val route = stack.last()
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
                onPermWalkOf = ::startWalk,
                onShowNow = { kr.dot.sb.platform.StandbyActivity.startNow(this) },
                onFirstRunDone = endFirstRun,
                onReset = ::reset,
                calendars = { kr.dot.sb.data.DeviceSources.calendars(this) },
                weekEvents = { kr.dot.sb.data.DeviceSources.week(this, java.time.LocalDate.now()) },
                photoFolders = { f -> kr.dot.sb.data.PhotoIndex.get(this, f).list() },
                pickFolder = { pickFolder.launch(DCIM) },
                removeFolder = ::removeFolder,
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

    // ---- 사진 폴더 (plan-r15 B1): the system folder picker, no media permission ------------------------------------------

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        // without a persisted grant the folder is unreadable after a restart (PhotoIndex would drop it)
        if (runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isFailure) {
            Toast.makeText(this, "이 폴더는 고를 수 없어요. 다른 폴더를 골라 주세요.", Toast.LENGTH_SHORT).show(); return@registerForActivityResult

        }
        update { it.withPhotoFolders(it.photoFolders + uri.toString()) }
    }

    private fun removeFolder(uri: String) {
        update { it.withPhotoFolders(it.photoFolders - uri) }
        runCatching { contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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

    // ---- permissions: one at a time, or a walk over a list (plan-r13 B1, plan-r15 F3) ---------------------------------

    private val walk = mutableStateOf<PermWalk?>(null)
    /** The perms the current walk covers ([Perm.CHARGE_START] on the first run, all of them in 권한). */
    private var walkScope: List<Perm> = Perm.entries
    private var firstRun by mutableStateOf(false)
    /** Set by the composition: back to the first-run steps after [reset]. */
    private var restart: () -> Unit = {}
    /** The walk opened a system page for its current step; [away] = this activity was paused since (the user left). */
    private var opened = false
    private var away = false

    /** No-backup: a phone restored from a backup, with none of the permissions, still gets the first run. */
    private fun walkDoneFile() = java.io.File(noBackupFilesDir, "perm_walk_done")
    /** plan-r15 F8: 처음 설정으로 asked for the first run again (it opens whatever the install looks like). */
    private fun againFile() = java.io.File(noBackupFilesDir, "first_run_again")

    /** plan-r15 F3: a walk survives rotation and a fold (the system page it opened comes back to a new activity). */
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        val w = walk.value ?: return
        out.putStringArrayList("walkAsked", ArrayList(w.asked.map { it.name }))
        out.putString("walkCurrent", w.current?.name)
        out.putStringArrayList("walkScope", ArrayList(walkScope.map { it.name }))
        out.putBoolean("walkOpened", opened)
        out.putBoolean("walkAway", away)
    }

    private fun restoreWalk(b: Bundle?) {
        val asked = b?.getStringArrayList("walkAsked") ?: return
        fun names(l: List<String>) = l.mapNotNull { n -> runCatching { Perm.valueOf(n) }.getOrNull() }
        walk.value = PermWalk(names(asked).toSet(), b.getString("walkCurrent")?.let { n -> runCatching { Perm.valueOf(n) }.getOrNull() })
        walkScope = names(b.getStringArrayList("walkScope").orEmpty())
        opened = b.getBoolean("walkOpened")
        // a system page shown across the rotation counts as left: onResume moves on
        away = b.getBoolean("walkAway") || opened
    }

    /**
     * plan-r15 F8 처음 설정으로: the config back to defaults, every persisted folder grant released, and the first run
     * opens again (the done mark goes, the "again" mark makes [PermWalk.starts] skip the fresh-install check).
     */
    private fun reset() {
        contentResolver.persistedUriPermissions.forEach { p ->
            runCatching { contentResolver.releasePersistableUriPermission(p.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        update { AppConfig() }
        runCatching { walkDoneFile().delete(); againFile().createNewFile() }
        firstRun = true
        restart()
    }

    private fun ask(p: Perm) {
        val names = Perms.runtimePermissions(p)
        if (names.isNotEmpty() && granted.value[p] != true) { pending = p; requestPermissions.launch(names.toTypedArray()) } else openScreen(p)
    }

    private fun openScreen(p: Perm) {
        val step = walk.value?.current == p
        if (Perms.open(this, p)) { if (step) { opened = true; away = false } } else if (step) step(p)
    }

    private fun startWalk(scope: List<Perm>) {
        walkScope = scope
        walk.value = PermWalk()
        step(null)
    }

    /** Step [from] finished (null = start): ask the next missing permission, or end. A repeated report is ignored. */
    private fun step(from: Perm?) {
        val w = walk.value ?: return
        granted.value = Perms.granted(this)
        val next = w.advance(from, walkScope.filter { granted.value[it] != true })
        if (next == w) return
        walk.value = next
        val p = next.current
        if (p == null) endWalk() else ask(p)
    }

    private fun endWalk() {
        walk.value = null
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
        if (runCatching { startActivity(i) }.isFailure) Toast.makeText(this, "무드등이 안 켜져요. 잠시 뒤 다시 눌러 주세요.", Toast.LENGTH_SHORT).show()
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
