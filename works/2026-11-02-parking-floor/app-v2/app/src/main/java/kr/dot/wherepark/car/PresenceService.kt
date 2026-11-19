package kr.dot.wherepark.car

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService

/** The system tells us when a linked car's Bluetooth appears or drops. */
class PresenceService : CompanionDeviceService() {
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        associationInfo.deviceMacAddress?.toString()?.let { ExitNotifier.onAppeared(this, it) }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        associationInfo.deviceMacAddress?.toString()?.let { ExitNotifier.onDisappeared(this, it) }
    }
}
