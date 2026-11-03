package sh.aminov.golda.ui

import sh.aminov.golda.data.AppLanguage
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.label
import sh.aminov.golda.data.ReconcileSchedule
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Which small sheet is open over the settings. */
private enum class Edit { Local, Base, Shown, Markup, Period, Buckets, Key, Model, Language, Notes }

/**
 * Settings as grouped lists, one value per row; a row opens a small sheet where that value is
 * the big number. Ordered by how often a traveller comes here: where you are first, the language last.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    data: AppData,
    onBack: () -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    snackbar: SnackbarHostState,
    onRefreshRates: () -> Unit,
    /** A category with its bucket changed. */
    onSaveCategory: (Category) -> Unit,
    onSaveKey: (String) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onWipe: () -> Unit,
    /** Voice notes not booked yet, with what can be done about them. */
    voiceBacklog: Int = 0,
    onRetryVoice: () -> Unit = {},
    onDiscardVoice: () -> Unit = {},
    /** Opens the regular payments. */
    onRegulars: () -> Unit = {},
) {
    var edit by remember { mutableStateOf<Edit?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(onExport) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    val s = data.settings
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(tr("Настройки", "Settings")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), tr("Назад", "Back")) } },
                scrollBehavior = bar,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Gap.m).padding(top = Gap.s, bottom = Gap.xl),
        ) {
            GroupLabel(tr("Где я", "Where I am"))
            SettingRow(
                0, 3, tr("Местная валюта", "Local currency"),
                painter = painterResource(R.drawable.ic_location),
                value = currencyLabel(s.localCurrency),
                supporting = tr("Для новых трат и голоса", "For new purchases and voice"),
            ) { edit = Edit.Local }
            SettingRow(
                1, 3, tr("Основная валюта", "Main currency"),
                painter = painterResource(R.drawable.ic_coins),
                value = currencyLabel(s.baseCurrency),
                supporting = tr("Крупные цифры и итоги", "Big numbers and totals"),
            ) { edit = Edit.Base }
            SettingRow(2, 3, tr("Показывать суммы в", "Show amounts in"), painter = painterResource(R.drawable.ic_eye), value = s.displayCurrencies.joinToString(" ") { Currencies.symbol(it) }) { edit = Edit.Shown }

            VSpace(Gap.l)
            val ratesDay = data.ratesDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            GroupLabel(
                tr("Курс", "Rates"),
                supporting = data.ratesDate?.let { tr("ЦБ на ", "CBR of ") + (ratesDay?.let(::fullDate) ?: it) },
                trailing = { IconButton(onClick = onRefreshRates) { Icon(painterResource(R.drawable.ic_refresh), tr("Обновить курсы", "Update rates"), tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
            )
            val foreign = s.displayCurrencies.filter { it != "RUB" }.filter { data.rates.official(it) != null && data.rates.display(it) != null }
            val rateCount = 1 + foreign.size
            SettingRow(0, rateCount, tr("Наценка к ЦБ", "Markup over the CBR"), painter = painterResource(R.drawable.ic_percent), value = Fmt.number(s.markup * 100, 1) + " %") { edit = Edit.Markup }
            foreign.forEachIndexed { i, code ->
                // The row says the currency itself; an empty glyph slot keeps the text in line with the markup's.
                GroupRow(
                    i + 1, rateCount,
                    leading = { GlyphSpace() },
                    trailing = { Value(tr("ЦБ ", "CBR ") + Fmt.number(data.rates.official(code)!!, 2)) },
                ) {
                    Text("1 ${Currencies.symbol(code)} = ${Fmt.number(data.rates.display(code)!!, 2)} ₽", style = MaterialTheme.typography.titleMedium.merge(Tnum), maxLines = 1)
                }
            }

            VSpace(Gap.l)
            GroupLabel(tr("Бюджет", "Budget"))
            val envelope = data.envelope
            SettingRow(
                0, 3, tr("Период трат", "Spending period"),
                painter = painterResource(R.drawable.ic_calendar),
                value = envelope?.account?.name,
                supporting = envelope?.let { periodLabel(it.start, it.end, data.today) } ?: tr("Нет счёта для трат", "No account to spend from"),
            ) { if (envelope != null) edit = Edit.Period }
            val expenseCategories = data.categories.filter { it.kind == CategoryKind.EXPENSE }
            SettingRow(
                1, 3, tr("Корзины категорий", "Category buckets"),
                painter = painterResource(R.drawable.ic_cat_other),
                supporting = tr("Регулярно: ", "Regular: ") + expenseCategories.filter { Buckets.of(it) == Bucket.REGULAR }.joinToString(", ") { it.label() }.ifBlank { "—" },
            ) { edit = Edit.Buckets }
            SettingRow(
                2, 3, tr("Регулярные платежи", "Regular payments"),
                painter = painterResource(R.drawable.ic_bell),
                supporting = regularsSummary(data, data.obligations),
                onClick = onRegulars,
            )

            VSpace(Gap.l)
            GroupLabel(tr("Голос", "Voice"))
            val defaultModel = Settings().geminiModel
            val voiceRows = if (voiceBacklog > 0) 3 else 2
            SettingRow(0, voiceRows, tr("Ключ Gemini", "Gemini key"), painter = painterResource(R.drawable.ic_key), value = if (s.hasGeminiKey) tr("сохранён", "saved") else tr("нет", "none")) { edit = Edit.Key }
            SettingRow(1, voiceRows, tr("Модель", "Model"), painter = painterResource(R.drawable.ic_chip), supporting = s.geminiModel + if (s.geminiModel == defaultModel) tr(" · по умолчанию", " · default") else "") { edit = Edit.Model }
            // Notes recorded but not booked: waiting for the network or the key, or set aside after a failure.
            if (voiceBacklog > 0) {
                SettingRow(
                    2, 3, tr("Не разобрано", "Not worked out"),
                    painter = painterResource(R.drawable.ic_mic),
                    value = voiceBacklog.toString(),
                    supporting = tr("Записи ещё не превратились в операции", "Notes not booked yet"),
                ) { edit = Edit.Notes }
            }

            VSpace(Gap.l)
            GroupLabel(tr("Данные", "Data"))
            SettingRow(0, 3, tr("Сохранить копию", "Save a backup"), painter = painterResource(R.drawable.ic_upload), supporting = tr("Всё, кроме ключа, в один файл", "Everything but the key, in one file")) {
                exporter.launch("golda-${LocalDate.now()}.json")
            }
            SettingRow(1, 3, tr("Восстановить", "Restore"), painter = painterResource(R.drawable.ic_download)) {
                importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
            val reminder: (Boolean) -> Unit = { on ->
                onChange { it.copy(reconcileReminder = on) }
                ReconcileSchedule.apply(context, on)
            }
            GroupRow(
                2, 3,
                onClick = { reminder(!s.reconcileReminder) },
                leading = { RowIcon(painter = painterResource(R.drawable.ic_bell)) },
                supporting = { Text(tr("По воскресеньям в 19:00", "Sundays at 19:00")) },
                trailing = { CheckSwitch(s.reconcileReminder, reminder) },
            ) { Text(tr("Напоминать о сверке", "Reconcile reminder")) }

            VSpace(Gap.l)
            GroupLabel(tr("Язык", "Language"))
            SettingRow(0, 1, tr("Язык приложения", "App language"), painter = painterResource(R.drawable.ic_translate), value = languageName(currentLanguage())) { edit = Edit.Language }

            // The one irreversible thing stands apart, last: a plain row that only says it in the
            // error colour; the confirmation carries the alarm.
            VSpace(Gap.xl)
            GroupRow(
                0, 1,
                onClick = { confirmWipe = true },
                leading = { Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error) },
            ) { Text(tr("Стереть всё", "Erase everything"), color = MaterialTheme.colorScheme.error) }
        }
    }

    when (edit) {
        Edit.Base -> PickSheet(tr("Основная валюта — итоги по сегодняшнему курсу", "Main currency: totals at today's rate"), onDismiss = { edit = null }) {
            BaseCurrencyChoice(s) { f -> onChange(f); edit = null }
        }
        Edit.Local -> PickSheet(tr("Местная валюта — в ней суммы, сказанные без валюты", "Local currency, for amounts said without one"), onDismiss = { edit = null }) {
            LocalCurrencyChoice(s) { f -> onChange(f); edit = null }
        }
        Edit.Shown -> PickSheet(tr("Показывать суммы в", "Show amounts in"), onDismiss = { edit = null }, done = true) {
            DisplayCurrencyToggles(s, onChange)
        }
        Edit.Markup -> NumberSheet(
            caption = tr("Наценка к курсу ЦБ", "Markup over the CBR rate"),
            initial = s.markup * 100,
            symbol = "%",
            under = tr("Обновляется сама после обмена рублей", "Updates itself after each ruble exchange"),
            onDismiss = { edit = null },
        ) { v -> onChange { it.copy(markup = v / 100) } }
        Edit.Period -> PeriodSheet(data, onDismiss = { edit = null }, onChange = onChange)
        Edit.Buckets -> PickSheet(tr("Что считается в «можно сегодня»: только «Каждый день»", "What counts in “safe today”: only “Everyday”"), onDismiss = { edit = null }, done = true) {
            BucketChoices(data.categories.filter { it.kind == CategoryKind.EXPENSE }, onSaveCategory)
        }
        Edit.Key -> KeySheet(s.hasGeminiKey, onDismiss = { edit = null }) { key -> onSaveKey(key); edit = null }
        Edit.Model -> ModelSheet(s.geminiModel, Settings().geminiModel, onDismiss = { edit = null }) { model -> onChange { it.copy(geminiModel = model) }; edit = null }
        Edit.Language -> LanguageSheet(onDismiss = { edit = null })
        Edit.Notes -> FormSheet(
            onDismiss = { edit = null },
            actions = {
                TonalAction(tr("Удалить", "Delete"), { onDiscardVoice(); edit = null })
                PrimaryAction(tr("Повторить", "Try again"), { onRetryVoice(); edit = null })
            },
        ) {
            val n = voiceBacklog.toLong()
            Caption(
                tr(
                    "$n ${plural(n, "запись", "записи", "записей", "note", "notes")} не ${plural(n, "разобрана", "разобраны", "разобрано", "", "")}: не было сети или Gemini не справился. Повторить сейчас или удалить, если уже записал вручную?",
                    "$n ${plural(n, "запись", "записи", "записей", "note", "notes")} not worked out yet: there was no network, or Gemini failed. Try again now, or delete if you already added them by hand?",
                ),
            )
        }
        null -> Unit
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(tr("Восстановить из файла?", "Restore from the file?")) },
            text = { Text(tr("Всё, что сейчас в Golda, заменится содержимым файла. Ключ Gemini останется.", "Everything in Golda now will be replaced by the file. The Gemini key stays.")) },
            confirmButton = { TextButton(onClick = { pendingImport = null; onImport(uri) }) { Text(tr("Восстановить", "Restore")) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(tr("Отмена", "Cancel")) } },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(tr("Стереть всё?", "Erase everything?")) },
            text = { Text(tr("Счета, операции и настройки удалятся без возможности вернуть.", "Accounts, operations and settings will be gone for good.")) },
            confirmButton = {
                TextButton(onClick = { confirmWipe = false; onWipe() }) { Text(tr("Стереть", "Erase"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}

private fun fullDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale))

/** "15-го" / "day 15". */
private fun dayOfMonth(day: Int): String = tr("$day-го", "day $day")

/** One setting: a plain glyph, the name, and its value at the end in quiet ink. */
@Composable
private fun SettingRow(
    index: Int,
    count: Int,
    title: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    GroupRow(
        index, count,
        onClick = onClick,
        leading = leading ?: when {
            icon != null -> ({ RowIcon(icon) })
            painter != null -> ({ RowIcon(painter = painter) })
            else -> null
        },
        supporting = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailing = value?.let { { Value(it) } },
    ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Value(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** A settings row's glyph: 24 dp, quiet ink, no circle. */
@Composable
private fun RowIcon(icon: ImageVector? = null, painter: Painter? = null) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        icon != null -> Icon(icon, null, Modifier.size(24.dp), tint = tint)
        painter != null -> Icon(painter, null, Modifier.size(24.dp), tint = tint)
    }
}

/** Where a glyph would be, for a row in a group of rows that have one. */
@Composable
private fun GlyphSpace() = Spacer(Modifier.size(24.dp))

/** A small sheet with one choice; picking usually closes it, [done] adds a "Готово" for several. */
@Composable
private fun PickSheet(caption: String, onDismiss: () -> Unit, done: Boolean = false, content: @Composable () -> Unit) {
    FormSheet(onDismiss = onDismiss, actions = if (done) ({ PrimaryAction(tr("Готово", "Done"), onDismiss) }) else null) {
        Caption(caption)
        VSpace(Gap.m)
        content()
    }
}

/** One number, big, with its unit: the markup, a rate. */
@Composable
private fun NumberSheet(
    caption: String,
    initial: Double,
    symbol: String,
    under: String? = null,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(if (initial == 0.0) "" else Fmt.number(initial)) }
    val value = Fmt.parseDouble(text)?.takeIf { it >= 0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    FormSheet(onDismiss = onDismiss, actions = { PrimaryAction(tr("Сохранить", "Save"), { value?.let { onSave(it); onDismiss() } }, value != null) }) {
        Caption(caption)
        VSpace(Gap.l)
        HeroAmountField(text, { text = it }, symbol, focusRequester = focus, onDone = { value?.let { onSave(it); onDismiss() } })
        under?.let { VSpace(Gap.xs); UnderLine(it) }
    }
}

@Composable
fun DayGrid(selected: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap.xs)) {
        (1..31).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val on = day == selected
                        Surface(
                            onClick = { onPick(day) },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = if (on) pickedColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(day.toString(), style = MaterialTheme.typography.titleSmall.merge(Tnum)) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeySheet(hasKey: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    FormSheet(
        onDismiss = onDismiss,
        actionGap = ButtonGroupDefaults.ConnectedSpaceBetween,
        actions = {
            if (hasKey) TonalAction(tr("Удалить", "Remove"), { onSave("") }, shapes = connectedStart())
            PrimaryAction(tr("Сохранить", "Save"), { onSave(key.trim()) }, key.isNotBlank(), weight = if (hasKey) 2f else 1f, shapes = if (hasKey) connectedEnd() else ButtonDefaults.shapes())
        },
    ) {
        Caption(if (hasKey) tr("Ключ Gemini · заменить", "Gemini key · replace") else tr("Ключ Gemini", "Gemini key"))
        VSpace(Gap.m)
        TonalField(
            key, { key = it }, Modifier.fillMaxWidth(),
            placeholder = tr("Вставь ключ", "Paste the key"),
            visualTransformation = PasswordVisualTransformation(),
            focusRequester = focus,
            onDone = { if (key.isNotBlank()) onSave(key.trim()) },
        )
        VSpace(Gap.s)
        Text(
            tr("Создаётся в Google AI Studio и хранится зашифрованным", "Made in Google AI Studio, stored encrypted"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelSheet(current: String, default: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var model by remember { mutableStateOf(current) }
    FormSheet(onDismiss = onDismiss, actions = { PrimaryAction(tr("Сохранить", "Save"), { onSave(model.trim()) }, model.isNotBlank()) }) {
        Caption(tr("Модель Gemini", "Gemini model"))
        VSpace(Gap.m)
        TonalField(model, { model = it }, Modifier.fillMaxWidth(), placeholder = default, onDone = { if (model.isNotBlank()) onSave(model.trim()) })
        if (model.trim() != default) {
            VSpace(Gap.s)
            TextButton(onClick = { model = default }, contentPadding = PaddingValues(horizontal = Gap.s)) { Text(tr("Вернуть $default", "Back to $default")) }
        }
    }
}

private fun languageName(tag: String) = when (tag) {
    "ru" -> "Русский"
    "en" -> "English"
    else -> tr("Как в системе", "System")
}

@Composable
private fun currentLanguage(): String = AppLanguage.tag(LocalContext.current)

/** Russian, English or whatever the phone speaks; the system recreates the screen in the new language. */
@Composable
private fun LanguageSheet(onDismiss: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val current = currentLanguage()
    PickSheet(tr("Язык приложения", "App language"), onDismiss) {
        ChoiceGroup(listOf("ru", "en", ""), current, { tag ->
            onDismiss()
            activity?.let { AppLanguage.set(it, tag) }
        }, Modifier.fillMaxWidth()) { ChoiceText(if (it.isEmpty()) tr("Системный", "System") else languageName(it)) }
    }
}

/** A monthly payment: what, how much, which day. ⋮ deletes it (with undo). */

/** The activity behind a Compose context, through any wrappers. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Each expense category with its bucket: three or four regular ones, a few big ones, the rest everyday. */
@Composable
private fun BucketChoices(categories: List<Category>, onSave: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap.m)) {
        categories.forEach { category ->
            Column {
                Text(category.label(), style = MaterialTheme.typography.titleMedium)
                VSpace(Gap.xs)
                ChoiceGroup(listOf(Bucket.DAILY, Bucket.REGULAR, Bucket.BIG), Buckets.of(category), { onSave(category.copy(bucket = it)) }, Modifier.fillMaxWidth()) {
                    ChoiceText(bucketLabel(it))
                }
            }
        }
    }
}
