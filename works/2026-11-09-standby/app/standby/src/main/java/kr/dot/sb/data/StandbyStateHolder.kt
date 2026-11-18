package kr.dot.sb.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.AlarmManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.SystemClock
import android.text.format.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kr.dot.sb.ui.state.AgendaItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.SLEEP_MINUTES
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.platform.Brightness
import kr.dot.sb.platform.ChargeStart
import kr.dot.sb.platform.ChargeRules
import kr.dot.sb.platform.ListenerBus
import kr.dot.sb.platform.SleepService
import kr.dot.sb.platform.DeviceTimerAlerts
import kr.dot.sb.platform.TimerAlerts
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.SleepState
import kr.dot.sb.ui.state.LiveTicks
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.FeedError
import kr.dot.sb.ui.state.usageFailed
import kr.dot.sb.ui.state.usageLoaded
import kr.dot.sb.ui.state.weatherFailed
import kr.dot.sb.ui.state.weatherLoaded
import kr.dot.sb.ui.theme.baseTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** What the screen host (dream or activity) must do for the holder. */
interface HostCallbacks {
    fun finish()
    fun finishForChargeRule(reason: String) = finish()
    /** 0..1, or -1f for "system auto". */
    fun setBrightness(b: Float)
    /** Run [block] after the keyguard is dismissed (dream: hand over to the activity first). */
    fun unlockThen(block: () -> Unit)
    fun openGlow()
}

/**
 * Combines every source into one [StandbyState]. One instance per dream/activity session: [start] registers sensors and
 * receivers, [stop] cancels everything. Work happens only while something changes: a minute ticker aligned to the
 * minute boundary, sensor events, and (only while the screen is on) feed polls. Off state = tickers cancelled.
 */
