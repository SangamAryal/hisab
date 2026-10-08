package app.hisab.core

/** An expense as far as balances are concerned. */
data class ExpenseEntry(val paidBy: String, val amount: Long, val shares: List<Share>)

/** "from paid to" settle-up. */
data class PaymentEntry(val from: String, val to: String, val amount: Long)

/** A suggested transfer that settles debts. */
data class Transfer(val from: String, val to: String, val amount: Long)

object Balances {
    /**
     * Net balance per member: positive means the group owes them,
     * negative means they owe the group. Always sums to zero.
     */
    fun net(
        memberIds: Collection<String>,
        expenses: List<ExpenseEntry>,
        payments: List<PaymentEntry>,
    ): Map<String, Long> {
        val net = memberIds.associateWith { 0L }.toMutableMap()
        for (e in expenses) {
            net[e.paidBy] = (net[e.paidBy] ?: 0L) + e.amount
            for (s in e.shares) net[s.memberId] = (net[s.memberId] ?: 0L) - s.amount
        }
        for (p in payments) {
            net[p.from] = (net[p.from] ?: 0L) + p.amount
            net[p.to] = (net[p.to] ?: 0L) - p.amount
        }
        return net
    }

    /**
     * Fewest-ish transfers to settle everyone: repeatedly match the person
     * owed the most with the person who owes the most. At most n-1 transfers.
     * Ties are broken by member id so the result is stable between refreshes.
     */
    fun simplify(net: Map<String, Long>): List<Transfer> {
        val creditors = net.filterValues { it > 0 }.map { it.key to it.value }.toMutableList()
        val debtors = net.filterValues { it < 0 }.map { it.key to -it.value }.toMutableList()
        val out = mutableListOf<Transfer>()
        val order = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            creditors.sortWith(order)
            debtors.sortWith(order)
            val (c, owed) = creditors[0]
            val (d, owes) = debtors[0]
            val x = minOf(owed, owes)
            out += Transfer(from = d, to = c, amount = x)
            if (owed == x) creditors.removeAt(0) else creditors[0] = c to owed - x
            if (owes == x) debtors.removeAt(0) else debtors[0] = d to owes - x
        }
        return out
    }
}
