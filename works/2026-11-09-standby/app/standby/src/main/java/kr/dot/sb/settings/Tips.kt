package kr.dot.sb.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType

/** Static pictograms shared by the tips list and the one-time pill. */
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
    TIMER_HOLD(TipGroup.CARD, "타이머 가운데 길게 누르기", "집중과 휴식이 바뀌어요.", Gesture.HOLD, "길게 누르면 집중 ↔ 휴식", "Stand Screen에서 타이머 카드를 **길게 누르면** 집중과 휴식이 바뀌어요."),
    NOTIF_SWIPE(TipGroup.CARD, "알림 밀어서 지우기", "'밀어서 지우기'를 누른 뒤 왼쪽으로 밀면 폰에서도 지워져요.", Gesture.LEFT, "← 밀면 폰에서도 지워져요", "알림 카드의 알림을 **왼쪽으로 밀면** 폰에서도 지워져요."),
    STACK_SWIPE(TipGroup.CARD, "겹친 카드 넘기기", "한 칸에 여러 카드를 쌓아 둘 수 있어요.", Gesture.UP_DOWN, "↕ 위아래로 넘기면 다음 카드", "카드가 여러 장인 칸은 **위아래로 넘겨** 봐요."),
    CLOCK_CARD(TipGroup.CARD, "시계 카드 넘기기", "누르면 나오는 양옆 화살표로 다른 시계로 바꿔요.", Gesture.LEFT, "누르고 ‹ › 로 다른 시계", "시계 카드를 **누르면 양옆 화살표**가 나와요. 화살표로 쓰는 시계 중 다른 시계로 바꿔요."),
    TENT(TipGroup.FOLD, "텐트로 세워 무드등", "90° 넘게 펼친 뒤 좁혀서 세워요.", Gesture.TENT, "↓ 무드등 밝기·색", "텐트일 때는 바깥 화면을 **위에서 끌어내려** 밝기·색을 바꿔요."),
    ;

    /** The single-card board used by the inline demo. */
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
            at.page == Pages.CLOCK -> listOfNotNull(Tip.CLOCK_SWIPE.takeIf { c.myClocks.size > 1 }, Tip.CLOCK_HOLD)
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

/** Clear gesture shapes; animation belongs to the expanded scene, never the list. */
@Composable
fun GestureGlyph(g: Gesture, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 26f
        fun point(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, point(x, y), point(xx, yy), 2.2f * s, StrokeCap.Round)
        fun up(y: Float) { line(9f, y + 4, 13f, y); line(13f, y, 17f, y + 4) }
        fun down(y: Float) { line(9f, y - 4, 13f, y); line(13f, y, 17f, y - 4) }
        when (g) {
            Gesture.PULL_DOWN -> { line(6f, 4f, 20f, 4f); line(13f, 8f, 13f, 20f); down(20f) }
            Gesture.UP_DOWN -> {
                line(13f, 3.5f, 13f, 22.5f); up(3.5f); down(22.5f)
                drawCircle(color, 2.6f * s, point(13f, 13f))
            }
            Gesture.HOLD -> {
                drawCircle(color, 3.2f * s, point(13f, 13f))
                drawCircle(color, 7f * s, point(13f, 13f), style = Stroke(2.2f * s))
                drawCircle(color, 10.6f * s, point(13f, 13f), style = Stroke(1.6f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2f * s, 3.4f * s))))
            }
            Gesture.LEFT -> {
                line(4f, 13f, 21f, 13f); line(4f, 13f, 8f, 9f); line(4f, 13f, 8f, 17f)
                drawCircle(color, 2.6f * s, point(21f, 13f))
            }
            Gesture.UP -> { line(6f, 22f, 20f, 22f); line(13f, 18f, 13f, 5f); up(5f) }
            Gesture.PRESS_KEY -> {
                drawRoundRect(color.copy(alpha = 0.5f), point(6f, 3f), androidx.compose.ui.geometry.Size(12f * s, 20f * s), androidx.compose.ui.geometry.CornerRadius(3f * s), style = Stroke(1.8f * s))
                line(21f, 8f, 21f, 13f)
            }
            Gesture.TENT -> {
                line(3f, 21f, 13f, 5f); line(13f, 5f, 23f, 21f)
                line(3f, 21f, 7f, 21f); line(19f, 21f, 23f, 21f)
                line(13f, 9f, 20f, 21f)
            }
        }
    }
}

/** One expanded demo, directly below its row; seen state remains independent of expansion. */
@Composable
fun TipsScreen(config: AppConfig, update: Update, back: () -> Unit) {
    var demo by rememberSaveable { mutableStateOf<String?>(null) }
    val fold = LocalSettingsEnv.current.phone == Phone.FOLD
    val acc = LocalAccent.current
    Page("팁", back) {
        val first = listOf(Tip.STACK_SWIPE, Tip.CLOCK_CARD, Tip.PULL_DOWN, Tip.PHOTOS_SWIPE, Tip.CLOCK_SWIPE, Tip.TIMER_HOLD)
        val order = (first + Tip.entries.filter { it !in first }).filter { it.group != TipGroup.FOLD || fold }
        run {
            Section(null) {
                order.forEach { t ->
                    item {
                        val new = t.name !in config.tipsSeen
                        val open = demo == t.name
                        Column {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                    .background(if (open) SK.surface2 else Color.Transparent)
                                    .semantics { stateDescription = if (open) "펼침" else "접힘" }
                                    .clickable {
                                        demo = if (open) null else t.name
                                        if (new) update { it.copy(tipsSeen = it.tipsSeen + t.name) }
                                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (open) acc else SK.surface2), Alignment.Center) {
                                    GestureGlyph(t.gesture, if (open) SK.bg else acc, size = 32.dp)
                                }
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    T(t.title, 16.sp, 500, SK.text, maxLines = 1)
                                    T(t.sub, 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), lineHeight = 18.sp)
                                }
                                if (new) Pill("새", acc, Modifier.padding(start = 8.dp))
                            }
                            AnimatedVisibility(open, enter = expandVertically(tween(250)), exit = shrinkVertically(tween(250))) {
                                TipDemo(t, config, active = open, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
