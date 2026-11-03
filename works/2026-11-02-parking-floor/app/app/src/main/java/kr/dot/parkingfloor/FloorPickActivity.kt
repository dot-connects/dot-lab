package kr.dot.parkingfloor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "직접 입력": pick the floor by hand. Confirms the latest park (and teaches its place), or, opened with
 * [EXTRA_NEW], records a new park at the last known location for a park the app missed.
 */
class FloorPickActivity : Activity() {
    companion object {
        const val EXTRA_NEW = "new"
        private val FLOORS = (-6..-1).toList() + (0..9).toList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fresh = intent.getBooleanExtra(EXTRA_NEW, false)
        val park = if (fresh) null else ParkStore.latest(this)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = if (fresh) "어느 층에 주차했나요?" else "몇 층이에요?"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        park?.placeName?.let { root.addView(TextView(this).apply { text = it; textSize = 14f }) }
        val grid = GridLayout(this).apply { columnCount = 4 }
        FLOORS.forEach { floor ->
            val label = FloorMath.labelOf(floor)
            grid.addView(Button(this).apply {
                text = label
                textSize = 18f
                isAllCaps = false
                if (park?.est == floor) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setOnClickListener { choose(park, floor) }
            }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0
                height = WRAP_CONTENT
            })
        }
        root.addView(grid, MATCH_PARENT, WRAP_CONTENT)
        setContentView(root)
    }

    private fun choose(park: Park?, floor: Int) {
        if (park != null) {
            ParkStore.confirm(this, park.id, floor)
        } else {
            val fix = lastLocation()
            val state = ParkStore.load(this)
            val place = fix?.let { PlaceResolver.match(state.places, it.latitude, it.longitude) ?: PlaceResolver.newPlace(it.latitude, it.longitude) }
            val now = System.currentTimeMillis()
            ParkStore.add(this, Park(now, now, place?.key, place?.name, fix?.latitude, fix?.longitude, null, floor, null, true, floor, "",
                place?.colors?.get(floor) ?: 0), place)
            if (place != null && place.name == null && fix != null) {
                val app = applicationContext
                PlaceResolver.name(app, fix.latitude, fix.longitude) { name ->
                    try {
                        if (name != null) {
                            ParkStore.updatePlace(app, place.key) { it.copy(name = name) }
                            ParkStore.update(app, now) { it.copy(placeName = name) }
                            ParkWidget.refresh(app)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        ParkWidget.refresh(this)
        finish()
    }

    private fun lastLocation(): Location? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = getSystemService(LocationManager::class.java)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }
            .filter { android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= 10 * 60 * 1_000_000_000L }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }
}
