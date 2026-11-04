package sh.aminov.golda.ui

import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.domain.AmountExpr
import sh.aminov.golda.domain.tr

/**
 * The field the keypad types into: what it allows, and what a key does to it. A form has at most
 * one at a time, the focused one.
 */
class PadTarget(
    val ops: Boolean,
    val decimals: Int,
    val press: (Char) -> Unit,
    val back: () -> Unit,
    val clear: () -> Unit,
)

/** The keypad of one form: shown under its actions while a number field is focused. */
class AmountPadState {
    var target by mutableStateOf<PadTarget?>(null)
}

/** The form's keypad, for its number fields; null outside a form, where a field keeps the system keyboard. */
val LocalAmountPad = staticCompositionLocalOf<AmountPadState?> { null }

/** A number field on the keypad: the expression being typed for show, and whether the field is focused. */
class PadInput(val modifier: Modifier, val expression: String?, val focused: Boolean)

/**
 * Wires a number field to the form's keypad: focused, it takes the keypad (and puts the system
 * keyboard away); keys change an expression it keeps, and [onText] is handed the plain number it
 * comes to. Text changed from outside (a prefill, a currency picked) drops the expression. Null
 * outside a form.
 */
@Composable
fun rememberPadInput(text: String, onText: (String) -> Unit, ops: Boolean, decimals: Int = 2, signed: Boolean = false): PadInput? {
    val pad = LocalAmountPad.current ?: return null
    var expression by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val currentText by rememberUpdatedState(text)
    val currentOnText by rememberUpdatedState(onText)
    LaunchedEffect(text) { expression?.let { if (AmountExpr.result(it, decimals) != text) expression = null } }
    val keyboard = LocalSoftwareKeyboardController.current
    fun set(next: String) {
        expression = next.takeIf { AmountExpr.hasOps(it) }
        currentOnText(AmountExpr.result(next, decimals))
    }
    // What the keys work on: the expression, or the number shown (its "-" as the keypad's minus).
    fun base() = expression ?: currentText.replace('-', AmountExpr.MINUS)
    val target = remember(ops, decimals, signed) {
        PadTarget(
            ops = ops,
            decimals = decimals,
            press = { key -> set(AmountExpr.press(base(), key, decimals, ops, signed)) },
            back = { set(AmountExpr.back(base())) },
            clear = { set("") },
        )
    }
    val modifier = Modifier.onFocusChanged { f ->
        focused = f.isFocused
        if (f.isFocused) {
            keyboard?.hide()
            pad.target = target
        } else if (pad.target === target) {
            pad.target = null
        }
    }
    return PadInput(modifier, expression?.let(AmountExpr::shown), focused)
}

/**
 * The keypad itself, where the system keyboard would be: digits and "," big and round, the four
 * operations in a column of their own where a sum can be worked out, ⌫ (held: clear all).
 * Hidden while the system keyboard is up, for the note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountPadHost(state: AmountPadState) {
    val target = state.target
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = target != null) { focusManager.clearFocus() }
    AnimatedVisibility(target != null && !WindowInsets.isImeVisible, enter = expandVertically(), exit = shrinkVertically()) {
        // The last target stays drawn while the keypad folds away.
        val shown = remember { mutableStateOf<PadTarget?>(null) }
        if (target != null) shown.value = target
        shown.value?.let { AmountPad(it) }
    }
}

@Composable
private fun AmountPad(target: PadTarget) {
    val haptic = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    val digits = unpickedColor()
    val onDigits = MaterialTheme.colorScheme.onSurface
    val opsColor = MaterialTheme.colorScheme.secondaryContainer
    val onOps = MaterialTheme.colorScheme.onSecondaryContainer
    val rows = listOf("789", "456", "123")
    val ops = listOf(AmountExpr.DIVIDE, AmountExpr.TIMES, AmountExpr.MINUS, AmountExpr.PLUS)
    Column(
        Modifier.fillMaxWidth().padding(start = Gap.m, end = Gap.m, bottom = Gap.m),
        verticalArrangement = Arrangement.spacedBy(Gap.s),
    ) {
        rows.forEachIndexed { i, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                row.forEach { c -> Key(c.toString(), digits, onDigits) { tap { target.press(c) } } }
                if (target.ops) OpKey(ops[i], opsColor, onOps) { tap { target.press(ops[i]) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            if (target.decimals > 0) Key(",", digits, onDigits) { tap { target.press(',') } } else Box(Modifier.weight(1f))
            Key("0", digits, onDigits) { tap { target.press('0') } }
            BackKey(opsColor, onOps, onClick = { tap(target.back) }, onLongClick = { tap(target.clear) })
            if (target.ops) OpKey(ops[3], opsColor, onOps) { tap { target.press(ops[3]) } }
        }
    }
}

private val KeyHeight = 56.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Key(label: String, container: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(KeyHeight).clip(CircleShape).background(container).combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge.merge(Tnum), color = content)
    }
}

@Composable
private fun RowScope.OpKey(op: Char, container: Color, content: Color, onClick: () -> Unit) {
    val name = when (op) {
        AmountExpr.PLUS -> tr("плюс", "plus")
        AmountExpr.MINUS -> tr("минус", "minus")
        AmountExpr.TIMES -> tr("умножить", "times")
        else -> tr("разделить", "divided by")
    }
    Box(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = name }) {
        Row { Key(op.toString(), container, content, onClick) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.BackKey(container: Color, content: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(KeyHeight).clip(CircleShape).background(container)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_backspace), tr("Стереть", "Delete"), Modifier.size(24.dp), tint = content)
    }
}
