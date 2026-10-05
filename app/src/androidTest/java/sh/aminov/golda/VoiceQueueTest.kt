package sh.aminov.golda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.data.Gemini
import sh.aminov.golda.data.GeminiException
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Repo
import sh.aminov.golda.data.VoiceParser
import sh.aminov.golda.data.VoiceQueue
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.VoiceItem
import sh.aminov.golda.domain.VoiceResult
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The voice policy without Gemini: a fake parser stands in for it, so no key and no network are
 * involved. Notes are tiny files in the test app's own queue folder, which is emptied before and after.
 */
@RunWith(AndroidJUnit4::class)
class VoiceQueueTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestGoldaApplication
    private val repo: Repo get() = app.repo
    private lateinit var original: VoiceParser
    private var calls = 0

    @Before
    fun setUp() {
        Emulator.require()
        runBlocking { GoldaUiTest.seed(repo) }
        original = repo.voiceParser
        VoiceQueue.wipe(app)
        calls = 0
    }

    @After
    fun tearDown() {
        repo.voiceParser = original
        VoiceQueue.wipe(app)
    }

    /** A note recorded at [recordedAt], old enough not to look like a recording in progress. */
    private fun note(recordedAt: Long = System.currentTimeMillis() - 60_000): File =
        File(VoiceQueue.dir(app), "$recordedAt.wav").apply {
            writeBytes(ByteArray(16))
            setLastModified(System.currentTimeMillis() - 60_000)
        }

    private fun failing(kind: GeminiException.Kind) = VoiceParser { _, _, _ ->
        calls++
        throw GeminiException("test", kind)
    }

    private fun coffee(date: String? = null) = VoiceParser { _, _, _ ->
        calls++
        VoiceResult("кофе 8 рублей", listOf(VoiceItem("expense", "8", "RUB", "кофе", null, null, null, null, date)))
    }

    private fun expenses() = runBlocking { repo.operations.first().filter { it.op.type == OpType.EXPENSE } }

    @Test
    fun aRejectedNoteIsSetAsideAndNotRetriedOnItsOwn() = runBlocking {
        note()
        repo.voiceParser = failing(GeminiException.Kind.REJECTED)
        repo.processVoiceQueue()
        assertEquals(1, calls)
        assertTrue(VoiceQueue.pending(app).isEmpty())
        assertEquals(1, VoiceQueue.setAside(app).size)
        assertEquals(1, repo.voiceBacklog.value)

        // The next start leaves it alone.
        repo.processVoiceQueue()
        assertEquals(1, calls)

        // "Повторить" in Settings tries it again, and now it books.
        repo.voiceParser = coffee()
        repo.retryVoiceNotes()
        assertEquals(1, expenses().size)
        assertEquals(0, repo.voiceBacklog.value)
    }

    @Test
    fun aBusyGeminiGetsAFewTriesThenTheNoteIsSetAside() = runBlocking {
        note()
        repo.voiceParser = failing(GeminiException.Kind.BUSY)
        repeat(VoiceQueue.MAX_ATTEMPTS + 2) { repo.processVoiceQueue() }
        assertEquals(VoiceQueue.MAX_ATTEMPTS, calls)
        assertEquals(1, VoiceQueue.setAside(app).size)
        assertTrue(expenses().isEmpty())
    }

    @Test
    fun withoutNetworkANoteWaitsAsLongAsItTakes() = runBlocking {
        note()
        repo.voiceParser = failing(GeminiException.Kind.OFFLINE)
        repeat(5) { repo.processVoiceQueue() }
        assertEquals(5, calls)
        assertEquals(0, VoiceQueue.attempts(VoiceQueue.pending(app).single()))
        assertTrue(VoiceQueue.setAside(app).isEmpty())
    }

    @Test
    fun anythingUnexpectedNeverEscapesTheQueue() = runBlocking {
        note()
        repo.voiceParser = VoiceParser { _, _, _ -> calls++; error("a reply nobody expected") }
        repo.processVoiceQueue() // must not throw
        assertEquals(1, VoiceQueue.setAside(app).size)
        repo.processVoiceQueue()
        assertEquals(1, calls)
    }

    @Test
    fun deletingTheBacklogDropsEveryNote() = runBlocking {
        note(System.currentTimeMillis() - 120_000)
        note()
        repo.voiceParser = failing(GeminiException.Kind.REJECTED)
        repo.processVoiceQueue() // the first is set aside, the second stays queued behind it
        repo.voiceParser = failing(GeminiException.Kind.OFFLINE)
        repo.processVoiceQueue()
        assertEquals(2, repo.voiceBacklog.value)
        repo.discardVoiceNotes()
        assertEquals(0, repo.voiceBacklog.value)
        assertEquals(0, VoiceQueue.count(app))
    }

    @Test
    fun yesterdayInAQueuedNoteIsTheDayBeforeItWasRecorded() = runBlocking {
        val zone = ZoneId.systemDefault()
        // Recorded on Monday evening, understood on Tuesday: "вчера" is Sunday.
        val monday = LocalDate.now(zone).minusDays(1)
        val recordedAt = monday.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        note(recordedAt)
        var prompt = ""
        repo.voiceParser = VoiceParser { _, system, _ ->
            prompt = system
            VoiceResult("вчера кофе 8 рублей", listOf(VoiceItem("expense", "8", "RUB", "кофе", null, null, null, null, monday.minusDays(1).toString())))
        }
        repo.processVoiceQueue()
        assertTrue("the prompt's today is the recording day: $prompt", prompt.contains("Сегодня $monday"))
        val booked = expenses().single()
        assertEquals(monday.minusDays(1), Ledger.localDate(booked.op.timestamp, zone))
    }

    @Test
    fun malformedRepliesAreRejectedNotThrown() {
        fun kind(body: String) = runCatching { Gemini.readReply(body) }.exceptionOrNull().let { (it as? GeminiException)?.kind }
        val rejected = GeminiException.Kind.REJECTED
        assertEquals(rejected, kind("{}"))
        assertEquals(rejected, kind("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(rejected, kind("""{"candidates":[{"finishReason":"MAX_TOKENS"}]}"""))
        assertEquals(rejected, kind("""{"candidates":[{"content":{"parts":[{"text":"[1,2]"}]}}]}"""))
        assertEquals(rejected, kind("""{"candidates":[{"content":{"parts":[{"text":"{\"transcript\":\"a\",\"items\":[{"}]}}]}"""))
        assertEquals(rejected, kind("""{"candidates":[{"content":{"parts":[{"text":"{\"items\":[1,2]}"}]}}]}"""))
        assertEquals(rejected, kind("not json at all"))
        val fine = Gemini.readReply("""{"candidates":[{"content":{"parts":[{"text":"{\"transcript\":\"кофе\",\"items\":[{\"intent\":\"expense\",\"amount\":\"8\",\"note\":\"кофе\"}]}"}]}}]}""")
        assertEquals("8", fine.items.single().amount)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun emulatorOnly() = Emulator.require()
    }
}
