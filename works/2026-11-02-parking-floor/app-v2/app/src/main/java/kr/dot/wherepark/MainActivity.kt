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
import kr.dot.wherepark.ui.iAmDriver
import kr.dot.wherepark.family.GroupActivity
import kr.dot.wherepark.family.AddGroupActivity
import kr.dot.wherepark.core.isMine
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.SpotParser
import kr.dot.wherepark.core.newCarId
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.ApiException
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.GroupStore
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
import kr.dot.wherepark.ui.StepCar
import kr.dot.wherepark.ui.StepGroups
import kr.dot.wherepark.ui.StepPractice
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
        val edit = i?.getStringExtra("edit_car")
        i?.removeExtra("edit_car")
        if (edit != null && top !is Nav.Onb && CarStore.cars(this).any { it.id == edit }) return reset(Nav.Home).also { go(Nav.Edit(edit)) }
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
                if (top !is Nav.Onb) reset(Nav.Onb(1))
                if (r.joined && (top as Nav.Onb).step == 2) reset(Nav.Onb(3))  // a group made or joined on the group step moves on to the car
            }
        }
    }

    /** Pull when the app opens and (re)register the push token; with no token the 15-minute pull takes over. */
    override fun onResume() {
        super.onResume()
        route()
        tick++
        if (top is Nav.Onb && (top as Nav.Onb).step == 4) practice = practiceNow(practice.raw)
        val app = applicationContext
        Thread {
            Push.start(app)
            // a group accepted my join request while I was away: ask on the join screen (never join blindly)
            if (!requestShown && JoinRequest.poll(app) is JoinRequest.Poll.Code) onUi {
                requestShown = true
                startActivity(Intent(this, JoinActivity::class.java))
            }
            // a merged pull refreshes widgets itself; otherwise refresh once here (re-reads the wallpaper colours, no broadcast for wallpaper changes)
            if (GroupStore.joined(app) && SyncEngine.pull(app)) onUi { tick++ } else WidgetRefresh.request(app)
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
            fixMic = { listen(null) }, addGroup = { startActivity(Intent(this, AddGroupActivity::class.java)) },
        ))
    }

    private fun listen(carId: String?, mode: String? = null) {
        startActivity(Intent(this, ListenActivity::class.java).putExtra("from", "app").also { i ->
            carId?.let { i.putExtra("car_id", it) }; mode?.let { i.putExtra("mode", it) }
        })
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
        ), park?.iAmDriver(GroupStore.me(this)?.id) ?: true)
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
            ParkStore.save(this, p.copy(state = ParkState.DRIVING, drivingBy = GroupStore.me(this)?.name))
            SyncQueue.add(this, Op.State(p.carId, ParkState.DRIVING, parkId = p.id))
        }
        SyncQueue.kick(this)
        WidgetRefresh.request(applicationContext)
        tick++
    }

    // car edit

    @Composable
    private fun Edit(n: Nav.Edit) {
        val cars = remember(n, tick) { CarStore.cars(this) }
        val d = draft?.takeIf { it.first == n }?.second ?: run {
            val car = cars.firstOrNull { it.id == n.carId }
                ?: Car(newCarId(), "", Wp.CAR_COLORS.first { c -> cars.none { it.color == c } }, null, (cars.maxOfOrNull { it.order } ?: -1) + 1)
            CarDraft(car).also { draft = n to it; draftLinkId = car.id }
        }
        val id = draftLinkId!!
        val car = cars.firstOrNull { it.id == id } ?: Car(id, d.name, d.color, null, 0)
        CarEditScreen(d, car, n.carId == null, tick >= 0 && CarLinkStore.address(this, id) != null, EditActions(
            share = { g, on -> share(id, g, on) },
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
        if (old != null && !old.isMine(GroupStore.me(this)?.id)) return  // someone else's car: only its owner edits it
        if (d.name.isBlank()) { if (old == null && leaving) CarLink.unlink(this, id); return }
        val car = (old ?: Car(id, "", 0, null, (CarStore.cars(this).maxOfOrNull { it.order } ?: -1) + 1))
            .copy(name = d.name.trim(), color = d.color, plate = d.plate.trim().ifEmpty { null })
        if (car == old) return
        if (old == null && CarStore.mine(this).size >= 4) { Toast.makeText(this, "차는 4대까지예요", Toast.LENGTH_SHORT).show(); return }
        if (old == null) CarStore.addMine(this, car)
        else { synchronized(SyncQueue) { CarStore.upsert(this, car); SyncQueue.add(this, Op.PutCar(car)) }; SyncQueue.kick(this) }
        WidgetRefresh.request(applicationContext)
        publishShortcuts()
    }

    /** 이 차가 보이는 그룹 switch: local car.groups now, `Share` op to the server. */
    private fun share(carId: String, groupId: String, on: Boolean) {
        synchronized(SyncQueue) {
            val c = CarStore.cars(this).firstOrNull { it.id == carId } ?: return
            CarStore.upsert(this, c.copy(groups = if (on) (c.groups + groupId).distinct() else c.groups - groupId))
            SyncQueue.add(this, Op.Share(carId, groupId, on))
        }
        SyncQueue.kick(this)
        WidgetRefresh.request(applicationContext)
        tick++
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
        group = { id -> startActivity(Intent(this, GroupActivity::class.java).putExtra("groupId", id)) },
        addGroup = { startActivity(Intent(this, AddGroupActivity::class.java)) },
        privacy = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppPrefs.PRIVACY_URL))) } },
        bigText = { bigText = it },
        widget = { id -> startActivity(Intent(this, kr.dot.wherepark.widget.WidgetConfigActivity::class.java).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)) },
        deleteAll = ::deleteAll,
    )

    /** 내 데이터 지우기: leave every group on the server (deleteMe, best effort), then wipe this phone. */
    private fun deleteAll() {
        val app = applicationContext
        Thread {
            // the server must forget this member first: wiping the phone offline would orphan its server data (T-0203).
            // 401 = the server already forgot it, so the wipe goes ahead.
            if (GroupStore.joined(app)) {
                val r = runCatching { Api.deleteMe(app).getOrThrow() }
                if (r.isFailure && (r.exceptionOrNull() as? ApiException)?.status != 401) {
                    onUi { Toast.makeText(this, "서버에 닿지 않아 지우지 못했어요. 인터넷을 켜고 다시 해 주세요", Toast.LENGTH_LONG).show() }
                    return@Thread
                }
            }
            LocalData.wipe(app)
            app.getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts()
            WidgetRefresh.request(app)
            onUi {
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
                maybeDownloadPack(); go(Nav.Onb(2))
            }
            2 -> StepGroups(joined = tick >= 0 && GroupStore.joined(this),
                create = { startActivity(Intent(this, AddGroupActivity::class.java)) },
                join = { startActivity(Intent(this, JoinActivity::class.java)) },
                ask = { startActivity(Intent(this, JoinActivity::class.java).putExtra(JoinActivity.ASK, true)) }) { go(Nav.Onb(3)) }
            3 -> StepCar(carStep, link = { linkBt(carStep.carId) { carStep.linked = CarLinkStore.address(this, carStep.carId) != null } },
                skip = {
                    val app = applicationContext; val id = carStep.carId  // a Bluetooth link made for a car that is never saved would linger
                    if (CarLinkStore.address(app, id) != null) Thread { kr.dot.wherepark.car.CarLink.unlink(app, id) }.start()
                    practice = practiceNow(null); go(Nav.Onb(4))
                }) {
                Onboarding.saveCar(this, Car(carStep.carId, carStep.car.trim(), Wp.CAR_COLORS.first(), carStep.plate.trim().ifEmpty { null }, 0))
                practice = practiceNow(null)
                go(Nav.Onb(4))
            }
            else -> StepPractice(practice, speak = {
                practiceRun.launch(Intent(this, ListenActivity::class.java).putExtra("from", "practice"))
            }, pin = { pinWidget(this); endOnboarding() }) { endOnboarding() }
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
