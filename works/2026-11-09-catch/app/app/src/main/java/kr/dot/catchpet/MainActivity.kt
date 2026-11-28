package kr.dot.catchpet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import kr.dot.catchpet.ui.App

class MainActivity : ComponentActivity() {
    /** Bumped when the pet's cloud or a widget asks for a screen; [openTo] says which ("pet" / "dex" / "catch"). */
    private val openSeq = mutableIntStateOf(0)
    private var openTo = "pet"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) take(intent)
        setContent { App(openSeq.intValue, openTo) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    override fun onResume() {
        super.onResume()
        kr.dot.catchpet.pet.PetPrefs.setOpenedAt(this, System.currentTimeMillis())   // the pet's "보고 싶었어" counts from here
        kr.dot.catchpet.pet.Weather.locate(this)   // weather on: the coarse location, only while the app is open
    }

    private fun take(i: Intent?) {
        val to = when {
            i?.getBooleanExtra(OPEN_PET, false) == true -> "pet"
            else -> i?.getStringExtra(OPEN) ?: return
        }
        openTo = to; openSeq.intValue++
    }

    companion object {
        const val OPEN_PET = "open_pet"
        /** "dex" or "catch": a widget's button (spec-extras 2). */
        const val OPEN = "open"
    }
}
