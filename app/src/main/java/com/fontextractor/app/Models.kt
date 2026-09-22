package com.fontextractor.app

import android.graphics.Typeface
import java.io.File

/** 글꼴을 하나 이상 포함한 설치 앱 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val fontCount: Int,
    val totalSize: Long,
    val isSystem: Boolean
) {
    /** 패키지명/앱 이름으로 봤을 때 글꼴 앱일 가능성이 높은지 */
    val isFontLike: Boolean
        get() {
            val s = "$packageName $label".lowercase()
            return FONT_KEYWORDS.any { s.contains(it) }
        }

    companion object {
        private val FONT_KEYWORDS = listOf("font", "폰트", "글꼴", "typeface", "monotype", "theme", "테마")
    }
}

/** APK 안의 글꼴 항목 하나 */
data class FontEntry(
    val apkPath: String,
    val entryName: String,
    val size: Long
) {
    val fileName: String get() = entryName.substringAfterLast('/')
    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()
}

/** 글꼴 목록 화면의 행 상태 */
class FontItem(val entry: FontEntry, val index: Int) {
    var cacheFile: File? = null
    var names: FontNameParser.Names? = null
    var typeface: Typeface? = null
    var checked = false
    var ready = false
    var error = false
}
