package app.hisab.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    @Test fun parsesAmounts() {
        assertEquals(125050, Money.parse("1,250.5", "NPR"))
        assertEquals(1200, Money.parse("1200", "JPY"))
        assertNull(Money.parse("12.5", "JPY"))
        assertEquals(12345, Money.parse("12.345", "KWD"))
        assertNull(Money.parse("1.234", "USD"))
        assertNull(Money.parse("0", "USD"))
        assertNull(Money.parse("-3", "USD"))
        assertNull(Money.parse("abc", "USD"))
        assertEquals(50, Money.parse(".5", "USD"))
    }

    @Test fun formatsAmounts() {
        assertEquals("Rs 1,250.50", Money.format(125050, "NPR"))
        assertEquals("-$3.00", Money.format(-300, "USD"))
        assertEquals("¥1,200", Money.format(1200, "JPY"))
        assertEquals("₹0.05", Money.format(5, "INR"))
        assertEquals("XYZ 10.00", Money.format(1000, "XYZ"))
    }

    @Test fun roundTripsInputText() {
        assertEquals("1250.50", Money.toInput(125050, "NPR"))
        assertEquals(125050, Money.parse(Money.toInput(125050, "NPR"), "NPR"))
        assertEquals("12", Money.toInput(1200, "USD"))
        assertEquals("12.05", Money.toInput(1205, "USD"))
    }
}

class SplitsTest {
    @Test fun equalSplitAddsUp() {
        val s = Splits.equal(1000, listOf("a", "b", "c"))
        assertEquals(listOf(334L, 333L, 333L), s.map { it.amount })
        assertTrue(Splits.isValid(1000, s))
    }

    @Test fun weightedSplitAddsUp() {
        val s = Splits.weighted(1000, mapOf("a" to 1, "b" to 1, "c" to 1))
        assertEquals(1000, s.sumOf { it.amount })
        val t = Splits.weighted(100, mapOf("a" to 2, "b" to 1, "c" to 0))
        assertEquals(mapOf("a" to 67L, "b" to 33L), t.associate { it.memberId to it.amount })
    }

    @Test fun rejectsBadShares() {
        assertTrue(!Splits.isValid(10, listOf(Share("a", 5), Share("a", 5))))
        assertTrue(!Splits.isValid(10, listOf(Share("a", 11), Share("b", -1))))
        assertTrue(!Splits.isValid(10, emptyList()))
    }
}

class BalancesTest {
    @Test fun netAndSimplify() {
        val members = listOf("asha", "bina", "chandra")
        val expenses = listOf(
            ExpenseEntry("asha", 900, Splits.equal(900, members)),       // each 300
            ExpenseEntry("bina", 300, Splits.equal(300, listOf("asha", "bina", "chandra"))), // each 100
        )
        val payments = listOf(PaymentEntry("chandra", "asha", 100))
        val net = Balances.net(members, expenses, payments)
        assertEquals(mapOf("asha" to 400L, "bina" to -100L, "chandra" to -300L), net)
        assertEquals(0, net.values.sum())
        val t = Balances.simplify(net)
        assertEquals(listOf(Transfer("chandra", "asha", 300), Transfer("bina", "asha", 100)), t)
    }

    @Test fun settledGroupHasNoTransfers() {
        val net = Balances.net(listOf("a", "b"), listOf(ExpenseEntry("a", 100, Splits.equal(100, listOf("a", "b")))), listOf(PaymentEntry("b", "a", 50)))
        assertTrue(Balances.simplify(net).isEmpty())
    }

    @Test fun simplifyNeverNeedsMoreThanNMinusOneTransfers() {
        val net = mapOf("a" to 50L, "b" to 30L, "c" to -20L, "d" to -25L, "e" to -35L)
        val t = Balances.simplify(net)
        assertTrue(t.size <= 4)
        val after = net.toMutableMap()
        t.forEach { after[it.from] = after[it.from]!! + it.amount; after[it.to] = after[it.to]!! - it.amount }
        assertTrue(after.values.all { it == 0L })
    }
}
