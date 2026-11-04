package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GroupHeader
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.Tnum
import sh.aminov.golda.ui.VSpace
import sh.aminov.golda.ui.accountSubline
import sh.aminov.golda.ui.regularPayments
import sh.aminov.golda.ui.typeLabel
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/accounts-screen.md`): the Accounts tab's header and list, three ways
 * against the real screen, in Marathon (light and dark) and Golda, drawn into `docs/tasks/accounts-screen/`.
 * Delete, with `accountSubline` back to private, once one is built in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class AccountsScreenPreviewTest(private val variant: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = listOf("a", "b", "c", "d").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val data by lazy { ScreenshotTest().demo() }

    private fun shot(theme: String, palette: String, mode: String) {
        compose.setContent {
            GoldaTheme(palette, mode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (variant == "a") AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> }) else Mock(data, variant)
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/accounts-screen/${variant}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun marathonLight() = shot("marathon_light", "marathon", "LIGHT")
    @Test fun marathonDark() = shot("marathon_dark", "marathon", "DARK")
    @Test fun goldaLight() = shot("golda_light", "golda", "LIGHT")
}

/** б, в, г: the header and the list as [v] says; the regular payments as they are. */
@Composable
private fun Mock(data: AppData, v: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = Gap.xl)) {
        item { Header(data, v) }
        item {
            GroupHeader("Счета", "Добавить счёт", {}, Modifier.padding(horizontal = Gap.m).padding(top = Gap.xl), alone = data.accounts.isEmpty())
        }
        data.accounts.forEachIndexed { i, account -> item { Row(data, account.id, i + 1, data.accounts.size + 1, v) } }
        regularPayments(data) {}
    }
}

/**
 * б: one ink, two sizes (14 and the figure), the figures semibold. в: the same sizes, the words at 70 %
 * of the same ink, the figures full. г: three sizes: 14 for the words, the other currencies at 22, the figure.
 * In all three: the sign in the figure's own ink, the interest on one line.
 */
@Composable
private fun Header(data: AppData, v: String) {
    val total = data.states.values.sumOf { it.rubMinor }
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val words = if (v == "c") ink.copy(alpha = 0.7f) else ink
    val text = MaterialTheme.typography.bodyMedium.merge(Tnum)
    HeroTile(Modifier.padding(horizontal = Gap.s)) {
        Text("Всего на счетах", style = text, color = words, maxLines = 1)
        VSpace(Gap.s)
        HeroAmount(data.base.whole(total), ink, signColor = if (v == "c") words else ink)
        VSpace(Gap.s)
        val others = data.others(total, data.base.code)
        if (v == "d") {
            Text(others, style = MaterialTheme.typography.titleLarge.merge(Tnum).copy(fontWeight = FontWeight.SemiBold), color = ink)
        } else {
            Text(others, style = text.copy(fontWeight = FontWeight.SemiBold), color = ink)
        }
        Debts.advice(data.accounts, data.states)?.let { advice ->
            VSpace(Gap.m)
            val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = words)) { append(head) }
                    tail?.let { withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(" $it") } }
                },
                style = text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * б: rows of one height (a one-line row keeps the second line's room, empty); в: a second line always; г: as в, the
 * amount one step up, at 22. In all three the name is bodyLarge and the amount semibold, the money first.
 */
@Composable
private fun Row(data: AppData, id: Long, i: Int, count: Int, v: String) {
    val state = data.states.getValue(id)
    val account = state.account
    val line = accountSubline(data, state).ifEmpty {
        if (v == "b") "" else account.interestRate?.takeIf { account.type == AccountType.CREDIT || account.type == AccountType.LOAN }
            ?.let { typeLabel(account.type).takeUnless { t -> account.name.contains(t, ignoreCase = true) }?.plus(" · ").orEmpty() + Fmt.percent(it / 100) }
            ?: typeLabel(account.type)
    }
    val amount = Fmt.amount(state.balanceMinor, state.currency)
    GroupRow(
        i, count, Modifier.padding(horizontal = Gap.m),
        onClick = {},
        // б keeps a second line's room in every row, empty where there is nothing to say.
        supporting = { Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            val style = if (v == "d") MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            Text(amount, style = style.merge(Tnum), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        },
    ) { Text(account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
