package kr.dot.sb.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.clock.spec
import kr.dot.sb.ui.theme.Look
import kr.dot.sb.ui.theme.StripPick
import kr.dot.sb.ui.theme.clockStyle
import kr.dot.sb.ui.theme.switchLook
import kr.dot.sb.ui.theme.withTheme
import kr.dot.sb.ui.theme.withThemeEdit

/**
 * plan-r15 F3 first run (mockup r15 "처음 켤 때 4단계"): 시계 → 분위기 → 자동으로 켜기 (the [Perm.CHARGE_START] bundle,
 * one screen, asked in order) → 준비됐어요 / 지금 보기. Every other grant is asked where its feature is. The step is
 * saved (rotation, fold); [SettingsEnv.onFirstRunDone] ends it for good.
 */
@Composable
fun FirstRunScreen(config: AppConfig, update: Update, env: SettingsEnv) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("시계를 골라 주세요", "분위기를 골라 주세요", "충전하면 자동으로 켜지게", "준비됐어요")
    val state = remember(config) { Sample.state(config) }
    val face = config.myClocks.firstOrNull() ?: FaceId.DIGITAL
    val preview = when (step) {
        0 -> Preview(config, listOf(Slide(spec(face).ko) { l -> ClockFace(face, config.clockStyle(face), l.state, Modifier.fillMaxSize(), analog = config.analogStyle) }))
        1, 3 -> Preview(config, pageSlides(config), initial = Pages.start(config))
        else -> null
    }
    Page(titles[step], if (step > 0) ({ step -= 1 }) else null, preview = preview) {
        T("${step + 1} / 4", 13.sp, 600, LocalAccent.current, Modifier.padding(start = 4.dp, top = 8.dp))
        when (step) {
            0 -> {
                T("누르면 바로 바뀌어요. 나중에 시계에서 언제든 바꿀 수 있어요.", 14.sp, 400, SK.muted, Modifier.padding(start = 4.dp, top = 6.dp, bottom = 12.dp), lineHeight = 20.sp)
                MAIN_FACES.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { f -> GalleryCard(f, f == face, config, state, Modifier.weight(1f)) { update { it.copy(myClocks = MyClocks.use(it.myClocks, f)) } } }
                    }
                }
            }
            1 -> {
                T("테마는 나중에 테마에서 바꾸거나 직접 만들 수 있어요.", 14.sp, 400, SK.muted, Modifier.padding(start = 4.dp, top = 6.dp, bottom = 12.dp), lineHeight = 20.sp)
                val board = remember(config.widgetLeft, config.widgetRight) { widgetsBoard(config) }
                val pick = StripPick.of(config)
                Theme.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { th ->
                            val cfg = remember(config, th) { config.withTheme(th).withThemeEdit(th, null) }
                            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                ThemeThumb(cfg, board, state, th.ko, pick == Look.Builtin(th), LocalAccent.current, { update { it.switchLook(Look.Builtin(th)) } }, wide = true)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            2 -> ChargeStep(env)
            else -> T("충전기에 꽂고 옆으로 눕혀 보세요.", 15.sp, 500, SK.text, Modifier.padding(start = 4.dp, top = 6.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val missing = Perm.CHARGE_START.any { env.granted[it] != true }
            when (step) {
                2 -> {
                    if (missing) ActionButton("켜러 가기", { env.onPermWalkOf(Perm.CHARGE_START) }, Modifier.fillMaxWidth())
                    ActionButton(if (missing) "나중에" else "다음", { step += 1 }, Modifier.fillMaxWidth(), primary = !missing)
                }
                3 -> {
                    ActionButton("지금 보기", { env.onFirstRunDone(); env.onShowNow() }, Modifier.fillMaxWidth())
                    ActionButton("설정 둘러보기", env.onFirstRunDone, Modifier.fillMaxWidth(), primary = false)
                }
                else -> ActionButton("다음", { step += 1 }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Step 3: where to tap (the screen saver list with Stand Screen picked) and the bundle as a checklist. */
@Composable
private fun ChargeStep(env: SettingsEnv) {
    T("다음 화면에서 Stand Screen을 골라 주세요.", 15.sp, 500, SK.text, Modifier.padding(start = 4.dp, top = 6.dp))
    // the system list drawn small, our row ticked (mockup step 3: "누를 곳을 그림으로")
    Group(Modifier.padding(top = 12.dp)) {
        listOf("시계", "사진 테이블", "Stand Screen", "색상").forEach { n ->
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(n, 15.sp, if (n == "Stand Screen") 700 else 400, if (n == "Stand Screen") SK.text else SK.muted, Modifier.weight(1f))
                    if (n == "Stand Screen") Icon(Ic.CHECK, LocalAccent.current, 20.dp)
                }
            }
        }
    }
    Section("필요한 것") {
        Perm.CHARGE_START.forEach { p ->
            item {
                val ok = env.granted[p] == true
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ok) Icon(Ic.CHECK, SK.ok, 18.dp) else androidx.compose.foundation.layout.Box(Modifier.padding(4.dp).size(10.dp).clip(CircleShape).background(SK.danger))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        T(p.ko, 15.sp, 600, SK.text)
                        T(p.why, 12.sp, 400, SK.muted, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
