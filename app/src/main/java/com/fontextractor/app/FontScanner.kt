package com.fontextractor.app

import android.content.pm.ApplicationInfo
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** 설치된 앱의 APK(base + split)를 열어 글꼴 파일을 찾는다. */
object FontScanner {
    val FONT_EXT = setOf("ttf", "otf", "ttc", "otc")

    // 확장자가 다르더라도 이 경로 아래에 있으면 매직바이트로 글꼴 여부를 확인
    private val FONT_DIRS = listOf("assets/", "res/font/", "res/raw/", "fonts/", "font/")

    // 매직바이트 검사에서 미리 제외할 확장자 (읽기 비용 절약)
    private val SKIP_EXT = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "svg", "xml", "json", "txt", "html", "css", "js",
        "mp3", "ogg", "wav", "mp4", "dex", "so", "arsc", "pb", "bin", "db", "zip", "apk", "jar", "wasm"
    )

    fun apkPaths(ai: ApplicationInfo): List<String> {
        val list = ArrayList<String>()
        ai.sourceDir?.let { list += it }
        ai.splitSourceDirs?.forEach { list += it }
        return list.distinct()
    }

    fun scanPackage(ai: ApplicationInfo): List<FontEntry> = apkPaths(ai).flatMap { scanApk(it) }

    fun scanApk(path: String): List<FontEntry> {
        val out = ArrayList<FontEntry>()
        try {
            ZipFile(path).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val name = e.name
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isFont = when {
                        ext in FONT_EXT -> true
                        ext in SKIP_EXT -> false
                        e.size >= 8 * 1024 && FONT_DIRS.any { name.startsWith(it) } -> hasFontMagic(zip, e)
                        else -> false
                    }
                    if (isFont) out += FontEntry(path, name, e.size)
                }
            }
        } catch (e: Exception) {
            // 읽을 수 없는 APK는 조용히 건너뜀
        }
        return out
    }

    private fun hasFontMagic(zip: ZipFile, e: ZipEntry): Boolean {
        return try {
            zip.getInputStream(e).use { ins ->
                val b = ByteArray(4)
                var n = 0
                while (n < 4) {
                    val r = ins.read(b, n, 4 - n)
                    if (r < 0) break
                    n += r
                }
                n == 4 && isFontMagic(b)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isFontMagic(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val tag = String(b, 0, 4, Charsets.ISO_8859_1)
        val trueType = b[0] == 0.toByte() && b[1] == 1.toByte() && b[2] == 0.toByte() && b[3] == 0.toByte()
        return trueType || tag == "OTTO" || tag == "true" || tag == "ttcf"
    }
}
