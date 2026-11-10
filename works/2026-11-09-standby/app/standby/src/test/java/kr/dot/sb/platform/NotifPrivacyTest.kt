package kr.dot.sb.platform

import kr.dot.sb.data.NotifPrivacy
import kr.dot.sb.data.NotifPrivacy.NO_OVERRIDE
import kr.dot.sb.data.NotifPrivacy.PRIVATE
import kr.dot.sb.data.NotifPrivacy.PUBLIC
import kr.dot.sb.data.NotifPrivacy.SECRET
import kr.dot.sb.data.Reveal
import kr.dot.sb.data.ShadeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The privacy mapping is where a mistake puts private text on the lock screen: every branch is pinned here. */
class NotifPrivacyTest {
    private fun reveal(
        v: Int = PRIVATE, channel: Int = NO_OVERRIDE, secure: Boolean = true, show: Boolean = true, allowPrivate: Boolean = true,
        pub: Boolean = false, tapOnly: Boolean = false,
    ) = NotifPrivacy.reveal(v, channel, secure, show, allowPrivate, pub, tapOnly)

    @Test fun showContentSettingShowsDefaultPrivateNotifications() {
        assertEquals(Reveal.FULL, reveal(PRIVATE, allowPrivate = true))
        assertEquals(Reveal.FULL, reveal(PUBLIC, allowPrivate = false))
    }

    @Test fun hideContentSettingHidesPrivateUnlessThereIsAPublicVersion() {
        assertEquals(Reveal.HIDDEN, reveal(PRIVATE, allowPrivate = false))
        assertEquals(Reveal.PUBLIC, reveal(PRIVATE, allowPrivate = false, pub = true))
    }

    @Test fun secretAndLockScreenNotificationsOffShowNothing() {
        assertEquals(Reveal.NONE, reveal(SECRET))
        assertEquals(Reveal.NONE, reveal(PUBLIC, show = false))
        assertEquals(Reveal.NONE, reveal(SECRET, tapOnly = true))
    }

    @Test fun theStricterOfAppAndChannelVisibilityWins() {
        assertEquals(Reveal.HIDDEN, reveal(PUBLIC, channel = PRIVATE, allowPrivate = false))
        assertEquals(Reveal.NONE, reveal(PUBLIC, channel = SECRET))
        assertEquals(Reveal.HIDDEN, reveal(PRIVATE, channel = PUBLIC, allowPrivate = false)) // a loose channel does not loosen the app
    }

    @Test fun phoneWithoutSecureLockShowsEverything() {
        assertEquals(Reveal.FULL, reveal(SECRET, secure = false, allowPrivate = false))
        assertEquals(Reveal.FULL, reveal(PRIVATE, secure = false, show = false))
    }

    @Test fun previewOnTapOnlyHidesWhatWouldShow() {
        assertEquals(Reveal.HIDDEN, reveal(PUBLIC, tapOnly = true))
        assertEquals(Reveal.HIDDEN, reveal(PRIVATE, allowPrivate = false, pub = true, tapOnly = true))
        assertEquals(Reveal.HIDDEN, reveal(PRIVATE, secure = false, tapOnly = true))
    }

    @Test fun hiddenTextNeverCarriesTheContent() {
        assertEquals("카카오톡" to "", NotifPrivacy.text(Reveal.HIDDEN, "카카오톡", "엄마", "저녁 먹었어?", "새 메시지", "1개"))
        assertEquals("카카오톡" to "", NotifPrivacy.text(Reveal.NONE, "카카오톡", "엄마", "저녁 먹었어?", null, null))
        assertEquals("새 메시지" to "1개", NotifPrivacy.text(Reveal.PUBLIC, "카카오톡", "엄마", "저녁 먹었어?", "새 메시지", "1개"))
        assertEquals("카카오톡" to "", NotifPrivacy.text(Reveal.PUBLIC, "카카오톡", "엄마", "저녁 먹었어?", null, null))
    }

    @Test fun fullTextIsFlattenedAndFallsBackToTheAppName() {
        assertEquals("엄마" to "저녁 먹었어? 내일 와", NotifPrivacy.text(Reveal.FULL, "카카오톡", " 엄마 ", "저녁 먹었어?\n 내일 와", null, null))
        assertEquals("쿠팡" to "", NotifPrivacy.text(Reveal.FULL, "쿠팡", "  ", null, null, null))
    }

    private fun include(
        pkg: String = "com.kakao.talk", category: String? = "msg", ongoing: Boolean = false, foreground: Boolean = false,
        summary: Boolean = false, fullScreen: Boolean = false, media: Boolean = false, importance: Int = 3, dndHidden: Boolean = false,
    ) = ShadeRules.include(pkg, "kr.dot.standby", category, ongoing, foreground, summary, fullScreen, media, importance, dndHidden)

    @Test fun shadeListsOrdinaryAndSilentNotifications() {
        assertTrue(include())
        assertTrue(include(importance = 2)) // silent section of the shade
        assertTrue(include(category = "missed_call"))
    }

    @Test fun shadeSkipsWhatStandbyShowsElsewhereOrTheShadeHides() {
        assertFalse(include(pkg = "kr.dot.standby"))
        assertFalse(include(ongoing = true))
        assertFalse(include(foreground = true))
        assertFalse(include(summary = true))
        assertFalse(include(media = true))
        assertFalse(include(category = "call"))
        assertFalse(include(category = "transport"))
        assertFalse(include(importance = 1))
        assertFalse(include(dndHidden = true))
        assertFalse(include(pkg = "com.sec.android.app.clockpackage", category = "alarm"))
    }
}
