package kr.dot.sb.data

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.CardType
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.PAGE_PHOTOS
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.PAGE_WIDGETS
import kr.dot.sb.model.NightMode
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.NotifItem
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.state.TimerState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure logic of the platform layer: no Android types, so JVM tests run it. */

/**
 * A two-way switch that flips only after its condition held for a while (night lux, flat/upright pose, pick-up).
 * Light sensors report on change, so a steady value sends no event: callers wake up at [pendingMs] and call update again.
 */
/**
 * Trailing debounce with a max wait (plan-r11 B1): [request] at [now] gives when to fire — [waitMs] after the latest
 * request, but never later than [maxWaitMs] after the first one of the burst. [fired] starts a new burst.
 */
class Debounce(private val waitMs: Long, private val maxWaitMs: Long) {
    private var firstAt: Long? = null
    fun request(now: Long): Long {
        val first = firstAt ?: now.also { firstAt = it }
        return minOf(now + waitMs, first + maxWaitMs)
    }
    fun fired() { firstAt = null }
}

/** plan-r11 B4: what the agenda/calendar cards show at [now] from the last calendar query. */
object Agenda {
    /** (today's agenda, at most 5; everything upcoming): items that ended before [now] are gone. */
    fun visible(all: List<kr.dot.sb.ui.state.AgendaItem>, now: java.time.LocalDateTime): Pair<List<kr.dot.sb.ui.state.AgendaItem>, List<kr.dot.sb.ui.state.AgendaItem>> {
        val left = all.filter { it.end == null || it.end.isAfter(now) }
        return left.filter { it.date == now.toLocalDate() }.take(5) to left
    }
}

class HoldSwitch(private val onMs: Long, private val offMs: Long) {
    var on = false
        private set
    private var since = -1L

    /** [toOn] true while the "turn on" condition holds (only read while off), [toOff] likewise while on. */
    fun update(nowMs: Long, toOn: Boolean, toOff: Boolean): Boolean {
        val signal = if (on) toOff else toOn
        if (!signal) { since = -1L; return on }
        if (since < 0) since = nowMs
        if (nowMs - since >= (if (on) offMs else onMs)) { on = !on; since = -1L }
        return on
    }

    /** ms until a running hold completes, or null when no hold is running. */
    fun pendingMs(nowMs: Long): Long? =
        if (since < 0) null else ((if (on) offMs else onMs) - (nowMs - since)).coerceAtLeast(0)

    fun force(value: Boolean) { on = value; since = -1L }
}

/** Night mode from lux: enter <= 3 for 5 s, exit >= 8 for 1.5 s (spec N1; thresholds come from AppConfig). */
class NightSwitch(var enterLux: Float = 3f, var exitLux: Float = 8f) {
    private val sw = HoldSwitch(5_000, 1_500)
    val night get() = sw.on
    fun update(nowMs: Long, lux: Float): Boolean = sw.update(nowMs, lux <= enterLux, lux >= exitLux)
    fun pendingMs(nowMs: Long) = sw.pendingMs(nowMs)
    fun prime(on: Boolean) = sw.force(on)
}

object NightRamp {
    const val IN_MS = 1000L   // to red (design-system 2.2 / spec N3)
    const val OUT_MS = 600L   // back to normal

    /** Linear fraction towards [target] after [elapsedMs]; a full 0..1 swing takes IN_MS up, OUT_MS down. */
    fun fraction(from: Float, target: Float, elapsedMs: Long): Float {
        val up = target > from
        val step = elapsedMs.toFloat() / (if (up) IN_MS else OUT_MS)
        return if (up) minOf(target, from + step) else maxOf(target, from - step)
    }

    /** SCHEDULE mode: night between [start] and [end] hours, wrapping past midnight. */
    fun scheduledNight(hour: Int, start: Int = 22, end: Int = 7): Boolean =
        if (start <= end) hour in start until end else hour >= start || hour < end
}

/** Median of the (at most 3) light readings of the last second: one phone-screen flash cannot reset a night hold (spec N1). */
class LuxMedian {
    private val times = LongArray(3)
    private val vals = FloatArray(3)
    private var n = 0

