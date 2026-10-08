package app.hisab.core

import kotlin.math.abs

/**
 * Money is always handled as a whole number of the currency's minor unit
 * (paisa, cents...), the same way the backend stores it.
 */
object Money {
    private val zeroDecimal = setOf(
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG",
        "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
    )
    private val threeDecimal = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

    private val symbols = mapOf(
        "NPR" to "Rs", "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£",
        "JPY" to "¥", "CNY" to "¥", "KRW" to "₩", "AUD" to "A$", "CAD" to "C$",
        "BDT" to "৳", "PKR" to "Rs", "LKR" to "Rs", "AED" to "AED", "SGD" to "S$",
        "MYR" to "RM", "THB" to "฿", "PHP" to "₱", "IDR" to "Rp", "NGN" to "₦",
        "BRL" to "R$", "MXN" to "MX$", "TRY" to "₺", "RUB" to "₽", "ZAR" to "R",
        "CHF" to "CHF", "NZD" to "NZ$", "HKD" to "HK$", "VND" to "₫",
    )

    /** Common currencies shown first in pickers. */
    val popular = listOf(
        "NPR", "INR", "USD", "EUR", "GBP", "AUD", "CAD", "JPY", "AED", "BDT",
        "PKR", "LKR", "SGD", "MYR", "THB", "PHP", "IDR", "CNY", "KRW", "NGN",
        "BRL", "MXN", "ZAR", "CHF", "NZD", "HKD", "TRY", "VND",
    )

    fun decimals(currency: String): Int = when (currency.uppercase()) {
        in zeroDecimal -> 0
        in threeDecimal -> 3
        else -> 2
    }

    fun symbol(currency: String): String = symbols[currency.uppercase()] ?: currency.uppercase()

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }

    /**
     * Parses what a person typed ("1,250.5", "1250", "12.345") into minor units.
     * Returns null for anything that isn't a positive amount with at most the
     * currency's number of decimals.
     */
    fun parse(input: String, currency: String): Long? {
        val d = decimals(currency)
        val s = input.trim().replace(",", "").replace(" ", "")
        if (s.isEmpty()) return null
        val match = Regex("^(\\d{0,12})(?:\\.(\\d*))?$").matchEntire(s) ?: return null
        val whole = match.groupValues[1]
        val frac = match.groupValues[2]
        if (whole.isEmpty() && frac.isEmpty()) return null
        if (frac.length > d) return null
        val wholePart = whole.ifEmpty { "0" }.toLong()
        val fracPart = if (d == 0) 0L else frac.padEnd(d, '0').ifEmpty { "0" }.toLong()
        val value = wholePart * pow10(d) + fracPart
        return if (value > 0) value else null
    }

    /** Plain number for text fields: 125050 NPR -> "1250.50"; trailing ".00" dropped. */
    fun toInput(minor: Long, currency: String): String {
        val d = decimals(currency)
        if (d == 0) return minor.toString()
        val p = pow10(d)
        val whole = minor / p
        val frac = (minor % p).toString().padStart(d, '0')
        return if (frac.all { it == '0' }) whole.toString() else "$whole.$frac"
    }

    /** "Rs 1,250.50", "-$3.00", "¥1,200". Groups thousands with commas. */
    fun format(minor: Long, currency: String): String {
        val d = decimals(currency)
        val p = pow10(d)
        val a = abs(minor)
        val whole = (a / p).toString().reversed().chunked(3).joinToString(",").reversed()
        val frac = if (d == 0) "" else "." + (a % p).toString().padStart(d, '0')
        val sym = symbol(currency)
        val sep = if (sym.length > 1 && sym.last().isLetter()) " " else ""
        return (if (minor < 0) "-" else "") + sym + sep + whole + frac
    }
}
