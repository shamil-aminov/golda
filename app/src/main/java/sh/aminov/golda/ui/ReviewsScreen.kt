package sh.aminov.golda.ui

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
import sh.aminov.golda.data.Review
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Gemini's reviews the way a notes app keeps notes: a plain bar with back and the title, each review
 * a card with its period, when it was written and its first lines, and a floating button at the
 * bottom right that writes a review of the period picked in insights ("Разобрать октябрь").
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val page = MaterialTheme.colorScheme.background
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Разборы", "Reviews"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), tr("Назад", "Back")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page, scrolledContainerColor = page),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!hasKey) {
                        onAddKey()
                    } else if (!working) {
                        working = true
                        error = null
                        onReview { problem -> working = false; error = problem }
                    }
                },
                // Without a key the one action is adding it: the action colour; otherwise Разбор's ice.
                containerColor = if (hasKey) iceColor() else actionColor(),
                contentColor = if (hasKey) onIceColor() else onActionColor(),
                modifier = Modifier.softShadow(RoundedCornerShape(16.dp)),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                Text(
                    when {
                        !hasKey -> tr("Добавить ключ Gemini", "Add a Gemini key")
                        working -> tr("Gemini пишет разбор", "Gemini is writing")
                        else -> tr("Разобрать ", "Review ") + ReviewPrompt.title(period, today)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gap.s, end = Gap.s, top = padding.calculateTopPadding() + Gap.s, bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(Gap.s),
        ) {
            error?.let { problem ->
                item(key = "error") { Text(problem, Modifier.padding(horizontal = Gap.m), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            }
            if (reviews.isEmpty()) {
                item(key = "empty") {
                    Text(
                        tr("Разборов пока нет. Gemini посмотрит траты за период и коротко напишет, куда ушли деньги и что можно урезать.",
                            "No reviews yet. Gemini reads the period's spending and writes briefly where the money went and what could be cut."),
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

/** One review, as a document: its Markdown with headings, points and bold figures. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(review: Review, onBack: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onBack)
    val page = MaterialTheme.colorScheme.background
    // Like the list it opens from: a plain bar with back, and delete at its end.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), tr("Назад", "Back")) } },
                actions = { IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), tr("Удалить разбор", "Delete review")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page, scrolledContainerColor = page),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Gap.l).padding(bottom = Gap.l)) {
            Markdown(review.text)
            VSpace(Gap.l)
            Text(tr("Gemini, ", "Gemini, ") + written(review), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
