package kr.dot.wherepark.car

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
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
    private val cdm: CompanionDeviceManager? = ctx.getSystemService(CompanionDeviceManager::class.java)
    override fun associations() = cdm?.myAssociations?.map { it.id to it.deviceMacAddress?.toString() }.orEmpty()
    override fun disassociate(id: Int) { cdm?.disassociate(id) }

    @Suppress("DEPRECATION")
    override fun stopObserving(address: String) { cdm?.stopObservingDevicePresence(address) }
}

/** Per-car Bluetooth link: one companion association per car; removing a car never touches the others. */
object CarLink {
    const val REQ_ASSOC = 41
    const val REQ_BT = 42

    fun norm(address: String): String = address.uppercase()

    fun hasPermission(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun requestPermission(activity: Activity) = activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BT)

    /** A Bluetooth device the phone is already paired with, by its readable name. */
    data class Paired(val name: String, val address: String, val car: Boolean)

    /** Paired devices, car audio first. Needs BLUETOOTH_CONNECT; empty without it or with Bluetooth off. */
    @SuppressLint("MissingPermission")
    fun paired(ctx: Context): List<Paired> = try {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().map { d ->
            val c = d.bluetoothClass?.deviceClass
            Paired(d.alias ?: d.name ?: d.address, norm(d.address), c == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || c == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE)
        }.sortedWith(compareBy({ !it.car }, { it.name }))
    } catch (_: SecurityException) {
        emptyList()
    }

    /**
     * Links [carId] through the system companion dialog: with [address] it only confirms that one paired device,
     * without it the system scans nearby devices. On success stores the address and starts presence observing.
     */
    @SuppressLint("MissingPermission")
    fun associate(activity: Activity, carId: String, address: String? = null, onDone: (Boolean) -> Unit = {}) {
        val cdm = activity.getSystemService(CompanionDeviceManager::class.java)
        val filter = BluetoothDeviceFilter.Builder().apply { if (address != null) setAddress(address) }.build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(address != null).build()
        cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                try {
                    activity.startIntentSenderForResult(intentSender, REQ_ASSOC, null, 0, 0, 0)
                } catch (_: Exception) {
                    onDone(false)
                }
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val got = associationInfo.deviceMacAddress?.toString()?.let(::norm)
                if (got == null) return onDone(false)
                val old = CarLinkStore.address(activity, carId)
                if (old != null && norm(old) != got) dropAssociation(activity, carId, RealCdm(activity))
                CarLinkStore.set(activity, carId, got)
                try {
                    @Suppress("DEPRECATION")
                    cdm.startObservingDevicePresence(got)
                    onDone(true)
                } catch (_: Exception) {
                    // no presence callbacks means a useless link: roll it back
                    try { cdm.disassociate(associationInfo.id) } catch (_: Exception) {}
                    CarLinkStore.set(activity, carId, null)
                    onDone(false)
                }
            }

            override fun onFailure(error: CharSequence?) = onDone(false)
        })
    }

    /** Removes only [carId]'s association and link, and its exit-notice state. Other cars keep theirs. Safe with no association. */
    fun unlink(ctx: Context, carId: String, cdm: Cdm = RealCdm(ctx)) {
        dropAssociation(ctx, carId, cdm)
        ExitNotifier.forget(ctx, carId)
    }

    internal fun dropAssociation(ctx: Context, carId: String, cdm: Cdm) {
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
