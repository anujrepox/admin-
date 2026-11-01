package com.example

import com.example.model.StickerItem
import com.example.model.StickerPackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testStickerItemFormattedSize() {
        val itemBytes = StickerItem(
            id = "1",
            name = "Test",
            fileName = "test.webp",
            filePath = "/test.webp",
            mimeType = "image/webp",
            isAnimated = false,
            sizeBytes = 500
        )
        assertEquals("500 B", itemBytes.formattedSize)

        val itemKb = itemBytes.copy(sizeBytes = 102400)
        assertEquals("100 KB", itemKb.formattedSize)
    }

    @Test
    fun testStickerPackInfoWhatsAppThreshold() {
        val emptyPack = StickerPackInfo()
        assertFalse(emptyPack.meetsWhatsAppMinCount)

        val threePack = StickerPackInfo(
            stickers = listOf(
                StickerItem("1", "A", "a.webp", "/a", "image/webp", false, 100),
                StickerItem("2", "B", "b.webp", "/b", "image/webp", false, 100),
                StickerItem("3", "C", "c.webp", "/c", "image/webp", false, 100)
            )
        )
        assertTrue(threePack.meetsWhatsAppMinCount)
    }
}
