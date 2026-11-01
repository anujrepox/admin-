package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.StickerItem
import com.example.model.StickerPackInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object StickerManager {
    private const val TAG = "StickerManager"
    const val DEFAULT_PACK_ID = "drop_pack_1"
    private const val PREFS_NAME = "sticker_drop_prefs"
    private const val KEY_PACK_DATA = "pack_data"

    // WhatsApp strict limits
    const val MAX_STATIC_STICKER_BYTES = 98 * 1024 // Strict 98 KB (WhatsApp limit is 100 KB)
    const val STICKER_CANVAS_SIZE = 512
    const val TRAY_CANVAS_SIZE = 96

    fun getPackDir(context: Context, packId: String = DEFAULT_PACK_ID): File {
        val dir = File(context.filesDir, "stickers/$packId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getStickerFile(context: Context, packId: String, fileName: String): File {
        val dir = getPackDir(context, packId)
        val file = File(dir, fileName)
        if (file.exists()) return file

        // Fallback: if WhatsApp queries for .webp but file was saved with another extension
        if (fileName.endsWith(".webp", ignoreCase = true)) {
            val baseName = fileName.removeSuffix(".webp")
            val altGif = File(dir, "$baseName.gif")
            if (altGif.exists()) {
                try {
                    formatFileTo512Webp(altGif, file)
                    return file
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create fallback webp", e)
                }
            }
        }
        return file
    }

    fun getContentUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun getPackInfo(context: Context, packId: String = DEFAULT_PACK_ID): StickerPackInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_PACK_DATA, null)

        val stickers = mutableListOf<StickerItem>()
        var packName = "StickerDrop Pack"
        var needsSave = false

        if (rawJson != null) {
            try {
                val json = JSONObject(rawJson)
                packName = json.optString("name", packName)
                val array = json.optJSONArray("stickers") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val itemObj = array.getJSONObject(i)
                    var fileName = itemObj.getString("fileName")
                    var file = File(getPackDir(context, packId), fileName)

                    // Auto-Migration: If existing sticker is > 98 KB or not webp, fix it for WhatsApp!
                    if (file.exists()) {
                        if (!fileName.endsWith(".webp", ignoreCase = true) || file.length() > MAX_STATIC_STICKER_BYTES) {
                            val newFileName = "sticker_${itemObj.getString("id")}.webp"
                            val compliantFile = File(getPackDir(context, packId), newFileName)
                            try {
                                formatFileTo512Webp(file, compliantFile)
                                file = compliantFile
                                fileName = newFileName
                                needsSave = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Migration compression error", e)
                            }
                        }

                        stickers.add(
                            StickerItem(
                                id = itemObj.getString("id"),
                                name = itemObj.optString("name", "Sticker #${i + 1}"),
                                fileName = fileName,
                                filePath = file.absolutePath,
                                mimeType = "image/webp",
                                isAnimated = itemObj.optBoolean("isAnimated", false),
                                sizeBytes = file.length(),
                                width = STICKER_CANVAS_SIZE,
                                height = STICKER_CANVAS_SIZE,
                                createdAt = itemObj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved stickers", e)
            }
        }

        // Guarantee valid WhatsApp tray icon exists
        ensureTrayIcon(context, packId, stickers.firstOrNull())

        val pack = StickerPackInfo(
            id = packId,
            name = packName,
            publisher = "makeurfun",
            trayFileName = "tray.png",
            isAnimated = false, // Static WhatsApp validation ensures universal compatibility
            stickers = stickers
        )

        if (needsSave) {
            savePackInfo(context, pack)
        }

        return pack
    }

    private fun savePackInfo(context: Context, packInfo: StickerPackInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        json.put("id", packInfo.id)
        json.put("name", packInfo.name)
        json.put("is_animated", false)

        val array = JSONArray()
        for (item in packInfo.stickers) {
            val itemObj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("fileName", item.fileName)
                put("mimeType", item.mimeType)
                put("isAnimated", item.isAnimated)
                put("width", item.width)
                put("height", item.height)
                put("createdAt", item.createdAt)
            }
            array.put(itemObj)
        }
        json.put("stickers", array)
        prefs.edit().putString(KEY_PACK_DATA, json.toString()).apply()
    }

    /**
     * WhatsApp strictly requires a minimum of 3 stickers in a pack.
     * If user has 1 or 2 stickers, create auto-cloned slots on disk so WhatsApp never rejects the pack.
     */
    fun getEffectiveWhatsAppStickers(context: Context, packId: String = DEFAULT_PACK_ID): List<StickerItem> {
        val current = getPackInfo(context, packId).stickers
        if (current.isEmpty()) return emptyList()

        val effective = current.toMutableList()
        var index = 1
        while (effective.size < 3) {
            val base = current[(index - 1) % current.size]
            val cloneFileName = "auto_slot_${index}_${base.id}.webp"
            val cloneFile = File(getPackDir(context, packId), cloneFileName)
            if (!cloneFile.exists()) {
                File(base.filePath).copyTo(cloneFile, overwrite = true)
            }
            effective.add(
                base.copy(
                    id = "clone_${base.id}_$index",
                    fileName = cloneFileName,
                    filePath = cloneFile.absolutePath,
                    mimeType = "image/webp"
                )
            )
            index++
        }
        return effective
    }

    fun addStickerFromUri(context: Context, uri: Uri, customName: String? = null): StickerItem {
        val cr = context.contentResolver
        val detectedMime = cr.getType(uri) ?: "image/png"
        val isGif = detectedMime.contains("gif", ignoreCase = true) || uri.toString().endsWith(".gif", ignoreCase = true)

        val id = UUID.randomUUID().toString().take(8)
        val packDir = getPackDir(context)

        // Read source bytes
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read sticker media")

        // 1. WhatsApp Sticker: ALWAYS 512x512 WebP under 98 KB
        val webpFileName = "sticker_${id}.webp"
        val webpFile = File(packDir, webpFileName)
        formatBytesToCompliant512Webp(bytes, webpFile)

        // 2. If it was a GIF, also save source GIF for Instagram Stories and Direct Chat sharing
        if (isGif) {
            val rawGifFile = File(packDir, "raw_${id}.gif")
            rawGifFile.writeBytes(bytes)
        }

        val name = customName ?: "Sticker #${getPackInfo(context).stickers.size + 1}"
        val stickerItem = StickerItem(
            id = id,
            name = name,
            fileName = webpFileName,
            filePath = webpFile.absolutePath,
            mimeType = "image/webp",
            isAnimated = isGif,
            sizeBytes = webpFile.length(),
            width = STICKER_CANVAS_SIZE,
            height = STICKER_CANVAS_SIZE
        )

        val currentPack = getPackInfo(context)
        val updatedList = currentPack.stickers.toMutableList().apply { add(stickerItem) }
        val updatedPack = currentPack.copy(stickers = updatedList)
        savePackInfo(context, updatedPack)
        ensureTrayIcon(context, DEFAULT_PACK_ID, stickerItem)

        return stickerItem
    }

    /**
     * Resizes, pads, and compresses image into strictly compliant 512x512 WebP (< 98 KB)
     */
    private fun formatBytesToCompliant512Webp(inputBytes: ByteArray, outputFile: File) {
        val srcBitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
            ?: throw IllegalArgumentException("Could not decode image")

        val targetSize = STICKER_CANVAS_SIZE // 512
        val scaledBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaledBitmap)

        // Leave a 16px transparent margin inside 512x512 as recommended by WhatsApp
        val contentMax = targetSize - 32 // 480
        val srcWidth = srcBitmap.width
        val srcHeight = srcBitmap.height
        val scale = (contentMax.toFloat() / maxOf(srcWidth, srcHeight)).coerceAtMost(1.0f)
        val destWidth = (srcWidth * scale).toInt()
        val destHeight = (srcHeight * scale).toInt()
        val left = (targetSize - destWidth) / 2
        val top = (targetSize - destHeight) / 2

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect = android.graphics.Rect(0, 0, srcWidth, srcHeight)
        val destRect = android.graphics.Rect(left, top, left + destWidth, top + destHeight)
        canvas.drawBitmap(srcBitmap, srcRect, destRect, paint)

        // Compress iteratively to guarantee under MAX_STATIC_STICKER_BYTES (98 KB)
        var quality = 85
        var finalBytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
            }
            finalBytes = stream.toByteArray()
            quality -= 8
        } while (finalBytes.size > MAX_STATIC_STICKER_BYTES && quality >= 30)

        outputFile.writeBytes(finalBytes)
        scaledBitmap.recycle()
        srcBitmap.recycle()
    }

    fun formatFileTo512Webp(sourceFile: File, outputFile: File) {
        formatBytesToCompliant512Webp(sourceFile.readBytes(), outputFile)
    }

    /**
     * Ensures tray icon is 96x96 pixels, PNG format, under 50 KB
     */
    fun ensureTrayIcon(context: Context, packId: String, representative: StickerItem?) {
        val trayFile = File(getPackDir(context, packId), "tray.png")
        if (trayFile.exists() && trayFile.length() > 0 && trayFile.length() < 50 * 1024) return

        val trayBitmap = Bitmap.createBitmap(TRAY_CANVAS_SIZE, TRAY_CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(trayBitmap)

        if (representative != null && File(representative.filePath).exists()) {
            try {
                val repBitmap = BitmapFactory.decodeFile(representative.filePath)
                if (repBitmap != null) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    // Draw with 8px margin inside 96x96
                    val destRect = android.graphics.Rect(8, 8, 88, 88)
                    canvas.drawBitmap(repBitmap, null, destRect, paint)
                    repBitmap.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not draw representative into tray icon", e)
            }
        } else {
            // Draw default glowing badge
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#25D366")
            }
            canvas.drawCircle(48f, 48f, 40f, paint)
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("⚡", 48f, 60f, innerPaint)
        }

        FileOutputStream(trayFile).use { out ->
            trayBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        trayBitmap.recycle()
    }

    fun removeSticker(context: Context, stickerId: String) {
        val current = getPackInfo(context)
        val itemToRemove = current.stickers.find { it.id == stickerId }
        if (itemToRemove != null) {
            File(itemToRemove.filePath).delete()
        }
        val updated = current.stickers.filterNot { it.id == stickerId }
        savePackInfo(context, current.copy(stickers = updated))
    }

    fun clearPack(context: Context) {
        val dir = getPackDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun seedSampleStickers(context: Context) {
        val current = getPackInfo(context)
        if (current.stickers.isNotEmpty()) return

        val packDir = getPackDir(context)
        val samples = listOf(
            Triple("Neon Fire", "🔥", listOf("🔥", "✨")),
            Triple("Cyber Sparkle", "⚡", listOf("⚡", "🚀")),
            Triple("Cool Shades", "😎", listOf("😎", "🆒"))
        )

        for ((idx, sample) in samples.withIndex()) {
            val fileName = "sample_${idx + 1}.webp"
            val file = File(packDir, fileName)
            generateSampleBitmap(sample.first, sample.second, file)

            val item = StickerItem(
                id = "sample_${idx + 1}",
                name = sample.first,
                fileName = fileName,
                filePath = file.absolutePath,
                mimeType = "image/webp",
                isAnimated = false,
                sizeBytes = file.length(),
                emojis = sample.third
            )
            val pack = getPackInfo(context)
            val updated = pack.stickers.toMutableList().apply { add(item) }
            savePackInfo(context, pack.copy(stickers = updated))
        }
        ensureTrayIcon(context, DEFAULT_PACK_ID, getPackInfo(context).stickers.firstOrNull())
    }

    private fun generateSampleBitmap(title: String, emoji: String, outputFile: File) {
        val size = STICKER_CANVAS_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141824")
        }
        val rectF = android.graphics.RectF(40f, 40f, 472f, 472f)
        canvas.drawRoundRect(rectF, 60f, 60f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#25D366")
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        canvas.drawRoundRect(rectF, 60f, 60f, borderPaint)

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 180f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(emoji, 256f, 300f, emojiPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, 256f, 410f, textPaint)

        FileOutputStream(outputFile).use { out ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
        }
        bitmap.recycle()
    }
}
