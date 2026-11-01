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

        // Metadata Columns
        private const val STICKER_PACK_IDENTIFIER = "sticker_pack_identifier"
        private const val STICKER_PACK_NAME = "sticker_pack_name"
        private const val STICKER_PACK_PUBLISHER = "sticker_pack_publisher"
        private const val STICKER_PACK_ICON = "sticker_pack_icon"
        private const val ANDROID_APP_DOWNLOAD_LINK = "android_play_store_link"
        private const val IOS_APP_DOWNLOAD_LINK = "ios_app_download_link"
        private const val PUBLISHER_EMAIL = "publisher_email"
        private const val PUBLISHER_WEBSITE = "publisher_website"
        private const val PRIVACY_POLICY_WEBSITE = "privacy_policy_website"
        private const val LICENSE_AGREEMENT_WEBSITE = "license_agreement_website"
        private const val IMAGE_DATA_VERSION = "image_data_version"
        private const val AVOID_CACHE = "avoid_cache"
        private const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        // Sticker Columns
        private const val STICKER_FILE_NAME = "sticker_file_name"
        private const val STICKER_PACK_ANIMATION_DATA = "sticker_pack_animation_data"
        private const val STICKER_PACK_EMOJIS = "sticker_pack_emojis"

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH)

        fun setupMatcher(authority: String) {
            MATCHER.addURI(authority, "metadata", METADATA)
            MATCHER.addURI(authority, "metadata/*", METADATA_PACK)
            MATCHER.addURI(authority, "stickers/*", STICKERS)
            MATCHER.addURI(authority, "stickers_asset/*/*", STICKERS_ASSET)
        }
    }

    override fun onCreate(): Boolean {
        val authority = context?.packageName + ".stickercontentprovider"
        setupMatcher(authority)
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
        val ctx = context ?: return null
        val packInfo = StickerManager.getPackInfo(ctx)

        return when (code) {
            METADATA -> {
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

                // Only expose pack if it has stickers
                if (packInfo.stickers.isNotEmpty()) {
                    cursor.addRow(
                        arrayOf(
                            packInfo.id,
                            packInfo.name,
                            packInfo.publisher,
                            packInfo.trayFileName,
                            "", // android download link
                            "", // ios download link
                            "support@stickerdrop.app",
                            "https://github.com",
                            "https://github.com/privacy",
                            "https://github.com/license",
                            "1",
                            0, // avoid cache
                            if (packInfo.isAnimated) 1 else 0
                        )
                    )
                }
                cursor
            }

            METADATA_PACK -> {
                val packId = uri.lastPathSegment
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
                if (packInfo.id == packId && packInfo.stickers.isNotEmpty()) {
                    cursor.addRow(
                        arrayOf(
                            packInfo.id,
                            packInfo.name,
                            packInfo.publisher,
                            packInfo.trayFileName,
                            "",
                            "",
                            "support@stickerdrop.app",
                            "https://github.com",
                            "https://github.com/privacy",
                            "https://github.com/license",
                            "1",
                            0,
                            if (packInfo.isAnimated) 1 else 0
                        )
                    )
                }
                cursor
            }

            STICKERS -> {
                val packId = uri.lastPathSegment
                val cursor = MatrixCursor(
                    arrayOf(
                        STICKER_FILE_NAME,
                        STICKER_PACK_ANIMATION_DATA,
                        STICKER_PACK_EMOJIS
                    )
                )
                if (packInfo.id == packId) {
                    val effectiveStickers = StickerManager.getEffectiveWhatsAppStickers(ctx)
                    for (sticker in effectiveStickers) {
                        cursor.addRow(
                            arrayOf(
                                sticker.fileName,
                                "", // animation data
                                sticker.emojis.joinToString(",")
                            )
                        )
                    }
                }
                cursor
            }

            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val code = MATCHER.match(uri)
        if (code != STICKERS_ASSET) {
            throw IllegalArgumentException("Invalid URI for openAssetFile: $uri")
        }

        val segments = uri.pathSegments
        // URI format: /stickers_asset/<pack_id>/<file_name>
        if (segments.size < 3) {
            throw IllegalArgumentException("Invalid path segments: $uri")
        }

        val packId = segments[1]
        val fileName = segments[2]
        val ctx = context ?: throw FileNotFoundException("Context is null")

        val file = StickerManager.getStickerFile(ctx, packId, fileName)
        if (!file.exists()) {
            Log.e(TAG, "File not found for WhatsApp: ${file.absolutePath}")
            throw FileNotFoundException("Sticker file not found: $fileName")
        }

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? {
        val code = MATCHER.match(uri)
        val authority = context?.packageName + ".stickercontentprovider"
        return when (code) {
            METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
            METADATA_PACK -> "vnd.android.cursor.item/vnd.$authority.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
            STICKERS_ASSET -> "image/webp"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
