package kr.dot.sb.settings

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
import kr.dot.sb.model.FaceId

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
            var stack by rememberSaveable { mutableStateOf(listOf(Routes.HOME)) }
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
            )
            SettingsTheme(config.styles.getValue(FaceId.DIGITAL).color.c()) {
                RouteHost(route, config, { f -> ConfigStore.update(this, f) }, nav, env)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted.value = Perms.granted(this)
    }

    private fun onPermClick(p: Perm) {
        val rp = Perms.runtimePermission(p)
        if (rp != null && checkSelfPermission(rp) != PackageManager.PERMISSION_GRANTED) { pending = p; requestPermission.launch(rp) } else Perms.open(this, p)
    }

    private fun testGlow() {
        val i = Intent().setClassName(packageName, "kr.dot.sb.platform.GlowActivity").putExtra("test", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
