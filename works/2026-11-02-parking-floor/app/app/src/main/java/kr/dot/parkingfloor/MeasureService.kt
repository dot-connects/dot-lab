package kr.dot.parkingfloor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One measurement session. Everything runs on the main thread, so the fields need no locks. */
class MeasureService : Service(), SensorEventListener, LocationListener {
    companion object {
        const val EXTRA_PLACE = "place"
        const val EXTRA_CAR = "car"
        const val EXTRA_FLOOR = "floor"
        private const val SESSION_MS = 3 * 60 * 60 * 1000L
        private const val SAMPLE_US = 200_000
        private const val BASELINE_S = 5.0
        private const val LABEL_WINDOW_S = 5.0
        private const val MIN_BASELINE_SAMPLES = 3
        private const val MAX_CLOCK_OFFSET_NS = 2_000_000_000L

        /** The running session, or null when idle. Lives only as long as the process. */
        var current: MeasureService? = null
            private set
    }

    var place = ""; private set
    var car = false; private set
    var baseIndex = 0; private set
    var baseHpa: Double? = null; private set
    var lastHpa: Double? = null; private set
    var lastDeltaM: Double? = null; private set
    var lastEstimate: Int? = null; private set
    var samples = 0; private set
    var btEvents = 0; private set
    var gpsActive = false; private set
    var lastFix: Location? = null; private set
    var lastParkMs: Long? = null; private set
    var lastMark: String? = null; private set
    var sensorFailed = false; private set
    var btReady = false; private set
    private class Mark(val tS: Double, val trueIndex: Int, val previousTrue: Int?, val previousParkMs: Long?)
    private var undoable: Mark? = null
    private var lastTrue: Int? = null
    private var startNs = 0L
    private var clockChecked = false
    private var useArrivalClock = false
    private var collectFromS = 0.0
    private var collectUntilS: Double? = null
    private val collected = ArrayList<Double>()
    private var bucketSecond = -1L
    private val bucket = ArrayList<Double>()
    private val recent = ArrayDeque<Pair<Double, Double>>()
    private var writer: BufferedWriter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var receiverOn = false
    private val handler = Handler(Looper.getMainLooper())
    private val endTimer = Runnable { finish("time limit") }
    private val wall = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            val name = runCatching { device?.name }.getOrNull().orEmpty()
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            btEvents++
            row("bt", note = "${intent.action?.substringAfterLast('.')} $name ${if (state >= 0) state else ""}".trim())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (current != null) return START_NOT_STICKY
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        place = intent.getStringExtra(EXTRA_PLACE).orEmpty()
        car = intent.getBooleanExtra(EXTRA_CAR, false)
        baseIndex = intent.getIntExtra(EXTRA_FLOOR, 0)
        startInForeground()
        startNs = SystemClock.elapsedRealtimeNanos()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val file = File(filesDir, "session-$stamp-${if (car) "car" else "foot"}.csv")
        writer = file.bufferedWriter().also {
            it.write("﻿type,t_s,wall,place,mode,pressure_hpa,base_hpa,base_index,est_index,est_label,delta_m,floor_m,true_index,true_label,lat,lng,acc_m,fix_age_s,note\n")
            it.flush()
        }
        current = this
        row("start")
        beginBaseline(0.0)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "parkingfloor:measure")
            .also { it.acquire(SESSION_MS + 60_000) }
        val sensors = getSystemService(SensorManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)
        sensorFailed = sensor == null || !sensors.registerListener(this, sensor, SAMPLE_US)
        if (sensorFailed) row("note", note = "sensor registration failed")
        if (car) startCarInputs()
        handler.postDelayed(endTimer, SESSION_MS)
        return START_NOT_STICKY
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocation() =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun startInForeground() {
        val note = notification()
        val useLocation = car && hasLocation() && getSystemService(LocationManager::class.java).isLocationEnabled
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        try {
            startForeground(1, note, if (useLocation) base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else base)
            gpsActive = useLocation
        } catch (_: SecurityException) {
            startForeground(1, note, base)
            gpsActive = false
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("measure", "측정 중", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, "measure")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("주차층 측정 중")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startCarInputs() {
        if (gpsActive) {
            try {
                getSystemService(LocationManager::class.java)
                    .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            } catch (_: SecurityException) {
                gpsActive = false
            } catch (_: IllegalArgumentException) {
                gpsActive = false
            }
        }
        if (granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            registerReceiver(btReceiver, filter, RECEIVER_EXPORTED)
            receiverOn = true
            btReady = true
        }
    }

    override fun onLocationChanged(location: Location) {
        lastFix = location
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val arrivalNs = SystemClock.elapsedRealtimeNanos()
        if (!clockChecked) {
            clockChecked = true
            val offsetNs = event.timestamp - arrivalNs
            useArrivalClock = kotlin.math.abs(offsetNs) > MAX_CLOCK_OFFSET_NS
            row("note", note = "sensor clock offset ${offsetNs / 1_000_000} ms, arrival clock $useArrivalClock")
        }
        val eventNs = if (useArrivalClock) arrivalNs else event.timestamp
        val second = (eventNs - startNs) / 1_000_000_000L
        if (second != bucketSecond) {
            if (bucket.isNotEmpty()) sample(bucketSecond.toDouble(), FloorMath.median(bucket))
            bucket.clear()
            bucketSecond = second
        }
        bucket.add(event.values[0].toDouble())
    }

    private fun sample(tS: Double, hpa: Double) {
        lastHpa = hpa
        samples++
        recent.addLast(tS to hpa)
        while (recent.size > 3) recent.removeFirst()
        val until = collectUntilS
        if (until != null) {
            if (tS >= collectFromS) collected.add(hpa)
            if (tS >= until && collected.size >= MIN_BASELINE_SAMPLES) {
                baseHpa = FloorMath.median(collected)
                collectUntilS = null
                row("base", tS = tS, hpa = baseHpa)
            }
        }
        val base = baseHpa
        if (base != null) {
            lastDeltaM = FloorMath.heightM(hpa, base)
            lastEstimate = FloorMath.estimate(baseIndex, lastDeltaM!!)
        }
        row("sample", tS = tS, hpa = hpa, withEstimate = base != null)
    }

    fun nowS() = (SystemClock.elapsedRealtimeNanos() - startNs) / 1e9

    private fun beginBaseline(fromS: Double) {
        baseHpa = null
        lastDeltaM = null
        lastEstimate = null
        collected.clear()
        collectFromS = fromS
        collectUntilS = fromS + BASELINE_S
    }

    /** Re-reference at the last tapped floor, or at the start floor when none was tapped. */
    fun rebase() {
        lastTrue?.let { baseIndex = it }
        undoable = null
        row("rebase", note = "base floor ${FloorMath.labelOf(baseIndex)}")
        beginBaseline(nowS())
        lastMark = "기준 다시: ${FloorMath.labelOf(baseIndex)}"
    }

    fun mark(trueIndex: Int, park: Boolean) {
        val tS = nowS()
        val fresh = recent.filter { tS - it.first <= LABEL_WINDOW_S }.map { it.second }
        val hpa = if (fresh.isEmpty()) null else FloorMath.median(fresh)
        val base = baseHpa
        val delta = if (hpa != null && base != null) FloorMath.heightM(hpa, base) else null
        val estimate = delta?.let { FloorMath.estimate(baseIndex, it) }
        row(if (park) "park" else "label", tS = tS, hpa = hpa, delta = delta, estimate = estimate, trueIndex = trueIndex)
        undoable = Mark(tS, trueIndex, lastTrue, lastParkMs)
        lastTrue = trueIndex
        if (park) {
            lastParkMs = SystemClock.elapsedRealtime()
            // The widget is a design prototype; it must never take the measurement down with it.
            try {
                ParkWidget.setParked(this, FloorMath.labelOf(trueIndex), place)
            } catch (e: Throwable) {
                row("note", note = "widget update failed: ${e.javaClass.simpleName}")
            }
        }
        lastMark = "실제 ${FloorMath.labelOf(trueIndex)} · 추정 ${estimate?.let(FloorMath::labelOf) ?: "없음"}"
    }

    fun undo() {
        val target = undoable ?: return
        row("undo", trueIndex = target.trueIndex, note = "cancels t_s %.1f".format(Locale.ROOT, target.tS))
        lastTrue = target.previousTrue
        lastParkMs = target.previousParkMs
        undoable = null
        lastMark = "마지막 기록 취소"
    }

    fun finish(reason: String = "button") {
        if (writer == null) return
        row("end", note = reason)
        stopSelf()
        cleanUp()
    }

    private fun row(
        type: String,
        tS: Double = nowS(),
        hpa: Double? = null,
        withEstimate: Boolean = false,
        delta: Double? = if (withEstimate) lastDeltaM else null,
        estimate: Int? = if (withEstimate) lastEstimate else null,
        trueIndex: Int? = null,
        note: String = "",
    ) {
        val out = writer ?: return
        val fix = if (type == "sample" || type == "park" || type == "label") lastFix else null
        val fixAge = fix?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1e9 }
        val columns = listOf(
            type, "%.1f".format(Locale.ROOT, tS), wall.format(Date()), FloorMath.csv(place), if (car) "car" else "foot",
            hpa?.let { "%.3f".format(Locale.ROOT, it) }.orEmpty(),
            baseHpa?.let { "%.3f".format(Locale.ROOT, it) }.orEmpty(),
            baseIndex.toString(), estimate?.toString().orEmpty(), estimate?.let(FloorMath::labelOf).orEmpty(),
            delta?.let { "%.2f".format(Locale.ROOT, it) }.orEmpty(), FLOOR_HEIGHT_M.toString(),
            trueIndex?.toString().orEmpty(), trueIndex?.let(FloorMath::labelOf).orEmpty(),
            fix?.latitude?.toString().orEmpty(), fix?.longitude?.toString().orEmpty(),
            fix?.accuracy?.toString().orEmpty(), fixAge?.let { "%.0f".format(Locale.ROOT, it) }.orEmpty(),
            FloorMath.csv(note),
        )
        out.write(columns.joinToString(",") + "\n")
        out.flush()
    }

    private fun cleanUp() {
        handler.removeCallbacks(endTimer)
        getSystemService(SensorManager::class.java).unregisterListener(this)
        if (gpsActive) runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        if (receiverOn) unregisterReceiver(btReceiver)
        receiverOn = false
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        writer?.close()
        writer = null
        current = null
    }

    override fun onDestroy() {
        if (writer != null) row("end", note = "service destroyed")
        cleanUp()
        super.onDestroy()
    }
}
