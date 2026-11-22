package kr.dot.wherepark.core

import java.time.Instant
import java.time.ZoneId

/** Contract id: "c_" + 8 lowercase hex (the Worker routes only ^c_[0-9a-f]{8}$). */
fun newCarId(): String = "c_" + "%08x".format(java.security.SecureRandom().nextInt())

data class Car(val id: String, val name: String, val color: Int, val plate: String?, val order: Int,
    val ownerId: String? = null, val ownerName: String? = null, val groups: List<String> = emptyList())

fun Car.isMine(meId: String?) = ownerId == null || ownerId == meId

enum class ParkState { PARKED, DRIVING, LEFT }

data class Park(
    val id: Long, val carId: String, val spot: Spot, val atMs: Long, val byId: String, val byName: String,
    val raw: String?, val photo: Boolean, val state: ParkState, val drivingBy: String? = null,
    val byOther: Boolean = false, val drivingOther: Boolean = false, val leftAt: Long? = null, val drivingById: String? = null,
)

object CarResolver {
    /** Order: explicit id, bluetooth id, spoken name, last used, first by order. Unknown ids are skipped. */
    fun resolve(explicitCarId: String?, btCarId: String?, spokenCarName: String?, cars: List<Car>, lastUsedCarId: String?, meId: String? = null): Car? =
        cars.firstOrNull { it.id == explicitCarId }
            ?: cars.firstOrNull { it.id == btCarId }
            ?: spokenCarName?.let { n -> cars.filter { it.name == n }.sortedByDescending { it.isMine(meId) }.firstOrNull() }
            ?: cars.firstOrNull { it.id == lastUsedCarId }
            ?: cars.minByOrNull { it.order }
}

object ParkRules {
    fun accept(cur: Park?, incoming: Park): Boolean = cur == null || cur.id == incoming.id || incoming.atMs > cur.atMs

    fun whenLabel(atMs: Long, nowMs: Long, zone: ZoneId = ZoneId.of("Asia/Seoul")): String {
        val at = Instant.ofEpochMilli(atMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val d = at.toLocalDate()
        return when {
            !d.isBefore(today) -> "%02d:%02d".format(at.hour, at.minute)
            d == today.minusDays(1) -> "어제 %02d:%02d".format(at.hour, at.minute)
            else -> "${d.monthValue}월 ${d.dayOfMonth}일"
        }
    }
}
