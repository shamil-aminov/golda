package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Backup
import sh.aminov.golda.data.BackupFormat
import sh.aminov.golda.data.Review
import sh.aminov.golda.data.ReviewMessage
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.Settings

/** The talk under a review: what Gemini reads, in what order, and that it survives a backup. */
class ReviewChatTest {
    @Test
    fun theTalkStartsFromTheDataAndTheReview() {
        val turns = ReviewPrompt.turns(
            data = "Расходы всего: 30 000 ₽",
            review = "## октябрь\nБольше всего ушло на жильё.",
            earlier = listOf(true to "А на кофе?", false to "Кофе 23 раза, всего 10 350 ₽."),
            question = "  А если бросить такси?  ",
        )
        // The data and the request come first, as the person's turn; the review is the model's answer.
        assertEquals(true, turns[0].first)
        assertTrue(turns[0].second.contains("Расходы всего: 30 000 ₽"))
        assertEquals(false to "## октябрь\nБольше всего ушло на жильё.", turns[1])
        // Then the talk so far, oldest first, and the new question, trimmed.
        assertEquals(true to "А на кофе?", turns[2])
        assertEquals(false to "Кофе 23 раза, всего 10 350 ₽.", turns[3])
        assertEquals(true to "А если бросить такси?", turns.last())
        assertEquals(5, turns.size)
        // People and the model take turns, as the API expects.
        turns.zipWithNext().forEach { (a, b) -> assertTrue(a.first != b.first) }
        assertTrue(ReviewPrompt.chatSystem.contains("показывай расчёт"))
    }

    @Test
    fun aBackupKeepsTheTalk() {
        val backup = Backup(
            exportedAt = 0, settings = Settings(),
            accounts = emptyList(), categories = emptyList(), operations = emptyList(), postings = emptyList(),
            rates = emptyList(), obligations = emptyList(), goals = emptyList(), wishes = emptyList(),
            reviews = listOf(Review(1, 20_000, 20_030, 5, "## октябрь")),
            reviewMessages = listOf(ReviewMessage(1, 1, true, "А на кофе?", 6), ReviewMessage(2, 1, false, "10 350 ₽", 7)),
        )
        val back = BackupFormat.decode(BackupFormat.encode(backup))
        assertEquals(backup.reviewMessages, back.reviewMessages)
        // A file from before the talk loads with none.
        val old = BackupFormat.encode(backup.copy(reviewMessages = emptyList())).replace(Regex(""",?\s*"reviewMessages":\s*\[\s*]"""), "")
        assertEquals(emptyList<ReviewMessage>(), BackupFormat.decode(old).reviewMessages)
    }
}
