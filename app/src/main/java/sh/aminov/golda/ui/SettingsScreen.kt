package sh.aminov.golda.ui

import java.time.ZoneId
import java.time.Instant
import sh.aminov.golda.data.AutoBackup
import android.content.Intent
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
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
private enum class Edit { Local, Base, Shown, Markup, Key, Model, ReviewModel, Language, Notes }

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
    /** Updates the rates; calls back once done, so the row can say it is working meanwhile. */
    onRefreshRates: (onDone: () -> Unit) -> Unit,
    onSaveKey: (String) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onWipe: () -> Unit,
    /** Voice notes not booked yet, with what can be done about them. */
    voiceBacklog: Int = 0,
    onRetryVoice: () -> Unit = {},
    onDiscardVoice: () -> Unit = {},
    /** Opens the theme page. */
    onTheme: () -> Unit = {},
) {
    var edit by remember { mutableStateOf<Edit?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var updatingRates by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(onExport) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    // The weekly backup's folder: kept across restarts, written to by the background job.
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        onChange { it.copy(autoBackupFolder = uri.toString()) }
        AutoBackup.apply(context, uri.toString())
    }
    val s = data.settings
    BackHandler(onBack = onBack)

    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { CollapsingHeader(tr("Настройки", "Settings"), onBack, bar) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Like Accounts: a small label over each group, the group one card of rows.
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Gap.m).padding(bottom = Gap.xl)) {
            SettingsGroup(
                tr("Где я", "Where I am"),
                SettingItem(tr("Местная валюта", "Local currency"), currencyLabel(s.localCurrency) + tr(", для новых трат и голоса", ", for new purchases and voice")) { edit = Edit.Local },
                SettingItem(tr("Основная валюта", "Main currency"), currencyLabel(s.baseCurrency) + tr(", крупные цифры и итоги", ", big numbers and totals")) { edit = Edit.Base },
                SettingItem(tr("Валюты", "Currencies"), s.displayCurrencies.joinToString(" ") { Currencies.symbol(it) }) { edit = Edit.Shown },
            )

            val ratesDay = data.ratesDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val foreign = s.displayCurrencies.filter { it != "RUB" }.filter { data.rates.official(it) != null && data.rates.display(it) != null }
            SettingsGroup(
                tr("Курс", "Rates"),
                SettingItem(tr("Наценка к ЦБ", "Markup over the CBR"), Fmt.number(s.markup * 100, 1) + " %") { edit = Edit.Markup },
                *foreign.map { code ->
                    SettingItem("1 ${Currencies.symbol(code)} = ${Fmt.number(data.rates.display(code)!!, 2)} ₽", tr("ЦБ ", "CBR ") + Fmt.number(data.rates.official(code)!!, 2))
                }.toTypedArray(),
                // Rarely needed, so a plain row at the end rather than a button in the header.
                SettingItem(
                    tr("Обновить курс", "Update rates"),
                    if (updatingRates) tr("Обновляю", "Updating") else data.ratesDate?.let { tr("ЦБ на ", "CBR of ") + (ratesDay?.let(::fullDate) ?: it) },
                ) {
                    if (!updatingRates) {
                        updatingRates = true
                        onRefreshRates { updatingRates = false }
                    }
                },
            )

            val defaults = Settings()
            fun modelLine(model: String, default: String) = model + if (model == default) tr(", по умолчанию", ", default") else ""
            SettingsGroup(
                "Gemini",
                SettingItem(tr("Ключ Gemini", "Gemini key"), if (s.hasGeminiKey) tr("Сохранён", "Saved") else tr("Нет", "None")) { edit = Edit.Key },
                SettingItem(tr("Модель для голоса", "Voice model"), modelLine(s.geminiModel, defaults.geminiModel)) { edit = Edit.Model },
                SettingItem(tr("Модель для разборов", "Review model"), modelLine(s.reviewModel, defaults.reviewModel)) { edit = Edit.ReviewModel },
                // Notes recorded but not booked: waiting for the network or the key, or set aside after a failure.
                *listOfNotNull(
                    if (voiceBacklog > 0) SettingItem(tr("Не разобрано: $voiceBacklog", "Not processed: $voiceBacklog"), tr("Голосовые ждут разбора", "Voice notes waiting to be processed")) { edit = Edit.Notes } else null,
                ).toTypedArray(),
            )

            SettingsGroup(
                tr("Данные", "Data"),
                SettingItem(tr("Сохранить копию", "Save a backup"), tr("Всё, кроме ключа, в один файл", "Everything but the key, in one file")) {
                    exporter.launch("golda-${LocalDate.now()}.json")
                },
                SettingItem(tr("Восстановить", "Restore"), tr("Заменить всё содержимым файла", "Replace everything with a file")) {
                    importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                },
                // On: a folder is picked once, then a copy goes there every week by itself.
                SettingItem(
                    tr("Копия каждую неделю", "A copy every week"),
                    when {
                        s.autoBackupFolder == null -> tr("В папку, которую выберешь, хранятся последние ${AutoBackup.KEEP}", "Into a folder you pick, the last ${AutoBackup.KEEP} kept")
                        s.lastAutoBackup == 0L -> tr("Первая сейчас появится в папке", "The first one is on its way")
                        else -> tr("Последняя ", "Last one ") + fullDate(Instant.ofEpochMilli(s.lastAutoBackup).atZone(ZoneId.systemDefault()).toLocalDate())
                    },
                    checked = s.autoBackupFolder != null,
                ) {
                    if (s.autoBackupFolder == null) {
                        folderPicker.launch(null)
                    } else {
                        onChange { it.copy(autoBackupFolder = null) }
                        AutoBackup.apply(context, null)
                    }
                },
            )

            val reminder: (Boolean) -> Unit = { on ->
                onChange { it.copy(reconcileReminder = on) }
                ReconcileSchedule.apply(context, on)
            }
            SettingsGroup(
                tr("Напоминания", "Reminders"),
                // The whole row is the switch, for TalkBack too.
                SettingItem(tr("Напоминать о сверке", "Reconcile reminder"), tr("По воскресеньям в 19:00", "Sundays at 19:00"), checked = s.reconcileReminder) {
                    reminder(!s.reconcileReminder)
                },
            )

            SettingsGroup(tr("Оформление", "Appearance"), SettingItem(tr("Тема", "Theme"), themeLabel(s.palette), onClick = onTheme))
            SettingsGroup(tr("Язык", "Language"), SettingItem(tr("Язык приложения", "App language"), languageName(currentLanguage())) { edit = Edit.Language })

            // The one irreversible thing stands apart, last, in the error colour; the confirmation carries the alarm.
            VSpace(Gap.l)
            SettingsGroup(null, SettingItem(tr("Стереть всё", "Erase everything"), tr("Счета, операции и настройки", "Accounts, operations and settings"), danger = true) { confirmWipe = true })
        }
    }

    val currencyPick = when (edit) {
        Edit.Base -> CurrencyPick.BASE
        Edit.Local -> CurrencyPick.LOCAL
        Edit.Shown -> CurrencyPick.SHOWN
        else -> null
    }
    FormPageHost(currencyPick) { pick -> CurrencyPage(data, pick, onChange) { edit = null } }

    when (edit) {
        // The currencies are a list page of their own, over Settings.
        Edit.Base, Edit.Local, Edit.Shown -> Unit
        Edit.Markup -> NumberSheet(
            caption = tr("Наценка к курсу ЦБ", "Markup over the CBR rate"),
            initial = s.markup * 100,
            symbol = "%",
            under = tr("Сама подстроится после обмена", "Adjusts itself after an exchange"),
            onDismiss = { edit = null },
        ) { v -> onChange { it.copy(markup = v / 100) } }
        Edit.Key -> KeySheet(s.hasGeminiKey, onDismiss = { edit = null }) { key -> onSaveKey(key); edit = null }
        Edit.Model -> ModelSheet(
            tr("Модель для голоса: быстрая, слушает покупки", "Voice model: a fast one that hears purchases"),
            s.geminiModel, Settings().geminiModel, listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"),
            onDismiss = { edit = null },
        ) { model -> onChange { it.copy(geminiModel = model) }; edit = null }
        Edit.ReviewModel -> ModelSheet(
            tr("Модель для разборов: думает дольше, пишет глубже", "Review model: thinks longer, writes deeper"),
            s.reviewModel, Settings().reviewModel, listOf("gemini-3.8-flash", "gemini-4-argon", "gemini-3.5-flash-lite"),
            note = tr("gemini-4-argon работает только с платным ключом", "gemini-4-argon needs a paid key"),
            onDismiss = { edit = null },
        ) { model -> onChange { it.copy(reviewModel = model) }; edit = null }
        Edit.Language -> LanguageSheet(onDismiss = { edit = null })
        Edit.Notes -> FormSheet(
            onDismiss = { edit = null },
            actions = {
                RoundAction(R.drawable.ic_delete, tr("Удалить голосовые", "Delete the voice notes")) { onDiscardVoice(); edit = null }
                PrimaryAction(tr("Повторить", "Try again"), { onRetryVoice(); edit = null })
            },
        ) {
            val n = voiceBacklog.toLong()
            Caption(
                tr(
                    "$n ${plural(n, "голосовое", "голосовых", "голосовых", "voice note", "voice notes")} не разобрано: не было сети или Gemini не справился. Повторить сейчас или удалить, если уже записал вручную?",
                    "$n ${plural(n, "голосовое", "голосовых", "голосовых", "voice note", "voice notes")} not processed yet: there was no network, or Gemini failed. Try again now, or delete them if you already added them by hand?",
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
            text = { Text(tr("Счета, операции и настройки удалятся без возможности вернуть", "Accounts, operations and settings will be gone for good")) },
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

/** One setting: its title, what it is set to under it; [checked] makes the whole row a switch. */
private class SettingItem(
    val title: String,
    val summary: String? = null,
    val danger: Boolean = false,
    val checked: Boolean? = null,
    val onClick: (() -> Unit)? = null,
)

/** A group of settings as Accounts shows its rows: the label, then one card; no label for a row set apart. */
@Composable
private fun SettingsGroup(title: String?, vararg items: SettingItem) {
    if (title != null) GroupLabel(title, Modifier.padding(top = Gap.l))
    items.forEachIndexed { i, item ->
        val checked = item.checked
        GroupRow(
            i, items.size,
            if (checked != null) Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState(checked) } else Modifier,
            onClick = item.onClick,
            supporting = item.summary?.let { { Text(it) } },
            trailing = checked?.let { { CheckSwitch(it, null) } },
            trailingOnTitle = false,
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
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
    FormSheet(onDismiss = onDismiss, actions = { PrimaryAction(tr("Сохранить", "Save"), { value?.let { onSave(it); onDismiss() } }, value != null) }) {
        FocusWhenShown(focus)
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
                            modifier = Modifier.size(48.dp),
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
    FormSheet(
        onDismiss = onDismiss,
        actionGap = ButtonGroupDefaults.ConnectedSpaceBetween,
        actions = {
            if (hasKey) RoundAction(R.drawable.ic_delete, tr("Удалить ключ", "Remove the key")) { onSave("") }
            PrimaryAction(tr("Сохранить", "Save"), { onSave(key.trim()) }, key.isNotBlank())
        },
    ) {
        FocusWhenShown(focus)
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
private fun ModelSheet(
    caption: String,
    current: String,
    default: String,
    /** The usual choices, one tap each; any other id can be typed. */
    suggestions: List<String>,
    onDismiss: () -> Unit,
    note: String? = null,
    onSave: (String) -> Unit,
) {
    var model by remember { mutableStateOf(current) }
    FormSheet(onDismiss = onDismiss, actions = {
        if (model.trim() != default) TonalAction(tr("По умолчанию", "Default"), { model = default })
        PrimaryAction(tr("Сохранить", "Save"), { onSave(model.trim()) }, model.isNotBlank())
    }) {
        Caption(caption)
        VSpace(Gap.m)
        ToggleFlow(suggestions, { it == model.trim() }, { model = it }, { it })
        VSpace(Gap.m)
        TonalField(model, { model = it }, Modifier.fillMaxWidth(), placeholder = default, onDone = { if (model.isNotBlank()) onSave(model.trim()) })
        note?.let { VSpace(Gap.s); Caption(it) }
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
        }, Modifier.fillMaxWidth()) { ChoiceText(if (it.isEmpty()) tr("Как в системе", "System") else languageName(it)) }
    }
}

/** A monthly payment: what, how much, which day. ⋮ deletes it (with undo). */

/** The activity behind a Compose context, through any wrappers. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
