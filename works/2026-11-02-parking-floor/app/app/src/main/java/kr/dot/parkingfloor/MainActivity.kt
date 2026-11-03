package kr.dot.parkingfloor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.health.SystemHealthManager
import android.os.health.UidHealthStats
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home: the current park, setup (permissions, car), widget panel, history, and what the app cost. */
class MainActivity : Activity() {
    private companion object {
        const val REQ_PERMS = 1
        const val REQ_BACKGROUND = 2
        const val REQ_ASSOC = 3
        const val INK = 0xFF16191C.toInt()
        const val MUTE = 0xFF4F565D.toInt()
    }

    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF4F4F1.toInt()) }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, MATCH_PARENT, WRAP_CONTENT)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(dp(18) + bars.left, dp(14) + bars.top, dp(18) + bars.right, dp(18) + bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
        if (Family.joined(this)) Family.pull(this) { if (!isFinishing) render() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 15f, bold: Boolean = false, color: Int = INK) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, dp(3), 0, dp(3))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else INK)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            if (primary) setColor(INK) else { setColor(0x00000000); setStroke(dp(1), INK) }
        }
        minHeight = dp(48)
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, view ->
            addView(view, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    private fun section(title: String) {
        body.addView(text(title, 17f, true).apply { setPadding(0, dp(22), 0, dp(4)) })
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun render() {
        body.removeAllViews()
        body.addView(text("주차층", 26f, true))
        renderPark()
        renderFamily()
        renderSetup()
        renderWidget()
        renderHistory()
        renderCost()
        val log = DriveLog.recent(this, 8)
        if (log.isNotEmpty()) {
            section("최근 동작")
            log.forEach { body.addView(text(it, 12f, color = MUTE)) }
        }
        section("기타")
        body.addView(button("기록 못 한 주차 직접 넣기") {
            startActivity(Intent(this, FloorPickActivity::class.java).putExtra(FloorPickActivity.EXTRA_NEW, true))
        })
        body.addView(button("시험 기록 (층 측정 연구용)") { startActivity(Intent(this, TestActivity::class.java)) })
    }

    private fun renderPark() {
        val park = ParkStore.latest(this)
        if (park == null) {
            body.addView(text("아직 주차 기록이 없어요. 아래에서 차를 연결하면 블루투스가 끊길 때 자동으로 기록해요.", 15f, color = MUTE))
            return
        }
        val label = park.floor?.let(FloorMath::labelOf) ?: "?"
        body.addView(text(
            (park.placeName ?: if (park.lat == null) "장소 모름" else "장소 찾는 중") + " · " +
                ParkWidget.who(park) + (if (park.confirmed) "" else " (${ParkWidget.ago(park.atMs)})"),
            14f, color = MUTE,
        ).apply { setPadding(0, dp(10), 0, 0) })
        body.addView(text(if (park.confirmed) "확정" else if (park.floor == null) "층 모름" else "추정 · 확인해 주세요", 13f, true).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(if (park.confirmed) 0xFF3FBF7F.toInt() else PillarMark.HAZARD_YELLOW) }
            setPadding(dp(10), dp(2), dp(10), dp(2))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(6) })
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.BOTTOM }
        line.addView(ImageView(this).apply {
            setImageBitmap(PillarMark.render(this@MainActivity, label, !park.confirmed, park.color.takeIf { it != 0 }, INK, dp(110)))
        })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, dp(8)) }
        if (park.pillar.isNotEmpty()) info.addView(text(park.pillar, 26f, true))
        info.addView(text(if (park.confirmed) "엘리베이터에서 $label" else park.alt?.let { "아니면 ${FloorMath.labelOf(it)}" } ?: "", 16f, true))
        line.addView(info)
        body.addView(line)
        if (!park.confirmed) {
            val buttons = mutableListOf<View>()
            park.est?.let { est -> buttons += button("확정", true) { ParkStore.confirm(this, park.id, est); ParkWidget.refresh(this); Family.changed(this); render() } }
            buttons += button("직접 입력") { startActivity(Intent(this, FloorPickActivity::class.java)) }
            park.alt?.let { alt -> buttons += button(FloorMath.labelOf(alt)) { ParkStore.confirm(this, park.id, alt); ParkWidget.refresh(this); Family.changed(this); render() } }
            body.addView(row(*buttons.toTypedArray()))
        } else {
            body.addView(row(
                button("기둥 사진") { startActivity(Intent(this, PillarActivity::class.java)) },
                button("위치 입력") { startActivity(Intent(this, PillarActivity::class.java).putExtra(PillarActivity.EXTRA_TYPE, true)) },
                button("공유", true) { startActivity(ParkWidget.share(this, park)) },
            ))
        }
    }

    private var leaveArmedAt = 0L

    /** Family sharing: create or join with a code; once joined, members, invite and leave. */
    private fun renderFamily() {
        section("가족")
        if (!Family.joined(this)) {
            body.addView(text("같은 차를 쓰는 가족이 이 앱을 깔면, 누가 주차하든 모두의 위젯에 떠요. 층·기둥·건물 이름만 공유하고 위치 좌표는 보내지 않아요.", 14f, color = MUTE))
            val name = android.widget.EditText(this).apply { id = 4001; hint = "내 이름 (예: 엄마)"; setSingleLine() }
            body.addView(name)
            body.addView(button("가족 만들기", true) {
                val n = name.text.toString().trim()
                if (n.isEmpty()) return@button Toast.makeText(this, "이름을 먼저 적어 주세요", Toast.LENGTH_SHORT).show()
                Family.create(this, n) { err -> err?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }; render() }
            })
            val code = android.widget.EditText(this).apply { id = 4002; hint = "받은 초대 코드 (XXXX-XXXX-XXXX)"; setSingleLine() }
            body.addView(code)
            body.addView(button("코드로 합류") {
                val n = name.text.toString().trim()
                if (n.isEmpty()) return@button Toast.makeText(this, "이름을 먼저 적어 주세요", Toast.LENGTH_SHORT).show()
                Family.join(this, code.text.toString(), n) { err -> err?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }; render() }
            })
            return
        }
        val members = Family.members(this)
        body.addView(text(if (members.isEmpty()) "가족: ${Family.myName(this)} (나)" else "가족: " + members.joinToString(", ") { if (it.me) "${it.name} (나)" else it.name }, 15f, true))
        body.addView(text(
            if (PushSetup.configured) "가족이 주차하면 바로 알림이 오고 위젯이 바뀌어요."
            else "알림 설정 전이라 앱을 열 때 가족 주차를 받아 와요.",
            13f, color = MUTE,
        ))
        body.addView(row(
            button("초대 코드 보내기", true) {
                Family.invite(this) { code, err ->
                    if (code == null) return@invite Toast.makeText(this, err ?: "실패", Toast.LENGTH_LONG).show()
                    val msg = "주차층 가족 초대 코드: $code\n앱 → 가족 → 코드로 합류 (24시간 안에 한 번만 쓸 수 있어요)"
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "초대 코드 보내기"))
                }
            },
            button(if (System.currentTimeMillis() - leaveArmedAt < 3000) "한 번 더 누르면 나감" else "가족에서 나가기") {
                if (System.currentTimeMillis() - leaveArmedAt > 3000) {
                    leaveArmedAt = System.currentTimeMillis()
                    render()
                } else Family.leave(this) { render() }
            },
        ))
    }

    private fun renderSetup() {
        section("설정")
        val basics = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = basics.filterNot(::granted)
        if (missing.isNotEmpty()) {
            body.addView(text("블루투스·알림·위치 권한이 필요해요. 위치는 건물 이름을 찾는 데만 써요.", 14f, color = MUTE))
            body.addView(button("권한 허용", true) { requestPermissions(missing.toTypedArray(), REQ_PERMS) })
            return
        }
        if (!granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            body.addView(text("차에서 내릴 때 앱이 꺼져 있어도 건물 이름을 남기려면 위치를 '항상 허용'으로 바꿔 주세요.", 14f, color = MUTE))
            body.addView(button("위치 항상 허용") { requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND) })
        }
        val car = Settings.carName(this)
        if (car != null) {
            body.addView(text("연결된 차: $car", 15f, true))
            body.addView(text("차 블루투스가 연결되면 깨어나고, 끊기면 주차를 기록해요.", 13f, color = MUTE))
            body.addView(button("다른 차로 바꾸기") { showCars() })
        } else {
            body.addView(text("차 블루투스를 골라 주세요. 한 번만 하면 돼요.", 14f, color = MUTE))
            showCarList()
        }
    }

    /** Drops the old car's association so only one car wakes the app. */
    private fun showCars() {
        val cdm = getSystemService(CompanionDeviceManager::class.java)
        cdm.myAssociations.forEach { info ->
            try {
                @Suppress("DEPRECATION")
                info.deviceMacAddress?.toString()?.let { cdm.stopObservingDevicePresence(it) }
                cdm.disassociate(info.id)
            } catch (_: Exception) {
            }
        }
        Settings.clearCar(this)
        render()
    }

    @SuppressLint("MissingPermission")
    private fun showCarList() {
        val devices = getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
        if (devices.isEmpty()) {
            body.addView(text("페어링된 블루투스 기기가 없어요. 폰 설정에서 차와 먼저 연결해 주세요.", 14f, color = MUTE))
            return
        }
        devices.sortedBy { it.name ?: it.address }.forEach { device ->
            body.addView(button(device.name ?: device.address) { associate(device) })
        }
    }

    @SuppressLint("MissingPermission")
    private fun associate(device: BluetoothDevice) {
        val cdm = getSystemService(CompanionDeviceManager::class.java)
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build())
            .setSingleDevice(true)
            .build()
        cdm.associate(request, mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                try {
                    startIntentSenderForResult(intentSender, REQ_ASSOC, null, 0, 0, 0)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "연결 창을 열지 못했어요", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                Settings.setCar(this@MainActivity, device.address, device.name ?: device.address)
                try {
                    @Suppress("DEPRECATION")
                    cdm.startObservingDevicePresence(device.address)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "차 감지 등록 실패: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
                render()
            }

            override fun onFailure(error: CharSequence?) {
                Toast.makeText(this@MainActivity, "연결 안 됨: ${error ?: ""}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun renderWidget() {
        section("위젯")
        body.addView(text("밝은 판 불투명도. 0%면 완전 투명, 45%부터 글자가 검정으로 바뀌어요.", 14f, color = MUTE))
        val value = text("${Settings.panel(this)}%", 15f, true)
        body.addView(value)
        body.addView(SeekBar(this).apply {
            id = 3001
            max = 100
            progress = Settings.panel(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    val stepped = progress / 5 * 5
                    value.text = "$stepped%"
                    if (fromUser) {
                        Settings.setPanel(this@MainActivity, stepped)
                        ParkWidget.refresh(this@MainActivity)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        })
        body.addView(text("판이 45% 미만일 때 글자색", 14f, color = MUTE))
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf(Settings.INK_AUTO to "배경 따라", Settings.INK_LIGHT to "흰색", Settings.INK_DARK to "검정").forEachIndexed { i, (mode, label) ->
            group.addView(RadioButton(this).apply {
                id = 3100 + i
                text = label
                isChecked = Settings.ink(this@MainActivity) == mode
                setOnClickListener {
                    Settings.setInk(this@MainActivity, mode)
                    ParkWidget.refresh(this@MainActivity)
                }
            })
        }
        body.addView(group)
    }

    private fun renderHistory() {
        val parks = ParkStore.load(this).parks.reversed().drop(1).take(8)
        if (parks.isEmpty()) return
        section("지난 주차")
        val fmt = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
        parks.forEach { p ->
            val floor = p.floor?.let(FloorMath::labelOf) ?: "?"
            body.addView(text("${fmt.format(Date(p.atMs))} · ${p.placeName ?: "장소 모름"} · $floor ${p.pillar}${if (p.confirmed) "" else " (추정)"}", 14f))
        }
    }

    private fun renderCost() {
        section("이 앱이 쓴 것 (지난 충전 이후)")
        try {
            val stats = getSystemService(SystemHealthManager::class.java).takeMyUidSnapshot()
            fun measure(key: Int) = if (stats.hasMeasurement(key)) stats.getMeasurement(key) else 0L
            fun timers(key: Int) = if (stats.hasTimers(key)) stats.getTimers(key).values.sumOf { it.time } else 0L
            val cpuMs = measure(UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS) + measure(UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS)
            val sensorMs = timers(UidHealthStats.TIMERS_SENSORS)
            val wakeMs = timers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL)
            val gpsMs = if (stats.hasTimer(UidHealthStats.TIMER_GPS_SENSOR)) stats.getTimer(UidHealthStats.TIMER_GPS_SENSOR).time else 0L
            body.addView(text("센서 ${minutes(sensorMs)} · CPU ${"%.1f".format(cpuMs / 1000.0)}초 · 깨운 시간 ${minutes(wakeMs)} · GPS ${gpsMs / 1000}초", 14f))
            val (_, wakeUp) = DriveService.barometer(getSystemService(android.hardware.SensorManager::class.java))
            body.addView(text(if (wakeUp) "기압계가 스스로 깨우는 방식이라 운전 중에도 폰이 잘 수 있어요." else "이 폰 기압계는 스스로 못 깨워서 운전 중에만 폰을 깨워 둬요.", 13f, color = MUTE))
        } catch (_: Exception) {
            body.addView(text("이 폰에서는 사용량을 읽을 수 없어요.", 14f, color = MUTE))
        }
    }

    private fun minutes(ms: Long): String = if (ms < 60_000) "${ms / 1000}초" else "${ms / 60_000}분"

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        render()
    }

    @Deprecated("plain Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        render()
    }
}
