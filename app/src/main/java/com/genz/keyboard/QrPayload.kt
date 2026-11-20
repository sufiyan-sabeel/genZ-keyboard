package com.genz.keyboard

/**
 * A QR payload, and the rules for turning it into the string a scanner will read.
 *
 * Each type follows the de-facto format scanners expect (the same conventions ZXing, iOS and Android
 * use), because a code that only this app can read is not a QR code. Everything here is pure so the
 * escaping and validation rules — which are where these formats actually go wrong — can be tested
 * without a camera or a screen.
 *
 * No dependency on Curium: these are public, widely documented payload conventions.
 */
sealed interface QrPayload {

    /** The string encoded into the QR symbol. */
    fun encode(): String

    /** Human-readable label for history and lists. */
    fun summary(): String

    /** True when this payload contains a secret that must never be logged or auto-saved. */
    fun isSensitive(): Boolean = false

    // ---------------------------------------------------------------- types

    data class Url(val url: String) : QrPayload {
        override fun encode(): String = normaliseUrl(url)
        override fun summary(): String = encode()
    }

    data class Text(val text: String) : QrPayload {
        override fun encode(): String = text
        override fun summary(): String = text.take(60)
    }

    data class Wifi(
        val ssid: String,
        val password: String,
        val security: Security = Security.WPA,
        val hidden: Boolean = false
    ) : QrPayload {
        enum class Security { WPA, WEP, NONE }

        override fun encode(): String = buildString {
            append("WIFI:")
            append("T:").append(if (security == Security.NONE) "nopass" else security.name).append(';')
            append("S:").append(escape(ssid)).append(';')
            if (security != Security.NONE) append("P:").append(escape(password)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }

        override fun summary(): String = "Wi-Fi: $ssid"
        override fun isSensitive(): Boolean = security != Security.NONE
    }

    data class Email(val to: String, val subject: String = "", val body: String = "") : QrPayload {
        override fun encode(): String = buildString {
            append("mailto:").append(to.trim())
            val params = buildList {
                if (subject.isNotBlank()) add("subject=${percent(subject)}")
                if (body.isNotBlank()) add("body=${percent(body)}")
            }
            if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
        }

        override fun summary(): String = "Email: ${to.trim()}"
    }

    data class Phone(val number: String) : QrPayload {
        override fun encode(): String = "tel:" + digits(number)
        override fun summary(): String = "Phone: ${digits(number)}"
    }

    data class Sms(val number: String, val message: String = "") : QrPayload {
        override fun encode(): String =
            if (message.isBlank()) "sms:${digits(number)}" else "smsto:${digits(number)}:${message}"
        override fun summary(): String = "SMS: ${digits(number)}"
    }

    data class Contact(
        val name: String,
        val phone: String = "",
        val email: String = "",
        val org: String = ""
    ) : QrPayload {
        override fun encode(): String = buildString {
            append("BEGIN:VCARD\nVERSION:3.0\n")
            append("FN:").append(escapeVcard(name)).append('\n')
            if (org.isNotBlank()) append("ORG:").append(escapeVcard(org)).append('\n')
            if (phone.isNotBlank()) append("TEL;TYPE=CELL:").append(digits(phone)).append('\n')
            if (email.isNotBlank()) append("EMAIL:").append(email.trim()).append('\n')
            append("END:VCARD")
        }

        override fun summary(): String = "Contact: $name"
    }

    data class Geo(val latitude: Double, val longitude: Double, val label: String = "") : QrPayload {
        override fun encode(): String = buildString {
            append("geo:").append(latitude).append(',').append(longitude)
            if (label.isNotBlank()) append("?q=").append(latitude).append(',').append(longitude)
                .append('(').append(percent(label)).append(')')
        }

        override fun summary(): String = "Location: $latitude, $longitude"
    }

    data class Event(
        val title: String,
        val start: String,
        val end: String,
        val location: String = "",
        val description: String = ""
    ) : QrPayload {
        override fun encode(): String = buildString {
            append("BEGIN:VEVENT\n")
            append("SUMMARY:").append(escapeVcard(title)).append('\n')
            append("DTSTART:").append(start).append('\n')
            append("DTEND:").append(end).append('\n')
            if (location.isNotBlank()) append("LOCATION:").append(escapeVcard(location)).append('\n')
            if (description.isNotBlank()) append("DESCRIPTION:").append(escapeVcard(description)).append('\n')
            append("END:VEVENT")
        }

        override fun summary(): String = "Event: $title"
    }

    /**
     * An `otpauth://` provisioning URI.
     *
     * Treated as a secret: the issuer and account label are shown in the UI, but the shared secret is
     * never included in a summary, a log line or automatic history.
     */
    data class Otp(
        val issuer: String,
        val account: String,
        val secret: String,
        val period: Int = 30,
        val digits: Int = 6
    ) : QrPayload {
        override fun encode(): String {
            val label = percent("$issuer:$account")
            // secrets are usually shown in spaced groups (abcd efgh); the space is not part of the
            // key and a provisioning URI containing one is rejected by authenticator apps
            val key = secret.filterNot { it.isWhitespace() }.uppercase()
            return "otpauth://totp/$label?secret=$key" +
                "&issuer=${percent(issuer)}&period=$period&digits=$digits"
        }

        override fun summary(): String = "Authenticator: $issuer ($account)"
        override fun isSensitive(): Boolean = true
    }

    // ---------------------------------------------------------------- shared helpers

    companion object {
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

        /** Accept a bare domain by assuming https; never silently downgrade an explicit scheme. */
        fun normaliseUrl(raw: String): String {
            val t = raw.trim()
            return if (SCHEME.containsMatchIn(t)) t else "https://$t"
        }

        /** Strip formatting so the dialler receives something it can use. */
        fun digits(raw: String): String = raw.filter { it.isDigit() || it == '+' }

        /**
         * Escape a value inside the WIFI: grammar.
         *
         * `\`, `;`, `,`, `:` and `"` must be backslash-escaped or the payload splits at the wrong
         * place and a scanner reads a different SSID.
         */
        fun escape(v: String): String = buildString {
            for (c in v) {
                if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
                append(c)
            }
        }

        /** vCard/VEVENT text escaping: newlines and separators must not break the record. */
        fun escapeVcard(v: String): String =
            v.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

        /** Percent-encode a query component, keeping the result readable where possible. */
        fun percent(v: String): String = buildString {
            for (b in v.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch)
                else append('%').append("%02X".format(c))
            }
        }
    }
}
