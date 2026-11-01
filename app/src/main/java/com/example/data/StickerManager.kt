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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object StickerManager {
    private const val TAG = "StickerManager"
    const val DEFAULT_PACK_ID = "drop_pack_1"
    private const val PREFS_NAME = "sticker_drop_prefs"
    private const val KEY_PACK_DATA = "pack_data"

    fun getPackDir(context: Context, packId: String = DEFAULT_PACK_ID): File {
        val dir = File(context.filesDir, "stickers/$packId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getStickerFile(context: Context, packId: String, fileName: String): File {
        return File(getPackDir(context, packId), fileName)
    }

    fun getContentUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun getPackInfo(context: Context, packId: String = DEFAULT_PACK_ID): StickerPackInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_PACK_DATA, null)

        val stickers = mutableListOf<StickerItem>()
        var isAnimated = false
        var packName = "StickerDrop Pack"

        if (rawJson != null) {
            try {
                val json = JSONObject(rawJson)
                packName = json.optString("name", packName)
                isAnimated = json.optBoolean("is_animated", false)
                val array = json.optJSONArray("stickers") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val itemObj = array.getJSONObject(i)
                    val fileName = itemObj.getString("fileName")
                    val file = getStickerFile(context, packId, fileName)
                    if (file.exists()) {
                        stickers.add(
                            StickerItem(
                                id = itemObj.getString("id"),
                                name = itemObj.optString("name", "Sticker #${i + 1}"),
                                fileName = fileName,
                                filePath = file.absolutePath,
                                mimeType = itemObj.optString("mimeType", "image/webp"),
                                isAnimated = itemObj.optBoolean("isAnimated", false),
                                sizeBytes = file.length(),
                                width = itemObj.optInt("width", 512),
                                height = itemObj.optInt("height", 512),
                                createdAt = itemObj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved stickers", e)
            }
        }

        // Ensure tray icon exists
        ensureTrayIcon(context, packId, stickers.firstOrNull())

        return StickerPackInfo(
            id = packId,
            name = packName,
            publisher = "StickerDrop",
            trayFileName = "tray.png",
            isAnimated = stickers.any { it.isAnimated },
            stickers = stickers
        )
    }

    private fun savePackInfo(context: Context, packInfo: StickerPackInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        json.put("id", packInfo.id)
        json.put("name", packInfo.name)
        json.put("is_animated", packInfo.isAnimated)

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
     * WhatsApp requires at least 3 stickers in a pack.
     * If user has 1 or 2 stickers, we automatically mirror/duplicate slots
     * so WhatsApp never rejects the pack.
     */
    fun getEffectiveWhatsAppStickers(context: Context, packId: String = DEFAULT_PACK_ID): List<StickerItem> {
        val current = getPackInfo(context, packId).stickers
        if (current.isEmpty()) return emptyList()
        if (current.size >= 3) return current

        val effective = current.toMutableList()
        var index = 1
        while (effective.size < 3) {
            val base = current[(index - 1) % current.size]
            val cloneFileName = "auto_slot_${index}_${base.fileName}"
            val cloneFile = getStickerFile(context, packId, cloneFileName)
            if (!cloneFile.exists()) {
                File(base.filePath).copyTo(cloneFile, overwrite = true)
            }
            effective.add(
                base.copy(
                    id = "clone_${base.id}_$index",
                    fileName = cloneFileName,
                    filePath = cloneFile.absolutePath
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
        val isWebp = detectedMime.contains("webp", ignoreCase = true) || uri.toString().endsWith(".webp", ignoreCase = true)

        val id = UUID.randomUUID().toString().take(8)
        val packDir = getPackDir(context)

        // Read source bytes
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read sticker media")

        val fileName: String
        val savedFile: File
        val isAnimated = isGif || (isWebp && isLikelyAnimated(bytes))
        val targetMime: String

        if (isAnimated) {
            // Keep original animated format
            val extension = if (isGif) "gif" else "webp"
            fileName = "sticker_${id}.$extension"
            savedFile = File(packDir, fileName)
            savedFile.writeBytes(bytes)
            targetMime = if (isGif) "image/gif" else "image/webp"

            // Also produce a 512x512 WebP frame for WhatsApp compatibility if it was a GIF
            try {
                val staticPreviewFile = File(packDir, "preview_${id}.webp")
                create512WebPFrame(bytes, staticPreviewFile)
            } catch (e: Exception) {
                Log.w(TAG, "Could not generate static frame", e)
            }
        } else {
            // Static image: scale & format strictly into 512x512 WebP as required by WhatsApp
            fileName = "sticker_${id}.webp"
            savedFile = File(packDir, fileName)
            formatBitmapTo512Webp(bytes, savedFile)
            targetMime = "image/webp"
        }

        val name = customName ?: "Sticker #${getPackInfo(context).stickers.size + 1}"
        val stickerItem = StickerItem(
            id = id,
            name = name,
            fileName = fileName,
            filePath = savedFile.absolutePath,
            mimeType = targetMime,
            isAnimated = isAnimated,
            sizeBytes = savedFile.length(),
            width = 512,
            height = 512
        )

        val currentPack = getPackInfo(context)
        val updatedList = currentPack.stickers.toMutableList().apply { add(stickerItem) }
        val updatedPack = currentPack.copy(
            stickers = updatedList,
            isAnimated = updatedList.any { it.isAnimated }
        )
        savePackInfo(context, updatedPack)
        ensureTrayIcon(context, DEFAULT_PACK_ID, stickerItem)

        return stickerItem
    }

    private fun isLikelyAnimated(bytes: ByteArray): Boolean {
        // Quick check for WEBP with 'ANIM' chunk
        val header = String(bytes.take(64).toByteArray(), Charsets.US_ASCII)
        return header.contains("ANIM")
    }

    private fun formatBitmapTo512Webp(inputBytes: ByteArray, outputFile: File) {
        val srcBitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
            ?: throw IllegalArgumentException("Could not decode image")

        val targetSize = 512
        val scaledBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaledBitmap)

        val srcWidth = srcBitmap.width
        val srcHeight = srcBitmap.height
        val scale = (targetSize.toFloat() / maxOf(srcWidth, srcHeight)).coerceAtMost(1.0f)
        val destWidth = (srcWidth * scale).toInt()
        val destHeight = (srcHeight * scale).toInt()
        val left = (targetSize - destWidth) / 2
        val top = (targetSize - destHeight) / 2

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect = android.graphics.Rect(0, 0, srcWidth, srcHeight)
        val destRect = android.graphics.Rect(left, top, left + destWidth, top + destHeight)
        canvas.drawBitmap(srcBitmap, srcRect, destRect, paint)

        FileOutputStream(outputFile).use { out ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
            }
        }
        scaledBitmap.recycle()
        srcBitmap.recycle()
    }

    private fun create512WebPFrame(gifBytes: ByteArray, outputFile: File) {
        val srcBitmap = BitmapFactory.decodeByteArray(gifBytes, 0, gifBytes.size) ?: return
        val targetSize = 512
        val scaledBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaledBitmap)

        val srcWidth = srcBitmap.width
        val srcHeight = srcBitmap.height
        val scale = (targetSize.toFloat() / maxOf(srcWidth, srcHeight)).coerceAtMost(1.0f)
        val destWidth = (srcWidth * scale).toInt()
        val destHeight = (srcHeight * scale).toInt()
        val left = (targetSize - destWidth) / 2
        val top = (targetSize - destHeight) / 2

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, null, android.graphics.Rect(left, top, left + destWidth, top + destHeight), paint)

        FileOutputStream(outputFile).use { out ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
        }
        scaledBitmap.recycle()
        srcBitmap.recycle()
    }

    fun ensureTrayIcon(context: Context, packId: String, representative: StickerItem?) {
        val trayFile = File(getPackDir(context, packId), "tray.png")
        if (trayFile.exists() && trayFile.length() > 0) return

        val trayBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(trayBitmap)

        if (representative != null && File(representative.filePath).exists()) {
            try {
                val repBitmap = BitmapFactory.decodeFile(representative.filePath)
                if (repBitmap != null) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(repBitmap, null, android.graphics.Rect(0, 0, 96, 96), paint)
                    repBitmap.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not draw representative into tray icon", e)
            }
        } else {
            // Draw default glowing sticker badge
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#25D366")
            }
            canvas.drawCircle(48f, 48f, 44f, paint)
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("⚡", 48f, 60f, innerPaint)
        }

        FileOutputStream(trayFile).use { out ->
            trayBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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
        savePackInfo(context, current.copy(stickers = updated, isAnimated = updated.any { it.isAnimated }))
    }

    fun clearPack(context: Context) {
        val dir = getPackDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Seeds initial fun trending animated/static stickers so users can try immediately
     */
    fun seedSampleStickers(context: Context) {
        val current = getPackInfo(context)
        if (current.stickers.isNotEmpty()) return

        val packDir = getPackDir(context)

        val samples = listOf(
            SampleDef("Neon Flame", "#FF0055", "#FFAA00", "🔥", listOf("🔥", "✨")),
            SampleDef("Cyber Sparkle", "#00F59B", "#00E5FF", "⚡", listOf("⚡", "🚀")),
            SampleDef("Holo Heart", "#BD00FF", "#FF0077", "💖", listOf("💖", "😍")),
            SampleDef("Cool Sunglasses", "#00E5FF", "#3B82F6", "😎", listOf("😎", "🆒"))
        )

        for ((idx, sample) in samples.withIndex()) {
            val fileName = "sample_${idx + 1}.webp"
            val file = File(packDir, fileName)
            generateSampleStickerBitmap(sample.title, sample.emoji, sample.gradStart, sample.gradEnd, file)

            val item = StickerItem(
                id = "sample_${idx + 1}",
                name = sample.title,
                fileName = fileName,
                filePath = file.absolutePath,
                mimeType = "image/webp",
                isAnimated = false,
                sizeBytes = file.length(),
                emojis = sample.emojis
            )
            val pack = getPackInfo(context)
            val updated = pack.stickers.toMutableList().apply { add(item) }
            savePackInfo(context, pack.copy(stickers = updated))
        }
        ensureTrayIcon(context, DEFAULT_PACK_ID, getPackInfo(context).stickers.firstOrNull())
    }

    private data class SampleDef(
        val title: String,
        val gradStart: String,
        val gradEnd: String,
        val emoji: String,
        val emojis: List<String>
    )

    private fun generateSampleStickerBitmap(
        title: String,
        emoji: String,
        startColor: String,
        endColor: String,
        outputFile: File
    ) {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw soft glowing rounded sticker base
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                60f, 60f, 452f, 452f,
                Color.parseColor(startColor),
                Color.parseColor(endColor),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        val rectF = android.graphics.RectF(60f, 60f, 452f, 452f)
        canvas.drawRoundRect(rectF, 64f, 64f, bgPaint)

        // White border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawRoundRect(rectF, 64f, 64f, borderPaint)

        // Emoji center
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 170f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(emoji, 256f, 290f, emojiPaint)

        // Title text banner
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 4f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawText(title, 256f, 395f, textPaint)

        FileOutputStream(outputFile).use { out ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
            }
        }
        bitmap.recycle()
    }
}
