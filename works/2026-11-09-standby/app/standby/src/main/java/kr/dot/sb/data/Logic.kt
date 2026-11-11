package kr.dot.sb.data

import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
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
}

/** Night look from the settings (spec E12: 안 함 = the display never changes, so no night mode at all). */
object NightRules {
    fun on(mode: NightMode, display: DisplayOff, scheduled: Boolean, auto: Boolean): Boolean =
        display != DisplayOff.NEVER && when (mode) {
            NightMode.OFF -> false
            NightMode.SCHEDULE -> scheduled
            NightMode.AUTO -> auto
        }

    /** A ringing alarm or a shown notification keeps the screen on, whatever the idle/dark/flat decision says (spec I1). */
    fun displayOff(wanted: Boolean, alarm: Boolean, notification: Boolean) = wanted && !alarm && !notification
}

/** ON/OFF decision of the display (spec E10-E14). */
class DisplayOffMachine {
    var off = false
        private set
    private var last = 0L

    fun touch(nowMs: Long) { last = nowMs; off = false }

    /** Last touch time (a notification that woke the screen only sends it back to OFF when nothing touched since). */
    val lastTouch get() = last

    fun forceOff() { off = true }

    fun evaluate(nowMs: Long, mode: DisplayOff, lux: Float?, enterLux: Float, exitLux: Float): Boolean {
        when (mode) {
            DisplayOff.NEVER -> off = false
            DisplayOff.AFTER_20S -> if (!off && nowMs - last >= IDLE_MS) off = true
            DisplayOff.AUTO ->
                if (off) { if (lux != null && lux >= exitLux) touch(nowMs) }
                else if (nowMs - last >= IDLE_MS && lux != null && lux <= enterLux) off = true
        }
        return off
    }

    /** ms until idle time runs out while on, else null. */
    fun idleLeftMs(nowMs: Long): Long? = if (off) null else (IDLE_MS - (nowMs - last)).coerceAtLeast(0)

    companion object { const val IDLE_MS = 20_000L }
}

/** Pixel shift of the whole root: 3x3 grid {-3,0,3} dp, snake order, one point per minute (spec E20). */
object BurnIn {
    private val PATH = listOf(-3 to -3, 0 to -3, 3 to -3, 3 to 0, 0 to 0, -3 to 0, -3 to 3, 0 to 3, 3 to 3)

    /** Ping-pong over the snake so the shift never jumps across the grid. */
    fun at(step: Int): Pair<Int, Int> {
        val i = step.mod(16)
        return PATH[if (i > 8) 16 - i else i]
    }
}

/** Accelerometer: gravity low-pass, linear acceleration for wake (0.25) and pick-up (2.5 for 300 ms), pose (spec E2/E7/E13). */
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
    val wakeMotion get() = linear > WAKE

    fun update(nowMs: Long, x: Float, y: Float, z: Float) {
        if (!init) { gx = x; gy = y; gz = z; init = true }
        gx = A * gx + (1 - A) * x; gy = A * gy + (1 - A) * y; gz = A * gz + (1 - A) * z
        // magnitude, not axis: turning the phone moves gravity between axes but keeps |a| = g (E4 rotation must not read as pick-up)
        linear = abs(sqrt(x * x + y * y + z * z) - G)
        pickup.update(nowMs, linear > PICKUP, linear <= PICKUP)
        flat.update(nowMs, abs(gz) > 9.0f, abs(gz) < 8.0f)
    }

    companion object { const val G = 9.81f; const val A = 0.9f; const val WAKE = 0.25f; const val PICKUP = 2.5f }
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
class PomodoroTimer(private val focusSec: Int, private val breakSec: Int) {
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

    /** Switches focus<->break (stopped, full length) when the running countdown is over. True when it did. */
    fun finishIfDone(nowMs: Long): Boolean {
        if (!running || leftMs(nowMs) > 0) return false
        mode = if (mode == TimerMode.FOCUS) TimerMode.BREAK else TimerMode.FOCUS
        leftMsAtStart = total() * 1000L
        running = false
        return true
    }

    fun state(nowMs: Long) = TimerState(mode, ((leftMs(nowMs) + 999) / 1000).toInt(), total(), running)
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

/** What a posted notification is for (spec 2 interrupt table). */
enum class NotifKind { IGNORE, ALARM, LIVE, BIG }

object NotifRules {
    fun classify(
        pkg: String, own: String, category: String?, ongoing: Boolean, groupSummary: Boolean, importance: Int,
        fullScreen: Boolean, hasProgress: Boolean, isMedia: Boolean, secret: Boolean, passesFilter: Boolean,
    ): NotifKind = when {
        pkg == own -> NotifKind.IGNORE
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
        pkg == own ||
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