    fun add(nowMs: Long, lux: Float): Float {
        var k = 0
        for (i in 0 until n) if (nowMs - times[i] <= 1_000) { times[k] = times[i]; vals[k] = vals[i]; k++ }
        if (k == 3) { times[0] = times[1]; vals[0] = vals[1]; times[1] = times[2]; vals[1] = vals[2]; k = 2 }
        times[k] = nowMs; vals[k] = lux; n = k + 1
        return vals.copyOf(n).sorted()[n / 2]
    }

    /**
     * plan-r16 T6: the median of the readings at most 1 s old at [nowMs], else the newest reading. A sensor that went
     * quiet (steady light) must not leave a middle value of an old burst in place. Null = no reading yet.
     */
    fun value(nowMs: Long): Float? {
        if (n == 0) return null
        val fresh = (0 until n).filter { nowMs - times[it] <= 1_000 }.map { vals[it] }
        return if (fresh.isEmpty()) vals[n - 1] else fresh.sorted()[fresh.size / 2]
    }
}

/** plan-r16 T3 (operator Q1): the window keeps the screen on only while StandBy is lit, and never for a 지금 보기 preview. */
fun keepScreenOn(off: Boolean, showNow: Boolean): Boolean = !off && !showNow

/**
 * plan-r16 T4 (operator Q1): what a StandbyActivity does when it stops. Any screen-off ends the session (our own off
 * and the side key alike), so the next wake is the phone's lock screen; covered by something over the lock screen
 * (an alarm, a call, the dream stub, our mood light) keeps it; unlocked and gone (home, an app) ends it.
 */
enum class StopRule {
    KEEP, FINISH;

    companion object {
        fun onStop(interactive: Boolean, keyguardLocked: Boolean, glowCovering: Boolean, changingConfig: Boolean): StopRule = when {
            changingConfig || glowCovering -> KEEP
            !interactive -> FINISH
            keyguardLocked -> KEEP
            else -> FINISH
        }
    }
}

/** Night look from the settings (spec E12: 안 함 = the display never changes, so no night mode at all). */
object NightRules {
    /**
     * [forceNight] = the drawn theme is light (r10): a light page at night is a lamp, so night applies even under
     * mode OFF (then the AUTO lux rule decides) or display-off NEVER. Stored settings stay as they are.
     */
    fun on(mode: NightMode, display: DisplayOff, scheduled: Boolean, auto: Boolean, forceNight: Boolean = false): Boolean =
        (forceNight || display != DisplayOff.NEVER) && when (mode) {
            NightMode.OFF -> forceNight && auto
            NightMode.SCHEDULE -> scheduled
            NightMode.AUTO -> auto
        }

    /**
     * A ringing alarm, a shown notification or the mood light keeps the screen on, whatever the idle/dark/flat
     * decision says (spec I1). A sleeping machine (mood light done) is not "wanted" here: its caller drops [mood] first.
     */
    fun displayOff(wanted: Boolean, alarm: Boolean, notification: Boolean, mood: Boolean = false) =
        wanted && !alarm && !notification && !mood

    /** Quiet night (r10): at night a notification never lights a screen that is off; the alarm always does. */
    fun wakeOnNotification(night: Boolean, quiet: Boolean, displayOff: Boolean) = !(night && quiet && displayOff)
}

/** Heat protection (r10): hot from 42.0 °C, cool again at 40.0 °C; battery temperature in tenths of a degree. */
object HeatRule {
    fun next(tenths: Int, hot: Boolean): Boolean = if (hot) tenths > 400 else tenths >= 420
}

sealed interface MoodPhase {
    data object On : MoodPhase
    /** Last minute: [alpha] 1 → 0. */
    data class Fade(val alpha: Float) : MoodPhase
    data object Done : MoodPhase
}

/** Mood light auto-off (r10): full light, then a 60 s fade, then off. [minutes] 0 = never off. */
object MoodTimer {
    const val FADE_MS = 60_000L

    fun phase(elapsedMs: Long, minutes: Int): MoodPhase {
        if (minutes <= 0) return MoodPhase.On
        val left = minutes * 60_000L - elapsedMs
        return when {
            left <= 0 -> MoodPhase.Done
            left <= FADE_MS -> MoodPhase.Fade(left.toFloat() / FADE_MS)
            else -> MoodPhase.On
        }
    }
}

