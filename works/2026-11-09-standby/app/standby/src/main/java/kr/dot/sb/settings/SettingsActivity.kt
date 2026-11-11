package kr.dot.sb.settings

import kr.dot.sb.ui.theme.clockStyle
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId
import kr.dot.sb.platform.WidgetHost

/**
 * Launcher activity: one activity, in-app navigation by route string ([RouteHost]).
 * Config lives in [ConfigStore]; permission state is re-read every time the user comes back from a system page.
 */
class SettingsActivity : ComponentActivity() {
    private val granted = mutableStateOf<Map<Perm, Boolean>>(emptyMap())
    private val lux = mutableStateOf<Float?>(null)
    private var pending: Perm? = null

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted.value = Perms.granted(this)
        val p = pending
        pending = null
        val name = p?.let { Perms.runtimePermission(it) }
        // denied twice = the system no longer asks; send the user to the app's permission page instead
        if (!ok && p != null && name != null && !shouldShowRequestPermissionRationale(name)) Perms.open(this, p)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        granted.value = Perms.granted(this)
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
                    },
                )
            }
            BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
            val nav = remember {
                object : Nav {
                    override fun push(route: String) { stack = stack + route }
                    override fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
                }
            }
            val route = stack.last()
            LiveLux(route == Routes.NIGHT)
            val env = SettingsEnv(
                foldable = Perms.isFoldable(this),
                granted = granted.value,
                onPerm = ::onPermClick,
                lux = lux.value,
                onGlowTest = ::testGlow,
                licenseNames = assets.list("licenses")?.sorted().orEmpty(),
                licenseText = { f -> runCatching { assets.open("licenses/$f").bufferedReader().use { it.readText() } }.getOrDefault("") },
                versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "",
                pickWidget = ::pickWidget,
                configureWidget = { id -> configure(id, fresh = false) },
                widgetLabel = { id -> AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.let(::label) },
            )
            SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) {
                RouteHost(route, config, ::update, nav, env)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted.value = Perms.granted(this)
    }

    /** Every settings edit: widget ids that left the config (card, board or slot removed, widget swapped) are released (D6). */
    private fun update(f: (AppConfig) -> AppConfig) = ConfigStore.update(this) { old ->
        f(old).also { new -> BoardOps.removedWidgetIds(old, new).forEach { WidgetHost.delete(this, it) } }
    }

    // ---- phone widget: provider list -> bind (system asks once) -> the provider's configure screen ----------------------
    // ponytail: the pending id and callback live in fields; an activity recreated mid-flow (rotation) loses the callback
    // and leaves the id allocated until the next bind. Keep them in savedInstanceState if that shows up on the device.

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetDone: ((Int) -> Unit)? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configure(widgetId, fresh = true) else widgetCancelled()
    }

    private fun label(i: AppWidgetProviderInfo): String {
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(i.provider.packageName, 0)) }.getOrNull()
        return listOfNotNull(app, i.loadLabel(packageManager)).distinct().joinToString(" · ")
    }

    private fun pickWidget(done: (Int) -> Unit) {
        val am = AppWidgetManager.getInstance(this)
        val list = am.installedProviders.map { it to label(it) }.sortedBy { it.second }
        if (list.isEmpty()) { Toast.makeText(this, "고를 수 있는 위젯이 없어요", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("위젯 고르기").setItems(list.map { it.second }.toTypedArray()) { _, i ->
            val p = list[i].first
            widgetId = WidgetHost.get(this).allocateAppWidgetId()
            widgetDone = done
            if (am.bindAppWidgetIdIfAllowed(widgetId, p.provider)) configure(widgetId, fresh = true)
            else bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider),
            )
        }.show()
    }

    /** [fresh] = right after a bind: a cancel then deletes the id; a later "위젯 설정" cancel keeps the widget. */
    private fun configure(id: Int, fresh: Boolean) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info?.configure == null) {
            if (fresh) widgetBound() else Toast.makeText(this, "이 위젯은 따로 설정할 게 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        if (!fresh) widgetDone = null
        runCatching { WidgetHost.get(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null) }
            .onFailure { if (fresh) widgetBound() else Toast.makeText(this, "위젯 설정을 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("startAppWidgetConfigureActivityForResult reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CONFIGURE || widgetDone == null) return
        if (resultCode == RESULT_OK) widgetBound() else widgetCancelled()
    }

    private fun widgetBound() {
        widgetDone?.invoke(widgetId)
        widgetDone = null
    }

    private fun widgetCancelled() {
        WidgetHost.delete(this, widgetId)
        widgetDone = null
    }

    private fun onPermClick(p: Perm) {
        val rp = Perms.runtimePermission(p)
        if (rp != null && checkSelfPermission(rp) != PackageManager.PERMISSION_GRANTED) { pending = p; requestPermission.launch(rp) } else Perms.open(this, p)
    }

    private fun testGlow() {
        val i = Intent().setClassName(packageName, "kr.dot.sb.platform.GlowActivity").putExtra("test", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
