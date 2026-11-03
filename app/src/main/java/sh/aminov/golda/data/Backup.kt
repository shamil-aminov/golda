package sh.aminov.golda.data

import android.content.Context
import android.provider.DocumentsContract
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.aminov.golda.GoldaApplication
import sh.aminov.golda.TAB_ACCOUNTS
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.tr
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Everything Golda knows, in one file. The Gemini key is not part of it. */
@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: Settings,
    val accounts: List<Account>,
    val categories: List<Category>,
    val operations: List<Operation>,
    val postings: List<Posting>,
    val rates: List<Rate>,
    val obligations: List<Obligation>,
    val goals: List<Goal>,
    val wishes: List<Wish>,
    /** Gemini's reviews; a file from before them has none. */
    val reviews: List<Review> = emptyList(),
    /** The talks under the reviews; a file from before them has none. */
    val reviewMessages: List<ReviewMessage> = emptyList(),
)

/** The file format: unknown keys are skipped and missing ones take their defaults, so older files still load. */
object BackupFormat {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup = json.decodeFromString(Backup.serializer(), text)
}

class Backups(private val db: GoldaDb, private val settings: SettingsStore) {
    private val dao = db.dao()

    suspend fun export(): String {
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            settings = settings.flow.first().copy(hasGeminiKey = false),
            accounts = dao.accountsNow(),
            categories = dao.categoriesNow(),
            operations = dao.operationsAll(),
            postings = dao.postingsAll(),
            rates = dao.ratesNow(),
            obligations = dao.obligationsNow(),
            goals = dao.goalsNow(),
            wishes = dao.wishesAll(),
            reviews = dao.reviewsAll(),
            reviewMessages = dao.messagesAll(),
        )
        return BackupFormat.encode(backup)
    }

    /**
     * Replaces everything with the file's contents. The file is read in full first, and the old data
     * is deleted in the same transaction the new one is written in: a file that does not decode, or
     * does not fit (a posting of a missing account), changes nothing. The Gemini key stays.
     */
    suspend fun import(text: String): Backup {
        val backup = BackupFormat.decode(text)
        db.withTransaction {
            dao.clearPostings()
            dao.clearOperations()
            dao.clearWishes()
            dao.clearGoals()
            dao.clearObligations()
            dao.clearAccounts()
            dao.clearCategories()
            dao.clearRates()
            dao.clearReviews()
            dao.clearMessages()
            dao.insertCategories(backup.categories)
            dao.insertAccounts(backup.accounts)
            dao.insertOperations(backup.operations)
            dao.insertPostings(backup.postings)
            dao.upsertRates(backup.rates)
            dao.insertObligations(backup.obligations)
            dao.insertGoals(backup.goals)
            dao.insertWishes(backup.wishes)
            dao.insertReviews(backup.reviews)
            dao.insertMessages(backup.reviewMessages)
        }
        // The weekly backup's folder belongs to this phone, not to the file.
        settings.update { current ->
            backup.settings.copy(hasGeminiKey = current.hasGeminiKey, onboarded = true, autoBackupFolder = current.autoBackupFolder, lastAutoBackup = current.lastAutoBackup)
        }
        return backup
    }
}

/**
 * A backup once a week into a folder picked once: a file "golda-auto-<date>.json", the newest
 * [KEEP] kept and older ones removed, so a lost phone loses a week at most.
 */
object AutoBackup {
    private const val WORK = "autoBackup"
    const val KEEP = 4
    private const val PREFIX = "golda-auto-"

    /** Weekly from now while [folder] is set; the first one at once. Off without a folder. */
    fun apply(context: Context, folder: String?) {
        val work = WorkManager.getInstance(context)
        if (folder == null) {
            work.cancelUniqueWork(WORK)
            return
        }
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build())
    }

    /** Writes [text] as today's file in the [folder] tree and removes all but the newest [KEEP]. */
    fun write(context: Context, folder: String, text: String, today: java.time.LocalDate = java.time.LocalDate.now()) {
        val resolver = context.contentResolver
        val tree = Uri.parse(folder)
        val root = DocumentsContract.getTreeDocumentId(tree)
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, root)
        val name = "$PREFIX$today.json"
        val existing = children(context, tree, root)
        // One a day: today's again replaces itself.
        existing.filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) }
        val file = DocumentsContract.createDocument(resolver, dir, "application/json", name) ?: error("could not create $name")
        resolver.openOutputStream(file)?.use { it.write(text.toByteArray()) } ?: error("could not write $name")
        children(context, tree, root).filter { it.second.startsWith(PREFIX) }.sortedByDescending { it.second }.drop(KEEP).forEach {
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.first))
        }
    }

    /** The folder's files as (document id, name). */
    private fun children(context: Context, tree: Uri, root: String): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, root)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }.orEmpty()
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GoldaApplication
        val folder = app.repo.settings.flow.first().autoBackupFolder ?: return Result.success()
        return try {
            AutoBackup.write(app, folder, app.repo.backups.export())
            app.repo.settings.update { it.copy(lastAutoBackup = System.currentTimeMillis()) }
            Result.success()
        } catch (e: Exception) {
            // The folder gone or unwritable: try again later rather than lose the week.
            Result.retry()
        }
    }
}

/** Sunday at 19:00, a nudge to compare balances with the bank. */
object ReconcileSchedule {
    private const val WORK = "reconcile"

    fun apply(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).withHour(19).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileReminder>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build(),
        )
    }
}

class ReconcileReminder(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GoldaApplication
        I18n.russian = AppLanguage.russian(app)
        notify(
            app, "reconcile", tr("Сверка", "Reconciling"), 300_000,
            tr("Сверь балансы", "Check your balances"),
            tr("Неделя прошла. Открой банк и сравни счета в Golda, забытые траты найдутся сами",
                "A week has passed. Open your bank and compare the accounts in Golda; forgotten purchases will show up"),
            tab = TAB_ACCOUNTS,
        )
        return Result.success()
    }
}
