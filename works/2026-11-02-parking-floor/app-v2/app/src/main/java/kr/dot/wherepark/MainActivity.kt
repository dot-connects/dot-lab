package kr.dot.wherepark

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import kr.dot.wherepark.car.CarLink
import kr.dot.wherepark.car.ExitNotifier
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.SpotParser
import kr.dot.wherepark.core.newCarId
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.LocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncEngine
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.family.FamilyActivity
import kr.dot.wherepark.family.InviteActivity
import kr.dot.wherepark.family.JoinActivity
import kr.dot.wherepark.family.JoinRequest
import kr.dot.wherepark.family.Push
import kr.dot.wherepark.ui.AppActions
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.CarDraft
import kr.dot.wherepark.ui.CarEditScreen
import kr.dot.wherepark.ui.CarStep
import kr.dot.wherepark.ui.DetailActions
import kr.dot.wherepark.ui.DetailScreen
import kr.dot.wherepark.ui.EditActions
import kr.dot.wherepark.ui.HistoryScreen
import kr.dot.wherepark.ui.HomeData
import kr.dot.wherepark.ui.HomeScreen
import kr.dot.wherepark.ui.ListenActivity
import kr.dot.wherepark.ui.ListenModel
import kr.dot.wherepark.ui.Onboarding
import kr.dot.wherepark.ui.PlateScreen
import kr.dot.wherepark.ui.Practice
import kr.dot.wherepark.ui.Route
import kr.dot.wherepark.ui.SettingsActions
import kr.dot.wherepark.ui.SettingsScreen
import kr.dot.wherepark.ui.Step1
import kr.dot.wherepark.ui.Step2
import kr.dot.wherepark.ui.Step3
import kr.dot.wherepark.ui.Step4
import kr.dot.wherepark.ui.Wp
import kr.dot.wherepark.ui.WpTheme
import kr.dot.wherepark.widget.WidgetReceiver
import kr.dot.wherepark.widget.pinWidget

/** In-app screens (one Activity, a tiny back stack). Family, invite, join and widget settings stay their own Activities. */
sealed interface Nav {
    data object Home : Nav
    data class Detail(val carId: String) : Nav
    data class Plate(val carId: String) : Nav
    data class History(val carId: String) : Nav
    data object Settings : Nav
    data class Edit(val carId: String?) : Nav
    data class Onb(val step: Int) : Nav
}

class MainActivity : ComponentActivity() {
    private var stack by mutableStateOf(listOf<Nav>(Nav.Home))
    private var tick by mutableIntStateOf(0)
    private var bigText by mutableStateOf(false)
    private var joinedOnb = false
    private var joinLaunched = false
    private var requestShown = false  // an accepted join request opens its confirm screen once per launch
    private val carStep = CarStep(newCarId())
    private var practice by mutableStateOf(Practice(false, null, null, null))
    private var draft: Pair<Nav.Edit, CarDraft>? = null
    private var draftLinkId: String? = null
    private var pendingShot: Pair<Park, File>? = null
    private var rec: SpeechRecognizer? = null
    private var afterBt: (() -> Unit)? = null
    private var btPick by mutableStateOf<Pair<String, () -> Unit>?>(null)

    private val top get() = stack.last()
    private fun go(n: Nav) { stack = stack + n }
    private fun back() { if (stack.size > 1) stack = stack.dropLast(1) else finish() }
    private fun reset(n: Nav) { stack = listOf(n) }

