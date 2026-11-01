package com.example.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.StickerManager
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerProvider"

        // Uri Matcher IDs
        private const val METADATA = 1
        private const val METADATA_PACK = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4

        // Exact WhatsApp Metadata Query Column Names
        private const val STICKER_PACK_IDENTIFIER = "sticker_pack_identifier"
        private const val STICKER_PACK_NAME = "sticker_pack_name"
        private const val STICKER_PACK_PUBLISHER = "sticker_pack_publisher"
        private const val STICKER_PACK_ICON = "sticker_pack_icon"
        private const val ANDROID_APP_DOWNLOAD_LINK = "android_play_store_link"
        private const val IOS_APP_DOWNLOAD_LINK = "ios_app_download_link"
        private const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        private const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        private const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        private const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        private const val IMAGE_DATA_VERSION = "image_data_version"
        private const val AVOID_CACHE = "avoid_cache"
        private const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        // Exact WhatsApp Individual Sticker Query Column Names
        private const val STICKER_FILE_NAME = "sticker_file_name"
        private const val STICKER_FILE_EMOJI = "sticker_emoji"

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH)

        fun setupMatcher(authority: String) {
            MATCHER.addURI(authority, "metadata", METADATA)
            MATCHER.addURI(authority, "metadata/*", METADATA_PACK)
            MATCHER.addURI(authority, "stickers/*", STICKERS)
            MATCHER.addURI(authority, "stickers_asset/*/*", STICKERS_ASSET)
            MATCHER.addURI(authority, "stickers_asset/*", STICKERS_ASSET)
        }
    }

    override fun onCreate(): Boolean {
        val authority = (context?.packageName ?: "com.aistudio.stickerdrop.vqzr") + ".stickercontentprovider"
        setupMatcher(authority)
        // Also add the hardcoded build applicationId for safety
        setupMatcher("com.aistudio.stickerdrop.vqzr.stickercontentprovider")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val code = MATCHER.match(uri)
        val path = uri.path ?: ""
        val ctx = context ?: return null
        val packInfo = StickerManager.getPackInfo(ctx)

        val isMetadata = code == METADATA || path.endsWith("/metadata") || path == "/metadata"
        val isMetadataPack = code == METADATA_PACK || path.startsWith("/metadata/")
        val isStickers = code == STICKERS || (path.startsWith("/stickers/") && !path.contains("stickers_asset"))

        return when {
            isMetadata || isMetadataPack -> {
                val cursor = MatrixCursor(
                    arrayOf(
                        STICKER_PACK_IDENTIFIER,
                        STICKER_PACK_NAME,
                        STICKER_PACK_PUBLISHER,
                        STICKER_PACK_ICON,
                        ANDROID_APP_DOWNLOAD_LINK,
                        IOS_APP_DOWNLOAD_LINK,
                        PUBLISHER_EMAIL,
                        PUBLISHER_WEBSITE,
                        PRIVACY_POLICY_WEBSITE,
                        LICENSE_AGREEMENT_WEBSITE,
                        IMAGE_DATA_VERSION,
                        AVOID_CACHE,
                        ANIMATED_STICKER_PACK
                    )
                )

                if (packInfo.stickers.isNotEmpty()) {
                    cursor.addRow(
                        arrayOf(
                            packInfo.id,
                            packInfo.name,
                            packInfo.publisher,
                            packInfo.trayFileName,
                            "", // android download link
                            "", // ios download link
                            "support@makeurfun.com",
                            "https://makeurfun.com",
                            "https://makeurfun.com/privacy",
                            "https://makeurfun.com/license",
                            "1", // image data version
                            0, // avoid cache
                            0  // static sticker pack ensures universal WhatsApp acceptance
                        )
                    )
                }
                cursor
            }

            isStickers -> {
                val cursor = MatrixCursor(
                    arrayOf(
                        STICKER_FILE_NAME,
                        STICKER_FILE_EMOJI
                    )
                )
                val effectiveStickers = StickerManager.getEffectiveWhatsAppStickers(ctx)
                for (sticker in effectiveStickers) {
                    val webpFileName = if (sticker.fileName.endsWith(".webp", ignoreCase = true)) {
                        sticker.fileName
                    } else {
                        sticker.fileName.substringBeforeLast(".") + ".webp"
                    }
                    // WhatsApp restricts maximum 3 emojis per sticker
                    val emojiString = sticker.emojis.take(3).joinToString(",")
                    cursor.addRow(
                        arrayOf(
                            webpFileName,
                            emojiString
                        )
                    )
                }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }

            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val fileName = uri.lastPathSegment ?: throw FileNotFoundException("Invalid URI: $uri")
        val ctx = context ?: throw FileNotFoundException("Context is null")
        val packId = if (uri.pathSegments.size >= 3) uri.pathSegments[1] else StickerManager.DEFAULT_PACK_ID

        // Pre-flight check: if tray or clone file is requested and doesn't exist, generate immediately
        if (fileName.contains("tray", ignoreCase = true)) {
            val packInfo = StickerManager.getPackInfo(ctx, packId)
            StickerManager.ensureTrayIcon(ctx, packId, packInfo.stickers.firstOrNull())
        } else if (fileName.startsWith("auto_slot_", ignoreCase = true)) {
            StickerManager.getEffectiveWhatsAppStickers(ctx, packId)
        }

        val file = StickerManager.getStickerFile(ctx, packId, fileName)
        if (!file.exists()) {
            Log.e(TAG, "File not found for WhatsApp: ${file.absolutePath}, uri=$uri")
            throw FileNotFoundException("File not found: $fileName")
        }

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // Return exact file length in AssetFileDescriptor so WhatsApp validator can verify byte size
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val afd = openAssetFile(uri, mode)
        return afd?.parcelFileDescriptor
    }

    override fun getType(uri: Uri): String? {
        val path = uri.path ?: ""
        val fileName = uri.lastPathSegment ?: ""
        return when {
            path.contains("/metadata") -> "vnd.android.cursor.dir/vnd.${context?.packageName}.metadata"
            path.startsWith("/stickers/") && !path.contains("stickers_asset") -> "vnd.android.cursor.dir/vnd.${context?.packageName}.stickers"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/webp"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
