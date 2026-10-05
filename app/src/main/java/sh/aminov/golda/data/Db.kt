package sh.aminov.golda.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GoldaDao {
    @Query("SELECT * FROM Account ORDER BY sort, id")
    fun accounts(): Flow<List<Account>>

    @Transaction
    @Query("SELECT * FROM Operation ORDER BY timestamp DESC, id DESC")
    fun operations(): Flow<List<OperationFull>>

    @Query("SELECT * FROM Category ORDER BY kind, sort")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM Rate")
    fun rates(): Flow<List<Rate>>

    @Query("SELECT * FROM Obligation ORDER BY dayOfMonth, id")
    fun obligations(): Flow<List<Obligation>>

    @Upsert
    suspend fun upsertObligation(obligation: Obligation)

    @Query("SELECT * FROM Obligation")
    suspend fun obligationsNow(): List<Obligation>

    @Transaction
    @Query("SELECT * FROM Operation WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun operationsSince(since: Long): List<OperationFull>

    @Query("SELECT * FROM Goal ORDER BY isMain DESC, id")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal ORDER BY isMain DESC, id")
    suspend fun goalsNow(): List<Goal>

    @Upsert
    suspend fun upsertGoal(goal: Goal): Long

    @Query("UPDATE Goal SET isMain = 0 WHERE id != :id")
    suspend fun unsetMainExcept(id: Long)

    @Query("DELETE FROM Goal WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM Wish ORDER BY status, decideAt DESC")
    fun wishes(): Flow<List<Wish>>

    @Transaction
    @Query("SELECT * FROM Operation WHERE id = :id")
    suspend fun operation(id: Long): OperationFull?

    @Query("SELECT * FROM Wish WHERE id = :id")
    suspend fun wish(id: Long): Wish?

    @Upsert
    suspend fun upsertWish(wish: Wish): Long

    @Query("DELETE FROM Wish WHERE id = :id")
    suspend fun deleteWish(id: Long)

    // Emptying every table inside a restore's transaction, children first, so a failed restore rolls back.
    @Query("DELETE FROM Posting") suspend fun clearPostings()
    @Query("DELETE FROM Operation") suspend fun clearOperations()
    @Query("DELETE FROM Wish") suspend fun clearWishes()
    @Query("DELETE FROM Goal") suspend fun clearGoals()
    @Query("DELETE FROM Obligation") suspend fun clearObligations()
    @Query("DELETE FROM Account") suspend fun clearAccounts()
    @Query("DELETE FROM Category") suspend fun clearCategories()
    @Query("DELETE FROM Rate") suspend fun clearRates()

    // Everything at once, for backups.
    @Query("SELECT * FROM Operation") suspend fun operationsAll(): List<Operation>
    @Query("SELECT * FROM Posting") suspend fun postingsAll(): List<Posting>
    @Query("SELECT * FROM Wish") suspend fun wishesAll(): List<Wish>
    @Insert suspend fun insertAccounts(items: List<Account>)
    @Insert suspend fun insertOperations(items: List<Operation>)
    @Insert suspend fun insertObligations(items: List<Obligation>)
    @Insert suspend fun insertGoals(items: List<Goal>)
    @Insert suspend fun insertWishes(items: List<Wish>)

    @Query("DELETE FROM Obligation WHERE id = :id")
    suspend fun deleteObligation(id: Long)

    @Query("SELECT * FROM Account")
    suspend fun accountsNow(): List<Account>

    @Query("SELECT * FROM Posting WHERE operationId != :except")
    suspend fun postingsNow(except: Long = 0): List<Posting>

    @Query("SELECT * FROM Rate")
    suspend fun ratesNow(): List<Rate>

    @Query("SELECT * FROM Category ORDER BY kind, sort")
    suspend fun categoriesNow(): List<Category>

    @Query("SELECT COUNT(*) FROM Category")
    suspend fun categoryCount(): Int

    @Insert
    suspend fun insertCategories(categories: List<Category>)

    @Upsert
    suspend fun upsertRates(rates: List<Rate>)

    @Insert
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Query("DELETE FROM Operation WHERE id IN (SELECT operationId FROM Posting WHERE accountId = :accountId)")
    suspend fun deleteOperationsOf(accountId: Long)

    @Query("DELETE FROM Account WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Insert
    suspend fun insertOperation(op: Operation): Long

    @Update
    suspend fun updateOperation(op: Operation)

    @Insert
    suspend fun insertPostings(postings: List<Posting>)

    @Update
    suspend fun updatePosting(posting: Posting)

    @Query("DELETE FROM Posting WHERE operationId = :operationId")
    suspend fun deletePostings(operationId: Long)

    @Query("DELETE FROM Operation WHERE id = :id")
    suspend fun deleteOperation(id: Long)

    @Transaction
    suspend fun saveOperation(op: Operation, postings: List<Posting>): Long {
        val id = if (op.id == 0L) insertOperation(op) else op.id.also { updateOperation(op); deletePostings(it) }
        insertPostings(postings.map { it.copy(operationId = id) })
        return id
    }

    /** Undo of a delete: the operation and its postings come back with their ids. */
    @Transaction
    suspend fun restoreOperation(op: Operation, postings: List<Posting>) {
        insertOperation(op)
        insertPostings(postings)
    }
}

@Database(
    entities = [Account::class, Category::class, Operation::class, Posting::class, Rate::class, Obligation::class, Goal::class, Wish::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class GoldaDb : RoomDatabase() {
    abstract fun dao(): GoldaDao

    companion object {
        fun open(context: Context): GoldaDb =
            Room.databaseBuilder(context, GoldaDb::class.java, "golda.db").build()
    }
}
