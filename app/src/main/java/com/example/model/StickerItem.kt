package com.example.model

data class StickerItem(
    val id: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val isAnimated: Boolean,
    val sizeBytes: Long,
    val width: Int = 512,
    val height: Int = 512,
    val emojis: List<String> = listOf("🔥", "✨", "😎"),
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes.toDouble() / (1024 * 1024))
        }

    val typeLabel: String
        get() = when {
            isAnimated -> "Animated GIF/WebP"
            mimeType.contains("gif", ignoreCase = true) -> "GIF"
            mimeType.contains("webp", ignoreCase = true) -> "WebP Sticker"
            else -> "Sticker"
        }
}

data class StickerPackInfo(
    val id: String = "drop_pack_1",
    val name: String = "StickerDrop Pack",
    val publisher: String = "StickerDrop",
    val trayFileName: String = "tray.png",
    val isAnimated: Boolean = false,
    val stickers: List<StickerItem> = emptyList()
) {
    // WhatsApp requires a minimum of 3 stickers in a pack and maximum 30
    val meetsWhatsAppMinCount: Boolean
        get() = stickers.size >= 3

    val totalStickerCount: Int
        get() = stickers.size
}
