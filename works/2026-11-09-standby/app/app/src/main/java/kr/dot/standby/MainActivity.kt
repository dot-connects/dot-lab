package kr.dot.standby

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import java.io.File

/** Plain measurement screen: live values, permission buttons, tests, note, CSV share. */
class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var recent: TextView
    private lateinit var recordButton: Button
    private var fold = "fold=?"

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            addView(root)
        }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)

        root.addView(text("StandBy 측정", 22f, bold = true))
        root.addView(
            text(
                "오늘 밤 할 것 (기록 켠 채로)\n" +
                    "1. 접은 채 충전기에 꽂고 화면이 꺼질 때까지 그냥 두기 → 커버에 시계가 저절로 뜨는지\n" +
                    "2. 티빙에서 아무 영상 10초 재생 → 멈춤\n" +
                    "3. 삼성 시계 앱으로 2분 뒤 알람 → 울리면 알림창의 '해제' 누르기\n" +
                    "4. 방 불 끄기 → 1분 뒤 켜기 (야간 모드 밝기 기준)\n" +
                    "5. 밤새 충전, 아침에 CSV 보내기",
                13f,
            ),
        )
        status = text("", 13f).also(root::addView)

        recordButton = button("") { toggleRecording() }.also(root::addView)

        root.addView(text("권한·설정", 16f, bold = true, top = 20))
        root.addView(button("1. 알림 허용") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        root.addView(button("2. 알림 접근 허용 (알람·음악 감지)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("3. 정확한 알람 허용 (무드등 테스트)") {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        })
        root.addView(button("4. 전체 화면 알림 허용 (무드등 테스트)") {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        })
        root.addView(button("5. 화면보호기 설정 열기 → 'StandBy 측정' 선택") { openDreamSettings() })

        root.addView(text("테스트", 16f, bold = true, top = 20))
        root.addView(button("무드등 15초 뒤 켜기") {
            Toast.makeText(this, GlowTest.schedule(this, 15), Toast.LENGTH_LONG).show()
        })
        val note = EditText(this).apply {
            hint = "메모 (예: 살짝 엶, 완전히 펼침, 알람 울림)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(note)
        root.addView(button("메모 기록") {
            val t = note.text.toString().trim()
            if (t.isNotEmpty()) {
                EventLog.write(this, "note", "$t | hinge=${LogService.currentAngle} | $fold | ${display?.let { Probe.display(this, it) }}")
                note.setText("")
                render()
            }
        })

        root.addView(text("기록", 16f, bold = true, top = 20))
        root.addView(button("CSV 보내기") { share() })
        root.addView(button("기록 지우기") {
            EventLog.clear(this)
            render()
        })
        recent = text("", 11f).also(root::addView)
        showCrash(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    fold = Fold.describe(info.displayFeatures.filterIsInstance<FoldingFeature>())
                    EventLog.write(this@MainActivity, "app_fold", "$fold ${display?.let { Probe.display(this@MainActivity, it) }}")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        EventLog.write(this, "app_config", "w=${c.screenWidthDp}dp h=${c.screenHeightDp}dp ${display?.let { Probe.display(this, it) }}")
    }

    private fun toggleRecording() {
        if (LogService.running) stopService(Intent(this, LogService::class.java))
        else startForegroundService(Intent(this, LogService::class.java))
        handler.postDelayed({ render() }, 300)
    }

    private fun render() {
        val angle = LogService.currentAngle
        val nm = getSystemService(NotificationManager::class.java)
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(this, NotifListener::class.java).flattenToString()) == true
        val dream = runCatching { Settings.Secure.getString(contentResolver, "screensaver_components") }.getOrNull()
        status.text = listOf(
            "기록: " + if (LogService.running) "켜짐" else "꺼짐",
            "힌지 각도: " + if (angle.isNaN()) "기록을 켜면 보임" else "%.1f°".format(angle),
            "이 화면: " + (display?.let { Probe.display(this, it) } ?: "?"),
            "접힘: $fold",
            "배터리: " + Probe.battery(this),
            "알람: " + Probe.nextAlarm(this),
            "권한: 알림=${ok(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)}" +
                " 알림접근=${ok(listener)}" +
                " 정확한알람=${ok(getSystemService(AlarmManager::class.java).canScheduleExactAlarms())}" +
                " 전체화면=${ok(nm.canUseFullScreenIntent())}",
            "화면보호기: " + (dream ?: "읽을 수 없음"),
        ).joinToString("\n")
        recordButton.text = if (LogService.running) "기록 끄기" else "기록 시작"
        recent.text = EventLog.tail(this, 25).joinToString("\n")
    }

    private fun ok(b: Boolean) = if (b) "O" else "X"

    private fun openDreamSettings() {
        val tries = listOf(
            Intent(Settings.ACTION_DREAM_SETTINGS),
            // One UI may hide the screensaver page; this is the AOSP activity behind it
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DreamSettingsActivity")),
        )
        for (i in tries) {
            if (runCatching { startActivity(i) }.isSuccess) {
                EventLog.write(this, "dream_settings", "opened ${i.action ?: i.component}")
                return
            }
        }
        EventLog.write(this, "dream_settings", "no screen saver settings page")
        Toast.makeText(this, "화면보호기 설정을 열 수 없습니다", Toast.LENGTH_LONG).show()
    }

    private fun share() {
        val f = EventLog.file(this)
        if (!f.exists()) {
            Toast.makeText(this, "아직 기록이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "kr.dot.standby.probe.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "측정 기록 보내기"))
    }

    private fun showCrash(root: LinearLayout) {
        val f = File(filesDir, "crash.log")
        if (!f.exists()) return
        root.addView(text("지난번 앱 오류", 16f, bold = true, top = 20))
        root.addView(text(f.readText().take(800), 11f))
        root.addView(button("오류 지우기") { f.delete() })
    }

    private fun text(s: String, size: Float, bold: Boolean = false, top: Int = 6) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(if (bold) Color.WHITE else Color.rgb(200, 200, 200))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(top), 0, dp(4))
        setTextIsSelectable(!bold)
    }

    private fun button(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener(onClick)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
