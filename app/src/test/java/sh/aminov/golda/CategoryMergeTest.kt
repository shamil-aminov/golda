package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.DefaultCategories
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.CategoryMerge

/** Four expense categories in each bucket, and every category of an earlier version somewhere that exists. */
class CategoryMergeTest {
    private val expense = DefaultCategories.all.filter { it.kind == CategoryKind.EXPENSE }
    private val keys = expense.map { it.key }

    @Test
    fun fourInEachBucket() {
        val byBucket = expense.groupBy { Buckets.of(it) }.mapValues { (_, list) -> list.map { it.key } }
        assertEquals(listOf("food", "transport", "household", "treats"), byBucket[Bucket.DAILY])
        assertEquals(listOf("housing", "telecom", "subscriptions", "loans"), byBucket[Bucket.REGULAR])
        assertEquals(listOf("shopping", "travel", "health", "other"), byBucket[Bucket.BIG])
    }

    @Test
    fun everyEarlierCategoryGoesSomewhere() {
        val earlier = listOf(
            "eating_out", "groceries", "transport", "housing", "telecom", "fun", "health", "clothes", "subscriptions", "travel", "fees", "other",
            "food", "home", "shopping", "leisure",
        )
        for (key in earlier) {
            val into = CategoryMerge.into[key] ?: key
            assertTrue("$key goes to $into", into in keys)
        }
        assertEquals("treats", CategoryMerge.into["leisure"])
        assertEquals("housing", CategoryMerge.into["home"])
        assertEquals("food", CategoryMerge.into["groceries"])
    }
}
