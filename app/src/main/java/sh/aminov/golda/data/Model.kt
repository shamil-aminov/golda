package sh.aminov.golda.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

enum class AccountType { CARD, CASH, SAVINGS, CREDIT, LOAN }

enum class OpType { EXPENSE, INCOME, TRANSFER, ADJUSTMENT, OPENING }

enum class CategoryKind { EXPENSE, INCOME }

enum class WishStatus { WAITING, BOUGHT, SKIPPED }

/**
 * What kind of spending an expense is. Only everyday spending counts against the day's limit and its
 * pace; regular payments (rent, phone, subscriptions) and big one-offs (clothes, travel) are shown
 * apart, so a rent paid for two months does not sink one day.
 */
enum class Bucket { DAILY, REGULAR, BIG }

@Entity
@Serializable
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: String,
    val type: AccountType,
    /** Accounts with the same group (e.g. one multi-currency card's USD and GEL parts) are shown together. */
    val groupName: String? = null,
    /** Counts towards "можно сегодня". */
    val includeInFree: Boolean,
    /** Yearly percent: what a savings account earns (on the monthly minimum) or what a debt costs. */
    val interestRate: Double? = null,
    val sort: Int = 0,
    /** Debts: the day of month a payment is due and how much (the annuity, or a card's minimum). */
    val paymentDay: Int? = null,
    val paymentMinor: Long? = null,
    /** Credit cards: the last day of the interest-free period, as an epoch day. */
    val graceUntil: Long? = null,
)

@Entity
@Serializable
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id the voice parser maps phrases to. */
    val key: String,
    val name: String,
    val emoji: String,
    val kind: CategoryKind,
    val sort: Int = 0,
    /** The expense bucket; null means the default for [key] (see Buckets). */
    val bucket: Bucket? = null,
)

@Entity(
    foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId"), Index("timestamp")],
)
@Serializable
data class Operation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: OpType,
    val timestamp: Long,
    val categoryId: Long? = null,
    val note: String = "",
    /** What the user said, when the operation came from voice. */
    val voiceText: String? = null,
    /** Price in the shop's currency when it differs from the account's (80 ฿ paid from a USD card). */
    val purchaseAmountMinor: Long? = null,
    val purchaseCurrency: String? = null,
    /** The charged amount was estimated, not taken from the bank. */
    val isEstimate: Boolean = false,
    /** CBR rates of both sides of a currency exchange when it happened, to measure what it cost. */
    val cbrFrom: Double? = null,
    val cbrTo: Double? = null,
    /** An expense's own bucket, when it differs from its category's ("this one is not everyday"). */
    val bucket: Bucket? = null,
)

/** A payment that comes every month (loan, rent, subscription) and is set aside before "можно сегодня". */
@Entity
@Serializable
data class Obligation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val dayOfMonth: Int,
)

/** Something to save up for. Progress is the linked account's balance plus what was put aside by skipping purchases. */
@Entity
@Serializable
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val accountId: Long? = null,
    val savedMinor: Long = 0,
    /** The one purchases are compared with. */
    val isMain: Boolean = false,
)

/**
 * A review of a period written by Gemini: Markdown, kept so it can be read again. [fromDay] and
 * [toDay] are epoch days, both included.
 */
@Entity(indices = [Index("createdAt")])
@Serializable
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromDay: Long,
    val toDay: Long,
    val createdAt: Long,
    val text: String,
)

/** A purchase put on hold to think about. */
@Entity
@Serializable
data class Wish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val createdAt: Long,
    val decideAt: Long,
    val status: WishStatus = WishStatus.WAITING,
    val decidedAt: Long? = null,
)

/**
 * One side of an operation. [amountMinor] is in the account's currency;
 * [rubMinor] is what that money cost in rubles (kopecks), so the sum of an
 * account's postings gives both its balance and its cost basis.
 */
@Entity(
    foreignKeys = [
        ForeignKey(Operation::class, ["id"], ["operationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Account::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("operationId"), Index("accountId")],
)
@Serializable
data class Posting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: Long = 0,
    val accountId: Long,
    val amountMinor: Long,
    val rubMinor: Long,
)

/** Official CBR rate: rubles per one unit of [code]. */
@Entity
@Serializable
data class Rate(
    @PrimaryKey val code: String,
    val rubPerUnit: Double,
    val date: String,
)

data class OperationFull(
    @Embedded val op: Operation,
    @Relation(parentColumn = "id", entityColumn = "operationId") val postings: List<Posting>,
)
