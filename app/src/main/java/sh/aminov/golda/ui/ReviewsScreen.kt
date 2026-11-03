package sh.aminov.golda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
 * Gemini's reviews: the ones written before, newest first, and at the bottom the button that writes
 * one for the period picked in insights ("Разобрать октябрь"). Back sits by it, as on the forms.
 */
@Composable
fun ReviewsScreen(
    reviews: List<Review>,
    period: Period,
    today: LocalDate,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onReview: (onDone: (String?) -> Unit) -> Unit,
) {
    BackHandler(onBack = onBack)
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BottomPage(
        actions = {
            RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onBack)
            Button(
                onClick = {
                    working = true
                    error = null
                    onReview { problem -> working = false; error = problem }
                },
                enabled = !working,
                colors = ButtonDefaults.buttonColors(containerColor = iceColor(), contentColor = onIceColor()),
                modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    if (working) tr("Gemini пишет разбор", "Gemini is writing") else tr("Разобрать ", "Review ") + ReviewPrompt.title(period, today),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Text(tr("Разборы", "Reviews"), style = MaterialTheme.typography.headlineMedium)
        VSpace(Gap.l)
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            VSpace(Gap.m)
        }
        if (reviews.isEmpty()) {
            Text(
                tr("Разборов пока нет. Gemini посмотрит траты за период и коротко напишет, куда ушли деньги и что можно урезать.",
                    "No reviews yet. Gemini reads the period's spending and writes briefly where the money went and what could be cut."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reviews.forEachIndexed { i, review ->
            val span = Period(LocalDate.ofEpochDay(review.fromDay), LocalDate.ofEpochDay(review.toDay))
            GroupRow(
                i, reviews.size,
                onClick = { onOpen(review.id) },
                supporting = { Text(tr("Написан ", "Written ") + written(review), maxLines = 1) },
            ) { Text(ReviewPrompt.title(span, LocalDate.MAX).replaceFirstChar { it.uppercase() }) }
        }
    }
}

/** One review, as a document: its Markdown with headings, points and bold figures. */
@Composable
fun ReviewScreen(review: Review, onBack: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onBack)
    BottomPage(
        actions = {
            RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onBack)
            RoundAction(R.drawable.ic_delete, tr("Удалить", "Delete"), onDelete)
            Spacer(Modifier.weight(1f))
        },
    ) {
        Markdown(review.text)
        VSpace(Gap.l)
        Text(tr("Gemini, ", "Gemini, ") + written(review), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun written(review: Review): String =
    Instant.ofEpochMilli(review.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMMM", sh.aminov.golda.domain.I18n.locale))

/** A full page with no top bar: content from under the status bar, actions at the bottom. */
@Composable
private fun BottomPage(actions: @Composable RowScope.() -> Unit, content: @Composable () -> Unit) {
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
