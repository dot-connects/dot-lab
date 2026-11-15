package kr.dot.catchpet.pet

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Optional (operator 11-15): tells the floating pet where the keyboard's top edge is, so it stands above it instead
 * of covering what is being typed. An overlay gets no keyboard signal of its own; the accessibility window list names
 * the keyboard window. Only window changes are read, no screen text (same as 눈금 KeyboardWatch).
 */
class KeyboardWatch : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        PetService.keyboard(ime?.let { Rect().also(it::getBoundsInScreen).top })
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        PetService.keyboard(null)
        super.onDestroy()
    }
}
