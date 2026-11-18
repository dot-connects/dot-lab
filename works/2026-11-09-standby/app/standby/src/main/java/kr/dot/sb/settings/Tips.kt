package kr.dot.sb.settings

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.clockStyle

/** The motion a tip's little drawing loops (plan-r15 F10). */
enum class Gesture { PULL_DOWN, UP_DOWN, HOLD, LEFT, UP, PRESS_KEY, TENT }

enum class TipGroup(val ko: String) { SCREEN("Stand Screen 화면"), CARD("카드"), FOLD("폴드") }

/**
 * plan-r15 F10 (mockup tips-mock): the hidden gestures. [title] + [sub] in 설정 › 팁; [pill] = the one-time line on the
 * Stand Screen ([Tips.pick] decides when); [line] = the footer under the related setting, the gesture in **bold**.
 * Stored by name in `AppConfig.tipsSeen` (renaming one shows it again once).
 */
enum class Tip(val group: TipGroup, val title: String, val sub: String, val gesture: Gesture, val pill: String?, val line: String) {
    PULL_DOWN(TipGroup.SCREEN, "위에서 끌어내리기", "테마·밝기·음량을 바로 바꿔요.", Gesture.PULL_DOWN, "↓ 위에서 끌어내리면 테마·밝기", "쓰는 중에는 화면을 **위에서 끌어내려** 바로 바꿀 수 있어요."),
    CLOCK_SWIPE(TipGroup.SCREEN, "시계를 위아래로 넘기기", "쓰는 시계가 차례로 바뀌어요.", Gesture.UP_DOWN, "↕ 위아래로 넘기면 다음 시계", "시계 화면을 **위아래로 넘기면** 다음 시계가 나와요."),
    CLOCK_HOLD(TipGroup.SCREEN, "시계를 길게 누르기", "그 자리에서 바로 꾸며요.", Gesture.HOLD, "길게 누르면 바로 꾸미기", "시계를 **길게 누르면** 그 자리에서 꾸밀 수 있어요."),
    PHOTOS_SWIPE(TipGroup.SCREEN, "사진을 위아래로 넘기기", "다른 폴더 사진으로 바뀌어요.", Gesture.UP_DOWN, "↕ 위아래로 넘기면 다른 폴더", "사진 화면을 **위아래로 넘기면** 다른 폴더 사진이 나와요."),
    BOARD_HOLD(TipGroup.SCREEN, "페이지를 길게 누르기", "그 페이지 설정이 열려요.", Gesture.HOLD, "길게 누르면 이 페이지 설정", "페이지를 **길게 누르면** 이 화면이 바로 열려요."),
    BANNER_UP(TipGroup.SCREEN, "알림을 위로 밀기", "새 알림이 바로 사라져요.", Gesture.UP, null, "새 알림을 **위로 밀면** 바로 사라져요."),
    SIDE_KEY(TipGroup.SCREEN, "옆 버튼 누르기", "화면이 꺼지고, 다음에 켜면 잠금 화면이 나와요.", Gesture.PRESS_KEY, null, "**옆 버튼**을 누르면 꺼지고, 다음에 켜면 잠금 화면이 나와요."),
    TIMER_HOLD(TipGroup.CARD, "타이머 길게 누르기", "집중과 휴식이 바뀌어요.", Gesture.HOLD, "길게 누르면 집중 ↔ 휴식", "Stand Screen에서 타이머 카드를 **길게 누르면** 집중과 휴식이 바뀌어요."),
    NOTIF_SWIPE(TipGroup.CARD, "알림 밀어서 지우기", "왼쪽으로 밀면 폰에서도 지워져요.", Gesture.LEFT, "← 밀면 폰에서도 지워져요", "알림 카드의 알림을 **왼쪽으로 밀면** 폰에서도 지워져요."),
    STACK_SWIPE(TipGroup.CARD, "겹친 카드 넘기기", "한 칸에 여러 카드를 쌓아 둘 수 있어요.", Gesture.UP_DOWN, "↕ 위아래로 넘기면 다음 카드", "카드가 여러 장인 칸은 **위아래로 넘겨** 봐요."),
    CLOCK_CARD(TipGroup.CARD, "시계 카드 넘기기", "위아래로 넘기면 다른 시계로 바뀌어요.", Gesture.UP_DOWN, "↕ 넘기면 다른 시계", "시계 카드를 **위아래로 넘기면** 쓰는 시계 중 다른 시계로 바뀌어요."),
    TENT(TipGroup.FOLD, "텐트로 세워 무드등", "90° 넘게 펼친 뒤 좁혀서 세워요.", Gesture.TENT, "↓ 무드등 밝기·색", "텐트일 때는 바깥 화면을 **위에서 끌어내려** 밝기·색을 바꿔요."),
    ;

