package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.Car
import org.json.JSONArray

object CarStore {
    private const val KEY = "wp_cars"

    fun cars(ctx: Context): List<Car> = Prefs.arr(ctx, KEY).toList(Wire::car).sortedBy { it.order }

    private fun write(ctx: Context, cars: List<Car>) = Prefs.put(ctx, KEY, JSONArray(cars.map { Wire.carJson(it, 0) }))

    fun upsert(ctx: Context, c: Car) = synchronized(SyncQueue) { write(ctx, cars(ctx).filter { it.id != c.id } + c) }

    fun remove(ctx: Context, id: String) = synchronized(SyncQueue) { write(ctx, cars(ctx).filter { it.id != id }) }

    fun replaceFromServer(ctx: Context, cars: List<Car>) = synchronized(SyncQueue) { write(ctx, cars) }
}
