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
 * The one way to pick a day everywhere: Сегодня, Вчера, and a third that is the calendar before a
 * day is picked and the day itself after. [onPick] gets null when the calendar should open.
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
                ChoiceText(if (picked == DayPick.OTHER) dayLabel(date, today) else tr("Другой день", "Another day"))
            }
        }
    }
}

/**
 * The one way to pick an account everywhere: cards in a row that scrolls sideways, edge to edge,
 * each with the name and the balance (or the currency). The picked one is filled.
 */
@Composable
fun AccountCards(accounts: List<Account>, picked: Long?, onPick: (Long) -> Unit, subtitle: (Account) -> String) {
    Row(
        Modifier.bleed(Gap.m).horizontalScroll(rememberScrollState()).padding(horizontal = Gap.m).selectableGroup(),
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
                modifier = Modifier.width(128.dp).heightIn(min = 64.dp).semantics { selected = on; role = Role.RadioButton },
                shape = RoundedCornerShape(corner),
                color = container,
                contentColor = ink,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp))
                    Text(subtitle(account), style = MaterialTheme.typography.labelLarge.merge(Tnum), color = ink.copy(alpha = 0.8f), maxLines = 1)
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
