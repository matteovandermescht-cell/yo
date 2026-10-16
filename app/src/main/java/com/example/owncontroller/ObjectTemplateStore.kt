package com.example.owncontroller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Stores user-labelled crops from screenshots as local visual templates. */
object ObjectTemplateStore {
    private fun directory(context: Context): File =
        File(context.filesDir, "object_templates").apply { mkdirs() }

    fun save(context: Context, label: String, bitmap: Bitmap): Boolean {
        val safeLabel = label.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").take(40).trim()
        if (safeLabel.isBlank() || bitmap.width < 8 || bitmap.height < 8) return false
        val file = File(directory(context), safeLabel.lowercase().replace(Regex("\\s+"), "_") + ".png")
        return file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun labels(context: Context): List<String> =
        directory(context).listFiles()?.filter { it.isFile && it.extension.equals("png", true) }
            ?.map { it.nameWithoutExtension.replace("_", " ") }?.sorted().orEmpty()

    fun loadAll(context: Context): List<Pair<String, Bitmap>> =
        directory(context).listFiles()?.filter { it.isFile && it.extension.equals("png", true) }
            ?.mapNotNull { file -> BitmapFactory.decodeFile(file.absolutePath)?.let { file.nameWithoutExtension.replace("_", " ") to it } }
            .orEmpty()
}