class StandbyStateHolder(
    app: Context, private val host: HostCallbacks, private val ownRotation: Boolean,
    private val alerts: TimerAlerts = DeviceTimerAlerts.shared(app),
    private val automatic: Boolean = false,
) {
    private val ctx = app.applicationContext
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) // new one per start(): stop() cancels it
    private val cfgFlow = ConfigStore.flow(ctx)
    private val cfg: AppConfig get() = cfgFlow.value
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val hasHinge = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

    private fun minute(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private fun el() = SystemClock.elapsedRealtime()

    private val _state = MutableStateFlow(
        StandbyState(
            now = minute(), use24h = cfg.use24h ?: DateFormat.is24HourFormat(ctx), night = lastNight,
            agenda = emptyList(), permissions = DeviceSources.permissions(ctx),
        ),
    )
    val state: StateFlow<StandbyState> = _state

    /** r11 perf: per-second timer values and the media position, read only by those cards ([LiveTicks]). */
    private val _ticks = MutableStateFlow(LiveTicks())
    val ticks: StateFlow<LiveTicks> = _ticks

    /** Content rotation in degrees (0/90/180/270); only moves when [ownRotation] (dream). */
    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation

    /** Config as the UI should see it: unfolded + 책 보드 = start page is the board with id "book". */
    private val _ui = MutableStateFlow(cfg)
    val uiConfig: StateFlow<AppConfig> = _ui

    private val nightSw = NightSwitch()
    private val machine = DisplayOffMachine()
    private val motion = MotionDetector()
    private val rot = RotationTracker()
    private val lux = LuxMedian()
    private var wasOffAtNotif = false
    private var wasAsleepAtNotif = false
    /** Battery heat (HeatRule state, before the heatProtect option). */
    private var hot = false
    /** The unfold mood light is on screen (reported by StandbyRoot): keeps the display on. */
    private var mood = false
    private var notifWokeAt = -1L
    private val media = MediaSource(ctx) { m ->
        val old = _state.value.media
        // r11 perf: a position-only update (the 5 s refresh while playing) goes to the ticks, not the whole state
        if (m != null && old != null && m.copy(positionMs = old.positionMs) == old) _ticks.update { it.copy(mediaPositionMs = m.positionMs) }
        else { _ticks.update { it.copy(mediaPositionMs = null) }; _state.update { it.copy(media = m) } }
    }

    private var lastLux: Float? = null
    private var luxOn = false
    /** What the last config needed ([Needs]); null = not checked since [start]. */
    private var memWanted: Boolean? = null
    private var photosWanted: Boolean? = null
    private var nightTarget = lastNight
    private var wasFlat = false
    private var lastMotionTouch = 0L
    private var startedAt = 0L
    private var exited = false
    private var lastPlugged: Int? = null
    private var lastBrightness = Float.NaN
    private var page = 0
    private var started = false
    private var lastStaticAt = 0L
    private var lastWeatherAt = 0L
    private var lastUsageAt = 0L
    private val usageFeed = UsageFeature.feed(ctx)
    private var lastPhotosAt = 0L
    private var memoryDay: LocalDate? = null
    private var lastCity = cfg.city
    /** Folders + chip filter last loaded; a change reloads the photos page. */
    private var photoKey: Pair<List<String>, String?>? = null
    /** Folders the memory picture was last picked from; a change picks again. */
    private var memFolders: List<String>? = null

    private var visibleJob: Job? = null
    private var pollJob: Job? = null
    private var nightJob: Job? = null
    private var notifJob: Job? = null
    private var liveJob: Job? = null
    private val endJobs = HashMap<String, Job>()
    private var notifShownAt = 0L

    private val lightL = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { lastLux = lux.add(el(), e.values[0]); evaluate() }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    private val accelL = object : SensorEventListener {
        // batched events arrive in bursts: time each by its own timestamp (elapsed-realtime based), not by delivery
        override fun onSensorChanged(e: SensorEvent) = onAccel(eventMs(e), e.values[0], e.values[1], e.values[2])
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    /**
     * Charger out (wired or wireless): StandBy ends, however it was opened (r8: a tile or dream hand-over start stayed
     * on after the phone left the pad). Also re-arms the "start on charge" job at once (see [ChargeStart]).
     */
    private val unplugRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            ChargeStart.rearmNow(ctx)
            if (automatic) { checkChargeRule(0); return }
            if (!exited) { exited = true; ChargeStart.log(ctx, "닫힘 · 충전기 뽑힘"); host.finish() }
        }
    }
    private val nextAlarmRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { scope.launch { refreshStatic(force = true) } }
    }
    /** Heat protection: battery temperature (tenths of °C) on every BATTERY_CHANGED; a change re-applies the brightness. */
    private val batteryRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            if (plugged != lastPlugged) {
                lastPlugged = plugged
                if (!checkChargeRule(plugged)) return
            }
            val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } ?: return
            val h = HeatRule.next(t, hot)
            if (h == hot) return
            hot = h
            publishHot()
            evaluate()
        }
    }

    private fun checkChargeRule(plugged: Int? = null): Boolean {
        if (!automatic) return true
        val c = cfg
        val reason = ChargeRules.allowed(c.startOnCharge, plugged ?: ChargeStart.plugged(ctx), c.chargeWirelessOnly) ?: return true
        if (!exited) {
            exited = true
            ChargeStart.log(ctx, "닫힘 · 규칙 · $reason")
            host.finishForChargeRule(reason)
        }
        return false
    }

    private fun publishHot() {
        val h = hot && cfg.heatProtect
        if (_state.value.hot != h) _state.update { it.copy(hot = h) }
    }

    fun start() {
        if (started) return
        if (!checkChargeRule()) return
        started = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        exited = false
        lastPlugged = null
        startedAt = el()
        machine.touch(startedAt)
        nightSw.prime(lastNight >= 0.5f) // a dream restarted in the dark starts red, not day
        luxOn = false
        syncLux(cfg)
        // r11 perf: the motion filters are tuned for ~66 ms samples (SENSOR_DELAY_UI), so keep that rate but let the
        // hardware batch 150 ms (about 7 wake-ups/s instead of 15); pick-up still answers within ~0.45 s
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(accelL, it, SensorManager.SENSOR_DELAY_UI, ACCEL_BATCH_US) }
        ctx.registerReceiver(unplugRx, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), Context.RECEIVER_NOT_EXPORTED)
        ctx.registerReceiver(nextAlarmRx, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        ctx.registerReceiver(batteryRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED) // sticky: the current value arrives at once
        media.start()
        runCatching { ctx.contentResolver.registerContentObserver(android.provider.CalendarContract.Events.CONTENT_URI, true, calendarObs) }
        onConfiguration(ctx.resources.configuration.smallestScreenWidthDp, initial = true)
        // StandBy is on screen (opened from the end notification or not): that alert and notification are done
        alerts.stop()
        for (id in timers.keys) Timers.effects(ctx).clearEnd(id)
        lastStaticAt = 0L // permissions (알람 권한) again on the first tick: back from a settings page
        // a countdown that ran out while closed and was not ended by its alarm yet (Doze) ends now, with the card alert
        for ((id, t) in timers) { finish(id, t, el()); scheduleTimerEnd(id) }
        publishTimers()
        // a notification button or the end alarm changed a timer (Timers is shared): re-arm and show it
        scope.launch { Timers.changed.collect { for (id in timers.keys) scheduleTimerEnd(id); publishTimers() } }

        var chargeOptions = cfg.startOnCharge to cfg.chargeWirelessOnly
        scope.launch {
            cfgFlow.collect { c ->
                val options = c.startOnCharge to c.chargeWirelessOnly
                if (options != chargeOptions) {
                    chargeOptions = options
                    if (!checkChargeRule()) return@collect
                }
                _state.update {
                    var n = it.copy(use24h = c.use24h ?: DateFormat.is24HourFormat(ctx), worldCity = c.worldCities.firstOrNull() ?: it.worldCity)
                    if (!c.burnInShift) n = n.copy(shiftX = 0, shiftY = 0)
                    n
                }
                pushUi()
                publishHot()
                syncLux(c)
                syncMedia(c)
                if (photoKey != null && photoKey != (c.photoFolders to c.photoFilter)) launch { reloadPhotos() }
                if (memWanted == true && memFolders != null && memFolders != c.photoFolders) launch { loadMemory(LocalDate.now()) }
                if (c.city != lastCity) { lastCity = c.city; lastWeatherAt = 0; if (!_state.value.displayOff) launch { fetchWeather() } }
                if (c.excludedCalendars != lastExcluded) { val first = lastExcluded == null; lastExcluded = c.excludedCalendars; if (!first) launch { refreshCalendar() } }
                evaluate()
            }
        }
        scope.launch {
            ListenerBus.alarm.collect { a ->
                if (a !== alarmClosed) alarmClosed = null
                // off: the clock app's own alarm screen is the only one (StandBy stays as it is)
                val shown = a?.takeIf { cfg.alarmScreen && a !== alarmClosed }
                _state.update { it.copy(alarm = shown?.let { s -> AlarmRinging(s.label, s.snooze != null, s.stop != null) }) }
                machine.touch(el()); evaluate() // alarm preempts everything and wakes OFF; when it ends the idle time restarts
            }
        }
        scope.launch { ListenerBus.notifications.collect { showNotification(it) } }
        ListenerBus.refreshShade() // lock-screen settings may have changed since the last post
        scope.launch { ListenerBus.shade.collect { l -> _state.update { it.copy(notifications = l) } } }
        scope.launch {
            ListenerBus.live.collect { l -> _state.update { it.copy(live = l, liveExpanded = if (l == null) false else it.liveExpanded) } }
        }
        scope.launch { SleepBus.state.collect { s -> _state.update { it.copy(sleep = s) } } }
        // media position moves only while playing: refresh every 5 s then, never otherwise
        scope.launch {
            _state.map { it.media?.playing == true }.distinctUntilChanged().collectLatest { p ->
                while (p) { delay(5_000); if (!_state.value.displayOff) media.publish() }
            }
        }
        scope.launch {
            _state.map { it.timers.values.any { t -> t.running } }.distinctUntilChanged().collectLatest { r ->
                while (r) { delay(1_000); if (!_state.value.displayOff) publishTimers(full = false) }
            }
        }
        startVisible()
        evaluate()
    }

    fun stop() {
        if (!started) return
        started = false
        sm.unregisterListener(lightL); sm.unregisterListener(accelL)
        luxOn = false
        memWanted = null; photosWanted = null
        runCatching { ctx.unregisterReceiver(nextAlarmRx) }
        runCatching { ctx.unregisterReceiver(batteryRx) }
        runCatching { ctx.unregisterReceiver(unplugRx) }
        runCatching { ctx.contentResolver.unregisterContentObserver(calendarObs) }
        calendarJob?.cancel()
        media.stop()
        lastNight = _state.value.night
        flushBrightness()
        scope.cancel()
    }

    // ---- display / night ---------------------------------------------------------------------------------------

    private fun evaluate() {
        val now = el()
        val c = cfg
        nightSw.enterLux = c.nightEnterLux; nightSw.exitLux = c.nightExitLux
        lastLux?.let { nightSw.update(now, it) }
        val nightOn = NightRules.on(
            c.nightMode, c.displayOff,
            scheduled = c.nightMode == NightMode.SCHEDULE && NightRamp.scheduledNight(LocalTime.now().hour, c.nightStartHour, c.nightEndHour),
            auto = nightSw.night,
            forceNight = c.baseTheme().tokens.light,
        )
        setNight(if (nightOn) 1f else 0f)
        val flat = motion.isFlat && c.displayOff != DisplayOff.NEVER // E12: 안 함 = never black
        val off = NightRules.displayOff(
            machine.evaluate(now, c.displayOff, lastLux, c.nightEnterLux, c.nightExitLux) || flat,
            _state.value.alarm != null, _state.value.notification != null, mood = mood && !machine.asleep, // sleep wins over mood
        )
        setOff(off, nightOn)
        schedulePoll(now)
    }

    /** Sensors send nothing while the value is steady, so the end of a hold or of the idle time needs its own wake-up. */
    private fun schedulePoll(now: Long) {
        pollJob?.cancel()
        val c = cfg
        val luxNight = c.nightMode == NightMode.AUTO || (c.nightMode == NightMode.OFF && c.baseTheme().tokens.light) // light theme: forced AUTO
        val waits = listOfNotNull(
            if (luxNight) nightSw.pendingMs(now) else null,
            if (cfg.displayOff != DisplayOff.NEVER) machine.idleLeftMs(now)?.takeIf { it > 0 } else null,
        )
        val w = waits.minOrNull() ?: return
        pollJob = scope.launch { delay(w + 20); evaluate() }
    }

    private fun setNight(target: Float) {
        if (target == nightTarget) return
        nightTarget = target
        nightJob?.cancel()
        nightJob = scope.launch {
            val from = _state.value.night
            val t0 = el()
            while (true) {
                val f = NightRamp.fraction(from, target, el() - t0)
                _state.update { it.copy(night = f) }
                if (f == target) break
                delay(33)
            }
        }
    }

    private var pendingBrightness: Float? = null
    private var brightJob: Job? = null

    /** Window value before the current bar edit, for the trail. */
    private var barFrom = Float.NaN
    /** [hot] when the brightness was last applied: a change is the heat cap moving ("열" in the trail). */
    private var hotApplied = false
    /** Last "열" trail line (elapsed ms); [Long.MIN_VALUE] / 2 = never, so the first one always logs. */
    private var heatLoggedAt = Long.MIN_VALUE / 2

    /**
     * Live from the on-screen bar ([p] = bar position, r14 perceptual curve): shown at once, written to the config once
     * the finger has paused (not per frame).
     */
    fun setUserBrightness(p: Float) {
        val b = Brightness.clamp(Brightness.fromBar(p))
        val w = Brightness.applied(b, cfg, hot)
        if (pendingBrightness == null) barFrom = lastBrightness
        pendingMood = mood
        lastBrightness = w
        host.setBrightness(w)
        pendingBrightness = b
        publishCapped()
        brightJob?.cancel()
        brightJob = scope.launch { delay(500); flushBrightness() }
    }

    private fun flushBrightness() {
        val b = pendingBrightness ?: return
        pendingBrightness = null
        logBrightness("막대", barFrom, lastBrightness) // one line per edit, not per frame
        // plan-r15 E8: over the mood light the bar is the mood light's own brightness
        if (pendingMood) ConfigStore.update(ctx) { it.copy(mood = it.mood.copy(bright = Brightness.toBar(b))) }
        else ConfigStore.update(ctx) { Brightness.store(it, nightTarget >= 0.5f, b) }
    }

    /** The bar edit in [pendingBrightness] was made over the mood light. */
    private var pendingMood = false

    /** The bar asks for more than heat protection lets through (same test as [Brightness.applied]). */
    private fun publishCapped() {
        val v = pendingBrightness ?: if (nightTarget >= 0.5f) cfg.nightMaxBrightness else cfg.dayBrightness ?: Brightness.AUTO
        val capped = Brightness.applied(v, cfg, hot) != v
        if (_state.value.brightCapped != capped) _state.update { it.copy(brightCapped = capped) }
    }

    /** r14 B7 trail (settings, personal build): "밝기 {source} {old}→{new}". */
    private fun logBrightness(source: String, old: Float, new: Float) {
        fun pct(v: Float) = when { v.isNaN() -> "–"; v == Brightness.AUTO -> "자동"; else -> "${(v * 100).roundToInt()}%" }
        ChargeStart.log(ctx, "밝기 $source ${pct(old)}→${pct(new)}")
    }

    private fun setOff(off: Boolean, nightOn: Boolean) {
        if (_state.value.displayOff != off) {
            _state.update { it.copy(displayOff = off) }
            if (off) { visibleJob?.cancel(); visibleJob = null } else startVisible()
        }
        publishCapped()
        // while a bar edit is still unsaved the config holds the old value: don't snap the screen back to it
        if (!off && pendingBrightness != null) return
        // plan-r15 E8: the mood light has its own brightness, up to the panel maximum (heat protection still caps it)
        val b = if (!off && mood) Brightness.applied(Brightness.fromBar(cfg.mood.bright), cfg, hot) else Brightness.target(off, nightOn, cfg, hot)
        if (b != lastBrightness) {
            // screen off/on is not logged: it repeats all night and would push the charge-start lines out of the trail
            // a battery near 42 °C toggles the heat cap often: one "열" line per 10 min at most, for the same reason
            val heat = hot != hotApplied
            if (!off && lastBrightness != Brightness.OFF && (!heat || el() - heatLoggedAt >= HEAT_LOG_MS)) {
                if (heat) heatLoggedAt = el()
                logBrightness(if (heat) "열" else "밤", lastBrightness, b)
            }
            lastBrightness = b; host.setBrightness(b)
        }
        hotApplied = hot
    }

    private fun eventMs(e: SensorEvent): Long {
        val t = e.timestamp / 1_000_000
        val now = el()
        return if (kotlin.math.abs(now - t) < 2_000) t else now // a clock base that does not match: delivery time
    }

    /** r11 perf: the light sensor only while a rule reads lux ([Needs.lux]); re-checked on every config change. */
    private fun syncLux(c: AppConfig) {
        val want = Needs.lux(c.nightMode, c.displayOff, c.baseTheme().tokens.light)
        if (want == luxOn) return
        luxOn = want
        if (want) sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sm.registerListener(lightL, it, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000) } // 5 s batching
        else { sm.unregisterListener(lightL); lastLux = null }
    }

    /**
     * r11 perf: slideshow photos only while the photos page is on, the memory picture only while a memories card or the
     * blurred-photo background shows it; what is no longer needed is dropped from the state (its bitmaps go with it).
     */
    private fun syncMedia(c: AppConfig) {
        val mem = Needs.memory(c)
        val ph = Needs.photos(c)
        val memChanged = mem != memWanted
        val phChanged = ph != photosWanted
        memWanted = mem; photosWanted = ph
        if (memChanged && !mem) { memoryDay = null; memFolders = null; _state.update { it.copy(memory = null) } }
        if (phChanged && !ph) { photoKey = null; _state.update { it.copy(photos = null) } }
        when {
            // loadMemory reloads the photos too (their count depends on the memory)
            memChanged && mem -> { val d = LocalDate.now(); memoryDay = d; scope.launch { loadMemory(d) } }
            phChanged && ph -> scope.launch { reloadPhotos() }
            // a dropped memory frees a photo slot (2 -> 3)
            memChanged && ph -> scope.launch { reloadPhotos() }
        }
    }

    private fun onAccel(now: Long, x: Float, y: Float, z: Float) {
        motion.update(now, x, y, z)
        if (ownRotation && rot.update(now, x, y, z)) _rotation.value = rot.degrees
        if (!exited && now - startedAt > 3_000 && motion.pickedUp) { exited = true; host.finish(); return } // E7
        val flat = motion.isFlat
        if (flat != wasFlat) { wasFlat = flat; if (!flat) machine.touch(now); evaluate() }
        // E13: motion keeps/wakes the screen, only under 자동
        if (cfg.motionToWake && cfg.displayOff == DisplayOff.AUTO && !flat && motion.wakeMotion && now - lastMotionTouch > 1_000) {
            lastMotionTouch = now; machine.touch(now); evaluate()
        }
    }

    // ---- visible tickers (cancelled while off) -----------------------------------------------------------------

    private fun startVisible() {
        visibleJob?.cancel()
        visibleJob = scope.launch {
            launch { minuteLoop() }
            launch { every(30 * 60_000L, { lastWeatherAt }) { fetchWeather() } }
            launch { every(5 * 60_000L, { lastUsageAt }) { fetchUsage() } }
            launch { every(15 * 60_000L, { lastCalendarAt }) { refreshCalendar(); true } }
            // a new set once every photo of the current one had its turn (at least 45 s); the interval is re-read each loop
            launch {
                while (true) {
                    delay(maxOf(45_000L, cfg.photoIntervalSec * 1000L * (_state.value.photos?.size ?: 0)))
                    if (photosShowing()) reloadPhotos()
                }
            }
        }
    }

    private suspend fun minuteLoop() {
        while (true) {
            val t = LocalDateTime.now()
            val (sx, sy) = if (cfg.burnInShift) BurnIn.at(System.currentTimeMillis() / 60_000L) else (0 to 0)
            _state.update { it.copy(now = t.truncatedTo(ChronoUnit.MINUTES), shiftX = sx, shiftY = sy) }
            publishTimers()
            if (memWanted == true && memoryDay != t.toLocalDate()) { memoryDay = t.toLocalDate(); scope.launch { loadMemory(t.toLocalDate()) } }
            if (calendarDay != null && calendarDay != t.toLocalDate()) scope.launch { refreshCalendar() }
            refreshStatic(force = false)
            evaluate()
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000))
        }
    }

    private suspend fun every(period: Long, last: () -> Long, job: suspend () -> Boolean) {
        while (true) {
            val age = System.currentTimeMillis() - last()
            if (age >= period) delay(if (job()) period else minOf(period, 120_000L)) else delay(period - age)
        }
    }

    // ---- sources -----------------------------------------------------------------------------------------------

    /** Next alarm and permissions each minute; the calendar comes from [refreshCalendar] (B4) and only re-filters here. */
    private suspend fun refreshStatic(force: Boolean) {
        if (!force && el() - lastStaticAt < 60_000) return
        lastStaticAt = el()
        val (alarm, perms) = withContext(Dispatchers.IO) { DeviceSources.nextAlarm(ctx) to DeviceSources.permissions(ctx) }
        val calendarOn = perms.calendar != _state.value.permissions.calendar // granted or taken away: query again
        _state.update { it.copy(nextAlarm = alarm, permissions = perms) }
        if (calendarOn) refreshCalendar() else publishAgenda()
    }

    // ---- calendar (plan-r11 B4): queried every 15 min, on a provider change, at date change, on an exclusion change -

    private var calendarItems: List<AgendaItem>? = null
    private var lastExcluded: Set<String>? = null
    private var lastCalendarAt = 0L
    private var calendarDay: LocalDate? = null
    private var calendarJob: Job? = null
    private val calendarObs = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        // a sync fires many changes: one query 2 s after the last
        override fun onChange(selfChange: Boolean) {
            calendarJob?.cancel()
            calendarJob = scope.launch { delay(2_000); refreshCalendar() }
        }
    }

    private var calendarLoaded = false

    private suspend fun refreshCalendar() {
        lastCalendarAt = System.currentTimeMillis()
        val now = LocalDateTime.now()
        calendarDay = now.toLocalDate()
        calendarItems = withContext(Dispatchers.IO) { DeviceSources.upcoming(ctx, now, cfg.excludedCalendars) }
        calendarLoaded = true
        publishAgenda()
    }

    /** Drop what ended since the query (no query): runs each minute. */
    private fun publishAgenda() {
        if (!calendarLoaded) return // before the first query: keep the empty start state (no "permission needed" flash)
        val items = calendarItems
        if (items == null) { _state.update { it.copy(agenda = null, upcoming = emptyList()) }; return }
        val (agenda, upcoming) = Agenda.visible(items, LocalDateTime.now())
        _state.update { if (it.agenda == agenda && it.upcoming == upcoming) it else it.copy(agenda = agenda, upcoming = upcoming) }
    }

    // plan-r14 C2: any network (the forecast is a few KB); the error kind only, never the exception (URLs carry tokens)
    private suspend fun fetchWeather(): Boolean {
        if (!Feeds.online(ctx)) { markWeatherOffline(FeedError.NO_NETWORK); return false }
        val city = cfg.city
        return try {
            val w = withContext(Dispatchers.IO) { WeatherParser.parse(Feeds.weatherText(city.lat, city.lon), city.name, System.currentTimeMillis() / 1000) }
            lastWeatherAt = System.currentTimeMillis()
            _state.update { it.weatherLoaded(w) }
            true
        } catch (e: Exception) {
            // class name only (no message: some feed URLs carry tokens); personal build's measure log
            kr.dot.sb.platform.Measure.log(ctx, "날씨 실패 · ${city.name} · ${e.javaClass.simpleName}")
            markWeatherOffline(FeedError.FAILED); false
        }
    }

    private fun markWeatherOffline(e: FeedError) = _state.update { it.weatherFailed(e) }

    private suspend fun fetchUsage(): Boolean {
        if (!UsageFeature.enabled || !cfg.usageEnabled) { lastUsageAt = System.currentTimeMillis(); return true }
        val validated = Feeds.online(ctx)
        if (Feeds.noNetwork(validated, Feeds.onWifi(ctx), lan = true)) { markUsageOffline(FeedError.NO_NETWORK); return false }
        // unvalidated Wi-Fi: only the LAN route ran, so its failure reads as no internet
        val u = usageFeed.fetch() ?: run { markUsageOffline(if (validated) FeedError.FAILED else FeedError.NO_NETWORK); return false }
        lastUsageAt = System.currentTimeMillis()
        _state.update { it.usageLoaded(u) }
        return true
    }

    private fun markUsageOffline(e: FeedError) = _state.update { it.usageFailed(e) }

    private suspend fun reloadPhotos() {
        if (photosWanted != true) return
        lastPhotosAt = el()
        val n = if (_state.value.memory != null) 2 else 3 // max 3 bitmaps in memory
        val c = cfg
        photoKey = c.photoFolders to c.photoFilter
        // plan-r15 B1: picks from the shared index (rebuilt on a folder change or after 6 h), never re-lists
        val (p, s) = withContext(Dispatchers.IO) { PhotoIndex.get(ctx, c.photoFolders).let { s -> PhotoIndex.photos(ctx, s, c.photoFilter, n) to s } }
        if (photosWanted != true) return // the photos page went off while loading
        val hint = PhotoIndex.hint(c.photoFolders, s)
        _state.update { it.copy(photos = p.takeIf { hint == null }, photoFolders = s.list().map { f -> f.uri to f.name }, photoHint = hint) }
    }

    private suspend fun loadMemory(day: LocalDate) {
        if (memWanted != true) return
        val folders = cfg.photoFolders
        memFolders = folders
        val (m, hint) = withContext(Dispatchers.IO) { PhotoIndex.get(ctx, folders).let { s -> PhotoIndex.memory(ctx, s, day) to PhotoIndex.hint(folders, s) } }
        if (memWanted != true) return // switched off while loading
        _state.update { it.copy(memory = m, photoHint = hint) }
        reloadPhotos()
    }

    // ---- interrupts --------------------------------------------------------------------------------------------

    private fun showNotification(n: kr.dot.sb.ui.state.BigNotification) {
        if (_state.value.alarm != null) return // dropped while an alarm shows; it stays in the shade
        // quiet night: no banner, no wake; the notification card still updates through ListenerBus.shade
        if (!NightRules.wakeOnNotification(nightTarget >= 0.5f, cfg.nightNotifQuiet, _state.value.displayOff)) return
        notifJob?.cancel()
        notifJob = scope.launch {
            val wait = 2_000 - (el() - notifShownAt) // a newer one replaces the shown one after >= 2 s
            if (_state.value.notification != null && wait > 0) delay(wait)
            notifShownAt = el()
            // woken from OFF by this notification: back to OFF afterwards unless the user touched the screen (spec I4)
            if (_state.value.notification == null) { wasOffAtNotif = _state.value.displayOff; wasAsleepAtNotif = machine.asleep }
            _state.update { it.copy(notification = n) }
            machine.touch(el()); notifWokeAt = machine.lastTouch; evaluate()
            delay(6_500) // 6 s + fade
            endNotification(n.key)
        }
    }

    private fun endNotification(key: String?) {
        if (key != null && _state.value.notification?.key != key) return
        _state.update { it.copy(notification = null) }
        if (wasOffAtNotif && machine.lastTouch == notifWokeAt) { if (wasAsleepAtNotif) machine.sleep() else machine.forceOff() } else machine.touch(el())
        wasOffAtNotif = false; wasAsleepAtNotif = false
        evaluate()
    }

    // ---- pomodoro ----------------------------------------------------------------------------------------------

    /** Every slot's timer, shared with the notification buttons and the end alarm ([Timers]). */
    private val timers get() = Timers.all(ctx)
    private fun timer(id: String) = Timers.get(ctx, id)

    /** A change made here: persisted, alarm and ongoing notification refreshed; an end heads-up of [id] goes away. */
    private fun saved(id: String) { alerts.stop(); Timers.effects(ctx).clearEnd(id); Timers.save(ctx, id, el()) }

    /** Timer values to the ticks; [full] = also into [StandbyState] (a start, stop, reset or end: the running set changed). */
    private fun publishTimers(full: Boolean = true) {
        val now = el()
        // a timer that ends on this tick changes the running set: always a full publish, and it wakes the screen
        val ended = timers.map { (id, t) -> finish(id, t, now) }.any { it }
        val m = timers.mapValues { (_, t) -> t.state(now) }
        _ticks.update { it.copy(timers = m) }
        if (full || ended) _state.update { it.copy(timers = m) }
        if (ended && !full) { machine.touch(now); evaluate() }
    }

    /**
     * [PomodoroTimer.finishIfDone] plus the end alert in the card (3 rounds; falling when a break ended, i.e. back in
     * focus). The holder runs only while StandBy shows, so no heads-up: [Timers.onAlarm] leaves this one to us.
     */
    private fun finish(id: String, t: PomodoroTimer, now: Long): Boolean {
        if (!t.finishIfDone(now)) return false
        Timers.save(ctx, id, now)
        alerts.play(cfg.timerAlert, falling = t.mode == TimerMode.FOCUS, times = 3)
        return true
    }

    private fun scheduleTimerEnd(id: String) {
        endJobs[id]?.cancel()
        val t = timers[id] ?: return
        if (!t.running) return
        endJobs[id] = scope.launch {
            delay(t.leftMs(el()) + 50)
            if (finish(id, t, el())) { publishTimers(); machine.touch(el()); evaluate() } // wakes the screen when focus/break ends
        }
    }

    // ---- actions (called through HostActions) ------------------------------------------------------------------

    fun touch() { machine.touch(el()); evaluate() }
    fun moodShowing(on: Boolean) { if (on != mood) { mood = on; evaluate() } }
    /** Mood light auto-off ran out: sticky off until a touch (which then shows the board). */
    fun moodDone() { mood = false; MoodLatch.done = true; machine.sleep(); evaluate() }
    /** The photos page, or the clock page while 사진 위 시간 is among its faces (plan-r15 E4: it draws them too). */
    private fun photosShowing() = page == 1 || (page == 2 && FaceId.PHOTOS_TIME in kr.dot.sb.settings.MyClocks.pages(cfg))

    fun onPage(p: Int) {
        page = p
        // "first entry = clock, then the last viewed page": remembered through startPage (not while the unfold rule steers it)
        if (!_state.value.unfolded) ConfigStore.update(ctx) { it.copy(startPage = p) }
        if (photosShowing() && el() - lastPhotosAt > 30_000) scope.launch { reloadPhotos() }
    }
    /** Photos page chip swipe: null = every folder, else one picked folder; a view filter, the folder list stays. */
    fun photoTheme(folder: String?) = ConfigStore.update(ctx) { it.copy(photoFilter = folder?.takeIf { f -> f in it.photoFolders }) }
    fun mediaPlayPause() = media.playPause()
    fun mediaNext() = media.next()
    fun mediaPrev() = media.prev()
    val mediaPackage get() = media.packageName
    fun timerToggle(id: String) { timer(id).toggle(el()); saved(id); scheduleTimerEnd(id); publishTimers() }
    fun timerReset(id: String) { timer(id).reset(); saved(id); endJobs[id]?.cancel(); publishTimers() }
    fun timerSwitch(id: String) { if (timer(id).switchMode()) { saved(id); publishTimers() } else alerts.stop() }
    /** Timer ring drag: a stopped timer's new focus length (saved like 카드 › 타이머) or a paused one's time left. */
    fun timerSet(id: String, minutes: Int) {
        if (timer(id).set(minutes * 60)) ConfigStore.update(ctx) { it.copy(focusMinutes = minutes) }
        Timers.save(ctx, id, el())
        publishTimers()
    }
    fun sleepToggle() {
        if (SleepBus.state.value.playing) { SleepService.stop(ctx); return }
        startSleep()
    }
    fun sleepPick(sound: SleepSound, toggle: Boolean) {
        val playing = SleepBus.state.value.playing
        if (toggle && playing && cfg.sleepSound == sound) { SleepService.stop(ctx); return }
        ConfigStore.update(ctx) { it.copy(sleepSound = sound) }
        if (toggle || playing) startSleep()
    }
    fun sleepMinutesNext() {
        ConfigStore.update(ctx) { c -> c.copy(sleepMinutes = SLEEP_MINUTES.firstOrNull { it > c.sleepMinutes } ?: SLEEP_MINUTES.first()) }
        if (SleepBus.state.value.playing) startSleep()
    }
    // ForegroundServiceStartNotAllowedException etc. must not take the dream down; SleepService.start already set "playing"
    private fun startSleep() {
        runCatching { SleepService.start(ctx, cfg.sleepSound, cfg.sleepMinutes) }.onFailure { SleepBus.state.value = SleepState() }
    }
    fun dismissNotification() { notifJob?.cancel(); endNotification(null) }

    /** plan-r15 C1 닫기: the alarm has no snooze/stop action here; hide its takeover until it ends. */
    private var alarmClosed: kr.dot.sb.platform.AlarmSource? = null
    fun closeAlarm() {
        alarmClosed = ListenerBus.alarm.value ?: return
        _state.update { it.copy(alarm = null) }
        machine.touch(el()); evaluate()
    }
    fun notificationIntentOf(key: String) = ListenerBus.contentIntents[key]
    fun notificationPackageOf(key: String) = _state.value.notifications.firstOrNull { it.key == key }?.packageName

    /** Shade cards: gone from the card at once (the row has already flown out), then from the phone. */
    fun clearNotifications(keys: List<String>) {
        val k = keys.toSet()
        _state.update { s -> s.copy(notifications = s.notifications.filterNot { it.key in k }) }
        ListenerBus.cancel(keys)
    }
    fun retryFeeds() { scope.launch { fetchWeather() }; scope.launch { fetchUsage() } }

    fun liveToggle() {
        _state.update { it.copy(liveExpanded = !it.liveExpanded) }
        liveJob?.cancel()
        if (_state.value.liveExpanded) liveJob = scope.launch { delay(30_000); _state.update { it.copy(liveExpanded = false) } } // 30 s idle closes
    }

    fun onConfiguration(smallestWidthDp: Int, initial: Boolean = false) {
        val u = smallestWidthDp >= 600 && hasHinge
        if (!u) MoodLatch.done = false // folded: the next unfold may light the mood light again
        if (u == _state.value.unfolded) return
        _state.update { it.copy(unfolded = u) }
        pushUi()
    }

    private fun pushUi() {
        val c = cfg
        val book = c.boards.indexOfFirst { it.id == "book" }
        _ui.value = if (_state.value.unfolded && c.onUnfold == UnfoldAction.BOOK_BOARD && book >= 0) c.copy(startPage = 3 + book) else c
    }

    companion object {
        /** Accelerometer batching (r11 perf): the most a pick-up waits for delivery. */
        private const val ACCEL_BATCH_US = 150_000
        private const val HEAT_LOG_MS = 10 * 60_000L
        /** Survives dream restarts: the night look is right on the first frame. */
        @Volatile private var lastNight = 0f
    }
}
