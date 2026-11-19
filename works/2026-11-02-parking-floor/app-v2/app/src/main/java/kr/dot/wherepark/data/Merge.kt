package kr.dot.wherepark.data

import android.content.Context

object Merge {
    /**
     * Server is canon. Cars and parks of keys that still have a car:/park: op in the queue keep the local value,
     * a pending DelCar car is not revived. Dropped (false) when the queue changed since [genBefore] was read.
     * CarLinkStore is never touched; ParkRules.accept is not used here.
     */
    fun state(ctx: Context, st: ApiState, genBefore: Long): Boolean = synchronized(SyncQueue) {
        if (SyncQueue.generation(ctx) != genBefore) return@synchronized false
        write(ctx, st)
        true
    }

    internal fun write(ctx: Context, st: ApiState) {
        val ops = SyncQueue.ops(ctx)
        val deleted = ops.filterIsInstance<Op.DelCar>().map { it.carId }.toSet()
        val localCars = ops.filterIsInstance<Op.PutCar>().associate { it.car.id to it.car }
        val keepPark = ops.filterIsInstance<Op.PutPark>().map { it.carId }.toSet()
        val cars = (st.cars.filter { it.id !in deleted && it.id !in localCars } + localCars.values).sortedBy { it.order }
        CarStore.replaceFromServer(ctx, cars)
        val ids = cars.map { it.id }.toSet()
        ParkStore.carIds(ctx).filter { it !in ids }.forEach { ParkStore.clearCar(ctx, it) }
        cars.filter { it.id !in keepPark }.forEach { ParkStore.setFromServer(ctx, it.id, st.parks[it.id]) }
        FamilyStore.setState(ctx, st.groupName, st.members)
    }
}
