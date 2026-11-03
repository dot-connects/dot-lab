package kr.dot.parkingfloor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.abs

/**
 * Runs only while the car Bluetooth is connected. Keeps the barometer trace of the drive (1 Hz, batched by the
 * sensor hub) and the last outdoor GPS fix other apps produced (passive, no fixes of our own). When the car
 * disconnects it turns the trace into a park, stores it, redraws the widget and stops.
 */
class DriveService : Service(), SensorEventListener2, LocationListener {
    companion object {
        const val ACTION_START = "kr.dot.parkingfloor.DRIVE_START"
        const val ACTION_PARK = "kr.dot.parkingfloor.DRIVE_PARK"
        private const val CHANNEL = "drive"
        private const val NOTE_ID = 2
        private const val MAX_SAMPLES = 3600
        private const val MAX_DRIVE_MS = 3 * 60 * 60 * 1000L
        private const val OUTDOOR_ACCURACY_M = 35f
        private const val PLACE_FIX_MAX_AGE_S = 3 * 60.0
        private const val WINDOW_FIX_MAX_AGE_S = 6 * 60.0
        private const val MAX_DELTA_M = 60.0
        private const val NAME_WAIT_MS = 10_000L
        private const val MIN_DRIVE_S = 90.0
        private const val MIN_MOVE_M = 1.5

        @Volatile var running = false
            private set

        fun start(context: Context, action: String) {
            try {
                context.startForegroundService(Intent(context, DriveService::class.java).setAction(action))
            } catch (e: Exception) {
                DriveLog.add(context, "start refused ($action): ${e.javaClass.simpleName}")
            }
        }

        /** Uses the barometer's wake-up variant when the phone has one, so the CPU can sleep while driving. */
        fun barometer(sensors: SensorManager): Pair<Sensor?, Boolean> {
            val wake = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE, true)
            return if (wake != null) wake to true else sensors.getDefaultSensor(Sensor.TYPE_PRESSURE) to false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val samples = ArrayDeque<ParkDetector.Sample>()
    private var lastSecond = -1L
    private var startedS = 0.0
    private var outdoorFix: Location? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var parking = false
    private var parkEndS = 0.0
    /** Between the disconnect and stop(); a reconnect then starts a new drive once this one is stored. */
    private var closing = false
    private var restart = false
    private val sensors by lazy { getSystemService(SensorManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // always satisfy the startForegroundService contract first
        val ok = try {
            startForeground(NOTE_ID, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            true
        } catch (e: Exception) {
            DriveLog.add(this, "startForeground failed: ${e.javaClass.simpleName}")
            false
        }
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PARK -> if (running) park() else stopSelf()
            else -> if (!running) begin() else if (closing) restart = true
        }
        return START_NOT_STICKY
    }

    private fun note(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "운전 중", NotificationManager.IMPORTANCE_MIN))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("주차 위치를 기억하는 중")
            .setOngoing(true)
            .build()
    }

