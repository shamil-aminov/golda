package sh.aminov.golda.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.aminov.golda.R
import sh.aminov.golda.data.Account
import sh.aminov.golda.domain.tr
import java.time.LocalDate

/** Which of the three date buttons. */
enum class DayPick { TODAY, YESTERDAY, OTHER }

/**
 * The one way to pick a day everywhere: Сегодня, Вчера, and a third that is the calendar and «Дата»
 * before a day is picked and the day itself after. [onPick] gets null when the calendar should open.
 */
@Composable
fun DateChoices(date: LocalDate, today: LocalDate, onPick: (LocalDate?) -> Unit) {
    val yesterday = today.minusDays(1)
    val picked = when (date) {
        today -> DayPick.TODAY
        yesterday -> DayPick.YESTERDAY
        else -> DayPick.OTHER
    }
    ChoiceGroup(DayPick.entries, picked, { pick ->
        when (pick) {
            DayPick.TODAY -> onPick(today)
            DayPick.YESTERDAY -> onPick(yesterday)
            DayPick.OTHER -> onPick(null)
        }
    }, Modifier.fillMaxWidth()) { pick ->
        when (pick) {
            DayPick.TODAY -> ChoiceText(tr("Сегодня", "Today"))
            DayPick.YESTERDAY -> ChoiceText(tr("Вчера", "Yesterday"))
            DayPick.OTHER -> {
                Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp))
                Spacer(Modifier.width(Gap.xs))
                // One short word, so the third choice never truncates on a narrow row; the picked day replaces it.
                ChoiceText(if (picked == DayPick.OTHER) dayLabel(date, today) else tr("Дата", "Date"))
            }
        }
    }
}

/**
 * The one way to pick an account everywhere: cards in a row that scrolls sideways, edge to edge,
 * each shaped like a bank card with the name at the top and the balance (or the currency) at the bottom. The picked one is filled.
 */
@Composable
fun AccountCards(accounts: List<Account>, picked: Long?, onPick: (Long) -> Unit, subtitle: (Account) -> String) {
    val cardWidth = 120.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.4f)
    // The picked card is always in sight: when it would sit past the right edge, the row moves just
    // enough to show it whole; when it is already in view, the row stays at its start.
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val card = with(density) { cardWidth.roundToPx() }
    val gap = with(density) { Gap.s.roundToPx() }
    val inset = with(density) { Gap.m.roundToPx() }
    LaunchedEffect(Unit) {
        val index = accounts.indexOfFirst { it.id == picked }
        if (index < 0) return@LaunchedEffect
        val viewport = snapshotFlow { scroll.viewportSize }.first { it > 0 }
        val end = inset + index * (card + gap) + card + inset
        // Moved by whole cards, so a whole card stands at the left edge, never half of one.
        if (end > viewport) scroll.scrollTo(((end - viewport + card + gap - 1) / (card + gap)) * (card + gap))
    }
    Row(
        Modifier.bleed(Gap.m).horizontalScroll(scroll).padding(horizontal = Gap.m).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Gap.s),
    ) {
        accounts.forEach { account ->
            val on = account.id == picked
            val spec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
            val container by animateColorAsState(if (on) pickedColor() else unpickedColor(), spec, label = "card")
            val ink by animateColorAsState(if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface, spec, label = "card-ink")
            val corner by animateDpAsState(if (on) 24.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "card-corner")
            Surface(
                onClick = { onPick(account.id) },
                // A bank card's 1.586 : 1, grown with the font up to a point so the words keep their room.
                modifier = Modifier.width(cardWidth).aspectRatio(1.586f).semantics { selected = on; role = Role.RadioButton },
                shape = RoundedCornerShape(corner),
                color = container,
                contentColor = ink,
            ) {
                // The name at the top, the balance at the bottom, as on a card.
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    // A long name of several words goes on two lines, split at a space, never inside a word,
                    // so "Мультивалютная USD" and "… GEL" are told apart; one word shrinks to fit instead.
                    val lines = account.name.trim().let { n ->
                        val cut = n.lastIndexOf(' ')
                        if (n.length > 12 && cut > 0) listOf(n.substring(0, cut), n.substring(cut + 1)) else listOf(n)
                    }
                    Column {
                        lines.forEach { line ->
                            Text(
                                line, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                            )
                        }
                    }
                    Text(subtitle(account), style = MaterialTheme.typography.labelLarge.merge(Tnum), color = ink.copy(alpha = 0.8f), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp))
                }
            }
        }
    }
}

/**
 * The one choice tile: an icon over a short name, for a category or an account type. Filled when
 * picked; [outlined] when the app picked it and you have not (the auto category).
 */
@Composable
fun ChoiceTile(icon: Int, label: String, on: Boolean, modifier: Modifier = Modifier, outlined: Boolean = false, onClick: () -> Unit) {
    val filled = on && !outlined
    val corner by animateDpAsState(if (on) 24.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    val container by animateColorAsState(if (filled) pickedColor() else unpickedColor(), MaterialTheme.motionScheme.fastEffectsSpec(), label = "tile")
    val ink = if (filled) onPickedColor() else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp).semantics { selected = on; role = Role.RadioButton },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = ink,
        border = if (on && outlined) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant) else null,
    ) {
        Column(Modifier.padding(horizontal = 2.dp, vertical = Gap.s), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = if (filled) ink else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(Gap.xs))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp))
        }
    }
}
