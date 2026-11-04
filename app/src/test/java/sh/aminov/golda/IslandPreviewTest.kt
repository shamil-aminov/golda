package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.aminov.golda.ui.CardCorner
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.actionColor
import sh.aminov.golda.ui.isDark
import sh.aminov.golda.ui.onActionColor
import sh.aminov.golda.ui.onPickedColor
import sh.aminov.golda.ui.pickedColor
import sh.aminov.golda.ui.softShadow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GoldaToolbar
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.ToolbarTab
import sh.aminov.golda.ui.VoiceControl
import sh.aminov.golda.ui.VoiceState
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/island.md`): the floating toolbar over Home and Accounts as the app shows them,
 * at the top and scrolled to the end, in Golda light and Marathon dark at «Просторно», into `docs/tasks/island/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class IslandPreviewTest(private val name: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("now", "o1", "o2", "o3").flatMap { v ->
            listOf("golda_light", "marathon_dark").flatMap { t -> listOf("home_top", "home_end", "accounts_top").map { arrayOf<Any>("${v}_${it}_$t") } }
        }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun shot() {
        val data = ScreenshotTest().demo()
        val (palette, mode) = if ("marathon" in name) "marathon" to "DARK" else "golda" to "LIGHT"
        val home = "_home_" in name
        val end = "_end_" in name
        compose.setContent {
            GoldaTheme(palette, mode, "ROOMY") {
                Scaffold(
                    floatingActionButton = { if (name.startsWith("now")) Island(if (home) 0 else 1) else Mock(if (home) 0 else 1, specs.getValue(name.substringBefore('_'))) },
                    floatingActionButtonPosition = FabPosition.Center,
                ) { padding ->
                    val list = rememberLazyListState()
                    if (end) LaunchedEffect(Unit) { list.scrollToItem(1000) }
                    if (home) HomeScreen(data, padding, list, {}, {}, onEdit = {})
                    else AccountsScreen(data, padding, {}, {}, { _, _, _ -> })
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/tasks/island/$name.png")
    }
}

@Composable
private fun Island(selected: Int) = GoldaToolbar(
    tabs = listOf(
        ToolbarTab(R.drawable.ic_home, R.drawable.ic_home_fill, "Главная"),
        ToolbarTab(R.drawable.ic_wallet, R.drawable.ic_wallet_fill, "Счета"),
        ToolbarTab(R.drawable.ic_chart, R.drawable.ic_chart_fill, "Аналитика"),
    ),
    selected = selected, position = { selected.toFloat() }, onSelect = {},
    voice = VoiceControl(VoiceState.Idle, {}), onAdd = {},
)

/**
 * The island's proportions. o1: as now, its edges on the cards' (8 from the screen). o2: as o1, the icons the
 * app's 24 and "+" the headers' tonal 32 circle. o3: as o2, 56 tall (a 48 target and 4 round it), the mic 56.
 */
private class Spec(val side: Dp, val height: Dp, val icon: Dp, val tonalPlus: Boolean)

private val specs = mapOf(
    "o1" to Spec(8.dp, 64.dp, 26.dp, false),
    "o2" to Spec(8.dp, 64.dp, 24.dp, true),
    "o3" to Spec(8.dp, 56.dp, 24.dp, true),
)

/** The toolbar rebuilt with [s]'s proportions, its logic as GoldaToolbar's: tabs on a pill, a hairline, "+", the mic. */
@Composable
private fun Mock(selected: Int, s: Spec) {
    val scheme = MaterialTheme.colorScheme
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val inner = (s.height - 48.dp) / 2
    Row(Modifier.width(screen - s.side * 2).padding(bottom = 0.dp), horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            Modifier.weight(1f).height(s.height).softShadow(CircleShape), shape = CircleShape,
            color = if (isDark()) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh, contentColor = scheme.onSurfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = inner.coerceAtLeast(4.dp) + 4.dp, vertical = inner), verticalAlignment = Alignment.CenterVertically) {
                val icons = listOf(R.drawable.ic_home to R.drawable.ic_home_fill, R.drawable.ic_wallet to R.drawable.ic_wallet_fill, R.drawable.ic_chart to R.drawable.ic_chart_fill)
                icons.forEachIndexed { i, (icon, fill) ->
                    val on = i == selected
                    Box(Modifier.weight(1f).height(48.dp).background(if (on) pickedColor() else androidx.compose.ui.graphics.Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (on) fill else icon), null, Modifier.size(s.icon), tint = if (on) onPickedColor() else scheme.onSurfaceVariant)
                    }
                }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = Gap.xs), color = scheme.outlineVariant)
                Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                    if (s.tonalPlus) FilledTonalIconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(20.dp)) }
                    else Icon(painterResource(R.drawable.ic_add), null, Modifier.size(s.icon))
                }
            }
        }
        Surface(Modifier.size(s.height), shape = RoundedCornerShape(CardCorner), color = actionColor(), contentColor = onActionColor()) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(32.dp)) }
        }
    }
}