    private fun begin() {
        running = true
        parking = false
        closing = false
        outdoorFix = null
        lastSecond = -1L
        samples.clear()
        startedS = nowS()
        val (sensor, wakeUp) = barometer(sensors)
        if (sensor == null) {
            DriveLog.add(this, "no barometer")
            stop()
            return
        }
        DriveLog.add(this, "drive start (wake-up barometer: $wakeUp)")
        if (!wakeUp) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "parkingfloor:drive").apply { acquire(MAX_DRIVE_MS) }
        }
        sensors.registerListener(this, sensor, 1_000_000, 60_000_000)
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                getSystemService(LocationManager::class.java)
                    .requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, this, Looper.getMainLooper())
            } catch (_: Exception) {
            }
        }
        handler.postDelayed({ stop() }, MAX_DRIVE_MS)
    }

    private fun nowS() = SystemClock.elapsedRealtimeNanos() / 1e9

    override fun onSensorChanged(event: SensorEvent) {
        val tS = event.timestamp / 1e9
        val second = tS.toLong()
        if (second == lastSecond) return
        lastSecond = second
        samples.addLast(ParkDetector.Sample(tS, event.values[0].toDouble()))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onFlushCompleted(sensor: Sensor?) {
        handler.post { finishPark() }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy <= OUTDOOR_ACCURACY_M) outdoorFix = location
    }

    private fun park() {
        if (parking || closing) return
        parking = true
        closing = true
        parkEndS = nowS()
        // pull what the sensor hub is still holding; fall back to a timer if the flush never reports
        if (!sensors.flush(this)) handler.post { finishPark() } else handler.postDelayed({ finishPark() }, 2500)
    }

    private fun finishPark() {
        if (!running) return
        if (!parking) return
        parking = false
        val waiting = try {
            record()
        } catch (e: Throwable) {
            DriveLog.add(this, "park failed: ${e.javaClass.simpleName}")
            false
        }
        // give the geocoder a moment; the service keeps the process alive meanwhile
        if (waiting) handler.postDelayed({ stop() }, NAME_WAIT_MS) else stop()
    }

    /** Stores the park. Returns true when a place name lookup is still running. */
    private fun record(): Boolean {
        val endS = parkEndS
        val anyFix = bestFix(endS)
        val fixAge = anyFix?.let { endS - it.elapsedRealtimeNanos / 1e9 }
        val windowFixS = if (anyFix != null && fixAge!! <= WINDOW_FIX_MAX_AGE_S) anyFix.elapsedRealtimeNanos / 1e9 else null
        val fix = anyFix?.takeIf { fixAge!! <= PLACE_FIX_MAX_AGE_S }
        val result = ParkDetector.detect(samples.toList(), endS, windowFixS)?.takeIf { abs(it.deltaM) <= MAX_DELTA_M }
        val driveS = endS - startedS
        DriveLog.add(this, "park: drive %.0fs, samples %d, delta %s, fix age %s".format(
            driveS, samples.size, result?.let { "%.1fm".format(it.deltaM) } ?: "none", fixAge?.let { "%.0fs".format(it) } ?: "none"))
        // a Bluetooth blip in the same spot must not overwrite a real park
        if (driveS < MIN_DRIVE_S && (result == null || abs(result.deltaM) < MIN_MOVE_M)) {
            DriveLog.add(this, "ignored: short drive")
            return false
        }

        val state = ParkStore.load(this)
        val place = fix?.let { PlaceResolver.match(state.places, it.latitude, it.longitude) ?: PlaceResolver.newPlace(it.latitude, it.longitude) }
        val estimate = result?.let { FloorModel.estimate(it.deltaM, place?.pairs.orEmpty()) }
        val park = Park(
            id = System.currentTimeMillis(),
            atMs = System.currentTimeMillis(),
            placeKey = place?.key,
            placeName = place?.name,
            lat = fix?.latitude,
            lng = fix?.longitude,
            deltaM = result?.deltaM,
            est = estimate?.floor,
            alt = estimate?.alt,
            confirmed = false,
            floor = estimate?.floor,
            pillar = "",
            color = estimate?.floor?.let { place?.colors?.get(it) } ?: 0,
        )
        ParkStore.add(this, park, place)
        ParkWidget.refresh(this)
        Family.changed(this)
        if (place != null && place.name == null && fix != null) {
            val appContext = applicationContext
            PlaceResolver.name(appContext, fix.latitude, fix.longitude) { name ->
                try {
                    if (name != null) {
                        ParkStore.updatePlace(appContext, place.key) { it.copy(name = name) }
                        ParkStore.update(appContext, park.id) { it.copy(placeName = name) }
                        ParkWidget.refresh(appContext)
                        Family.changed(appContext)
                    }
                    DriveLog.add(appContext, "place name: ${name ?: "none"}")
                } catch (_: Throwable) {
                }
                handler.post { if (running) stop() }
            }
            return true
        }
        return false
    }

    /** The last outdoor fix of this drive, or a recent last-known location from any provider. */
    private fun bestFix(endS: Double): Location? {
        outdoorFix?.let { return it }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = getSystemService(LocationManager::class.java)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } }
            .filter { endS - it.elapsedRealtimeNanos / 1e9 <= WINDOW_FIX_MAX_AGE_S }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    private fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            sensors.unregisterListener(this)
        } catch (_: Exception) {
        }
        try {
            getSystemService(LocationManager::class.java).removeUpdates(this)
        } catch (_: Exception) {
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        if (restart) {
            restart = false
            DriveLog.add(this, "reconnected while storing: new drive")
            begin()
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) stop()
        super.onDestroy()
    }
}
