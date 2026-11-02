package kr.dot.parkingfloor

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private companion object {
        const val END_LOCK_MS = 2 * 60 * 1000L
        const val CONFIRM_MS = 3000L
    }

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            ticker.postDelayed(this, 1000)
        }
    }
    private lateinit var startCard: LinearLayout
    private lateinit var runCard: LinearLayout
    private lateinit var sessionList: LinearLayout
    private lateinit var crashBox: LinearLayout
    private lateinit var placeInput: EditText
    private lateinit var carMode: RadioButton
    private lateinit var floorPick: Spinner
    private lateinit var startButton: Button
    private lateinit var estimateText: TextView
    private lateinit var statsText: TextView
    private lateinit var markText: TextView
    private lateinit var parkButton: Button
    private lateinit var endButton: Button
    private var hasBarometer = false
    private var starting = false
    private var parkPending = false
    private var endArmedAt = 0L
    private var listedSessions: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasBarometer = getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFFFFFFF.toInt()) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root, MATCH_PARENT, WRAP_CONTENT)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            val side = dp(16)
            view.setPadding(side + bars.left, dp(12) + bars.top, side + bars.right, dp(12) + bars.bottom)
            insets
        }
        crashBox = column().also(root::addView)
        startCard = buildStartCard().also(root::addView)
        runCard = buildRunCard().also(root::addView)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        showCrash()
        ticker.post(tick)
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        super.onPause()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun text(value: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(0xFF17191C.toInt())
        setPadding(0, dp(4), 0, dp(4))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun buildStartCard(): LinearLayout {
        val card = column()
        card.addView(text("주차층", 26f, true))
        if (!hasBarometer) card.addView(text("기압계 없음", 18f, true))
        card.addView(text("장소"))
        placeInput = EditText(this).apply { hint = "건물 이름"; setSingleLine() }
        card.addView(placeInput)
        val walk = RadioButton(this).apply { text = "도보"; id = View.generateViewId(); isChecked = true }
        carMode = RadioButton(this).apply { text = "차"; id = View.generateViewId() }
        card.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(walk)
            addView(carMode)
        })
        card.addView(text("지금 층"))
        floorPick = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, FloorMath.FLOORS.map(FloorMath::labelOf),
            )
            setSelection(FloorMath.FLOORS.indexOf(0))
        }
        card.addView(floorPick)
        startButton = button("여기서 시작") { start() }.apply { isEnabled = hasBarometer }
        card.addView(startButton)
        card.addView(button("배터리 설정 열기") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        })
        card.addView(text("지난 기록", 17f, true))
        sessionList = column().also(card::addView)
        return card
    }

    private fun buildRunCard(): LinearLayout {
        val card = column()
        estimateText = text("측정 중", 64f, true).apply { gravity = Gravity.CENTER }
        statsText = text("")
        markText = text("", 16f, true)
        card.addView(estimateText)
        card.addView(statsText)
        card.addView(markText)
        val grid = GridLayout(this).apply { columnCount = 5 }
        FloorMath.FLOORS.forEach { index ->
            val cell = button(FloorMath.labelOf(index)) { tapFloor(index) }
            cell.minWidth = 0
            cell.minimumWidth = 0
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply { width = 0; height = WRAP_CONTENT })
        }
        card.addView(grid, MATCH_PARENT, WRAP_CONTENT)
        parkButton = button("주차함") {
            parkPending = true
            markText.text = "주차한 층을 누르세요"
        }
        card.addView(parkButton)
        card.addView(button("기준 다시") { MeasureService.current?.rebase(); render() })
        card.addView(button("마지막 취소") { MeasureService.current?.undo(); render() })
        endButton = button("끝내기") { end() }
        card.addView(endButton)
        return card
    }

    private fun tapFloor(index: Int) {
        val session = MeasureService.current ?: return
        session.mark(index, parkPending)
        parkPending = false
        render()
    }

    private fun end() {
        val session = MeasureService.current ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - endArmedAt > CONFIRM_MS) {
            endArmedAt = now
            render()
            return
        }
        endArmedAt = 0
        session.finish()
        render()
    }

    private fun start() {
        if (starting || MeasureService.current != null) return
        val name = placeInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "장소를 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }
        starting = true
        startButton.isEnabled = false
        val wanted = mutableListOf(Manifest.permission.POST_NOTIFICATIONS)
        if (carMode.isChecked) {
            wanted += Manifest.permission.ACCESS_FINE_LOCATION
            wanted += Manifest.permission.ACCESS_COARSE_LOCATION
            wanted += Manifest.permission.BLUETOOTH_CONNECT
        }
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) launch() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        launch()
    }

    private fun launch() {
        startForegroundService(
            Intent(this, MeasureService::class.java)
                .putExtra(MeasureService.EXTRA_PLACE, placeInput.text.toString().trim())
                .putExtra(MeasureService.EXTRA_CAR, carMode.isChecked)
                .putExtra(MeasureService.EXTRA_FLOOR, FloorMath.FLOORS[floorPick.selectedItemPosition]),
        )
        ticker.postDelayed({ starting = false; render() }, 2000)
    }

    private fun render() {
        val session = MeasureService.current
        startCard.visibility = if (session == null) View.VISIBLE else View.GONE
        runCard.visibility = if (session == null) View.GONE else View.VISIBLE
        if (session == null) {
            parkPending = false
            startButton.isEnabled = hasBarometer && !starting
            renderSessions()
            return
        }
        estimateText.text = session.lastEstimate?.let(FloorMath::labelOf) ?: "측정 중"
        val delta = session.lastDeltaM?.let { "%.1f m".format(it) } ?: "-"
        val hpa = session.lastHpa?.let { "%.2f hPa".format(it) } ?: "-"
        val lines = mutableListOf(
            "${session.place} · 기준 ${FloorMath.labelOf(session.baseIndex)}",
            "높이 $delta · $hpa · 샘플 ${session.samples}",
        )
        if (session.car) {
            val fix = session.lastFix
            lines += when {
                !session.gpsActive -> "GPS 없음"
                fix == null -> "GPS 찾는 중"
                else -> "GPS %.0f m · %d초 전".format(
                    fix.accuracy, (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000_000L,
                )
            }
            lines += "블루투스 기록 ${session.btEvents}건"
        }
        statsText.text = lines.joinToString("\n")
        if (!parkPending) markText.text = session.lastMark.orEmpty()
        parkButton.visibility = if (session.car) View.VISIBLE else View.GONE
        val lockLeft = if (session.car) session.lastParkMs?.let { END_LOCK_MS - (SystemClock.elapsedRealtime() - it) } else null
        val armed = SystemClock.elapsedRealtime() - endArmedAt <= CONFIRM_MS
        endButton.isEnabled = lockLeft == null || lockLeft <= 0
        endButton.text = when {
            lockLeft != null && lockLeft > 0 -> "끝내기 (${lockLeft / 1000 + 1}초 후)"
            armed -> "한 번 더 누르면 끝"
            else -> "끝내기"
        }
    }

    private fun sessionFiles(): List<File> =
        filesDir.listFiles { file -> file.name.startsWith("session-") }.orEmpty().sortedByDescending { it.name }

    private fun renderSessions() {
        val files = sessionFiles()
        val names = files.map { "${it.name}:${it.length()}" }
        if (names == listedSessions) return
        listedSessions = names
        sessionList.removeAllViews()
        if (files.isEmpty()) sessionList.addView(text("없음"))
        files.forEach { file ->
            sessionList.addView(button("보내기 · ${file.name.removePrefix("session-").removeSuffix(".csv")} · ${file.length() / 1024} KB") { share(file) })
        }
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "kr.dot.parkingfloor.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "기록 보내기"))
    }

    private fun showCrash() {
        crashBox.removeAllViews()
        val file = File(filesDir, "crash.log")
        if (!file.exists()) return
        val log = file.readText()
        crashBox.addView(text("지난번 앱 오류", 16f, true))
        crashBox.addView(text(log.take(600), 12f))
        crashBox.addView(button("오류 복사") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("crash", log))
        })
        crashBox.addView(button("오류 지우기") {
            file.delete()
            crashBox.removeAllViews()
        })
    }
}
