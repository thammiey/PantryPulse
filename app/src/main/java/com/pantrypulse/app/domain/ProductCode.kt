package com.pantrypulse.app.domain

/**
 * Pulls a product number (EAN / UPC / GTIN) out of what a QR code or Data Matrix holds.
 *
 * Classic barcodes carry the number directly. QR codes on packaging usually carry a GS1 Digital
 * Link URL (https://…/01/<gtin>/…), a GS1 element string ("(01)<gtin>…") or a link with the number
 * in it. A QR code with anything else (a website, Wi-Fi details…) has no product number.
 */
object ProductCode {
    private val digitalLink = Regex("""/01/(\d{14})(?:[/?#]|$)""")
    private val elementString = Regex("""^\W*\(?01\)?(\d{14})""")
    private val queryParam = Regex("""[?&](?:gtin|ean|upc|barcode|code)=(\d{8,14})(?:[&#]|$)""", RegexOption.IGNORE_CASE)

    /** Returns the digits to look up, or null when the code holds no product number. */
    fun fromQr(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.all(Char::isDigit) && text.length in 8..14) return normalize(text)
        (digitalLink.find(text) ?: elementString.find(text.replace("\u001d", "")) ?: queryParam.find(text))
            ?.groupValues?.get(1)?.let { return normalize(it) }
        return null
    }

    /** A 14-digit GTIN that starts with 0 is the same product as its 13-digit EAN. */
    private fun normalize(digits: String) = if (digits.length == 14 && digits.startsWith("0")) digits.drop(1) else digits
}
