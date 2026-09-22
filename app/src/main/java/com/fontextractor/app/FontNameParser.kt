package com.fontextractor.app

import java.io.File
import java.io.RandomAccessFile

/** TTF/OTF/TTC 의 name 테이블에서 글꼴 이름을 읽는다. */
object FontNameParser {
    data class Names(val family: String?, val subfamily: String?, val fullName: String?)

    private const val ID_FAMILY = 1
    private const val ID_SUBFAMILY = 2
    private const val ID_FULL = 4
    private const val ID_TYPO_FAMILY = 16
    private const val ID_TYPO_SUBFAMILY = 17
    private val WANTED = setOf(ID_FAMILY, ID_SUBFAMILY, ID_FULL, ID_TYPO_FAMILY, ID_TYPO_SUBFAMILY)

    fun parse(file: File): Names? = try {
        RandomAccessFile(file, "r").use { parse(it) }
    } catch (e: Exception) {
        null
    }

    private fun parse(raf: RandomAccessFile): Names? {
        val tag = ByteArray(4)
        raf.seek(0)
        raf.readFully(tag)
        var base = 0L
        if (String(tag, Charsets.ISO_8859_1) == "ttcf") {
            // TrueType Collection: 첫 번째 글꼴의 오프셋 테이블 사용
            raf.seek(12)
            base = raf.readInt().toLong() and 0xFFFFFFFFL
        }
        raf.seek(base + 4)
        val numTables = raf.readUnsignedShort()
        if (numTables <= 0 || numTables > 512) return null

        var nameOffset = -1L
        for (i in 0 until numTables) {
            raf.seek(base + 12 + i * 16L)
            raf.readFully(tag)
            raf.readInt() // checksum
            val offset = raf.readInt().toLong() and 0xFFFFFFFFL
            raf.readInt() // length
            if (String(tag, Charsets.ISO_8859_1) == "name") {
                nameOffset = offset
                break
            }
        }
        if (nameOffset < 0 || nameOffset >= raf.length()) return null

        raf.seek(nameOffset)
        raf.readUnsignedShort() // format
        val count = raf.readUnsignedShort()
        val stringOffset = raf.readUnsignedShort()
        val storage = nameOffset + stringOffset

        // nameId -> (우선순위, 값)
        val found = HashMap<Int, Pair<Int, String>>()
        for (i in 0 until count) {
            raf.seek(nameOffset + 6 + i * 12L)
            val platform = raf.readUnsignedShort()
            raf.readUnsignedShort() // encoding
            val language = raf.readUnsignedShort()
            val nameId = raf.readUnsignedShort()
            val length = raf.readUnsignedShort()
            val offset = raf.readUnsignedShort()
            if (nameId !in WANTED || length == 0 || length > 2048) continue

            val priority = when {
                platform == 3 && language == 0x412 -> 4 // Windows / 한국어
                platform == 3 && language == 0x409 -> 3 // Windows / 영어
                platform == 3 || platform == 0 -> 2     // Windows 기타 / Unicode
                platform == 1 -> 1                      // Macintosh
                else -> 0
            }
            val prev = found[nameId]
            if (prev != null && prev.first >= priority) continue

            val pos = storage + offset
            if (pos + length > raf.length()) continue
            val buf = ByteArray(length)
            raf.seek(pos)
            raf.readFully(buf)
            val raw = if (platform == 3 || platform == 0) {
                String(buf, Charsets.UTF_16BE)
            } else {
                String(buf, Charsets.ISO_8859_1)
            }
            val str = raw.trim { it <= ' ' }
            if (str.isNotEmpty()) found[nameId] = priority to str
        }

        val family = found[ID_TYPO_FAMILY]?.second ?: found[ID_FAMILY]?.second
        val subfamily = found[ID_TYPO_SUBFAMILY]?.second ?: found[ID_SUBFAMILY]?.second
        val full = found[ID_FULL]?.second
        if (family == null && full == null) return null
        return Names(family, subfamily, full)
    }
}
