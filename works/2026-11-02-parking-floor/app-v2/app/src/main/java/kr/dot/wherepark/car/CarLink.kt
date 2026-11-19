package kr.dot.wherepark.car

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import kr.dot.wherepark.data.CarLinkStore

/** The few CompanionDeviceManager calls we use, so unlink can be tested without a real manager. */
interface Cdm {
    /** (association id, mac address) of every association this app holds. */
    fun associations(): List<Pair<Int, String?>>
    fun disassociate(id: Int)
    fun stopObserving(address: String)
}

private class RealCdm(ctx: Context) : Cdm {
    private val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
    override fun associations() = cdm.myAssociations.map { it.id to it.deviceMacAddress?.toString() }
    override fun disassociate(id: Int) = cdm.disassociate(id)

    @Suppress("DEPRECATION")
    override fun stopObserving(address: String) = cdm.stopObservingDevicePresence(address)
}

/** Per-car Bluetooth link: one companion association per car; removing a car never touches the others. */
object CarLink {
    const val REQ_ASSOC = 41
    const val REQ_BT = 42

    fun norm(address: String): String = address.uppercase()

    fun hasPermission(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun requestPermission(activity: Activity) = activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BT)

    /** Shows the system device picker; on success stores the address for [carId] and starts presence observing. */
    @SuppressLint("MissingPermission")
    fun associate(activity: Activity, carId: String, onDone: (Boolean) -> Unit = {}) {
        val cdm = activity.getSystemService(CompanionDeviceManager::class.java)
        val request = AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter.Builder().build()).build()
        cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                try {
                    activity.startIntentSenderForResult(intentSender, REQ_ASSOC, null, 0, 0, 0)
                } catch (_: Exception) {
                    onDone(false)
                }
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val address = associationInfo.deviceMacAddress?.toString()?.let(::norm)
                if (address == null) return onDone(false)
                CarLinkStore.set(activity, carId, address)
                try {
                    @Suppress("DEPRECATION")
                    cdm.startObservingDevicePresence(address)
                    onDone(true)
                } catch (_: Exception) {
                    onDone(false)
                }
            }

            override fun onFailure(error: CharSequence?) = onDone(false)
        })
    }

    /** Removes only [carId]'s association and link. Other cars keep theirs. */
    fun unlink(ctx: Context, carId: String, cdm: Cdm = RealCdm(ctx)) {
        val address = CarLinkStore.address(ctx, carId) ?: return
        cdm.associations().filter { it.second?.let(::norm) == norm(address) }.forEach { (id, addr) ->
            try {
                addr?.let(cdm::stopObserving)
                cdm.disassociate(id)
            } catch (_: Exception) {
                // already gone
            }
        }
        CarLinkStore.set(ctx, carId, null)
    }
}
