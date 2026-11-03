package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.AccountRemoval
import sh.aminov.golda.domain.I18n

/** Deleting an account takes its own operations and leaves every other account's balance as it was. */
class AccountRemovalTest {
    private val savings = Account(1, "Альфа", "RUB", AccountType.SAVINGS, includeInFree = false)
    private val card = Account(2, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false)
    private val cash = Account(3, "Наличные", "RUB", AccountType.CASH, includeInFree = true)

    private fun op(id: Long, type: OpType, vararg legs: Pair<Account, Long>, note: String = "") =
        OperationFull(Operation(id, type, id * 1000, note = note), legs.map { (a, m) -> Posting(id * 10, id, a.id, m, m) })

    private fun balances(ops: List<OperationFull>) =
        ops.flatMap { it.postings }.groupBy { it.accountId }.mapValues { (_, ps) -> ps.sumOf { it.amountMinor } }

    @Test
    fun aPaidOffCardTakesItsHistoryButTheSavingsKeepThePayment() {
        I18n.russian = true
        val ops = listOf(
            op(1, OpType.OPENING, savings to 38_216_437),
            op(2, OpType.OPENING, card to -4_079_310),
            op(3, OpType.TRANSFER, savings to -4_079_310, card to 4_079_310),
            op(4, OpType.EXPENSE, card to -100_389),
        )
        val plan = AccountRemoval.plan(card, ops)
        assertEquals(listOf(2L, 4L), plan.delete)
        val kept = plan.keep.single()
        assertEquals(OpType.ADJUSTMENT, kept.op.type)
        assertEquals("Перевод на «Кредитка»", kept.op.note)
        assertEquals(listOf(-4_079_310L), kept.postings.map { it.amountMinor })
        val after = ops.filter { it.op.id !in plan.delete && it.op.id != 3L } + OperationFull(kept.op, kept.postings)
        assertEquals(34_137_127L, balances(after)[savings.id])
    }

    @Test
    fun aNoteOfItsOwnStays() {
        val plan = AccountRemoval.plan(card, listOf(op(1, OpType.TRANSFER, card to -5_000, cash to 5_000, note = "банкомат")))
        assertEquals("банкомат", plan.keep.single().op.note)
    }

    @Test
    fun otherAccountsKeepTheirBalances() = prop {
        val accounts = listOf(savings, card, cash)
        val leg = Arb.list(Arb.int(0..2), 1..3)
        checkAll(Arb.list(leg, 0..30), Arb.long(1L..1_000_000L)) { shapes, amount ->
            val ops = shapes.mapIndexed { i, idx ->
                val legs = idx.distinct().mapIndexed { j, a -> accounts[a] to if (j == 0) -amount else amount }
                op(i + 1L, if (legs.size > 1) OpType.TRANSFER else OpType.EXPENSE, *legs.toTypedArray())
            }
            val before = balances(ops)
            val plan = AccountRemoval.plan(card, ops)
            val keptIds = plan.keep.map { it.op.id }.toSet()
            val after = ops.filter { it.op.id !in plan.delete && it.op.id !in keptIds } + plan.keep.map { OperationFull(it.op, it.postings) }
            val now = balances(after)
            assertTrue(after.none { full -> full.postings.any { it.accountId == card.id } })
            listOf(savings, cash).forEach { a -> assertEquals(before[a.id] ?: 0L, now[a.id] ?: 0L) }
        }
    }
}
