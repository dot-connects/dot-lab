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
import android.os.SystemClock
import android.text.format.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.platform.Brightness
import kr.dot.sb.platform.ChargeStart
import kr.dot.sb.platform.ListenerBus
import kr.dot.sb.platform.SleepService
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.SleepState
import kr.dot.sb.ui.state.StandbyState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** What the screen host (dream or activity) must do for the holder. */
interface HostCallbacks {
    fun finish()
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
class StandbyStateHolder(app: Context, private val host: HostCallbacks, private val ownRotation: Boolean) {
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
    private var notifWokeAt = -1L
    private val media = MediaSource(ctx) { m -> _state.update { it.copy(media = m) } }

    private var lastLux: Float? = null
    private var nightTarget = lastNight
    private var wasFlat = false
    private var lastMotionTouch = 0L
    private var startedAt = 0L
    private var exited = false
    private var lastBrightness = Float.NaN
    private var page = 0
    private var started = false
    private var lastStaticAt = 0L
    private var lastWeatherAt = 0L
    private var lastUsageAt = 0L
    private var lastPhotosAt = 0L
    private var memoryDay: LocalDate? = null
    private var lastCity = cfg.city
    private var photoBucket: String? = null

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
        override fun onSensorChanged(e: SensorEvent) = onAccel(e.values[0], e.values[1], e.values[2])
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    /**
     * Charger out (wired or wireless): StandBy ends, however it was opened (r8: a tile or dream hand-over start stayed
     * on after the phone left the pad). Also re-arms the "start on charge" job at once (see [ChargeStart]).
     */
    private val unplugRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            ChargeStart.rearmNow(ctx)
            if (!exited) { exited = true; ChargeStart.log(ctx, "닫힘 · 충전기 뽑힘"); host.finish() }
        }
    }
    private val nextAlarmRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { scope.launch { refreshStatic(force = true) } }
    }

    fun start() {
        if (started) return
        started = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        exited = false
        startedAt = el()
        machine.touch(startedAt)
        nightSw.prime(lastNight >= 0.5f) // a dream restarted in the dark starts red, not day
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sm.registerListener(lightL, it, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000) } // 5 s batching
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(accelL, it, SensorManager.SENSOR_DELAY_UI) }
        ctx.registerReceiver(unplugRx, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), Context.RECEIVER_NOT_EXPORTED)
        ctx.registerReceiver(nextAlarmRx, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        media.start()
        onConfiguration(ctx.resources.configuration.smallestScreenWidthDp, initial = true)
        for ((id, t) in timers) { t.finishIfDone(el()); scheduleTimerEnd(id) }
        publishTimers()

        scope.launch {
            cfgFlow.collect { c ->
                _state.update {
                    var n = it.copy(use24h = c.use24h ?: DateFormat.is24HourFormat(ctx), worldCity = c.worldCities.firstOrNull() ?: it.worldCity)
                    if (!c.burnInShift) n = n.copy(shiftX = 0, shiftY = 0)
                    n
                }
                pushUi()
                if (c.city != lastCity) { lastCity = c.city; lastWeatherAt = 0; if (!_state.value.displayOff) launch { fetchWeather() } }
                evaluate()
            }
        }
        scope.launch {
            ListenerBus.alarm.collect { a ->
                _state.update { it.copy(alarm = a?.let { s -> AlarmRinging(s.label, true) }) }
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
                while (r) { delay(1_000); if (!_state.value.displayOff) publishTimers() }
            }
        }
        startVisible()
        evaluate()
    }

    fun stop() {
        if (!started) return
        started = false
        sm.unregisterListener(lightL); sm.unregisterListener(accelL)
        runCatching { ctx.unregisterReceiver(nextAlarmRx) }
        runCatching { ctx.unregisterReceiver(unplugRx) }
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
        )
        setNight(if (nightOn) 1f else 0f)
        val flat = motion.isFlat && c.displayOff != DisplayOff.NEVER // E12: 안 함 = never black
        val busy = _state.value.alarm != null || _state.value.notification != null
        val off = NightRules.displayOff(
            machine.evaluate(now, c.displayOff, lastLux, c.nightEnterLux, c.nightExitLux) || flat,
            _state.value.alarm != null, _state.value.notification != null,
        )
        setOff(off, nightOn)
        schedulePoll(now)
    }

    /** Sensors send nothing while the value is steady, so the end of a hold or of the idle time needs its own wake-up. */
    private fun schedulePoll(now: Long) {
        pollJob?.cancel()
        val waits = listOfNotNull(
            if (cfg.nightMode == NightMode.AUTO) nightSw.pendingMs(now) else null,
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

    /** Live from the on-screen bar: shown at once, written to the config once the finger has paused (not per frame). */
    fun setUserBrightness(v: Float) {
        val b = Brightness.clamp(v)
        lastBrightness = b
        host.setBrightness(b)
        pendingBrightness = b
        brightJob?.cancel()
        brightJob = scope.launch { delay(500); flushBrightness() }
    }

    private fun flushBrightness() {
        val b = pendingBrightness ?: return
        pendingBrightness = null
        ConfigStore.update(ctx) { Brightness.store(it, nightTarget >= 0.5f, b) }
    }

    private fun setOff(off: Boolean, nightOn: Boolean) {
        if (_state.value.displayOff != off) {
            _state.update { it.copy(displayOff = off) }
            if (off) { visibleJob?.cancel(); visibleJob = null } else startVisible()
        }
        // while a bar edit is still unsaved the config holds the old value: don't snap the screen back to it
        if (!off && pendingBrightness != null) return
        val b = Brightness.target(off, nightOn, cfg)
        if (b != lastBrightness) { lastBrightness = b; host.setBrightness(b) }
    }

    private fun onAccel(x: Float, y: Float, z: Float) {
        val now = el()
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
            launch { while (true) { delay(45_000); if (page == 1) reloadPhotos() } }
        }
    }

    private suspend fun minuteLoop() {
        while (true) {
            val t = LocalDateTime.now()
            val (sx, sy) = if (cfg.burnInShift) BurnIn.at(t.hour * 60 + t.minute) else (0 to 0)
            _state.update { it.copy(now = t.truncatedTo(ChronoUnit.MINUTES), shiftX = sx, shiftY = sy) }
            publishTimers()
            if (memoryDay != t.toLocalDate()) { memoryDay = t.toLocalDate(); scope.launch { loadMemory(t.toLocalDate()) } }
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

    private suspend fun refreshStatic(force: Boolean) {
        if (!force && el() - lastStaticAt < 60_000) return
        lastStaticAt = el()
        val now = LocalDateTime.now()
        val (upcoming, alarm, perms) = withContext(Dispatchers.IO) { Triple(DeviceSources.upcoming(ctx, now), DeviceSources.nextAlarm(ctx), DeviceSources.permissions(ctx)) }
        val agenda = upcoming?.filter { it.date == now.toLocalDate() }?.take(5)
        _state.update { it.copy(agenda = agenda, upcoming = upcoming.orEmpty(), nextAlarm = alarm, permissions = perms) }
    }

    private suspend fun fetchWeather(): Boolean {
        if (!Feeds.onWifi(ctx)) { markWeatherOffline(); return false }
        val city = cfg.city
        return try {
            val w = withContext(Dispatchers.IO) { WeatherParser.parse(Feeds.weatherText(city.lat, city.lon), city.name, System.currentTimeMillis() / 1000) }
            lastWeatherAt = System.currentTimeMillis()
            _state.update { it.copy(weather = w) }
            true
        } catch (e: Exception) { markWeatherOffline(); false }
    }

    private fun markWeatherOffline() = _state.update { it.copy(weather = it.weather?.copy(offline = true)) }

    private suspend fun fetchUsage(): Boolean {
        if (!cfg.usageEnabled) { lastUsageAt = System.currentTimeMillis(); return true }
        if (!Feeds.onWifi(ctx)) { markUsageOffline(); return false }
        return try {
            val u = withContext(Dispatchers.IO) { UsageParser.parse(Feeds.usageText(ctx), System.currentTimeMillis() / 1000) }
            lastUsageAt = System.currentTimeMillis()
            _state.update { it.copy(usage = u) }
            true
        } catch (e: Exception) { markUsageOffline(); false }
    }

    private fun markUsageOffline() = _state.update { it.copy(usage = it.usage?.copy(offline = true)) }

    private suspend fun reloadPhotos() {
        lastPhotosAt = el()
        val n = if (_state.value.memory != null) 2 else 3 // max 3 bitmaps in memory
        val b = photoBucket
        val have = _state.value.photoBuckets
        val (p, buckets) = withContext(Dispatchers.IO) { DeviceSources.photos(ctx, n, b) to have.ifEmpty { DeviceSources.buckets(ctx) } }
        _state.update { it.copy(photos = p, photoBuckets = buckets) }
    }

    private suspend fun loadMemory(day: LocalDate) {
        val m = withContext(Dispatchers.IO) { DeviceSources.memory(ctx, day) }
        _state.update { it.copy(memory = m) }
        reloadPhotos()
    }

    // ---- interrupts --------------------------------------------------------------------------------------------

    private fun showNotification(n: kr.dot.sb.ui.state.BigNotification) {
        if (_state.value.alarm != null) return // dropped while an alarm shows; it stays in the shade
        notifJob?.cancel()
        notifJob = scope.launch {
            val wait = 2_000 - (el() - notifShownAt) // a newer one replaces the shown one after >= 2 s
            if (_state.value.notification != null && wait > 0) delay(wait)
            notifShownAt = el()
            // woken from OFF by this notification: back to OFF afterwards unless the user touched the screen (spec I4)
            if (_state.value.notification == null) wasOffAtNotif = _state.value.displayOff
            _state.update { it.copy(notification = n) }
            machine.touch(el()); notifWokeAt = machine.lastTouch; evaluate()
            delay(6_500) // 6 s + fade
            endNotification(n.key)
        }
    }

    private fun endNotification(key: String?) {
        if (key != null && _state.value.notification?.key != key) return
        _state.update { it.copy(notification = null) }
        if (wasOffAtNotif && machine.lastTouch == notifWokeAt) machine.forceOff() else machine.touch(el())
        wasOffAtNotif = false
        evaluate()
    }

    // ---- pomodoro ----------------------------------------------------------------------------------------------

    private fun timer(id: String) = timers.getOrPut(id) { PomodoroTimer(cfg.focusMinutes * 60, cfg.breakMinutes * 60) }

    private fun publishTimers() {
        val now = el()
        timers.values.forEach { it.finishIfDone(now) }
        _state.update { it.copy(timers = timers.mapValues { (_, t) -> t.state(now) }) }
    }

    private fun scheduleTimerEnd(id: String) {
        endJobs[id]?.cancel()
        val t = timers[id] ?: return
        if (!t.running) return
        endJobs[id] = scope.launch {
            delay(t.leftMs(el()) + 50)
            if (t.finishIfDone(el())) { publishTimers(); machine.touch(el()); evaluate() } // wakes the screen when focus/break ends
        }
    }

    // ---- actions (called through HostActions) ------------------------------------------------------------------

    fun touch() { machine.touch(el()); evaluate() }
    fun onPage(p: Int) {
        page = p
        // "first entry = clock, then the last viewed page": remembered through startPage (not while the unfold rule steers it)
        if (!_state.value.unfolded) ConfigStore.update(ctx) { it.copy(startPage = p) }
        if (p == 1 && el() - lastPhotosAt > 30_000) scope.launch { reloadPhotos() }
    }
    /** Photos page theme swipe: null = 추천, else an album name (spec P1). */
    fun photoTheme(bucket: String?) { photoBucket = bucket; scope.launch { reloadPhotos() } }
    fun mediaPlayPause() = media.playPause()
    fun mediaNext() = media.next()
    fun mediaPrev() = media.prev()
    val mediaPackage get() = media.packageName
    fun timerToggle(id: String) { timer(id).toggle(el()); scheduleTimerEnd(id); publishTimers() }
    fun timerReset(id: String) { timer(id).reset(); endJobs[id]?.cancel(); publishTimers() }
    fun sleepToggle() {
        if (SleepBus.state.value.playing) { SleepService.stop(ctx); return }
        // ForegroundServiceStartNotAllowedException etc. must not take the dream down; SleepService.start already set "playing"
        runCatching { SleepService.start(ctx, cfg.sleepSound, cfg.sleepMinutes) }.onFailure { SleepBus.state.value = SleepState() }
    }
    fun dismissNotification() { notifJob?.cancel(); endNotification(null) }
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
        /** Survive dream restarts: a pomodoro keeps running and the night look is right on the first frame. */
        private val timers = HashMap<String, PomodoroTimer>()
        @Volatile private var lastNight = 0f
    }
}
