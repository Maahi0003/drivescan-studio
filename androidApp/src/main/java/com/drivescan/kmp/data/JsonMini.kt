package com.drivescan.kmp.data

/**
 * Minimal JSON parser (objects/arrays/strings/numbers/booleans/null) —
 * just enough to read the app's own index file. No external library,
 * keeping the manual build pipeline dependency-free.
 */
class JsonParser(private val text: String) {
    private var pos = 0

    fun parse(): Any? {
        skipWs()
        val value = readValue()
        skipWs()
        return value
    }

    private fun skipWs() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun readValue(): Any? {
        if (pos >= text.length) return null
        return when (text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't', 'f' -> readBoolean()
            'n' -> {
                // null
                pos += 4
                null
            }
            else -> readNumber()
        }
    }

    private fun readObject(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        pos++ // {
        skipWs()
        if (pos < text.length && text[pos] == '}') {
            pos++
            return map
        }
        while (pos < text.length) {
            skipWs()
            val key = readString()
            skipWs()
            if (pos < text.length && text[pos] == ':') pos++
            skipWs()
            map[key] = readValue()
            skipWs()
            if (pos < text.length && text[pos] == ',') {
                pos++
                continue
            }
            if (pos < text.length && text[pos] == '}') pos++
            break
        }
        return map
    }

    private fun readArray(): List<Any?> {
        val list = mutableListOf<Any?>()
        pos++ // [
        skipWs()
        if (pos < text.length && text[pos] == ']') {
            pos++
            return list
        }
        while (pos < text.length) {
            skipWs()
            list.add(readValue())
            skipWs()
            if (pos < text.length && text[pos] == ',') {
                pos++
                continue
            }
            if (pos < text.length && text[pos] == ']') pos++
            break
        }
        return list
    }

    private fun readString(): String {
        val sb = StringBuilder()
        if (pos < text.length && text[pos] == '"') pos++
        while (pos < text.length) {
            val c = text[pos]
            if (c == '\\' && pos + 1 < text.length) {
                when (text[pos + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'u' -> {
                        if (pos + 5 < text.length) {
                            val hex = text.substring(pos + 2, pos + 6)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            pos += 4
                        }
                    }
                    else -> sb.append(text[pos + 1])
                }
                pos += 2
                continue
            }
            if (c == '"') {
                pos++
                break
            }
            sb.append(c)
            pos++
        }
        return sb.toString()
    }

    private fun readBoolean(): Boolean {
        return if (text.startsWith("true", pos)) {
            pos += 4
            true
        } else {
            pos += 5
            false
        }
    }

    private fun readNumber(): Number {
        val start = pos
        while (pos < text.length && text[pos] in "-0123456789.eE+") pos++
        val raw = text.substring(start, pos)
        return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: 0L
    }
}

/** Typed accessors over parsed JSON values. */
fun Map<String, Any?>.str(key: String): String? = this[key] as? String
fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
fun Map<String, Any?>.bool(key: String): Boolean = this[key] as? Boolean ?: false
fun Map<String, Any?>.strList(key: String): List<String> =
    (this[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.objList(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