/**
 * plan-r15 E8 mood light effects at [tMs] since start: the flat colour (ARGB) and a level 0..1 for it. [Mood.speed]
 * 0..1 = 천천히..빠르게. 색 바뀜 eases through [kr.dot.sb.model.MOOD_COLORS] from the picked colour; 숨쉬기 dims slowly
 * to 55 % and back; 촛불 flickers a little (three detuned sines, no randomness, so it never jumps).
 */
object MoodFx {
    /** Frame step while an effect runs: slow changes need no 60 fps on a big panel. */
    const val FRAME_MS = 50L

    fun at(m: kr.dot.sb.model.Mood, tMs: Long): Pair<Long, Float> {
        val t = tMs / 1000.0
        val sp = m.speed.coerceIn(0f, 1f).toDouble()
        return when (m.effect) {
            kr.dot.sb.model.MoodEffect.STILL -> m.color to 1f
            kr.dot.sb.model.MoodEffect.CYCLE -> {
                val pal = kr.dot.sb.model.MOOD_COLORS.map { it.second }
                val start = pal.indexOf(m.color).coerceAtLeast(0)
                val per = 24.0 - 20.0 * sp // seconds per colour
                val x = t / per
                val i = x.toLong()
                val f = (x - i).toFloat().let { it * it * (3 - 2 * it) } // smoothstep
                lerpArgb(pal[((start + i) % pal.size).toInt()], pal[((start + i + 1) % pal.size).toInt()], f) to 1f
            }
            kr.dot.sb.model.MoodEffect.BREATHE -> {
                val per = 12.0 - 9.0 * sp
                m.color to (0.55 + 0.45 * (0.5 + 0.5 * kotlin.math.cos(2 * Math.PI * t / per))).toFloat()
            }
            kr.dot.sb.model.MoodEffect.CANDLE -> {
                val w = 0.6 + 2.4 * sp
                val n = 0.06 * kotlin.math.sin(t * 2.3 * w) + 0.04 * kotlin.math.sin(t * 5.1 * w + 1.3) + 0.03 * kotlin.math.sin(t * 8.7 * w + 2.1)
                m.color to (0.87 + n).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    private fun lerpArgb(a: Long, b: Long, f: Float): Long {
        fun ch(sh: Int) = (((a shr sh) and 0xFF) + (((b shr sh) and 0xFF) - ((a shr sh) and 0xFF)) * f).toLong().coerceIn(0, 255) shl sh
        return 0xFF000000L or ch(16) or ch(8) or ch(0)
    }
}

/**
 * The unfold mood light ended by its timer: no host shows another one until the phone is folded again (any host that
 * sees the fold resets it) or the charger goes in again. Process-wide on purpose: dream, activity and GlowActivity share it.
 */
object MoodLatch {
    @Volatile var done = false
}

/** Live Activity times (r10): when each step was first seen, and an ETA from a "N분" value. */
object LiveTimes {
    private val ETA = Regex("^(\\d{1,3})\\s*분")

    fun etaMinutes(value: String): Int? = ETA.find(value.trim())?.groupValues?.get(1)?.toInt()

    /** Steps the progress has reached (what the live screen lights up). */
    fun reached(progress: Float?, steps: Int): Int =
        progress?.let { p -> (p.coerceIn(0f, 1f) * steps).roundToInt().coerceAtLeast(if (p > 0f) 1 else 0) } ?: 0

    /** [next] with [prev]'s times carried over, each newly reached step stamped [nowMs]; the ETA moves only when the value does. */
    fun stamp(prev: LiveActivity?, next: LiveActivity, nowMs: Long): LiveActivity {
        val r = reached(next.progress, next.steps)
        val times = List(next.steps) { i -> prev?.stepTimes?.getOrNull(i) ?: if (i < r) nowMs else null }
        val eta = if (prev != null && prev.value == next.value) prev.etaAt else etaMinutes(next.value)?.let { nowMs + it * 60_000L }
        return next.copy(stepTimes = times, etaAt = eta)
    }
}

/** ON/OFF decision of the display (spec E10-E14). */
class DisplayOffMachine {
    var off = false
        private set
    /** Sticky off (mood light done): every mode keeps it, NEVER and the AUTO lux wake included; only [touch] clears it. */
    var asleep = false
        private set
    private var last = 0L
    /** Q4: the bright-room ceiling counts from the last user touch only (a notification wake must not reset it). */
    private var userLast = 0L
    /** Went off in the dark (AUTO): light coming back wakes it; the bright-room ceiling does not. */
    private var darkOff = false

    fun touch(nowMs: Long, user: Boolean = true) { last = nowMs; if (user) userLast = nowMs; off = false; asleep = false }

    fun sleep() { off = true; asleep = true }

    /** Last touch time (a notification that woke the screen only sends it back to OFF when nothing touched since). */
    val lastTouch get() = last

    fun forceOff() { off = true }

    fun evaluate(nowMs: Long, mode: DisplayOff, lux: Float?, enterLux: Float, exitLux: Float): Boolean {
        if (asleep) return true
        when (mode) {
            DisplayOff.NEVER -> off = false
            DisplayOff.AFTER_20S -> if (!off && nowMs - last >= IDLE_MS) off = true
            // plan-r16 T6: no sensor or no reading = the 20 s rule; Q4: a bright room still goes off after BRIGHT_IDLE_MS
            DisplayOff.AUTO ->
                if (off) { if (darkOff && lux != null && lux >= exitLux) touch(nowMs) }
                else if (nowMs - last >= IDLE_MS && (lux == null || lux <= enterLux)) { off = true; darkOff = true }
                else if (nowMs - userLast >= BRIGHT_IDLE_MS) { off = true; darkOff = false }
        }
        return off
    }

    /** ms until the next idle limit of [mode] while on, else null (AUTO past 20 s: the bright-room ceiling). */
    fun idleLeftMs(nowMs: Long, mode: DisplayOff = DisplayOff.AFTER_20S): Long? {
        if (off) return null
        val idle = nowMs - last
        if (mode == DisplayOff.AUTO && idle >= IDLE_MS) return (BRIGHT_IDLE_MS - (nowMs - userLast)).coerceAtLeast(0)
        return (IDLE_MS - idle).coerceAtLeast(0)
    }

    companion object {
        const val IDLE_MS = 20_000L
        /** plan-r16 Q4: 방이 어두우면 in a bright room goes off after 10 min without a touch. */
        const val BRIGHT_IDLE_MS = 600_000L
    }
}

/**
 * Pixel shift of the content layer (r10): ±8 dp over a 17 × 17 grid, a snake walked one 1 dp step per minute and
 * back again (ping-pong), so neighbouring minutes never differ by more than 1 dp. Step = minutes since the epoch:
 * no jump at midnight.
 */
object BurnIn {
    const val MAX = 8
    private const val N = 2 * MAX + 1
    private const val LAST = N * N - 1

    fun at(epochMinute: Long): Pair<Int, Int> {
        val i = epochMinute.mod(2L * LAST).toInt()
        val k = if (i > LAST) 2 * LAST - i else i
        val row = k / N
        val col = if (row % 2 == 0) k % N else N - 1 - k % N
        return (col - MAX) to (row - MAX)
    }
}

/** Accelerometer: gravity low-pass, linear acceleration for pick-up (2.5 for 300 ms), pose (spec E2/E7). */
class MotionDetector {
    var gz = 0f
        private set
    var linear = 0f
        private set
    private var gx = 0f
    private var gy = 0f
    private var init = false
    private val pickup = HoldSwitch(300, 0)
    private val flat = HoldSwitch(1_000, 1_000)

    val isFlat get() = flat.on
    val pickedUp get() = pickup.on

    fun update(nowMs: Long, x: Float, y: Float, z: Float) {
        if (!init) { gx = x; gy = y; gz = z; init = true }
        gx = A * gx + (1 - A) * x; gy = A * gy + (1 - A) * y; gz = A * gz + (1 - A) * z
        // magnitude, not axis: turning the phone moves gravity between axes but keeps |a| = g (E4 rotation must not read as pick-up)
        linear = abs(sqrt(x * x + y * y + z * z) - G)
        pickup.update(nowMs, linear > PICKUP, linear <= PICKUP)
        flat.update(nowMs, abs(gz) > 9.0f, abs(gz) < 8.0f)
    }

    companion object { const val G = 9.81f; const val A = 0.9f; const val PICKUP = 2.5f }
}

/** Content rotation from gravity (dreams cannot request orientation): 600 ms debounce, 30 deg hysteresis (spec E4). */
class RotationTracker {
    var degrees = 0
        private set
    private var cand = -1
    private var candSince = 0L

    /** Returns true when [degrees] changed. Sign convention: x > 0 = device's right side up = content turns +90 (clockwise). */
    fun update(nowMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (abs(z) > 8.0f) { cand = -1; return false } // lying flat: keep the last rotation
        val theta = (Math.toDegrees(atan2(x.toDouble(), y.toDouble())) + 360.0) % 360.0
        val target = ((theta / 90.0).roundToInt() * 90) % 360
        val d = abs(((theta - degrees + 540.0) % 360.0) - 180.0)
        if (target == degrees || d < 60.0) { cand = -1; return false }
        if (cand != target) { cand = target; candSince = nowMs }
        if (nowMs - candSince >= 600) { degrees = target; cand = -1; return true }
        return false
    }
}

/** Pomodoro per slot: focus/break, wall-clock based so display-off costs nothing. */
class PomodoroTimer(private var focusSec: Int, private var breakSec: Int) {
    var mode = TimerMode.FOCUS
        private set
    var running = false
        private set
    private var leftMsAtStart = focusSec * 1000L
    private var startedAt = 0L

    private fun total() = if (mode == TimerMode.FOCUS) focusSec else breakSec

    fun leftMs(nowMs: Long): Long = if (running) (leftMsAtStart - (nowMs - startedAt)).coerceAtLeast(0) else leftMsAtStart

    fun toggle(nowMs: Long) {
        if (running) { leftMsAtStart = leftMs(nowMs); running = false } else {
            if (leftMsAtStart <= 0) leftMsAtStart = total() * 1000L
            startedAt = nowMs; running = true
        }
    }

    fun reset() { mode = TimerMode.FOCUS; leftMsAtStart = focusSec * 1000L; running = false }

    /** Settings lengths: taken only while stopped at full length; a running or paused session keeps its own total. */
    fun lengths(focus: Int, brk: Int) {
        if (running || leftMsAtStart != total() * 1000L) return // != : a stopped break dragged to its own minutes keeps them
        focusSec = focus; breakSec = brk; leftMsAtStart = total() * 1000L
    }

    /**
     * Ring drag (11-14 #10): stopped at full length in focus = a new focus length; paused (or a stopped break) = the
     * time left. Ignored while running. Returns true when it changed the focus length (the caller saves it).
     */
    fun set(sec: Int): Boolean {
        if (running) return false
        val full = leftMsAtStart >= total() * 1000L
        return if (mode == TimerMode.FOCUS && full) { focusSec = sec; leftMsAtStart = sec * 1000L; true }
        else { leftMsAtStart = sec * 1000L; false }
    }

    /** Ring long press (r15 §4): focus<->break at the other mode's full length. Ignored while running; true when it switched. */
    fun switchMode(): Boolean {
        if (running || leftMsAtStart in 1 until total() * 1000L) return false // paused mid-session keeps its time
        mode = if (mode == TimerMode.FOCUS) TimerMode.BREAK else TimerMode.FOCUS
        leftMsAtStart = total() * 1000L
        return true
    }

    /** Switches focus<->break (stopped, full length) when the running countdown is over. True when it did. */
    fun finishIfDone(nowMs: Long): Boolean {
        if (!running || leftMs(nowMs) > 0) return false
        mode = if (mode == TimerMode.FOCUS) TimerMode.BREAK else TimerMode.FOCUS
        leftMsAtStart = total() * 1000L
        running = false
        return true
    }

    fun state(nowMs: Long) = TimerState(mode, ((leftMs(nowMs) + 999) / 1000).toInt(), total(), running)

    /** When the running countdown reaches 0 (elapsedRealtime); null while stopped. */
    fun endAt(): Long? = if (running) startedAt + leftMsAtStart else null

    fun snap() = TimerSnap(mode, running, leftMsAtStart, startedAt, focusSec, breakSec)

    companion object {
        fun of(s: TimerSnap) = PomodoroTimer(s.focusSec, s.breakSec).apply {
            mode = s.mode; running = s.running; leftMsAtStart = s.leftMs; startedAt = s.startedAt
        }
    }
}

object MediaKinds {
    private val VIDEO = setOf(
        "com.google.android.youtube", "net.cj.cjhv.gs.tving", "com.netflix.mediaclient", "kr.co.captv.pooqV2",
        "com.coupang.mobile.play", "com.coupang.play", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
    )

    fun kind(pkg: String) = if (pkg in VIDEO) MediaKind.VIDEO else MediaKind.MUSIC
}

object AlarmActions {
    private val SNOOZE = listOf("다시 알림", "다시알림", "스누즈", "snooze")
    private val STOP = listOf("해제", "중지", "중단", "끄기", "dismiss", "stop")
    private fun has(t: String, words: List<String>) = words.any { t.lowercase().contains(it) }
    fun isSnooze(title: String) = has(title, SNOOZE)
    fun isStop(title: String) = !isSnooze(title) && has(title, STOP)

    val CLOCK_PACKAGES = setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
    fun isAlarm(pkg: String, category: String?, fullScreen: Boolean) =
        pkg in CLOCK_PACKAGES && (category == "alarm" || fullScreen)
}

/**
 * plan-r15 C1: which alarm opens the takeover. [ringing] = a clock-app alarm notification that shows a snooze button or
 * a full-screen intent; [clock] = any clock-app alarm notification still posted; [audio] = something plays with
 * USAGE_ALARM. Audio alone (our own timer tone, a preview, a sibling flavor's tone) never opens it: it needs a clock
 * notification to stand behind it.
 */
object AlarmTakeover {
    fun <T : Any> pick(ringing: T?, clock: T?, audio: Boolean): T? = ringing ?: if (audio) clock else null
}

/**
 * plan-r15 C4: the pre-alarm glow fires only while the alarm it was armed for is still the user's next one. Once ours
 * has fired, the phone's next alarm clock is the user's own; [nextIsOurs] = a sibling flavor's glow alarm hides it, so
 * the stored time is trusted then.
 */
object GlowFire {
    fun valid(stored: Long, next: Long?, nextIsOurs: Boolean): Boolean = stored > 0 && (nextIsOurs || next == stored)
}

/**
 * Package ids of this app's flavors (personal, public, the old public test id). With two on one phone each must ignore
 * the other's notifications and glow alarm, or they react to each other (plan-r15 A2).
 */
object OurApps {
    val IDS = setOf("kr.dot.standby", "kr.dot.standscreen", "kr.dot.standby.pub")
    fun isOurs(pkg: String?, own: String) = pkg == own || pkg in IDS
}

/** What a posted notification is for (spec 2 interrupt table). */
enum class NotifKind { IGNORE, ALARM, LIVE, BIG }

object NotifRules {
    fun classify(
        pkg: String, own: String, category: String?, ongoing: Boolean, groupSummary: Boolean, importance: Int,
        fullScreen: Boolean, hasProgress: Boolean, isMedia: Boolean, secret: Boolean, passesFilter: Boolean,
    ): NotifKind = when {
        OurApps.isOurs(pkg, own) -> NotifKind.IGNORE
        AlarmActions.isAlarm(pkg, category, fullScreen) -> NotifKind.ALARM
        isMedia || category == "transport" || category == "call" -> NotifKind.IGNORE
        ongoing -> if (hasProgress) NotifKind.LIVE else NotifKind.IGNORE
        groupSummary || secret || !passesFilter || importance < 3 /* IMPORTANCE_DEFAULT */ -> NotifKind.IGNORE
        else -> NotifKind.BIG
    }
}

/** Which notifications the shade cards list: what the phone's own shade shows, minus what StandBy shows elsewhere. */
object ShadeRules {
    fun include(
        pkg: String, own: String, category: String?, ongoing: Boolean, foreground: Boolean, groupSummary: Boolean,
        fullScreen: Boolean, isMedia: Boolean, importance: Int, hiddenFromShade: Boolean,
    ): Boolean = !(
        OurApps.isOurs(pkg, own) ||
            AlarmActions.isAlarm(pkg, category, fullScreen) || // the alarm screen owns it
            ongoing || foreground || // downloads, deliveries, services: the live pill or nothing
            isMedia || category == "transport" || category == "call" || // the music card / the call screen
            groupSummary || // its children are listed instead
            importance < 2 || // IMPORTANCE_MIN is folded away in the phone's shade too
            hiddenFromShade // Do Not Disturb set to hide it from the notification list
        )
}

/** How much of a notification StandBy may show; [NONE] = not even that it exists. */
enum class Reveal { FULL, PUBLIC, HIDDEN, NONE }

/**
 * The phone's lock-screen privacy (AOSP NotificationLockscreenUserManager), applied to every StandBy surface. StandBy
 * stands in for the lock screen, and the keyguard can lock under it with no broadcast, so a secure phone always gets
 * lock-screen rules here, unlocked or not.
 */
object NotifPrivacy {
    const val SECRET = -1 // Notification.VISIBILITY_SECRET
    const val PRIVATE = 0 // Notification.VISIBILITY_PRIVATE (the default)
    const val PUBLIC = 1 // Notification.VISIBILITY_PUBLIC
    const val NO_OVERRIDE = -1000 // NotificationListenerService.Ranking: the channel sets no lock-screen visibility

    /**
     * [channelOverride] = the channel's lock-screen visibility (the stricter of it and the app's wins). [showOnLock] /
     * [allowPrivate] = the user's "show notifications" / "show content" lock-screen settings. [tapOnly] = StandBy's own
     * "탭할 때만 미리보기".
     */
    fun reveal(
        visibility: Int, channelOverride: Int, secure: Boolean, showOnLock: Boolean, allowPrivate: Boolean,
        hasPublic: Boolean, tapOnly: Boolean,
    ): Reveal {
        val v = if (channelOverride == NO_OVERRIDE) visibility else minOf(visibility, channelOverride)
        val r = when {
            !secure -> Reveal.FULL
            v <= SECRET || !showOnLock -> return Reveal.NONE
            v == PRIVATE && !allowPrivate -> if (hasPublic) Reveal.PUBLIC else Reveal.HIDDEN
            else -> Reveal.FULL
        }
        return if (tapOnly) Reveal.HIDDEN else r
    }

    /**
     * Title and text the UI may hold for [r]: the app's own text, its public version, or the app name with no text.
     * Blank titles fall back to the app name; text is flattened to one paragraph.
     */
    fun text(r: Reveal, app: String, title: String?, text: String?, pubTitle: String?, pubText: String?): Pair<String, String> {
        fun flat(s: String?) = s.orEmpty().trim().replace(Regex("\\s*\\n\\s*"), " ")
        return when (r) {
            Reveal.FULL -> flat(title).ifEmpty { app } to flat(text)
            Reveal.PUBLIC -> flat(pubTitle).ifEmpty { app } to flat(pubText)
            Reveal.HIDDEN, Reveal.NONE -> app to ""
        }
    }
}

/** Notifications from one app and one conversation (same sender/title) shown as one row with a count (spec-r9). */
data class NotifGroup(val items: List<NotifItem>) {
    val head: NotifItem get() = items.first()
    val count: Int get() = items.size
}

/** Newest first; a group sorts by its newest item. Hidden items carry the app name as title, so they merge per app. */
fun notifGroups(list: List<NotifItem>): List<NotifGroup> =
    list.sortedByDescending { it.postedAtEpochMs }.groupBy { it.packageName to it.title }.values.map { NotifGroup(it) }

/** r11 perf: what a config needs from the device, so the holder registers sensors and loads pictures only then. */
object Needs {
    /**
     * The light sensor: night by lux (AUTO, or OFF on a light theme, which is forced to AUTO) or the screen going off
     * when dark (display-off AUTO). Schedule night on a dark theme with display-off 20 s / never needs none.
     */
    fun lux(nightMode: NightMode, displayOff: DisplayOff, lightTheme: Boolean): Boolean =
        nightMode == NightMode.AUTO || (nightMode == NightMode.OFF && lightTheme) || displayOff == DisplayOff.AUTO

    /** The slideshow photos: while the photos page is on, or 사진 위 시간 is one of the clock page's faces (plan-r15 E4). */
    fun photos(c: AppConfig): Boolean = PAGE_PHOTOS !in c.pagesOff || FaceId.PHOTOS_TIME in c.myClocks

    /** The memory picture: a memories card on a page that is on, or the blurred-photo background. */
    fun memory(c: AppConfig): Boolean {
        val widgets = if (PAGE_WIDGETS in c.pagesOff) emptyList() else c.widgetLeft + c.widgetRight
        val cards = c.boards.filter { it.on }.flatMap { b -> b.slots.flatten() } + widgets
        return c.boardBg == BoardBg.PHOTO_BLUR || CardType.MEMORIES in cards
    }
}
