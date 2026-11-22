package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.Car

object Merge {
    /**
     * Server is canon. Cars and parks of keys that still have a car:/park: op in the queue keep the local value,
     * a pending DelCar car is not revived. Dropped (false) when the queue changed since [genBefore] was read.
     * Cars the server refused (CarStore.localOnly) stay with their parks. CarLinkStore is never touched here (CarStore
     * unlinks cars that leave, except pending join carry-over); ParkRules.accept is not used here.
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
        val serverIds = st.cars.map { it.id }.toSet()
        // refused PutCar (CarStore.localOnly): the server never had the car, so its absence is not a family deletion
        val localOnly = CarStore.localOnly(ctx).filter { it !in serverIds }.toSet()
        val keepLocal = CarStore.cars(ctx).filter { it.id in localOnly && it.id !in deleted && it.id !in localCars }
        // pending park/undo/state ops win over the pulled park until they are sent
        val keepPark = ops.filter { it is Op.PutPark || it is Op.Undo || it is Op.State }.map { it.carId }.toSet() + localOnly
        val shares = ops.filterIsInstance<Op.Share>()
        fun withShares(c: Car) = shares.filter { it.carId == c.id }.fold(c) { acc, s -> acc.copy(groups = if (s.on) (acc.groups + s.groupId).distinct() else acc.groups - s.groupId) }
        val cars = (st.cars.filter { it.id !in deleted && it.id !in localCars }.map(::withShares) + localCars.values.map(::withShares) + keepLocal).sortedBy { it.order }
        CarStore.setLocalOnly(ctx, localOnly)
        CarStore.replaceFromServer(ctx, cars)
        val ids = cars.map { it.id }.toSet()
        ParkStore.carIds(ctx).filter { it !in ids }.forEach { ParkStore.clearCar(ctx, it) }
        cars.filter { it.id !in keepPark }.forEach { ParkStore.setFromServer(ctx, it.id, st.parks[it.id]) }
        val before = GroupStore.groups(ctx)
        GroupStore.setState(ctx, st.me, st.groups)
        if (before.isNotEmpty()) before.filter { b -> st.groups.none { it.id == b.id } }.forEach { SyncEngine.notify(ctx, "'${it.name}' 그룹에서 나가게 됐어요") }
        Upgrade.afterPull(ctx, st)
    }
}
