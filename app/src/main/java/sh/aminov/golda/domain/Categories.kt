package sh.aminov.golda.domain

import sh.aminov.golda.data.Category

private val categoryNames = mapOf(
    "eating_out" to ("Кафе" to "Eating out"),
    "groceries" to ("Продукты" to "Groceries"),
    "transport" to ("Транспорт" to "Transport"),
    "housing" to ("Жильё" to "Housing"),
    "telecom" to ("Связь" to "Phone"),
    "fun" to ("Развлечения" to "Fun"),
    "health" to ("Здоровье" to "Health"),
    "clothes" to ("Одежда" to "Clothes"),
    "subscriptions" to ("Подписки" to "Subscriptions"),
    "travel" to ("Путешествия" to "Travel"),
    "fees" to ("Комиссии" to "Fees"),
    "other" to ("Прочее" to "Other"),
    "salary" to ("Зарплата" to "Salary"),
    "interest" to ("Проценты" to "Interest"),
    "gift" to ("Подарок" to "Gift"),
    "other_income" to ("Прочее" to "Other"),
)

/** Default names that have since been shortened: data saved with them still counts as the default. */
private val formerNames = mapOf("eating_out" to "Еда вне дома")

/** Built-in categories speak the app's language; ones the user renames keep their name. */
fun Category.label(): String =
    categoryNames[key]?.takeIf { it.first == name || formerNames[key] == name }?.let { tr(it.first, it.second) } ?: name
