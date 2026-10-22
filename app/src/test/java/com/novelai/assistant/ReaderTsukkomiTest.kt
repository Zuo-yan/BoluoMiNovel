package com.novelai.assistant

import com.novelai.assistant.ui.reader.sfTsukkomiChapterId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTsukkomiTest {
    @Test fun remoteIdIsUsedForSfChapterWithCompositeLocalPrimaryKey() {
        assertEquals(
            "6367390",
            sfTsukkomiChapterId("sf:540307:6367390", "6367390")
        )
    }

    @Test fun localChapterNeverFabricatesARemoteId() {
        assertNull(sfTsukkomiChapterId("local-chapter-1", null))
        assertNull(sfTsukkomiChapterId("local-chapter-1", "bad"))
    }
}
