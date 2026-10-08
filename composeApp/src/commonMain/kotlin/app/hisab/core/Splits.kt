package app.hisab.core

/** One person's share of an expense, in minor units. */
data class Share(val memberId: String, val amount: Long)

object Splits {
    /**
     * Splits [amount] equally between [memberIds]. Leftover minor units (when
     * it doesn't divide evenly) go one each to the first people in the list,
     * so the shares always add up exactly.
     */
    fun equal(amount: Long, memberIds: List<String>): List<Share> {
        require(memberIds.isNotEmpty()) { "Pick at least one person" }
        require(memberIds.toSet().size == memberIds.size) { "Duplicate person" }
        val n = memberIds.size
        val base = amount / n
        val rest = (amount % n).toInt()
        return memberIds.mapIndexed { i, id -> Share(id, base + if (i < rest) 1 else 0) }
    }

    /**
     * Splits [amount] by weights (e.g. shares 2:1:1, or nights stayed).
     * Rounds down, then hands remaining units to the largest remainders.
     */
    fun weighted(amount: Long, weights: Map<String, Int>): List<Share> {
        val entries = weights.filterValues { it > 0 }.toList()
        require(entries.isNotEmpty()) { "Give at least one person a share" }
        val total = entries.sumOf { it.second.toLong() }
        val raw = entries.map { (id, w) -> Triple(id, amount * w / total, (amount * w) % total) }
        val left = amount - raw.sumOf { it.second }
        val extra = raw.sortedByDescending { it.third }.take(left.toInt()).map { it.first }.toSet()
        return raw.map { (id, base, _) -> Share(id, base + if (id in extra) 1 else 0) }
    }

    /** True when [shares] are valid for [amount]: non-negative, unique, exact total. */
    fun isValid(amount: Long, shares: List<Share>): Boolean =
        shares.isNotEmpty() &&
            shares.all { it.amount >= 0 } &&
            shares.map { it.memberId }.toSet().size == shares.size &&
            shares.sumOf { it.amount } == amount
}
