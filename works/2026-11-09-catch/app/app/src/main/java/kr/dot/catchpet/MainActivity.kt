package kr.dot.catchpet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences(Pets.PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        col.addView(TextView(this).apply { text = "우리집 몬스터"; textSize = 24f })
        col.addView(TextView(this).apply { text = "웹 도감에서 내보낸 .dotpet 파일을 열어 녀석을 데려오세요." })
        col.addView(Button(this).apply { text = "펫 파일 열기"; setOnClickListener { openFile() } })
        col.addView(Button(this).apply {
            text = "내리기"
            setOnClickListener { stopService(Intent(this@MainActivity, PetService::class.java)); status.text = "내렸어요." }
        })
        status = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        col.addView(status)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun openFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        @Suppress("DEPRECATION") startActivityForResult(i, REQ_OPEN)
    }

    @Deprecated("plain Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_OPEN || resultCode != RESULT_OK || uri == null) return
        status.text = "읽는 중…"
        Thread {
            val msg = try {
                val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                val r = PetFile.parse(text)
                val saved = r.creatures.count { Pets.save(this, it) }
                val skipped = r.droppedPoses + r.droppedCreatures + (r.creatures.size - saved)
                "${saved}마리 데려왔어요." + if (skipped > 0) " (못 읽은 모습·녀석 ${skipped}개는 뺐어요)" else ""
            } catch (e: Exception) {
                "펫 파일(.dotpet)이 아니에요."
            }
            runOnUiThread { status.text = msg; refresh() }
        }.start()
    }

    private fun refresh() {
        listBox.removeAllViews()
        val pets = Pets.list(this)
        if (pets.isEmpty()) {
            listBox.addView(TextView(this).apply { text = "아직 데려온 녀석이 없어요." })
            return
        }
        val current = prefs.getString(Pets.KEY_PET, null)
        for (p in pets) {
            val moves = PetFile.moves(p.labels).joinToString(" · ") { MOVE_KO[it] ?: it }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 24, 0, 24) }
            row.addView(TextView(this).apply {
                textSize = 18f
                text = (if (p.id == current) "★ " else "") + "No.%03d %s".format(p.no, p.name)
            })
            row.addView(TextView(this).apply {
                text = "${KIND_KO[p.kind] ?: p.kind} · ${PetFile.count(p.labels, p.kind)}\n행동: $moves"
            })
            val go = Button(this).apply { text = "띄우기"; visibility = View.GONE; setOnClickListener { float(p) } }
            row.addView(go)
            row.setOnClickListener { go.visibility = if (go.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            listBox.addView(row)
        }
    }

    private fun float(p: Pets.Info) {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "'다른 앱 위에 표시'를 허용한 뒤 다시 [띄우기]를 눌러 주세요."
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
        prefs.edit().putString(Pets.KEY_PET, p.id).apply()
        startForegroundService(Intent(this, PetService::class.java))
        status.text = "${p.name} 띄웠어요."
        refresh()
    }

    companion object {
        private const val REQ_OPEN = 1
        private const val REQ_NOTIF = 2
        private val KIND_KO = mapOf("dog" to "강아지", "cat" to "고양이", "person" to "사람", "pokemon" to "포켓몬")
        private val MOVE_KO = mapOf(
            "bounce" to "통통", "flinch" to "움찔", "walk" to "걷기",
            "run" to "달리기", "sleep" to "잠자기", "hide" to "숨기",
        )
    }
}
