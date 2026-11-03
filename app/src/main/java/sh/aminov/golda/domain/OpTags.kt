package sh.aminov.golda.domain

import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull

/**
 * Gemini's precise tags on operations ("кафе", "такси", "подписка"): finer than the 12 categories,
 * kept quietly in the record for the card, the export and reviews.
 */
object OpTags {
    /** Notes sent in one request at most. */
    const val BATCH = 150

    private const val MAX_LENGTH = 24

    const val SYSTEM = """Тебе дают записи о покупках и доходах, по одной на строку. Для каждой дай точную метку: одно-два слова по-русски, в нижнем регистре, без точек.
Метка точнее категории: «кафе», «продукты», «фастфуд», «такси», «автобус», «аренда», «коммуналка», «мобильная связь», «интернет», «подписка», «сервер», «одежда», «техника», «аптека», «зарплата», «кэшбэк».
Похожие траты называй одинаково. Верни массив объектов {note, tag}, note точно как в строке."""

    /** The note as a key: trimmed, lower case. */
    fun key(note: String): String = note.trim().lowercase()

    /** A purchase or income with a note and no tag yet. */
    fun wanted(op: Operation): Boolean =
        (op.type == OpType.EXPENSE || op.type == OpType.INCOME) && op.tag == null && op.note.isNotBlank()

    /** Tags already given, by note: a note seen again gets the same tag without asking. */
    fun known(operations: List<OperationFull>): Map<String, String> = operations
        .sortedBy { it.op.timestamp }
        .mapNotNull { full -> full.op.tag?.let { key(full.op.note) to it } }
        .filter { it.first.isNotEmpty() }
        .toMap()

    /** A reply's tag made tidy ("Кафе." → "кафе"), or null when nothing usable is left. */
    fun clean(raw: String): String? = raw.trim().trim('.', ',', '«', '»', '"', '\'').trim().lowercase()
        .takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
