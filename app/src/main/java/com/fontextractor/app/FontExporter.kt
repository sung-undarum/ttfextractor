package com.fontextractor.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/** 글꼴을 공용 Download/FontExtractor/<앱이름>/ 폴더에 저장한다. */
object FontExporter {
    const val ROOT_DIR = "FontExtractor"

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun sanitize(name: String): String =
        name.replace(ILLEGAL, "_").trim().ifEmpty { "font" }

    fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "otf" -> "font/otf"
        "ttc", "otc" -> "font/collection"
        else -> "font/ttf"
    }

    /** 성공 시 저장된 경로(표시용)를 돌려준다. */
    fun save(ctx: Context, input: InputStream, fileName: String, subFolder: String): Result<String> = runCatching {
        val folder = sanitize(subFolder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val relative = Environment.DIRECTORY_DOWNLOADS + "/" + ROOT_DIR + "/" + folder
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(fileName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert 실패")
            try {
                val out = resolver.openOutputStream(uri) ?: error("출력 스트림을 열 수 없음")
                out.use { input.copyTo(it) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "$relative/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "$ROOT_DIR/$folder")
            dir.mkdirs()
            val target = uniqueFile(dir, fileName)
            target.outputStream().use { out -> input.copyTo(out) }
            target.absolutePath
        }
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var f = File(dir, fileName)
        if (!f.exists()) return f
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }
}
