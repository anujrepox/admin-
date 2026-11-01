package com.example.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.model.StickerItem
import java.io.File

object TransferHelper {
    private const val TAG = "TransferHelper"

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val PACKAGE_INSTAGRAM = "com.instagram.android"

    const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    const val ACTION_INSTAGRAM_STORY = "com.instagram.share.ADD_TO_STORY"

    const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isWhatsAppInstalled(context: Context): Boolean {
        return isAppInstalled(context, PACKAGE_WHATSAPP) || isAppInstalled(context, PACKAGE_WHATSAPP_BUSINESS)
    }

    fun isInstagramInstalled(context: Context): Boolean {
        return isAppInstalled(context, PACKAGE_INSTAGRAM)
    }

    /**
     * Builds intent to add pack to WhatsApp using official WhatsApp Sticker Pack API
     */
    fun createAddToWhatsAppIntent(context: Context, packId: String, packName: String): Intent {
        val authority = "${context.packageName}.stickercontentprovider"
        return Intent().apply {
            action = ACTION_ENABLE_STICKER_PACK
            putExtra(EXTRA_STICKER_PACK_ID, packId)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, authority)
            putExtra(EXTRA_STICKER_PACK_NAME, packName)
            if (isAppInstalled(context, PACKAGE_WHATSAPP)) {
                setPackage(PACKAGE_WHATSAPP)
            } else if (isAppInstalled(context, PACKAGE_WHATSAPP_BUSINESS)) {
                setPackage(PACKAGE_WHATSAPP_BUSINESS)
            }
        }
    }

    /**
     * Adds sticker as an interactive floating sticker on Instagram Story
     */
    fun createInstagramStoryIntent(context: Context, sticker: StickerItem): Intent {
        val file = File(sticker.filePath)
        val contentUri = StickerManager.getContentUriForFile(context, file)

        return Intent(ACTION_INSTAGRAM_STORY).apply {
            type = sticker.mimeType
            putExtra("interactive_asset_uri", contentUri)
            putExtra("top_background_color", "#000000")
            putExtra("bottom_background_color", "#0D0E12")
            putExtra("content_url", "https://stickerdrop.app")
            setPackage(PACKAGE_INSTAGRAM)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Direct share to WhatsApp chat/status as sticker/image
     */
    fun createWhatsAppDirectShareIntent(context: Context, sticker: StickerItem): Intent {
        val file = File(sticker.filePath)
        val contentUri = StickerManager.getContentUriForFile(context, file)

        val targetPkg = if (isAppInstalled(context, PACKAGE_WHATSAPP)) {
            PACKAGE_WHATSAPP
        } else if (isAppInstalled(context, PACKAGE_WHATSAPP_BUSINESS)) {
            PACKAGE_WHATSAPP_BUSINESS
        } else null

        return Intent(Intent.ACTION_SEND).apply {
            type = sticker.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            if (targetPkg != null) {
                setPackage(targetPkg)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Direct share to Instagram Direct / Feed
     */
    fun createInstagramDirectShareIntent(context: Context, sticker: StickerItem): Intent {
        val file = File(sticker.filePath)
        val contentUri = StickerManager.getContentUriForFile(context, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = sticker.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            if (isAppInstalled(context, PACKAGE_INSTAGRAM)) {
                setPackage(PACKAGE_INSTAGRAM)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Fallback standard Android Share Chooser if app is not installed
     */
    fun createGeneralShareIntent(context: Context, sticker: StickerItem, title: String): Intent {
        val file = File(sticker.filePath)
        val contentUri = StickerManager.getContentUriForFile(context, file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = sticker.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "Shared via StickerDrop")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, title)
    }
}