    /** The Stand Screen page [Tips.demo] shows behind the gesture in 설정 › 팁. */
    val demoCard: CardType? get() = when (this) { TIMER_HOLD -> CardType.TIMER; NOTIF_SWIPE -> CardType.NOTIF; CLOCK_CARD -> CardType.CLOCK; else -> null }
}

/** What the Stand Screen shows right now, for [Tips.pick]. [page] = [Pages] index; [cards] = the cards on a board page. */
data class TipContext(val page: Int, val cards: List<List<CardType>> = emptyList(), val tent: Boolean = false)

object Tips {
    /**
     * plan-r15 F10 ①: the one tip to show now, or null. One per session (the caller asks once); a seen tip never
     * again. The very first Stand Screen gets the pull-down nudge; later sessions the page's own gesture.
     */
    fun pick(c: AppConfig, at: TipContext): Tip? {
        fun Tip.fresh() = name !in c.tipsSeen
        // the outer screen of a tent has one thing to learn
        if (at.tent) return Tip.TENT.takeIf { it.fresh() }
        if (Tip.PULL_DOWN.fresh()) return Tip.PULL_DOWN
        val cards = at.cards.flatten()
        val candidates = when {
            at.page == Pages.CLOCK -> listOfNotNull(Tip.CLOCK_SWIPE.takeIf { c.rotateClocks && c.myClocks.size > 1 }, Tip.CLOCK_HOLD)
            at.page == 1 -> listOfNotNull(Tip.PHOTOS_SWIPE.takeIf { c.photoFolders.size > 1 })
            else -> listOfNotNull(
                Tip.TIMER_HOLD.takeIf { CardType.TIMER in cards },
                Tip.NOTIF_SWIPE.takeIf { CardType.NOTIF in cards },
                Tip.STACK_SWIPE.takeIf { at.cards.any { it.size > 1 } },
                Tip.CLOCK_CARD.takeIf { CardType.CLOCK in cards && c.myClocks.size > 1 },
                Tip.BOARD_HOLD,
            )
        }
        return candidates.firstOrNull { it.fresh() && it.pill != null }
    }
}

