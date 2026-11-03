package kr.dot.parkingfloor

import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The system tells this service when the paired car connects or drops (CompanionDeviceManager presence).
 * Being a companion app is also what lets [DriveService] start from the background.
 */
class CarPresenceService : CompanionDeviceService() {
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        if (isCar(associationInfo)) DriveService.start(this, DriveService.ACTION_START)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if (isCar(associationInfo)) DriveService.start(this, DriveService.ACTION_PARK)
    }

    /** Only the car chosen in settings; an old association left behind must not wake the app. */
    private fun isCar(info: AssociationInfo): Boolean {
        val car = Settings.carAddress(this) ?: return false
        return info.deviceMacAddress?.toString().equals(car, ignoreCase = true)
    }

    @Deprecated("API 31-32 callbacks")
    override fun onDeviceAppeared(address: String) = DriveService.start(this, DriveService.ACTION_START)

    @Deprecated("API 31-32 callbacks")
    override fun onDeviceDisappeared(address: String) = DriveService.start(this, DriveService.ACTION_PARK)
}

/** Backup trigger: the plain Bluetooth connect/disconnect broadcast for the saved car. */
class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val car = Settings.carAddress(context) ?: return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        if (!device.address.equals(car, ignoreCase = true)) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> DriveService.start(context, DriveService.ACTION_START)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (DriveService.running) DriveService.start(context, DriveService.ACTION_PARK)
        }
    }
}
