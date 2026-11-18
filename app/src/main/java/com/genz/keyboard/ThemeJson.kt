package com.genz.keyboard

/**
 * Minimal JSON reader and writer for the theme format.
 *
 * Hand-rolled rather than `org.json` because the platform's `org.json` is a **stub in JVM unit
 * tests** — its methods return defaults instead of parsing, so a codec built on it cannot be
 * tested at all. The theme format is a small flat object, so a focused parser is cheaper than a
 * serialization dependency and is fully verifiable.
 *
 * Supports the subset the format needs: objects, arrays, strings, numbers, booleans and null.
 * Anything else is rejected rather than guessed at.
 */
internal object ThemeJson {

    fun encode(fields: Map<String, Any?>): String = buildString { writeObject(this, fields) }

    /** @return the parsed object, or null if the text is not a well-formed JSON object. */
    fun decode(text: String): Map<String, Any?>? {
        val p = Parser(text)
        val v = runCatching { p.value() }.getOrNull() ?: return null
        p.skipWs()
        if (!p.atEnd()) return null
        val map = v as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, Any?>()
        for ((k, value) in map) {
            if (k !is String) return null
            out[k] = value
        }
        return out
    }

    private fun writeObject(sb: StringBuilder, fields: Map<String, Any?>) {
        sb.append('{')
        var first = true
        for ((k, v) in fields) {
            if (v == null) continue
            if (!first) sb.append(',')
            first = false
            writeString(sb, k)
            sb.append(':')
            writeValue(sb, v)
        }
        sb.append('}')
    }

    private fun writeValue(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Int, is Long -> sb.append(v.toString())
            is Double -> sb.append(if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString())
            is Boolean -> sb.append(v.toString())
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(sb, v as Map<String, Any?>)
            }
            is List<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u").append(String.format("%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char {
            if (i >= s.length) throw IllegalArgumentException("unexpected end")
            return s[i]
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) throw IllegalArgumentException("expected $c")
            i++
        }

        fun value(): Any? {
            skipWs()
            return when (peek()) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("bad literal")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val m = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                expect(':')
                m[k] = value()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw IllegalArgumentException("bad object")
                }
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val l = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { i++; return l }
            while (true) {
                l.add(value())
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw IllegalArgumentException("bad array")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape $e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '-' || s[i] == '+')) i++
            val text = s.substring(start, i)
            return text.toDoubleOrNull() ?: throw IllegalArgumentException("bad number")
        }
    }

    /** Decode a top-level JSON array. Returns null when the text is not an array. */
    fun decodeArray(text: String): List<Any?>? {
        val t = text.trim()
        if (!t.startsWith("[") || !t.endsWith("]")) return null
        return try {
            Parser(t).value() as? List<Any?>
        } catch (e: Exception) {
            null
        }
    }
}
