package kr.dot.sb.settings

import kr.dot.sb.ui.theme.clockStyle
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.SizeF
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.platform.WidgetFit
import kr.dot.sb.platform.WidgetHost

/**
 * Launcher activity: one activity, in-app navigation by route string ([RouteHost]).
 * Config lives in [ConfigStore]; permission state is re-read every time the user comes back from a system page.
 * StandBy opens it at "widget:<board>:<slot>:<index>" for a phone widget's long press: an empty card gets 위젯 고르기,
 * a placed one its menu, and the activity closes back to StandBy when that is done ([fromBoard]).
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
        savedInstanceState?.let { b ->
            widgetId = b.getInt("widgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
            widgetTarget = b.getString("widgetTarget")
            widgetAsk.value = b.getString("widgetAsk")
            widgetMenu.value = b.getString("widgetMenu")
            configuring = b.getBoolean("configuring")
        }
        if (savedInstanceState == null && fromBoard) {
            // "widget:<board>:<slot>:<index>" -> "<board>|<slot>:<index>"
            val parts = intent.getStringExtra("section").orEmpty().split(':')
            if (parts.size == 4) {
                val t = "${parts[1]}|${parts[2]}:${parts[3]}"
                if (widgetIdOf(ConfigStore.flow(this).value, t) == null) widgetAsk.value = t else widgetMenu.value = t
            }
        }
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
                        section == "photos" || section == "calendar" || section == "notifications" -> listOf(Routes.HOME, Routes.PERMS)
                        else -> listOf(Routes.HOME, section)
                    }.let { if (section == null && walkOffer.value) it + Routes.PERMS else it },
                )
            }
            BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
            val nav = remember {
                object : Nav {
                    override fun push(route: String) { stack = stack + route }
                    override fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
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
                pickWidget = { b, key -> widgetAsk.value = "$b|$key" },
                configureWidget = ::configure,
                widgetConfigurable = ::configurable,
                permWalkOffer = walkOffer.value,
                onPermWalk = ::startWalk,
                onPermLater = { endWalk(); nav.back() },
                widgetLabel = { id -> AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.let(::label) },
                calendars = { kr.dot.sb.data.DeviceSources.calendars(this) },
                photoFolders = { kr.dot.sb.data.DeviceSources.folders(this) },
                playSample = ::playSample,
                chargeTrail = { kr.dot.sb.platform.ChargeStart.trail(this) },
            )
            SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) {
                Box(Modifier.fillMaxSize()) {
                    RouteHost(route, config, ::update, nav, env)
                    widgetMenu.value?.let { WidgetMenu(it, config) }
                    widgetAsk.value?.let { WidgetPicker(it, config) }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("widgetId", widgetId)
        outState.putString("widgetTarget", widgetTarget)
        outState.putString("widgetAsk", widgetAsk.value)
        outState.putString("widgetMenu", widgetMenu.value)
        outState.putBoolean("configuring", configuring)
    }

    /** Every settings edit: widget ids that left the config (card, board or slot removed, widget swapped) are released (D6). */
    private fun update(f: (AppConfig) -> AppConfig) = ConfigStore.update(this) { old ->
        f(old).also { new -> BoardOps.removedWidgetIds(old, new).forEach { WidgetHost.delete(this, it) } }
    }

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

    // ---- phone widget (plan-r13 B10): 위젯 고르기 sheet -> bind with the slot sizes -> configure only when required ------

    /** "<board>|<slot>:<index>" while 위젯 고르기 is open / while a placed widget's menu is open. */
    private val widgetAsk = mutableStateOf<String?>(null)
    private val widgetMenu = mutableStateOf<String?>(null)
    /** The id being bound and the card it goes to (kept across rotation / unfold). */
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetTarget: String? = null
    private var widgetOptions: Bundle? = null
    /** 위젯 설정 of a placed widget is open. */
    private var configuring = false
    private val fromBoard get() = intent.getStringExtra("section")?.startsWith("widget:") == true

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) bound() else widgetCancelled()
    }

    private fun label(i: AppWidgetProviderInfo): String {
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(i.provider.packageName, 0)) }.getOrNull()
        return listOfNotNull(app, i.loadLabel(packageManager)).distinct().joinToString(" · ")
    }

    private fun configurable(id: Int): Boolean = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        ?.let { it.configure != null && it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0 } == true

    private fun widgetIdOf(c: AppConfig, target: String): Int? {
        val (b, key) = target.split('|').let { it[0] to it.getOrElse(1) { "" } }
        return c.boards.firstOrNull { it.id == b }?.options?.get(key)?.widgetId
    }

    @Composable
    private fun WidgetPicker(target: String, config: AppConfig) {
        val (boardId, key) = target.split('|').let { it[0] to it.getOrElse(1) { "0:0" } }
        val layout = config.boards.firstOrNull { it.id == boardId }?.layout ?: Layout.ONE
        val slot = key.substringBefore(':').toIntOrNull() ?: 0
        val dev = PreviewDevice.of(Perms.phone(this), inner = false)
        val slots = remember(layout, slot) { WidgetFit.slotSizes(layout, slot, dev.w.toFloat(), dev.h.toFloat(), dev.cover) }
        val catalog = remember(target) { WidgetCatalog.load(this) }
        WidgetPickerSheet(catalog.choices, slots, catalog.art, onDismiss = { widgetAsk.value = null; if (fromBoard) finish() }) { c ->
            catalog.infos[c.key]?.let { bind(it, target, slots) }
        }
    }

    @Composable
    private fun WidgetMenu(target: String, config: AppConfig) {
        val id = widgetIdOf(config, target)
        WidgetMenuSheet(
            label = id?.let { AppWidgetManager.getInstance(this).getAppWidgetInfo(it) }?.let(::label),
            configurable = id != null && configurable(id),
            onConfigure = { widgetMenu.value = null; if (id != null) configure(id) },
            onChange = { widgetMenu.value = null; widgetAsk.value = target },
            onLook = { widgetMenu.value = null },
            onRemove = { widgetMenu.value = null; place(target, null); if (fromBoard) finish() },
            onDismiss = { widgetMenu.value = null; if (fromBoard) finish() },
        )
    }

    /** The slot's portrait and landscape sizes for the provider (min/max of both, and the exact list on 12+). */
    private fun sizeOptions(slots: List<WidgetFit.Size>) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, slots.minOf { it.w }.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, slots.maxOf { it.w }.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, slots.minOf { it.h }.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, slots.maxOf { it.h }.toInt())
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(slots.map { SizeF(it.w, it.h) }))
    }

    private fun bind(info: AppWidgetProviderInfo, target: String, slots: List<WidgetFit.Size>) {
        val am = AppWidgetManager.getInstance(this)
        widgetId = WidgetHost.get(this).allocateAppWidgetId()
        widgetTarget = target
        val opts = sizeOptions(slots).also { widgetOptions = it }
        if (am.bindAppWidgetIdIfAllowed(widgetId, info.profile, info.provider, opts)) { bound(); return }
        Toast.makeText(this, "다음 화면에서 '항상 허용'을 체크하면 이후엔 안 물어봐요", Toast.LENGTH_LONG).show()
        bindWidget.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, opts),
        )
    }

    /** Bound: the provider's configure screen only when it must run now, else placed at once. */
    private fun bound() {
        val am = AppWidgetManager.getInstance(this)
        widgetOptions?.let { runCatching { am.updateAppWidgetOptions(widgetId, it) } }
        val info = am.getAppWidgetInfo(widgetId)
        val f = info?.widgetFeatures ?: 0
        val must = info != null && WidgetFit.needsConfigure(
            info.configure != null, f and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0, f and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0,
        )
        if (!must) return placed()
        runCatching { WidgetHost.get(this).startAppWidgetConfigureActivityForResult(this, widgetId, 0, REQ_CONFIGURE, null) }.onFailure { placed() }
    }

    /** The new id goes to its card (the old one is released by [update]); the sheets close, and from StandBy so does this. */
    private fun placed() {
        widgetTarget?.let { place(it, widgetId) }
        widgetTarget = null
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        widgetAsk.value = null
        if (fromBoard) finish()
    }

    /** Card [target]'s widget id ← [id] (null = 빼기); nothing when that card stopped being a phone widget meanwhile. */
    private fun place(target: String, id: Int?) = update { c ->
        val (b, key) = target.split('|').let { it[0] to it.getOrElse(1) { "" } }
        val (slot, j) = key.split(':').map { it.toIntOrNull() ?: -1 }.let { it.getOrElse(0) { -1 } to it.getOrElse(1) { -1 } }
        if (c.boards.firstOrNull { it.id == b }?.slots?.getOrNull(slot)?.getOrNull(j) != CardType.WIDGET) c
        else BoardOps.replace(c, b) { bb -> bb.copy(options = bb.options + (key to (bb.options[key] ?: SlotOptions()).copy(widgetId = id))) }
    }

    /** Bind or configure cancelled: the id goes, the sheet stays open for another pick. */
    private fun widgetCancelled() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetHost.delete(this, widgetId)
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        widgetTarget = null
    }

    /** 위젯 설정 of a placed widget: its configure screen; a cancel keeps the widget. */
    private fun configure(id: Int) {
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.configure == null) {
            Toast.makeText(this, "이 위젯은 따로 설정할 게 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { WidgetHost.get(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null); configuring = true }
            .onFailure { Toast.makeText(this, "위젯 설정을 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("startAppWidgetConfigureActivityForResult reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CONFIGURE) return
        if (widgetTarget != null) { if (resultCode == RESULT_OK) placed() else widgetCancelled() }
        else if (configuring) { configuring = false; if (fromBoard) finish() }
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

    private companion object { const val REQ_CONFIGURE = 41 }

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
