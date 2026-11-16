package kr.dot.catchpet.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import kr.dot.catchpet.MainActivity
import kr.dot.catchpet.pet.PetPrefs
import kr.dot.catchpet.pet.PetService
import kr.dot.catchpet.ui.petId

/**
 * A tap on the widget's pet (operator 11-16, mockup widget-sizes v2): float the pet, or put it down. No screen of
 * its own; an activity (not a broadcast) so starting the pet's foreground service is always allowed. Without a pet
 * or the overlay permission it opens the app's pet screen instead.
 */
class WidgetToggle : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            PetPrefs.overlayOn(this) -> PetService.stop(this)
            petId(this) == null || !Settings.canDrawOverlays(this) ->
                startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN_PET, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            else -> PetService.start(this)
        }
        finish()
    }
}