/** A small looping drawing of [g]: a dot that pulls, swipes or presses (mockup tips-mock: no finger, one moving dot). */
@Composable
fun GestureGlyph(g: Gesture, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    // a delay loop, not an infinite transition: render tests wait for idle and an endless animation never is (shots draw t = 0)
    var t by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val life = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(g, life) {
        var k = 0
        life.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { while (true) { kotlinx.coroutines.delay(40); k = (k + 1) % 40; t = k / 40f } }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val k = (t * 1.25f).coerceAtMost(1f) // a short rest at the end of each loop
        val dot = 0.09f * s
        val faint = color.copy(alpha = 0.35f)
        fun track(a: Offset, b: Offset) = drawLine(faint, a, b, 0.05f * s, StrokeCap.Round)
        when (g) {
            Gesture.PULL_DOWN, Gesture.TENT -> {
                drawLine(color, Offset(0.35f * s, 0.12f * s), Offset(0.65f * s, 0.12f * s), 0.06f * s, StrokeCap.Round)
                track(Offset(0.5f * s, 0.2f * s), Offset(0.5f * s, 0.85f * s))
                drawCircle(color, dot, Offset(0.5f * s, (0.2f + 0.6f * k) * s))
            }
            Gesture.UP_DOWN -> {
                track(Offset(0.5f * s, 0.15f * s), Offset(0.5f * s, 0.85f * s))
                val y = if (k < 0.5f) 0.75f - 1.1f * k else 0.2f + 1.1f * (k - 0.5f)
                drawCircle(color, dot, Offset(0.5f * s, y * s))
            }
            Gesture.UP -> {
                track(Offset(0.5f * s, 0.15f * s), Offset(0.5f * s, 0.85f * s))
                drawCircle(color, dot, Offset(0.5f * s, (0.8f - 0.6f * k) * s))
            }
            Gesture.LEFT -> {
                track(Offset(0.15f * s, 0.5f * s), Offset(0.85f * s, 0.5f * s))
                drawCircle(color, dot, Offset((0.8f - 0.6f * k) * s, 0.5f * s))
            }
            Gesture.HOLD -> {
                drawCircle(color, dot, Offset(0.5f * s, 0.5f * s))
                drawCircle(color.copy(alpha = 1f - k), (0.12f + 0.3f * k) * s, Offset(0.5f * s, 0.5f * s), style = Stroke(0.05f * s))
            }
            Gesture.PRESS_KEY -> {
                drawRoundRect(faint, Offset(0.3f * s, 0.12f * s), androidx.compose.ui.geometry.Size(0.4f * s, 0.76f * s), androidx.compose.ui.geometry.CornerRadius(0.08f * s), style = Stroke(0.05f * s))
                drawLine(color, Offset(0.76f * s, 0.32f * s), Offset(0.76f * s, 0.48f * s), 0.06f * s, StrokeCap.Round)
                drawCircle(color.copy(alpha = if (k in 0.2f..0.6f) 1f else 0.2f), dot, Offset(0.88f * s, 0.4f * s))
            }
        }
    }
}

/**
 * 설정 › 팁 (plan-r15 F10 ②): grouped by Stand Screen 화면 / 카드 / 폴드, a small looping gesture per row, "새" until
 * seen; a tap plays it over the preview (the page it belongs to) and marks it seen.
 */
@Composable
fun TipsScreen(config: AppConfig, update: Update, back: () -> Unit) {
    var demo by rememberSaveable { mutableStateOf(Tip.PULL_DOWN.name) }
    val tip = Tip.valueOf(demo)
    val fold = LocalSettingsEnv.current.phone == Phone.FOLD
    val acc = LocalAccent.current
    val face = kr.dot.sb.ui.clock.ClockRegistry.pages(config.myClocks).first()
    val slide = Slide(tip.title) { l ->
        val card = tip.demoCard
        when {
            card != null -> BoardView(kr.dot.sb.model.Board("tip", "", kr.dot.sb.model.Layout.ONE, listOf(listOf(card))), l.state, config, StandbyActions.NONE, Modifier.fillMaxSize())
            tip == Tip.PHOTOS_SWIPE -> pageSlides(config, include = 1).first { it.name == "사진" }.draw(this, l)
            tip == Tip.BOARD_HOLD || tip == Tip.STACK_SWIPE -> BoardView(widgetsBoard(config), l.state, config, StandbyActions.NONE, Modifier.fillMaxSize())
            tip == Tip.TENT -> Box(Modifier.fillMaxSize().background(Color(config.mood.color)))
            else -> kr.dot.sb.ui.clock.ClockFace(face, config.clockStyle(face), l.state, Modifier.fillMaxSize(), analog = config.analogStyle)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), Alignment.Center) { GestureGlyph(tip.gesture, Color.White, size = 160.dp) } // literal-ok: demo veil
    }
    Page("팁", back, preview = Preview(config, listOf(slide), full = false)) {
        TipGroup.entries.filter { it != TipGroup.FOLD || fold }.forEach { g ->
            Section(g.ko) {
                Tip.entries.filter { it.group == g }.forEach { t ->
                    item {
                        val new = t.name !in config.tipsSeen
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                                demo = t.name
                                if (new) update { it.copy(tipsSeen = it.tipsSeen + t.name) }
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (t == tip) acc.copy(alpha = 0.16f) else SK.surface2), Alignment.Center) {
                                GestureGlyph(t.gesture, acc, size = 32.dp)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                T(t.title, 16.sp, 500, SK.text, maxLines = 1)
                                T(t.sub, 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), lineHeight = 18.sp)
                            }
                            if (new) Pill("새", acc, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
