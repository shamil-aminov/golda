package sh.aminov.golda

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.json.JSONObject
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Gemini
import sh.aminov.golda.data.Snapshot
import sh.aminov.golda.data.DefaultCategories
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.VoicePrompt
import java.io.File
import java.time.ZoneId
import kotlin.math.max

/** A receipt's photo on its way to Gemini: as small as it can be and still be read. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapshotTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun theLongSideComesDownAndNeverUp() = prop {
        checkAll(Arb.int(1..9000), Arb.int(1..9000)) { w, h ->
            val (fw, fh) = Snapshot.fitted(w, h)
            assertTrue(max(fw, fh) <= Snapshot.LONGEST_SIDE)
            assertTrue(fw <= w && fh <= h)
            if (max(w, h) > Snapshot.LONGEST_SIDE) assertEquals(Snapshot.LONGEST_SIDE, max(fw, fh)) else assertEquals(w to h, fw to fh)
        }
    }

    @Test
    fun decodingNeverDropsUnderTheLongSide() = prop {
        checkAll(Arb.int(1..9000), Arb.int(1..9000)) { w, h ->
            val sample = Snapshot.sampleSize(w, h)
            assertTrue(sample == 1 || max(w, h) / sample >= Snapshot.LONGEST_SIDE)
            assertEquals(0, sample and (sample - 1))
        }
    }

    private fun photo(width: Int, height: Int): File {
        val file = folder.newFile("photo.jpg")
        file.outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    @Test
    fun aBigPhotoComesOutSmallerAndAJpeg() {
        val to = folder.newFile("out.jpg")
        assertTrue(Snapshot.shrink(photo(3200, 2400), to))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(to.path, bounds)
        assertEquals(1600, bounds.outWidth)
        assertEquals(1200, bounds.outHeight)
    }

    @Test
    fun notAPhotoIsNotShrunk() {
        val junk = folder.newFile("junk.jpg").apply { writeText("not a picture") }
        assertFalse(Snapshot.shrink(junk, folder.newFile("out2.jpg")))
    }

    @Test
    fun aReceiptsReplyIsOneExpenseOfItsTotalNamedByTheShop() {
        val reply = """{"candidates":[{"content":{"parts":[{"text":${JSONObject.quote("""{"transcript":"Spar 45.50","items":[{"intent":"expense","amount":"45.50","currency":"GEL","note":"Spar","category":"food","account_id":null,"to_account_id":null,"to_amount":null,"date":null}]}""")}}]}}]}"""
        val account = Account(1, "TBC", "GEL", AccountType.CARD, includeInFree = true)
        val categories = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val settings = Settings(localCurrency = "GEL", displayCurrencies = listOf("GEL"))
        val actions = VoiceMapper.actions(Gemini.readReply(reply), listOf(account), categories, settings, Rates(emptyMap(), 0.0), 0L, ZoneId.of("UTC"), emptyMap())
        val draft = (actions.single() as VoiceAction.Record).draft
        assertEquals(4_550L, draft.amountMinor)
        assertEquals("Spar", draft.note)
        assertEquals(categories.first { it.key == "food" }.id, draft.categoryId)
        assertFalse(draft.isEstimate)
    }

    @Test
    fun aReceiptAsksForOneItemAndTheTotal() {
        assertTrue(VoicePrompt.RECEIPT.contains("один элемент items"))
        assertTrue(VoicePrompt.RECEIPT.contains("итог к оплате"))
    }

    @Test
    fun aNoteGoesByItsKind() {
        assertEquals("image/jpeg", Gemini.mime(File("1.jpg")))
        assertEquals("audio/ogg", Gemini.mime(File("1.ogg")))
        assertEquals("audio/aac", Gemini.mime(File("1.aac")))
        assertEquals("audio/mp4", Gemini.mime(File("1.m4a")))
        assertEquals("audio/wav", Gemini.mime(File("1.wav")))
    }
}
