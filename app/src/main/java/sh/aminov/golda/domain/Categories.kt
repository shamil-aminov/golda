package sh.aminov.golda.domain

import sh.aminov.golda.data.Category

private val categoryNames = mapOf(
    "food" to ("Еда" to "Food"),
    "transport" to ("Транспорт" to "Transport"),
    "household" to ("Бытовое" to "Household"),
    "treats" to ("Радости" to "Treats"),
    "housing" to ("Жильё" to "Housing"),
    "telecom" to ("Связь" to "Phone"),
    "subscriptions" to ("Подписки" to "Subscriptions"),
    "loans" to ("Кредиты" to "Loans"),
    "shopping" to ("Покупки" to "Shopping"),
    "travel" to ("Поездки" to "Travel"),
    "health" to ("Здоровье" to "Health"),
    "other" to ("Прочее" to "Other"),
    "salary" to ("Зарплата" to "Salary"),
    "interest" to ("Проценты" to "Interest"),
    "gift" to ("Подарок" to "Gift"),
    "other_income" to ("Прочее" to "Other"),
)

/** Built-in categories speak the app's language; ones the user renames keep their name. */
fun Category.label(): String =
    categoryNames[key]?.takeIf { it.first == name }?.let { tr(it.first, it.second) } ?: name

/**
 * The expense categories: four in each bucket (SPEC §5.3). Everyday splits what is needed from what is
 * not ("Радости": coffee, beer, sweets), so insights can say how much went on what could wait.
 * [into] is where each category of an earlier version went; one of your own is not in it and stays.
 */
object CategoryMerge {
    val into = mapOf(
        // The six of the first 0.17 builds.
        "home" to "housing",
        "leisure" to "treats",
        // The twelve before them.
        "eating_out" to "food",
        "groceries" to "food",
        "fun" to "treats",
        "clothes" to "shopping",
        "fees" to "other",
    )

    /** Names a default had in an earlier build: a row still called so takes the current one. */
    val formerNames = mapOf("treats" to setOf("Удовольствия"))
}
