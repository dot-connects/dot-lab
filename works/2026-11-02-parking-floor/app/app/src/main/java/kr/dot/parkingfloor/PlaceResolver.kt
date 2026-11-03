package kr.dot.parkingfloor

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import java.util.Locale

/** Matches a GPS fix to a known garage, or makes a new one, and names it with the platform geocoder (no key). */
object PlaceResolver {
    private const val SAME_PLACE_M = 120f

    fun match(places: List<Place>, lat: Double, lng: Double): Place? {
        val out = FloatArray(1)
        return places.minByOrNull { p ->
            Location.distanceBetween(lat, lng, p.lat, p.lng, out)
            out[0]
        }?.takeIf { p ->
            Location.distanceBetween(lat, lng, p.lat, p.lng, out)
            out[0] <= SAME_PLACE_M
        }
    }

    fun newPlace(lat: Double, lng: Double): Place =
        Place("p${System.currentTimeMillis()}", null, lat, lng, emptyList(), emptyMap())

    /** Looks the name up in the background; [done] gets null when there is no network or no result. */
    fun name(context: Context, lat: Double, lng: Double, done: (String?) -> Unit) {
        if (!Geocoder.isPresent()) return done(null)
        try {
            Geocoder(context, Locale.KOREA).getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = done(addresses.firstOrNull()?.let(::label))
                override fun onError(errorMessage: String?) = done(null)
            })
        } catch (_: Exception) {
            done(null)
        }
    }

    /** A building name when the geocoder has one, else the street and number. */
    fun label(a: Address): String? {
        val premises = a.premises?.takeIf { it.isNotBlank() }
        val feature = a.featureName?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() || c == '-' } }
        val street = listOfNotNull(a.thoroughfare ?: a.subLocality, a.subThoroughfare).joinToString(" ").takeIf { it.isNotBlank() }
        return premises ?: feature ?: street ?: a.locality
    }
}
