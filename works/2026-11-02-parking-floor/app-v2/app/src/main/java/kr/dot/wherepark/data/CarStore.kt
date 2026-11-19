package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.car.CarLink
import kr.dot.wherepark.core.Car
import org.json.JSONArray

object CarStore {
    private const val KEY = "wp_cars"
    private const val LOCAL = "wp_car_local"

    fun cars(ctx: Context): List<Car> = Prefs.arr(ctx, KEY).toList(Wire::car).sortedBy { it.order }

    private fun write(ctx: Context, cars: List<Car>) = Prefs.put(ctx, KEY, JSONArray(cars.map { Wire.carJson(it, 0) }))

    fun upsert(ctx: Context, c: Car) = synchronized(SyncQueue) { write(ctx, cars(ctx).filter { it.id != c.id } + c) }

    /** Removing a car also unlinks its Bluetooth (outside the lock: it talks to the system). */
    fun remove(ctx: Context, id: String) {
        synchronized(SyncQueue) { write(ctx, cars(ctx).filter { it.id != id }); setLocalOnly(ctx, localOnly(ctx) - id) }
        CarLink.unlink(ctx, id)
    }

    /**
     * Cars whose PutCar the server refused with 400/404/413 (SyncEngine.settle). The server never stored them, so a pull
     * would read "not on the server" as "deleted by family" and erase the car and its history. Merge keeps these cars and
     * their local parks instead; an id leaves the set once the server lists it (a later PutCar went through) or the car is removed.
     */
    fun localOnly(ctx: Context): Set<String> = Prefs.arr(ctx, LOCAL).let { a -> (0 until a.length()).map(a::getString).toSet() }
    fun setLocalOnly(ctx: Context, ids: Set<String>) = Prefs.put(ctx, LOCAL, if (ids.isEmpty()) null else JSONArray(ids.toList()))

    fun replaceFromServer(ctx: Context, cars: List<Car>) {
        val gone = synchronized(SyncQueue) {
            val old = cars(ctx).map { it.id }
            write(ctx, cars)
            old - cars.map { it.id }.toSet()
        }
        val carried = FamilyStore.carryOver(ctx).map { it.id }.toSet()  // join step 5 pending: CarLinkStore stays until declined
        gone.filter { it !in carried }.forEach { CarLink.unlink(ctx, it) }
    }
}
