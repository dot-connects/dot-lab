package kr.dot.sb.data

import kr.dot.sb.data.PhotoRules.Grant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PhotoRulesTest {
    @Test fun cameraNamesGiveTheirDate() {
        val t = LocalDateTime.of(2023, 11, 9, 15, 30, 12)
        assertEquals(t, PhotoRules.nameDate("20231109_153012.jpg"))           // Samsung
        assertEquals(t, PhotoRules.nameDate("IMG_20231109_153012.jpg"))       // AOSP / many
        assertEquals(t, PhotoRules.nameDate("PXL_20231109_153012345.jpg"))    // Pixel, millis after the seconds
        assertEquals(t, PhotoRules.nameDate("KakaoTalk_20231109_153012345.jpg"))
        assertEquals(LocalDateTime.of(2023, 11, 9, 0, 0), PhotoRules.nameDate("IMG-20231109-WA0001.jpg")) // date only
    }

    @Test fun otherNamesFallBackToModified() {
        assertNull(PhotoRules.nameDate("Screenshot_20231109-153012_One UI Home.jpg"))
        assertNull(PhotoRules.nameDate("여행 사진.jpg"))
        assertNull(PhotoRules.nameDate("1699512345678.jpg"))     // epoch millis, not a date
        assertNull(PhotoRules.nameDate("IMG_20231399_153012.jpg")) // no month 13
        assertNull(PhotoRules.nameDate("IMG_202311091.jpg"))       // nine digits: not a date
    }

    @Test fun onlyAMissingGrantDropsAFolder() {
        assertEquals(Grant.DROP, PhotoRules.grant(persisted = false, readOk = false))
        assertEquals(Grant.DROP, PhotoRules.grant(persisted = false, readOk = true))
        assertEquals(Grant.RETRY, PhotoRules.grant(persisted = true, readOk = false)) // SD card out: keep, ask again
        assertEquals(Grant.KEEP, PhotoRules.grant(persisted = true, readOk = true))
    }
}
