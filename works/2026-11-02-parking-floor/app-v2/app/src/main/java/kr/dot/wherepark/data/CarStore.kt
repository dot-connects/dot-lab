package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.car.CarLink
import kr.dot.wherepark.core.Car
import org.json.JSONArray

object CarStore {
    private const val KEY = "wp_cars"

    fun cars(ctx: Context): List<Car> = Prefs.arr(ctx, KEY).toList(Wire::car).sortedBy { it.order }

    private fun write(ctx: Context, cars: List<Car>) = Prefs.put(ctx, KEY, JSONArray(cars.map { Wire.carJson(it, 0) }))

    fun upsert(ctx: Context, c: Car) = synchronized(SyncQueue) { write(ctx, cars(ctx).filter { it.id != c.id } + c) }

    /** Removing a car also unlinks its Bluetooth (outside the lock: it talks to the system). */
    fun remove(ctx: Context, id: String) {
        synchronized(SyncQueue) { write(ctx, cars(ctx).filter { it.id != id }) }
        CarLink.unlink(ctx, id)
    }

    fun replaceFromServer(ctx: Context, cars: List<Car>) {
        val gone = synchronized(SyncQueue) {
            val old = cars(ctx).map { it.id }
            write(ctx, cars)
            old - cars.map { it.id }.toSet()
        }
        gone.forEach { CarLink.unlink(ctx, it) }
    }
}
