package kr.dot.catchpet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import kr.dot.catchpet.ui.App

class MainActivity : ComponentActivity() {
    /** Bumped when the pet's cloud asks for the pet settings; the app switches to that screen. */
    private val openPet = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) take(intent)
        setContent { App(openPet.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(i: Intent?) { if (i?.getBooleanExtra(OPEN_PET, false) == true) openPet.intValue++ }

    companion object { const val OPEN_PET = "open_pet" }
}