    private val notifAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    private val btAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) afterBt?.invoke(); afterBt = null }
    private val practiceRun = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val raw = r.data?.getStringExtra("raw")?.takeIf { it.isNotBlank() }
        practice = practiceNow(raw ?: practice.raw)
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (p, f) = pendingShot ?: return@registerForActivityResult
        pendingShot = null
        if (ok) Thread { ListenModel(applicationContext, p.carId, null, "app").photoTaken(p, f); onUi { tick++ } }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        joinLaunched = savedInstanceState?.getBoolean("join_launched") ?: false
        enableEdgeToEdge()
        bigText = AppPrefs.bigText(this)
        route()
        openCar(intent)
        setContent { WpTheme(bigText) { App(); BtPicker() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route()
        openCar(intent)
    }

    /** Widget row / dynamic shortcut "흰 차 어디?" -> that car's detail. */
    private fun openCar(i: Intent?) {
        val id = i?.getStringExtra("car_id") ?: return
        i.removeExtra("car_id")
        if (top !is Nav.Onb && CarStore.cars(this).any { it.id == id }) reset(Nav.Home).also { go(Nav.Detail(id)) }
    }

    /** Step 0 (invisible): an invite code goes to the join screen once; otherwise the four steps (car step skipped after a join). */
    private fun route() {
        when (val r = Onboarding.route(this, null)) {
            Route.Home -> if (top is Nav.Onb) reset(Nav.Home)
            is Route.Join -> {
                if (top !is Nav.Onb) reset(Nav.Onb(1))
                if (!joinLaunched) {
                    joinLaunched = true
                    startActivity(Intent(this, JoinActivity::class.java).setData(Uri.parse("https://dot-lab-parking.dotconnects.workers.dev/j#${r.code}")))
                }
            }
            is Route.Steps -> {
                joinedOnb = r.joined
                if (top !is Nav.Onb) reset(Nav.Onb(1))
                if (r.joined && (top as Nav.Onb).step == 2) reset(Nav.Onb(3))
            }
        }
    }

    /** Pull when the app opens and (re)register the push token; with no token the 15-minute pull takes over. */
    override fun onResume() {
        super.onResume()
        route()
        tick++
        if (top is Nav.Onb && (top as Nav.Onb).step == 3) practice = practiceNow(practice.raw)
        val app = applicationContext
        Thread {
            WidgetRefresh.request(app)  // re-reads the wallpaper colours (no broadcast for wallpaper changes)
            Push.start(app)
            // a family accepted my join request while I was away: ask on the join screen (never join blindly)
            if (!requestShown && JoinRequest.poll(app) is JoinRequest.Poll.Code) onUi {
                requestShown = true
                startActivity(Intent(this, JoinActivity::class.java))
            }
            if (FamilyStore.joined(app) && SyncEngine.pull(app)) onUi { tick++ }  // a merged pull also refreshes widgets
        }.start()
        publishShortcuts()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("join_launched", joinLaunched)
    }

    /** 차 편집 has no save button: also save when the app goes to the background. */
    override fun onStop() {
        super.onStop()
        saveDraft()
    }

    /** Results of background work land only while this screen is alive. */
    private fun onUi(f: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) f() }

    override fun onDestroy() {
        rec?.destroy(); rec = null
        super.onDestroy()
    }

    @Composable
    private fun App() {
        val t = tick
        BackHandler(stack.size > 1) { leaving(); back() }
        when (val n = top) {
            Nav.Home -> Home(t)
            is Nav.Detail -> CarOr(n.carId) { car -> Detail(car, t) }
            is Nav.Plate -> CarOr(n.carId) { car ->
                var copied by remember { mutableStateOf(false) }
                KeepScreenOn()
                PlateScreen(car, copied) { copy("plate", car.plate.orEmpty()); copied = true }
            }
            is Nav.History -> CarOr(n.carId) { car -> HistoryScreen(car, ParkStore.history(this, car.id), System.currentTimeMillis(), ::back) }
            Nav.Settings -> SettingsScreen(settingsActions())
            is Nav.Edit -> Edit(n)
            is Nav.Onb -> Onb(n.step)
        }
    }

    @Composable
    private fun CarOr(id: String, content: @Composable (Car) -> Unit) {
        val car = CarStore.cars(this).firstOrNull { it.id == id }
        if (car == null) { androidx.compose.runtime.LaunchedEffect(id) { reset(Nav.Home) }; return }
        content(car)
    }

    @Composable
    private fun KeepScreenOn() = DisposableEffect(Unit) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    // home

    @Composable
    private fun Home(t: Int) {
        val d = remember(t) { HomeData.load(this, System.currentTimeMillis(), WidgetReceiver.micReady(this)) }
        HomeScreen(d, AppActions(
            speak = ::listen, openCar = { go(Nav.Detail(it)) }, plate = { go(Nav.Plate(it)) }, settings = { go(Nav.Settings) },
            fixMic = { listen(null) }, newFamily = ::recreateFamily, retryGroup = ::recreateFamily,
            joinByCode = { startActivity(Intent(this, JoinActivity::class.java)) },
        ))
        val carry = remember(t) { if (FamilyStore.joined(this)) FamilyStore.carryOver(this) else emptyList() }
        if (carry.isNotEmpty()) AlertDialog(
            onDismissRequest = {}, containerColor = Wp.SF,
            text = { Text("차 ${carry.size}대를 새 가족으로 가져갈까요?") },
            confirmButton = { TextButton({ FamilyStore.acceptCarryOver(this); tick++ }) { Text("가져가기", color = Wp.FG) } },
            dismissButton = { TextButton({ FamilyStore.declineCarryOver(this); tick++ }) { Text("안 가져가요", color = Wp.MU) } },
        )
    }

    private fun listen(carId: String?, mode: String? = null) {
        startActivity(Intent(this, ListenActivity::class.java).putExtra("from", "app").also { i ->
            carId?.let { i.putExtra("car_id", it) }; mode?.let { i.putExtra("mode", it) }
        })
    }

    /** After a 401 band or a refused create: a new family from the stored name; SyncWorker creates it and re-sends the local cars. */
    private fun recreateFamily() {
        val name = AppPrefs.myName(this) ?: FamilyStore.me(this)?.name ?: "나"
        FamilyStore.setPendingGroup(this, name, "${name}네 가족")
        FamilyStore.dismissRemoved(this)
        SyncQueue.kick(this)
        tick++
    }

    // car detail

    @Composable
    private fun Detail(car: Car, t: Int) {
        val park = remember(car.id, t) { ParkStore.latest(this, car.id) }
        // local file, else the family member's upload (photo=true) fetched once and cached; off the main thread
        val photo by produceState<File?>(null, park?.id, park?.photo, t) {
            value = park?.let { p -> withContext(Dispatchers.IO) { LocalData.photo(applicationContext, p) } }
        }
        DetailScreen(car, park, photo, System.currentTimeMillis(), DetailActions(
            back = ::back, edit = { go(Nav.Edit(car.id)) }, fix = { listen(car.id, "edit") }, speak = { listen(car.id) },
            plate = { go(Nav.Plate(car.id)) }, history = { go(Nav.History(car.id)) },
            photo = { park?.let(::takePhoto) }, took = { park?.let(::tookCar) },
        ), kr.dot.wherepark.car.ExitNotifier.leftAt(this, car.id))
    }

    private fun takePhoto(p: Park) {
        val f = ListenModel(applicationContext, p.carId, null, "app").photoFile(p)
        pendingShot = p to f
        try { camera.launch(FileProvider.getUriForFile(this, "$packageName.files", f)) } catch (_: Exception) { pendingShot = null }
    }

    /** [가져갔어요]: same op as the Bluetooth reconnect (contracts "운행 중"). */
    private fun tookCar(p: Park) {
        synchronized(SyncQueue) {
            kr.dot.wherepark.car.ExitNotifier.markDriving(this, p.carId)
            ParkStore.save(this, p.copy(state = ParkState.DRIVING, drivingBy = FamilyStore.me(this)?.name))
            SyncQueue.add(this, Op.State(p.carId, ParkState.DRIVING))
        }
        SyncQueue.kick(this)
        WidgetRefresh.request(applicationContext)
        tick++
    }

    // car edit

    @Composable
    private fun Edit(n: Nav.Edit) {
        val cars = remember(n) { CarStore.cars(this) }
        val d = draft?.takeIf { it.first == n }?.second ?: run {
            val car = cars.firstOrNull { it.id == n.carId }
                ?: Car(newCarId(), "", Wp.CAR_COLORS.first { c -> cars.none { it.color == c } }, null, (cars.maxOfOrNull { it.order } ?: -1) + 1)
            CarDraft(car).also { draft = n to it; draftLinkId = car.id }
        }
        val id = draftLinkId!!
        CarEditScreen(d, n.carId == null, tick >= 0 && CarLinkStore.address(this, id) != null, EditActions(
            back = { leaving(); back() },
            link = { linkBt(id) { tick++ } },
            unlink = { CarLink.unlink(this, id); tick++ },
            delete = { deleteCar(id) },
        ))
    }

    /** Leaving 차 편집 saves it (no save button in the mockup); a new car with no name is dropped. */
    private fun leaving() {
        val n = draft?.first ?: return
        if (top != n) return
        saveDraft(leaving = true)
        draft = null
    }

    private fun saveDraft(leaving: Boolean = false) {
        val (n, d) = draft ?: return
        if (top != n) return
        val id = draftLinkId ?: return
        val old = CarStore.cars(this).firstOrNull { it.id == id }
        if (d.name.isBlank()) { if (old == null && leaving) CarLink.unlink(this, id); return }
        val car = Car(id, d.name.trim(), d.color, d.plate.trim().ifEmpty { null }, old?.order ?: ((CarStore.cars(this).maxOfOrNull { it.order } ?: -1) + 1))
        if (car == old) return
        if (old == null && CarStore.cars(this).size >= 4) { Toast.makeText(this, "차는 4대까지예요", Toast.LENGTH_SHORT).show(); return }
        synchronized(SyncQueue) { CarStore.upsert(this, car); SyncQueue.add(this, Op.PutCar(car)) }
        SyncQueue.kick(this)
        WidgetRefresh.request(applicationContext)
        publishShortcuts()
    }

    private fun deleteCar(id: String) {
        draft = null
        synchronized(SyncQueue) { SyncQueue.add(this, Op.DelCar(id)) }
        CarStore.remove(this, id)
        ParkStore.clearCar(this, id)
        SyncQueue.kick(this)
        WidgetRefresh.request(applicationContext)
        publishShortcuts()
        reset(Nav.Home); go(Nav.Settings)
    }

    /** BLUETOOTH_CONNECT first, then our list of paired devices by name; the system dialog only confirms the pick. */
    private fun linkBt(carId: String, done: () -> Unit) {
        val run = { btPick = carId to done }
        if (CarLink.hasPermission(this)) run() else { afterBt = run; btAsk.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    }

    @Composable
    private fun BtPicker() {
        val (carId, done) = btPick ?: return
        val list = remember(carId) { CarLink.paired(this) }
        val pick = { address: String? -> btPick = null; CarLink.associate(this, carId, address) { onUi(done) } }
        AlertDialog(
            onDismissRequest = { btPick = null }, containerColor = Wp.SF,
            title = { Text("차 블루투스 고르기", color = Wp.FG) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(androidx.compose.ui.Modifier.heightIn(max = 360.dp)) {
                    if (list.isEmpty()) item { Text("연결된 기기가 없어요. 블루투스를 켜고 차와 한 번 연결해 주세요.", color = Wp.MU) }
                    items(list.size) { i ->
                        val d = list[i]
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().clickable { pick(d.address) }.padding(vertical = 12.dp)) {
                            Text(d.name, color = Wp.FG, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            if (d.car) Text("차량 오디오", color = Wp.MU, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ pick(null) }) { Text("목록에 없어요", color = Wp.MU) } },
            dismissButton = { TextButton({ btPick = null }) { Text("취소", color = Wp.MU) } },
        )
    }

    // settings

    private fun settingsActions() = SettingsActions(
        back = ::back,
        editCar = { go(Nav.Edit(it)) },
        testNotice = {
            val cars = CarStore.cars(this)
            val car = cars.firstOrNull { CarLinkStore.address(this, it.id) != null } ?: cars.firstOrNull()
            when {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> notifAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                car == null -> Toast.makeText(this, "차를 먼저 넣어 주세요", Toast.LENGTH_SHORT).show()
                else -> ExitNotifier.testNotice(this, car.id)
            }
        },
        family = {
            when {
                FamilyStore.joined(this) -> startActivity(Intent(this, FamilyActivity::class.java))
                FamilyStore.pendingGroup(this) != null -> Toast.makeText(this, "인터넷이 연결되면 가족이 만들어져요", Toast.LENGTH_SHORT).show()
                else -> startActivity(Intent(this, JoinActivity::class.java))
            }
        },
        privacy = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppPrefs.PRIVACY_URL))) } },
        copyContact = { copy("contact", AppPrefs.CONTACT); Toast.makeText(this, "메일 주소를 복사했어요", Toast.LENGTH_SHORT).show() },
        bigText = { bigText = it },
        widget = { id -> startActivity(Intent(this, kr.dot.wherepark.widget.WidgetConfigActivity::class.java).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)) },
        deleteAll = ::deleteAll,
    )

    /** 내 데이터 지우기: leave the family on the server first (deleteMe), then wipe this phone. Offline = nothing is wiped. */
    private fun deleteAll() {
        val app = applicationContext
        Thread {
            val ok = !FamilyStore.joined(app) || Api.deleteMe(app).isSuccess
            if (ok) {
                LocalData.wipe(app)
                app.getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts()
                WidgetRefresh.request(app)
            }
            onUi {
                if (!ok) { Toast.makeText(this, "인터넷 연결을 확인해 주세요", Toast.LENGTH_SHORT).show(); return@onUi }
                bigText = false
                joinLaunched = true  // a used invite code is gone with the prefs; do not reopen the join screen
                reset(Nav.Onb(1))
                route()
            }
        }.start()
    }

    private fun copy(label: String, text: String) =
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))

    // onboarding

    @Composable
    private fun Onb(step: Int) {
        when (step) {
            1 -> Step1 {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                if (joinedOnb) { practice = practiceNow(null); go(Nav.Onb(3)) } else { maybeDownloadPack(); go(Nav.Onb(2)) }
            }
            2 -> Step2(carStep, link = { linkBt(carStep.carId) { carStep.linked = CarLinkStore.address(this, carStep.carId) != null } }) {
                val color = Wp.CAR_COLORS.first()
                Onboarding.saveCar(this, carStep.me, Car(carStep.carId, carStep.car.trim(), color, carStep.plate.trim().ifEmpty { null }, 0))
                practice = practiceNow(null)
                go(Nav.Onb(3))
            }
            3 -> Step3(practice, speak = {
                practiceRun.launch(Intent(this, ListenActivity::class.java).putExtra("from", "practice"))
            }) { go(Nav.Onb(4)) }
            else -> Step4(invite = { startActivity(Intent(this, InviteActivity::class.java)) }, later = ::endOnboarding,
                ask = { startActivity(Intent(this, JoinActivity::class.java).putExtra(JoinActivity.ASK, true)) }, pin = {
                pinWidget(this)
                endOnboarding()
            })
        }
    }

    private fun endOnboarding() {
        Onboarding.finish(this)
        reset(Nav.Home)
        tick++
    }

    private fun practiceNow(raw: String?): Practice {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val pack = when {
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this) -> false
            Prefs.sp(this).getString("wp_no_pack", null) != null -> false   // ListenActivity.NO_PACK
            raw != null -> true
            else -> null
        }
        return Practice(mic, pack, raw, raw?.let { SpotParser.parse(it, CarStore.cars(this).map { c -> c.name }).spot })
    }

    /** Step 2 on Wi-Fi: fetch the Korean on-device pack quietly so it already works underground. */
    private fun maybeDownloadPack() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val unmetered = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        if (!unmetered || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val r = rec ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { rec = it }
        r.checkRecognitionSupport(i, mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                val ko = (s.installedOnDeviceLanguages + s.pendingOnDeviceLanguages).any { it.replace('_', '-').lowercase().startsWith("ko") }
                if (!ko) { runCatching { r.triggerModelDownload(i) }; carStep.packDownloading = true }
                else if (s.pendingOnDeviceLanguages.any { it.lowercase().startsWith("ko") }) carStep.packDownloading = true
            }
            override fun onError(error: Int) {}
        })
    }

    /** Long-press shortcuts: static "말로 남기기" (shortcuts.xml) + one "흰 차 어디?" per car -> its detail. */
    private fun publishShortcuts() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        runCatching {
            sm.dynamicShortcuts = CarStore.cars(this).take(maxOf(0, sm.maxShortcutCountPerActivity - 1)).map { c ->
                ShortcutInfo.Builder(this, "car_${c.id}").setShortLabel("${c.name} 어디?")
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_car))
                    .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("car_id", c.id)).build()
            }
        }
    }
}
