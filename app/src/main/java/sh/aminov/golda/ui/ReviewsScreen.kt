package sh.aminov.golda.ui

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import kotlinx.coroutines.Job
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.domain.I18n
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.speech.RecognizerIntent
import android.content.Intent
import android.content.ActivityNotFoundException
import sh.aminov.golda.data.ReviewMessage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.imePadding
import sh.aminov.golda.data.Review
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Gemini's reviews the way a notes app keeps notes: the bold page title, each review a card with its
 * period, when it was written and its first lines; at the bottom back and the wide button that writes
 * a review of the period picked in insights ("Разобрать октябрь").
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewsScreen(
    reviews: List<Review>,
    period: Period,
    today: LocalDate,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onReview: (onDone: (String?) -> Unit) -> Unit,
    /** Without a Gemini key the button leads to Settings instead of failing. */
    hasKey: Boolean = true,
    onAddKey: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The page has a main action, so back stands beside it at the bottom, as on an account's page.
    Scaffold(
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(Gap.m), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onBack)
                Button(
                    onClick = {
                        if (!hasKey) {
                            onAddKey()
                        } else {
                            working = true
                            error = null
                            onReview { problem -> working = false; error = problem }
                        }
                    },
                    // Off while Gemini writes: one review at a time.
                    enabled = !working,
                    modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                    // Without a key the one action is adding it: the action colour; otherwise Разбор's ice.
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasKey) iceColor() else actionColor(),
                        contentColor = if (hasKey) onIceColor() else onActionColor(),
                    ),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    Text(
                        when {
                            !hasKey -> tr("Добавить ключ Gemini", "Add a Gemini key")
                            working -> tr("Gemini пишет разбор", "Gemini is writing")
                            else -> tr("Разобрать ", "Review ") + ReviewPrompt.title(period, today)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gap.s, end = Gap.s, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Gap.l),
            verticalArrangement = Arrangement.spacedBy(Gap.s),
        ) {
            // The page title the account page has, at the same height: the Scaffold already keeps it under the status bar.
            item(key = "title") {
                Text(
                    tr("Разборы", "Reviews"),
                    Modifier.padding(start = Gap.m, end = Gap.m, top = Gap.l, bottom = Gap.s),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            error?.let { problem ->
                item(key = "error") { Text(problem, Modifier.padding(horizontal = Gap.m), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            }
            if (reviews.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (hasKey) tr("Gemini посмотрит траты и коротко напишет, куда ушли деньги и что можно урезать",
                            "Gemini reads your spending and writes briefly where the money went and what could be cut")
                        else tr("Нужен ключ Gemini, чтобы он разобрал траты", "A Gemini key is needed for it to review your spending"),
                        Modifier.padding(Gap.m),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(reviews, key = { it.id }) { review ->
                val span = Period(LocalDate.ofEpochDay(review.fromDay), LocalDate.ofEpochDay(review.toDay))
                // A note card: the period as its title, when it was written, and its first lines.
                Tile(Modifier.fillMaxWidth(), onClick = { onOpen(review.id) }) {
                    Text(ReviewPrompt.title(span, LocalDate.MAX).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(tr("Написан ", "Written ") + written(review), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VSpace(Gap.s)
                    Text(preview(review.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

/** The review's text without its Markdown marks and its heading: what a note card shows of it. */
private fun preview(text: String): String = text.lines()
    .map { it.trim().trimStart('#', '-', '*', ' ').replace("**", "") }
    .filter { it.isNotBlank() }
    .drop(1)
    .joinToString(" ")

/**
 * One review, as a document: its Markdown with headings, points and bold figures, and under it a talk
 * about the same period: ask "сколько я сэкономил бы без такси" and Gemini answers from the data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    review: Review,
    messages: List<ReviewMessage>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    /** Asks; [onDone] gets null, or what went wrong in words. */
    /** Asks; returns the request, so it can be stopped. */
    onAsk: (question: String, onDone: (String?) -> Unit) -> Job,
) {
    BackHandler(onBack = onBack)
    val page = MaterialTheme.colorScheme.background
    var question by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    // A new question or answer scrolls into view.
    LaunchedEffect(messages.size, working) { if (messages.isNotEmpty() || working) scroll.animateScrollTo(scroll.maxValue) }
    // Speaking a question: the phone's own speech recognition writes it into the field, to read over
    // and send. No recognizer on the phone: the mic says so instead of failing.
    val context = LocalContext.current
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
            question = (question.trim() + " " + heard).trim().replaceFirstChar { it.uppercase() }
        }
    }
    fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, I18n.locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Спросите про эти траты", "Ask about this spending"))
        try {
            listen.launch(intent)
        } catch (e: ActivityNotFoundException) {
            error = tr("На телефоне нет распознавания речи", "This phone has no speech recognition")
        }
    }
    var asking by remember { mutableStateOf<Job?>(null) }
    // A tap anywhere on the capsule types into it, not only on the line of text.
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun send() {
        val q = question.trim()
        if (q.isEmpty() || working) return
        working = true
        error = null
        question = ""
        asking = onAsk(q) { problem -> working = false; error = problem; asking = null }
    }
    // Stopped by hand: the question stays, the answer never comes.
    fun stop() {
        asking?.cancel()
        asking = null
        working = false
    }
    // Like the list it opens from: a plain bar with back, and delete at its end; the question at the bottom.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), tr("Назад", "Back")) } },
                actions = { IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), tr("Удалить разбор", "Delete review")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page, scrolledContainerColor = page),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(page).imePadding().navigationBarsPadding().padding(Gap.m),
                horizontalArrangement = Arrangement.spacedBy(Gap.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One capsule, as in Gemini's own app: the field with the mic inside it; beside it the
                // send button once there is a question, or stop while the answer is being written.
                Surface(onClick = { field.requestFocus(); keyboard?.show() }, shape = RoundedCornerShape(28.dp), color = tileColor(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Row(Modifier.padding(start = Gap.m, end = Gap.xs), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).padding(vertical = Gap.s)) {
                            if (question.isEmpty()) Text(tr("Спросить про эти траты", "Ask about this spending"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicTextField(
                                question, { question = it },
                                Modifier.fillMaxWidth().focusRequester(field),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                            )
                        }
                        IconButton(onClick = ::speak) { Icon(painterResource(R.drawable.ic_mic), tr("Спросить голосом", "Ask by voice")) }
                    }
                }
                when {
                    working -> FilledTonalIconButton(
                        onClick = ::stop,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = unpickedColor(), contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Icon(painterResource(R.drawable.ic_stop), tr("Остановить", "Stop"), Modifier.size(20.dp)) }
                    question.isNotBlank() -> FilledIconButton(
                        onClick = ::send,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = actionColor(), contentColor = onActionColor()),
                    ) { Icon(painterResource(R.drawable.ic_send), tr("Спросить", "Ask")) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(scroll).padding(horizontal = Gap.l).padding(bottom = Gap.l)) {
            Markdown(review.text)
            VSpace(Gap.l)
            Text(tr("Gemini, ", "Gemini, ") + written(review), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            messages.forEach { message ->
                VSpace(Gap.l)
                if (message.fromUser) {
                    // The question on the right, in a card, as in any chat.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Surface(shape = RoundedCornerShape(20.dp), color = tileColor(), modifier = Modifier.padding(start = Gap.xl)) {
                            Text(message.text, Modifier.padding(horizontal = Gap.m, vertical = Gap.s + 4.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Markdown(message.text)
                }
            }
            if (working) {
                VSpace(Gap.l)
                Caption(tr("Gemini думает", "Gemini is thinking"))
            }
            error?.let {
                VSpace(Gap.m)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun written(review: Review): String =
    Instant.ofEpochMilli(review.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMMM", sh.aminov.golda.domain.I18n.locale))

/** A full page with no top bar: content from under the status bar, actions under it, as on the forms. */
@Composable
fun BottomPage(actions: @Composable RowScope.() -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = sheetColor()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Gap.m)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                VSpace(Gap.l)
                content()
                VSpace(Gap.l)
            }
            Row(Modifier.fillMaxWidth().padding(Gap.m), horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/**
 * The little Markdown a review uses: "#"–"###" headings, "-" or "*" points, "**bold**", and blank
 * lines between paragraphs. Anything else is shown as it is.
 */
@Composable
fun Markdown(text: String) {
    val body = MaterialTheme.typography.bodyLarge
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> VSpace(Gap.s)
                line.startsWith("### ") -> Text(inline(line.removePrefix("### ")), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = Gap.s))
                line.startsWith("## ") || line.startsWith("# ") -> Text(inline(line.substringAfter(' ')), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row {
                    Text("•", Modifier.width(Gap.m), style = body)
                    Text(inline(line.trimStart().drop(2)), style = body)
                }
                else -> Text(inline(line), style = body)
            }
        }
    }
}

/** "**bold**" spans inside a line. */
private fun inline(line: String): AnnotatedString = buildAnnotatedString {
    line.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}
