package sh.aminov.golda.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.layout.offset
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
private enum class Edit { Local, Base, Shown, Markup, Palette, Key, Model, Language, Notes }

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
    onSaveKey: (String) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onWipe: () -> Unit,
    /** Voice notes not booked yet, with what can be done about them. */
    voiceBacklog: Int = 0,
    onRetryVoice: () -> Unit = {},
    onDiscardVoice: () -> Unit = {},
) {
    var edit by remember { mutableStateOf<Edit?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(onExport) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    val s = data.settings
    BackHandler(onBack = onBack)

    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { CollapsingHeader(tr("Настройки", "Settings"), onBack, bar) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Android's own settings: no cards, a header with a hairline over each section, rows of
        // plain text with the value under the title.
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = Gap.xl)) {
            Section(tr("Где я", "Where I am"))
            SettingRow(tr("Местная валюта", "Local currency"), currencyLabel(s.localCurrency) + tr(", для новых трат и голоса", ", for new purchases and voice")) { edit = Edit.Local }
            SettingRow(tr("Основная валюта", "Main currency"), currencyLabel(s.baseCurrency) + tr(", крупные цифры и итоги", ", big numbers and totals")) { edit = Edit.Base }
            SettingRow(tr("Показывать суммы в", "Show amounts in"), s.displayCurrencies.joinToString(" ") { Currencies.symbol(it) }) { edit = Edit.Shown }

            val ratesDay = data.ratesDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            Section(tr("Курс", "Rates"))
            SettingRow(tr("Наценка к ЦБ", "Markup over the CBR"), Fmt.number(s.markup * 100, 1) + " %") { edit = Edit.Markup }
            val foreign = s.displayCurrencies.filter { it != "RUB" }.filter { data.rates.official(it) != null && data.rates.display(it) != null }
            foreign.forEach { code ->
                SettingRow(
                    "1 ${Currencies.symbol(code)} = ${Fmt.number(data.rates.display(code)!!, 2)} ₽",
                    tr("ЦБ ", "CBR ") + Fmt.number(data.rates.official(code)!!, 2),
                )
            }
            // Rarely needed, so a plain row at the end rather than a button in the header.
            SettingRow(tr("Обновить курс", "Update rates"), data.ratesDate?.let { tr("ЦБ на ", "CBR of ") + (ratesDay?.let(::fullDate) ?: it) }) { onRefreshRates() }

            Section(tr("Голос", "Voice"))
            val defaultModel = Settings().geminiModel
            SettingRow(tr("Ключ Gemini", "Gemini key"), if (s.hasGeminiKey) tr("Сохранён", "Saved") else tr("Нет", "None")) { edit = Edit.Key }
            SettingRow(tr("Модель", "Model"), s.geminiModel + if (s.geminiModel == defaultModel) tr(", по умолчанию", ", default") else "") { edit = Edit.Model }
            // Notes recorded but not booked: waiting for the network or the key, or set aside after a failure.
            if (voiceBacklog > 0) {
                SettingRow(tr("Не разобрано: $voiceBacklog", "Not worked out: $voiceBacklog"), tr("Записи ещё не превратились в операции", "Notes not booked yet")) { edit = Edit.Notes }
            }

            Section(tr("Данные", "Data"))
            SettingRow(tr("Сохранить копию", "Save a backup"), tr("Всё, кроме ключа, в один файл", "Everything but the key, in one file")) {
                exporter.launch("golda-${LocalDate.now()}.json")
            }
            SettingRow(tr("Восстановить", "Restore"), tr("Заменить всё содержимым файла", "Replace everything with a file")) {
                importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }

            val reminder: (Boolean) -> Unit = { on ->
                onChange { it.copy(reconcileReminder = on) }
                ReconcileSchedule.apply(context, on)
            }
            Section(tr("Напоминания", "Reminders"))
            SettingRow(tr("Напоминать о сверке", "Reconcile reminder"), tr("По воскресеньям в 19:00", "Sundays at 19:00"), trailing = { CheckSwitch(s.reconcileReminder, reminder) }) {
                reminder(!s.reconcileReminder)
            }

            Section(tr("Оформление", "Appearance"))
            SettingRow(tr("Палитра", "Palette"), Palette.of(s.palette).label) { edit = Edit.Palette }

            Section(tr("Язык", "Language"))
            SettingRow(tr("Язык приложения", "App language"), languageName(currentLanguage())) { edit = Edit.Language }

            // The one irreversible thing stands apart, last, in the error colour; the confirmation carries the alarm.
            Section("")
            SettingRow(tr("Стереть всё", "Erase everything"), tr("Счета, операции и настройки", "Accounts, operations and settings"), danger = true) { confirmWipe = true }
        }
    }

    when (edit) {
        Edit.Base -> PickSheet(tr("Основная валюта: итоги по сегодняшнему курсу", "Main currency: totals at today's rate"), onDismiss = { edit = null }) {
            BaseCurrencyChoice(s) { f -> onChange(f); edit = null }
        }
        Edit.Local -> PickSheet(tr("Местная валюта: в ней суммы, сказанные без валюты", "Local currency, for amounts said without one"), onDismiss = { edit = null }) {
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
        Edit.Key -> KeySheet(s.hasGeminiKey, onDismiss = { edit = null }) { key -> onSaveKey(key); edit = null }
        Edit.Model -> ModelSheet(s.geminiModel, Settings().geminiModel, onDismiss = { edit = null }) { model -> onChange { it.copy(geminiModel = model) }; edit = null }
        Edit.Language -> LanguageSheet(onDismiss = { edit = null })
        Edit.Palette -> PaletteSheet(Palette.of(s.palette), onDismiss = { edit = null }) { picked -> onChange { it.copy(palette = picked.key) } }
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

/**
 * A section's header the Android way: its name small and semibold, then a hairline to the edge.
 * An empty name is the line alone, to set a row apart.
 */
@Composable
private fun Section(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gap.l, end = if (trailing != null) Gap.s else Gap.l, top = Gap.l).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.width(Gap.s))
        }
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        trailing?.invoke()
    }
}

/** One setting: the title in large plain text, what it is set to under it; a switch at the end if it has one. */
@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Gap.l, vertical = Gap.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = quiet) }
        }
        trailing?.let {
            Spacer(Modifier.width(Gap.m))
            it()
        }
    }
}


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


/** The themes as rows: a strip of each one's colours and its name; picking applies it at once. */
@Composable
private fun PaletteSheet(current: Palette, onDismiss: () -> Unit, onPick: (Palette) -> Unit) {
    FormSheet(onDismiss = onDismiss, actions = { PrimaryAction(tr("Готово", "Done"), onDismiss) }) {
        Caption(tr("Палитра", "Palette"))
        VSpace(Gap.s)
        val system = androidx.compose.foundation.isSystemInDarkTheme()
        Palette.entries.forEach { palette ->
            val t = palette.tones(system)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(palette) }.padding(vertical = Gap.s, horizontal = Gap.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The page with a card, the picked tone, the action and the ink on it: the theme at a glance.
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(t.page).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(t.card, t.graphite, t.gold, t.text).forEach { c ->
                        Box(Modifier.size(20.dp).clip(CircleShape).background(c))
                    }
                }
                Spacer(Modifier.width(Gap.m))
                Text(
                    palette.label + when {
                        palette == Palette.GOLDA -> tr(", как в системе", ", follows the system")
                        t.dark -> tr(", тёмная", ", dark")
                        else -> tr(", светлая", ", light")
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (palette == current) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp))
            }
        }
    }
}
