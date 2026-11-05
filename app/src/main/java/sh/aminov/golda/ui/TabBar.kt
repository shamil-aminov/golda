package sh.aminov.golda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.domain.tr
import kotlin.math.abs

/** How tall the row of the tabs stands, the 48 of a tap. */
internal val TabBarHeight = 48.dp

/**
 * The tabs as words over every screen, on the page's own colour so rows scroll under it unseen: a tap
 * or a swipe moves between them, and the word in bold follows the finger. The person at the end opens
 * what is yours ([MineScreen]).
 */
@Composable
fun TabBar(words: List<String>, position: () -> Float, onSelect: (Int) -> Unit, onMine: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(scheme.background).statusBarsPadding().height(TabBarHeight).padding(horizontal = Gap.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Gap.m)) {
            words.forEachIndexed { index, word ->
                val nearness = (1f - abs(position() - index)).coerceIn(0f, 1f)
                val style = TextRole.rowName
                Box(
                    Modifier.height(TabBarHeight).clickable { onSelect(index) }.semantics {
                        role = Role.Tab
                        selected = nearness > 0.5f
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // The bold word holds the width, so its neighbours stay put as the weight turns.
                    Text(word, style = style.copy(fontWeight = TextRole.Accent), color = Color.Transparent, maxLines = 1)
                    Text(
                        word,
                        style = if (nearness > 0.5f) style.copy(fontWeight = TextRole.Accent) else style,
                        color = lerp(scheme.onSurfaceVariant, scheme.onSurface, nearness),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Its glyph's right edge on the cards' text.
        IconButton(onClick = onMine, modifier = Modifier.offset(x = 12.dp).testTag(Tags.MINE)) {
            Icon(painterResource(R.drawable.ic_person), tr("Моё", "Mine"), tint = scheme.onSurfaceVariant)
        }
    }
}

/** What is yours rather than the money's: the settings, and later the family. Only what exists is shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    BackHandler(onBack = onBack)
    val fold = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(fold.nestedScrollConnection),
        topBar = { CollapsingHeader(tr("Моё", "Mine"), onBack, fold) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Gap.l)) {
            item {
                GroupRow(0, 1, Modifier.padding(horizontal = Gap.m), onClick = onSettings) { Text(tr("Настройки", "Settings"), style = TextRole.rowName) }
            }
        }
    }
}
