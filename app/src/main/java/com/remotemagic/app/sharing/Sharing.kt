package com.remotemagic.app.sharing

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class TvCard(val name: String, val brand: String, val type: String, val status: String)

object Sharing {
    fun renderCard(card: TvCard): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(17, 24, 35))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        fun text(value: String, y: Float, size: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = size
            while (paint.measureText(value) > 980 && paint.textSize > 24) paint.textSize -= 2
            canvas.drawText(value, 540f, y, paint)
        }
        val gold = Color.rgb(231, 193, 115)
        text("REMOTE MAGIC", 140f, 80f, gold)
        // Generic TV illustration (TVs do not publish a product photo over these protocols).
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 18f
        paint.color = Color.rgb(25, 195, 255)
        canvas.drawRoundRect(RectF(190f, 220f, 890f, 630f), 30f, 30f, paint)
        canvas.drawLine(420f, 690f, 660f, 690f, paint)
        canvas.drawLine(540f, 630f, 540f, 690f, paint)
        text(card.name, 800f, 64f, Color.WHITE)
        text(card.brand, 875f, 46f, gold)
        text(card.type, 945f, 40f, Color.LTGRAY)
        text(card.status, 1015f, 40f, Color.rgb(80, 220, 130))
        return bitmap
    }

    fun shareImage(context: Context, bytes: ByteArray, fileName: String, mime: String) {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        context.startActivity(Intent.createChooser(send, null))
    }

    fun png(bitmap: Bitmap): ByteArray =
        java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** Android 10+ writes to the gallery without any permission. */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "remote-magic-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RemoteMagic")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }
}
