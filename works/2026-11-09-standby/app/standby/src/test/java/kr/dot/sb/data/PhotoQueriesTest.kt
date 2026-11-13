package kr.dot.sb.data

import kr.dot.sb.model.PhotoSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoQueriesTest {
    private val picks = PhotoQueries.of(PhotoSource.PICKS, emptyList(), emptySet())

    @Test fun picksReadsFavouritesThenRecent() {
        assertEquals(listOf("is_favorite=1", "datetaken>0"), picks.map { it.selection })
    }

    @Test fun recentIsTheNewest200() {
        assertEquals(listOf(PhotoQuery("datetaken>0", emptyList(), 200)), PhotoQueries.of(PhotoSource.RECENT, listOf("Camera"), setOf("Camera")))
    }

    @Test fun foldersKeepOnlyAlbumsThatStillExist() {
        val q = PhotoQueries.of(PhotoSource.FOLDERS, listOf("Camera", "지운 앨범", "여행"), setOf("Camera", "여행", "Screenshots")).single()
        assertEquals("bucket_display_name IN (?,?)", q.selection)
        assertEquals(listOf("Camera", "여행"), q.args)
    }

    @Test fun noFolderLeftFallsBackToPicks() {
        assertEquals(picks, PhotoQueries.of(PhotoSource.FOLDERS, emptyList(), setOf("Camera")))
        assertEquals(picks, PhotoQueries.of(PhotoSource.FOLDERS, listOf("지운 앨범"), setOf("Camera")))
    }
}
