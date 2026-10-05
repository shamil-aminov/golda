package sh.aminov.golda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.data.BackupFormat
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Posting
import sh.aminov.golda.data.Rate
import sh.aminov.golda.data.Repo
import sh.aminov.golda.data.VoiceQueue
import sh.aminov.golda.domain.Draft
import java.io.File

/** The data side of the bug hunt, on the test app's isolated storage. */
@RunWith(AndroidJUnit4::class)
class RepoBugHuntTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestGoldaApplication
    private val repo: Repo get() = app.repo
    private val dao get() = app.db.dao()

    @Before
    fun setUp() {
        Emulator.require()
        runBlocking { GoldaUiTest.seed(repo) }
    }

    private suspend fun account(name: String) = repo.accounts.first().single { it.name == name }

    /** #8: changing an old transfer's note leaves the markup, the CBR rates and the ruble values alone. */
    @Test
    fun editingAnOldTransferChangesNothingFinancial() = runBlocking {
        dao.upsertRates(listOf(Rate("USD", 83.25, "2026-07-01")))
        val rub = account("Карта ₽")
        val usd = account("Доллары")
        val id = repo.save(Draft(OpType.TRANSFER, System.currentTimeMillis(), rub.id, 4_600_000, toAccountId = usd.id, toAmountMinor = 50_000))!!
        val learned = repo.settings.flow.first().markup
        assertEquals(0.105, learned, 0.001)
        val before = dao.operation(id)!!

        // Months later the dollar costs 95, and only the note is changed.
        dao.upsertRates(listOf(Rate("USD", 95.0, "2026-10-05")))
        repo.save(Draft(OpType.TRANSFER, before.op.timestamp, rub.id, 4_600_000, toAccountId = usd.id, toAmountMinor = 50_000, note = "обменник", id = id))
        val after = dao.operation(id)!!
        assertEquals(learned, repo.settings.flow.first().markup, 0.0)
        assertEquals(before.op.cbrTo, after.op.cbrTo)
        assertEquals(before.postings.map { it.accountId to it.rubMinor }.toSet(), after.postings.map { it.accountId to it.rubMinor }.toSet())
        assertEquals("обменник", after.op.note)

        // Correcting the amount revalues the postings, but still teaches no markup and keeps the day's rates.
        repo.save(Draft(OpType.TRANSFER, before.op.timestamp, rub.id, 4_600_000, toAccountId = usd.id, toAmountMinor = 51_000, id = id))
        assertEquals(learned, repo.settings.flow.first().markup, 0.0)
        assertEquals(before.op.cbrTo, dao.operation(id)!!.op.cbrTo)
    }

    /** #16: undoing a delete after the account went is refused, not a crash; so is saving to it. */
    @Test
    fun anOperationOfADeletedAccountCannotComeBack() = runBlocking {
        val lari = account("Лари")
        val id = repo.save(Draft(OpType.EXPENSE, System.currentTimeMillis(), lari.id, 1_500))!!
        val full = dao.operation(id)!!
        repo.deleteOperation(id)
        repo.deleteAccount(lari.id)
        assertFalse(repo.restoreOperation(full))
        assertNull(repo.save(Draft(OpType.EXPENSE, System.currentTimeMillis(), lari.id, 1_500)))
    }

    /** #17: a backup that decodes but does not fit changes nothing. */
    @Test
    fun aRestoreThatFailsLeavesTheDataAsItWas() = runBlocking {
        val good = repo.backups.export()
        val broken = BackupFormat.decode(good).let { it.copy(postings = it.postings + Posting(accountId = 99_999, amountMinor = 100, rubMinor = 100, operationId = it.operations.first().id)) }
        val accountsBefore = repo.accounts.first().size
        val operationsBefore = repo.operations.first().size
        assertTrue(runCatching { repo.importBackup(BackupFormat.encode(broken)) }.isFailure)
        assertEquals(accountsBefore, repo.accounts.first().size)
        assertEquals(operationsBefore, repo.operations.first().size)
        // And a good file still restores.
        repo.importBackup(good)
        assertEquals(accountsBefore, repo.accounts.first().size)
    }

    /** #19: "Стереть всё" takes the queued voice notes with it. */
    @Test
    fun erasingEverythingDropsQueuedNotes() = runBlocking {
        File(VoiceQueue.dir(app), "${System.currentTimeMillis() - 60_000}.wav").writeBytes(ByteArray(16))
        repo.resetAll()
        assertEquals(0, VoiceQueue.count(app))
        assertEquals(0, repo.voiceBacklog.value)
    }

    /** #15: undo of a save puts back the markup it taught and the usual account. */
    @Test
    fun undoPutsBackWhatASaveChanged() = runBlocking {
        dao.upsertRates(listOf(Rate("USD", 83.25, "2026-07-01")))
        val rub = account("Карта ₽")
        val usd = account("Доллары")
        val point = repo.undoPoint(emptyList())
        val id = repo.save(Draft(OpType.TRANSFER, System.currentTimeMillis(), rub.id, 4_600_000, toAccountId = usd.id, toAmountMinor = 50_000))!!
        assertTrue(repo.settings.flow.first().markup != point.markup)
        repo.undo(point.copy(ids = listOf(id)))
        assertEquals(point.markup, repo.settings.flow.first().markup, 0.0)
        assertNull(dao.operation(id))
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun emulatorOnly() = Emulator.require()
    }
}
