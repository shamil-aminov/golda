package sh.aminov.golda.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.IntOffset
import sh.aminov.golda.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.aminov.golda.domain.tr
import kotlin.math.cos
import kotlin.math.sin

/*
 * The design system in one place. Spacing runs 4 · 8 · 16 · 24 · 40; corners are 32 for the one
 * hero tile on a screen, 28 for tiles and grouped lists (4 between rows), 16 for fields and full
 * pills for buttons. Text on a container always takes that container's "on" role.
 */

object Gap {
    /** Label and value inside one block. */
    val xs = 4.dp

    /** Items in a group, bento gaps. */
    val s = 8.dp

    /** Screen edge, small tile padding. */
    val m = 16.dp

    /** Between groups, hero tile padding. */
    val l = 24.dp

    /** Before an action bar or the next section. */
    val xl = 40.dp
}

/** Rows of a grouped list sit this far apart. */
val GroupGap = 2.dp

@Composable
@ReadOnlyComposable
fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/*
 * Colour has three jobs and nothing else (the tones are in Colors.kt). Gold: the one action to take
 * (the mic, Записать, Сохранить, Купить). Graphite fill: what is picked. Red: bad news only. White
 * card on light grey page: everything else, the hero tiles included; a hero stands out by its size.
 */

/** Every tile, list and field. */
@Composable
@ReadOnlyComposable
fun tileColor(): Color = MaterialTheme.colorScheme.surfaceContainer

/** The donut's slices by rank: light greys, never black, so the ring stays quiet; the last is "the rest". */
@Composable
@ReadOnlyComposable
fun sliceColors(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(onSurfaceVariant, onSurfaceVariant.copy(alpha = 0.6f).compositeOver(surface), outline, outline.copy(alpha = 0.5f).compositeOver(surface))
}

/** Every sheet sits on the page tone, so the tiles in it read as cards. */
@Composable
@ReadOnlyComposable
fun sheetColor(): Color = MaterialTheme.colorScheme.surface

/** What is picked: a toggle, a category, an account type, a tab. A graphite fill. */
@Composable
@ReadOnlyComposable
fun pickedColor(): Color = MaterialTheme.colorScheme.secondary

/** Text and icons on [pickedColor]. */
@Composable
@ReadOnlyComposable
fun onPickedColor(): Color = MaterialTheme.colorScheme.onSecondary

/** What could be picked but is not: the soft grey, so it reads on the white cards and the grey sheet alike. */
@Composable
@ReadOnlyComposable
fun unpickedColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

/** A circle at the start of a row: the page tone cut into the card. */
@Composable
@ReadOnlyComposable
fun circleColor(): Color = MaterialTheme.colorScheme.surface

/** The one big tile of a screen: a card like the rest, set apart by its size and its number. */
@Composable
fun HeroTile(
    modifier: Modifier = Modifier,
    // The one card on a tab: the top block, set off the flat page under it.
    container: Color = tileColor(),
    content: Color = MaterialTheme.colorScheme.onPrimaryFixed,
    onClick: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val color by animateColorAsState(container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hero")
    val ink by animateColorAsState(content, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "heroInk")
    val shape = MaterialTheme.shapes.extraLargeIncreased
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, contentColor = ink) {
            Column(Modifier.padding(horizontal = Gap.m, vertical = Gap.l), content = body)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, contentColor = ink) {
            // Placed 8 from the screen's edge, 16 inside: the text 24 from the edge, like every row's.
            Column(Modifier.padding(horizontal = Gap.m, vertical = Gap.l), content = body)
        }
    }
}

/** A block of the page as one card: 16 dp inside. */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    container: Color = tileColor(),
    content: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    // Placed 8 from the screen's edge, 16 inside: the text 24 from the edge, like the rows'.
    padding: PaddingValues = PaddingValues(Gap.m),
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, contentColor = content) {
            Column(Modifier.padding(padding), content = body)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, contentColor = content) {
            Column(Modifier.padding(padding), content = body)
        }
    }
}

/** A tile's small caption: labelLarge in the quiet ink of whatever it sits on. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * The label over a group, the way Android's settings head a section: small and semibold, then a
 * hairline to the end; lined up with the rows' text.
 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, supporting: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gap.s)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.width(Gap.s))
            // The cards set the groups apart; no hairline needed here.
            Spacer(Modifier.weight(1f))
            trailing?.let {
                Spacer(Modifier.width(Gap.s))
                it()
            }
        }
        supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Corner 28 at the ends of a grouped list, 4 between its rows. */
fun groupShape(index: Int, count: Int): Shape {
    // One card per group: round at its ends, square where its rows meet.
    val outer = 28.dp
    val inner = 0.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/**
 * One row of a grouped list. [onClick] null makes it a plain row. The gap under it is part of the
 * row, so a LazyColumn can lay rows out one item each.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    // A group of rows is one card, not a card per row.
    container: Color = tileColor(),
    headline: @Composable () -> Unit,
) {
    val shapes = ListItemDefaults.shapes(shape = groupShape(index, count))
    val colors = ListItemDefaults.segmentedColors(containerColor = container)
    // Rows reach 8 dp into the page's margin: the card's edge 8 from the screen, the text 24.
    val spaced = modifier.bleed(Gap.s)
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = spaced,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            onLongClick = onLongClick,
            colors = colors,
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = spaced,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            colors = colors,
            content = headline,
        )
    }
}

/** A 40 dp neutral circle for an icon at the start of a row. */
@Composable
fun IconCircle(modifier: Modifier = Modifier, color: Color = circleColor(), content: @Composable () -> Unit) {
    Box(modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { content() }
}

/** An icon in its neutral circle: a category, a transfer. */
@Composable
fun GlyphCircle(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    IconCircle(modifier) { Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** The tonal text field: corner 16, surfaceContainerHighest, no underline. */
@Composable
fun TonalField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    focusRequester: FocusRequester? = null,
) {
    val clear = Color.Transparent
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 56.dp).let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        textStyle = textStyle,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onDone != null) ImeAction.Done else imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = clear,
            unfocusedIndicatorColor = clear,
            disabledIndicatorColor = clear,
            errorIndicatorColor = clear,
        ),
    )
}

/**
 * One of a few: a connected row of toggle buttons. [fill] stretches it across the width;
 * otherwise each option is as wide as its label (at least [minWidth]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    height: Dp = 48.dp,
    minWidth: Dp = 56.dp,
    enabled: Boolean = true,
    label: @Composable RowScope.(T) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            val shapes = when {
                options.size == 1 -> ToggleButtonDefaults.shapesFor(height)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }.copy(checkedShape = CircleShape)
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = (if (fill) Modifier.weight(1f) else Modifier.widthIn(min = minWidth))
                    // At least this tall; taller when a large font needs it.
                    .heightIn(min = height)
                    .semantics { role = Role.RadioButton },
                enabled = enabled,
                shapes = shapes,
                colors = choiceColors(),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { label(option) }
        }
    }
}

/** What is picked: the soft accent as a full pill; the rest a tone above the surface. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun choiceColors() = ToggleButtonDefaults.colors(
    containerColor = unpickedColor(),
    contentColor = MaterialTheme.colorScheme.onSurface,
    checkedContainerColor = pickedColor(),
    checkedContentColor = onPickedColor(),
)

/** A label for a toggle that never wraps or shrinks. */
@Composable
fun ChoiceText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Any number of the given, as loose toggle buttons in rows: currencies to show, account groups. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ToggleFlow(options: List<T>, isOn: (T) -> Boolean, onToggle: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        options.forEach { option ->
            ToggleButton(
                checked = isOn(option),
                onCheckedChange = { onToggle(option) },
                modifier = Modifier.height(48.dp),
                shapes = ToggleButtonDefaults.shapesFor(48.dp).copy(shape = CircleShape, checkedShape = CircleShape),
                colors = choiceColors(),
            ) { ChoiceText(label(option)) }
        }
    }
}

/** A quiet tonal pill with an icon and a value; it opens a menu or a picker. */
@Composable
fun MetaPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    /** Worked out by the app, not picked: outlined and quiet. Picked: filled. Null: the plain tile look. */
    auto: Boolean? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = when (auto) {
            true -> Color.Transparent
            false -> scheme.secondaryContainer
            null -> tileColor()
        },
        contentColor = when (auto) {
            true -> scheme.onSurfaceVariant
            false -> scheme.onSecondaryContainer
            null -> scheme.onSurface
        },
        border = if (auto == true) BorderStroke(1.dp, scheme.outlineVariant) else null,
    ) {
        Row(Modifier.padding(start = if (icon != null || painter != null) 12.dp else 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = MaterialTheme.colorScheme.onSurfaceVariant
            when {
                icon != null -> Icon(icon, null, Modifier.size(18.dp), tint = tint)
                painter != null -> Icon(painter, null, Modifier.size(18.dp), tint = tint)
            }
            if (icon != null || painter != null) Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Gap.xs))
            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp), tint = tint)
        }
    }
}

/**
 * Delete, in a form page's top bar. A plain neutral bin: one action needs no menu. What follows the
 * tap keeps it safe: an account asks first (its operations go too), everything else can be undone.
 */
@Composable
fun DeleteAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(R.drawable.ic_delete), tr("Удалить", "Delete")) }
}

/** The M3 Expressive switch: a check on the thumb when on. */
@Composable
fun CheckSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // Off is still a visible control: the thumb and outline in the muted ink, not the faint line.
        colors = SwitchDefaults.colors(
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        thumbContent = if (checked) {
            { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/**
 * A big form as a page of its own, over everything in the same window: a top bar with ✕ (or a way
 * back) and the form's title and actions, the form scrolling in between, and [actions] pinned at the
 * bottom, riding on the keyboard. The page resizes with the keyboard frame by frame, so nothing
 * jumps. Back hides the keyboard first, then closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormPage(
    onClose: () -> Unit,
    title: String = "",
    /** The top bar's leading icon: ✕ that closes by default. */
    navigation: @Composable () -> Unit = { CloseButton(onClose) },
    topActions: @Composable RowScope.() -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** 2 dp for a connected group of actions. */
    actionGap: Dp = Gap.s,
    /** No top bar: the page starts under the status bar and its way back is among the [actions]. */
    topBar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    BackHidesKeyboard()
    val color = sheetColor()
    Surface(Modifier.fillMaxSize(), color = color) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))) {
            if (topBar) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = navigation,
                    actions = topActions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = color, scrolledContainerColor = color),
                )
            } else {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                VSpace(Gap.m)
            }
            // 24 dp under the form and 16 above the action bar: 40 in all, and never less while the
            // form scrolls under the bar.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Gap.m)
                    .padding(top = Gap.s, bottom = Gap.l),
                content = content,
            )
            if (actions != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Gap.m, end = Gap.m, top = Gap.m, bottom = Gap.m),
                    horizontalArrangement = Arrangement.spacedBy(actionGap),
                    content = actions,
                )
            }
        }
    }
}

/**
 * A page's header the way Android's own settings do it: a tall empty space with the title big and
 * bold at its foot, folding into a plain bar (back and the title) that stays put while the page
 * scrolls, with a hairline under it once something is underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingHeader(title: String, onBack: () -> Unit, scroll: TopAppBarScrollBehavior, actions: @Composable RowScope.() -> Unit = {}) {
    val type = MaterialTheme.typography
    val page = MaterialTheme.colorScheme.background
    Column {
        // The bar takes its expanded and collapsed titles from these two styles.
        MaterialTheme(
            typography = type.copy(
                headlineMedium = type.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                titleLarge = type.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            ),
        ) {
            LargeTopAppBar(
                // The title 24 from the edge, like the rows under it.
                title = { Text(title, Modifier.padding(start = Gap.s), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), tr("Назад", "Back")) } },
                actions = actions,
                expandedHeight = 200.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = page, scrolledContainerColor = page),
                scrollBehavior = scroll,
            )
        }
        if (scroll.state.collapsedFraction > 0.99f) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** ✕: the one way to close a form page. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(R.drawable.ic_close), tr("Закрыть", "Close")) }
}

/**
 * Where form pages appear: one transition for all of them, from MotionScheme.expressive — up a short
 * way and in, the reverse out — while what is behind stays still. [target] null shows nothing.
 */
@Composable
fun <T : Any> FormPageHost(target: T?, content: @Composable (T) -> Unit) {
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = target,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            ((slideInVertically(slide) { it / 12 } + fadeIn(fade)) togetherWith (slideOutVertically(slide) { it / 12 } + fadeOut(fade)))
                .using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        label = "formPage",
    ) { page -> if (page != null) content(page) }
}

/**
 * The shell of the small windows (one value, a pick): a bottom sheet that only opens fully, back
 * hides the keyboard first, the content scrolls, and [actions] stay pinned under it, above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    onDismiss: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** 2 dp for a connected group of actions. */
    actionGap: Dp = Gap.s,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        // On the card colour: a sheet stands off the dimmed page in dark themes too.
        containerColor = tileColor(),
    ) {
        BackHidesKeyboard()
        Column(Modifier.imePadding()) {
            // 24 dp under the form and 16 above the action bar: 40 in all, and never less while the
            // form scrolls under the bar.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Gap.m)
                    .padding(top = Gap.s, bottom = Gap.l),
                content = content,
            )
            if (actions != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Gap.m, end = Gap.m, top = Gap.m, bottom = Gap.m),
                    horizontalArrangement = Arrangement.spacedBy(actionGap),
                    content = actions,
                )
            }
        }
    }
}

/**
 * For menus opened while typing (an account, a currency): the popup does not take focus, so the
 * keyboard stays up and the sheet does not jump.
 */
val KeepKeyboard = PopupProperties(focusable = false)

/** The one big filled pill at the bottom of a form. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true, weight: Float = 1f, shapes: ButtonShapes = ButtonDefaults.shapes()) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = shapes,
        modifier = Modifier.weight(weight).heightIn(min = ButtonDefaults.MediumContainerHeight),
        colors = ButtonDefaults.buttonColors(containerColor = actionColor(), contentColor = onActionColor()),
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** The quieter partner of [PrimaryAction] in a connected pair: "Сомневаюсь", "Удалить". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.TonalAction(text: String, onClick: () -> Unit, enabled: Boolean = true, weight: Float = 1f, shapes: ButtonShapes = ButtonDefaults.shapes()) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shapes = shapes,
        modifier = Modifier.weight(weight).heightIn(min = ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Leading and trailing shapes of a connected pair of buttons. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun connectedStart(): ButtonShapes = ButtonGroupDefaults.connectedLeadingButtonShapes().let { ButtonDefaults.shapes(it.shape, it.pressedShape) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun connectedMiddle(): ButtonShapes = ButtonGroupDefaults.connectedMiddleButtonShapes().let { ButtonDefaults.shapes(it.shape, it.pressedShape) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun connectedEnd(): ButtonShapes = ButtonGroupDefaults.connectedTrailingButtonShapes().let { ButtonDefaults.shapes(it.shape, it.pressedShape) }

/**
 * The big number you type: bare, centred, as large as the width allows, with a grey "0" before
 * anything is typed and the currency after it. [onSymbolClick] makes the symbol a tiny menu anchor.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroAmountField(
    text: String,
    onText: (String) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
    maxSp: Float = 0f,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onSymbolClick: (() -> Unit)? = null,
    symbolMenu: (@Composable () -> Unit)? = null,
    placeholder: String = "0",
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val base = bigFigure((if (maxSp > 0f) maxSp else ENTRY_FIGURE_SP).sp)
        val chars = (if (text.isEmpty()) placeholder.length else text.length + text.length / 4).coerceAtLeast(1) + 1 + symbol.length
        val fit = with(LocalDensity.current) { (maxWidth * 0.9f / (chars * 0.66f)).toSp() }
        val big = if (fit < base.fontSize) base.copy(fontSize = fit, lineHeight = fit * 1.15f) else base.copy(lineHeight = base.fontSize * 1.15f)
        // A fixed height, one line of [big]: a sign from a fallback font ("฿", Thai metrics) would
        // otherwise make the baseline-aligned row taller, and everything under it jump.
        val height = with(LocalDensity.current) { big.lineHeight.toDp() }
        Box(Modifier.height(height), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = text,
            // Digits and one decimal mark, nothing else: a typed space or dash would make a number
            // the app reads differently from what the field shows.
            onValueChange = { typed -> onText(amountInput(typed)) },
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            textStyle = big.copy(color = color),
            singleLine = true,
            visualTransformation = Grouping,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            decorationBox = { field ->
                // On one baseline: "฿" comes from a fallback font with other metrics, and centring
                // the boxes would drop it below the digits.
                Row {
                    // The cursor stands before the grey "0", not on top of it.
                    Box(Modifier.width(IntrinsicSize.Max).alignByBaseline()) { field() }
                    if (text.isEmpty()) Text(placeholder, Modifier.alignByBaseline(), style = big, color = placeholderColor, maxLines = 1)
                    Box(Modifier.alignByBaseline()) {
                        Text(
                            " " + symbol,
                            style = big,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (onSymbolClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onSymbolClick) else Modifier,
                        )
                        symbolMenu?.invoke()
                    }
                }
            },
        )
        }
    }
}

/** A bare field that is just its text, big: a name in a form, a small amount. */
@Composable
fun BareField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        decorationBox = { field ->
            Row {
                // With a suffix the field is as wide as its text, so the symbol stands right after it,
                // on the same baseline.
                Box(if (suffix == null) Modifier.weight(1f) else Modifier.weight(1f, fill = false).width(IntrinsicSize.Max).alignByBaseline()) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                    field()
                }
                suffix?.let {
                    Spacer(Modifier.width(Gap.s))
                    Text(it, Modifier.alignByBaseline(), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

/** A short centred line under a hero number: bodyLarge onSurfaceVariant. */
@Composable
fun UnderLine(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.merge(Tnum),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The one progress mark: a wavy line, 8 dp in a hero and 4 dp in a row, in the ink of what it sits
 * on over a track of the same ink at 0.24. Full is also the overflow: the bar fills and the wave
 * settles flat. The percent never goes inside; a caption under it says it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyBar(
    progress: Float,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    hero: Boolean = false,
    flat: Boolean = progress >= 1f,
) {
    val px = with(LocalDensity.current) { (if (hero) 10.dp else 4.dp).toPx() }
    // Glides to a new value with a little give, so a saved purchase is seen leaving the bar.
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "bar")
    val stroke = remember(px) { Stroke(width = px, cap = StrokeCap.Round) }
    // Slow on purpose: reaching a goal is the wave calming down.
    val wave by animateFloatAsState(if (flat) 0f else 1f, tween(900, easing = FastOutSlowInEasing), label = "wave")
    LinearWavyProgressIndicator(
        progress = { shown },
        modifier = modifier.fillMaxWidth().height(if (hero) 24.dp else WavyProgressIndicatorDefaults.LinearContainerHeight),
        color = ink,
        trackColor = ink.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        // An unhurried drift: a wavelength in two and a half seconds, not one.
        waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength * 0.4f,
        // Full stays wavy until [flat] says otherwise, so it can settle.
        amplitude = { (if (it >= 1f) 1f else WavyProgressIndicatorDefaults.indicatorAmplitude(it)) * wave },
    )
}

/** "62 %": a share as a whole percent, the way every caption says it. */
fun wholePercent(share: Double): String = if (share > 0 && share < 0.005) "<1 %" else "${kotlin.math.round(share * 100).toLong()} %"

/** "1500" reads "1 500" while it is typed: narrow no-break spaces between the thousands. */
object Grouping : VisualTransformation {
    private val number = Regex("([-−]?)(\\d+)([.,]\\d*)?")

    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val m = number.matchEntire(s) ?: return TransformedText(text, OffsetMapping.Identity)
        val digits = m.groupValues[2]
        if (digits.length < 4) return TransformedText(text, OffsetMapping.Identity)
        val sign = m.groupValues[1].length
        val out = StringBuilder()
        // Where each boundary between the typed characters lands in what is shown.
        val boundary = IntArray(s.length + 1)
        s.forEachIndexed { i, c ->
            val k = i - sign
            if (k in 1 until digits.length && (digits.length - k) % 3 == 0) out.append(' ')
            out.append(c)
            boundary[i + 1] = out.length
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = boundary[offset.coerceIn(0, s.length)]
            override fun transformedToOriginal(offset: Int) = (s.length downTo 0).first { boundary[it] <= offset }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/**
 * Each slice's sweep in degrees. None is shorter than a dot plus its gap, (stroke + gap) / rMid, so
 * every gap keeps one length; what the tiny ones gain comes off the largest. Taps use the same angles.
 */
fun ringSweeps(shares: List<Float>, stroke: Float, gapPx: Float, rMid: Float): List<Float> {
    val total = shares.sum().takeIf { it > 0f } ?: return shares.map { 0f }
    val raw = shares.map { 360f * it / total }
    if (raw.count { it > 0f } <= 1) return raw
    // The trim takes (stroke + gap) / rMid off every slice; the drawn arc keeps at least as much again.
    val least = 2 * Math.toDegrees(((stroke + gapPx) / rMid).toDouble()).toFloat()
    val floored = raw.map { if (it > 0f) maxOf(it, least) else 0f }
    val largest = raw.indices.maxBy { raw[it] }
    val extra = floored.sum() - 360f
    return floored.mapIndexed { i, sweep -> if (i == largest) sweep - extra else sweep }
}

/**
 * Arcs of a ring with round ends and gaps of the same length along the whole ring: each part is
 * trimmed at both ends by half the gap plus the cap. A part too short for that is a dot.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGappedRing(
    shares: List<Float>,
    colors: List<Color>,
    stroke: Float,
    gapPx: Float,
) {
    if (shares.sum() <= 0f) return
    val rMid = (minOf(size.width, size.height) - stroke) / 2
    val center = Offset(size.width / 2, size.height / 2)
    val single = shares.count { it > 0f } == 1
    val trim = if (single) 0f else Math.toDegrees(((gapPx / 2 + stroke / 2) / rMid).toDouble()).toFloat()
    var start = -90f
    ringSweeps(shares, stroke, gapPx, rMid).forEachIndexed { i, sweep ->
        if (sweep <= 0f) return@forEachIndexed
        val drawSweep = sweep - 2 * trim
        if (single) {
            drawCircle(colors[i], radius = rMid, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        } else if (drawSweep > 0f) {
            drawArc(
                color = colors[i],
                startAngle = start + trim,
                sweepAngle = drawSweep,
                useCenter = false,
                topLeft = Offset(center.x - rMid, center.y - rMid),
                size = androidx.compose.ui.geometry.Size(rMid * 2, rMid * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
        } else {
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            drawCircle(colors[i], radius = stroke / 2, center = Offset(center.x + rMid * cos(mid).toFloat(), center.y + rMid * sin(mid).toFloat()))
        }
        start += sweep
    }
}

/**
 * Lays a 48 dp button out without adding height to its row, nudged right by [nudge], so its icon
 * lines up with the content edge while the touch target stays full size.
 */
fun Modifier.overhang(nudge: Dp = 12.dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width - nudge.roundToPx(), 0) { placeable.place(0, -placeable.height / 2) }
}

/** Wider than the space given by [x] at each side, and drawn from [x] further left. */
fun Modifier.bleed(x: Dp): Modifier = layout { measurable, constraints ->
    val px = x.roundToPx()
    val wider = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px) else constraints
    val placeable = measurable.measure(wider)
    layout(placeable.width - 2 * px, placeable.height) { placeable.place(-px, 0) }
}

/** Pressed, the thing gives a little (0.9) and springs back: a button that feels like one. */
@Composable
fun Modifier.squish(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "squish")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Every big figure in the app (the tabs' heroes, an account's balance, the entry amount): one bold,
 * tight style, so a number reads the same wherever it is the main thing.
 */
@Composable
fun bigFigure(size: TextUnit): TextStyle = MaterialTheme.typography.displayLargeEmphasized.merge(Tnum)
    // −6 %: the digits read as one word without touching.
    .copy(fontSize = size, lineHeight = size * 1.15f, fontWeight = FontWeight.Medium, letterSpacing = (-0.06).em)

/**
 * The type steps go by the golden ratio from 14 sp: 14, 22, 36, 58, 94. A tab's figure is the top
 * step, 94, smaller only when it would not fit; the entry field's is one step down.
 */
const val BIG_FIGURE_SP = 94f
const val ENTRY_FIGURE_SP = 58f

/** A group's action in its header: "+ Добавить" as a small pill, its target 48 dp. */
@Composable
fun HeaderAction(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tileColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.minimumInteractiveComponentSize(),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
            Spacer(Modifier.width(Gap.xs))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Vertical space from the spacing scale. */
@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

/** A row of two tiles of equal height. */
@Composable
fun Bento(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap.s), content = content)
}

/** A clickable that also takes a long press, for rows outside lists. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tapAndHold(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** The few test tags the instrumented tests look things up by, where no text says it uniquely. */
object Tags {
    /** Home's line of the day's money in the other currencies, under the big number. */
    const val HOME_OTHERS = "home-others"

    /** Home's big number, "Можно сегодня". */
    const val HOME_BIG = "home-big"

    /** Accounts' big number, "Всего". */
    const val ACCOUNTS_TOTAL = "accounts-total"

    /** Insights' big number in the donut. */
    const val INSIGHTS_TOTAL = "insights-total"

    /** Home's "· N дней до зарплаты". */
    const val HOME_RUNWAY = "home-runway"
    const val HOME_PERIOD = "home-period"

    /** Settings' "Час на руки" value. */
    const val HOUR_NET = "hour-net"

    /** The big facts tile in "Сомневаюсь": the hours of work, when an hourly income is set. */
    const val DECIDE_HOURS = "decide-hours"
}

/**
 * A soft shadow all round, centred under the thing rather than dropped below it: the floating
 * island and its mic.
 */
fun Modifier.softShadow(shape: Shape): Modifier = dropShadow(shape, Shadow(radius = 24.dp, color = Color.Black, alpha = 0.14f))

/** A round icon button the height of the save button, beside it: back, delete. */
@Composable
fun RowScope.RoundAction(icon: Int, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(ButtonDefaults.MediumContainerHeight),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = unpickedColor(), contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Icon(painterResource(icon), description) }
}

/** What an amount field keeps of what was typed: digits and the first "," or "." (as ","), two decimals at most. */
fun amountInput(typed: String): String {
    val out = StringBuilder()
    var mark = false
    var decimals = 0
    for (c in typed) {
        when {
            c.isDigit() -> if (!mark || decimals < 2) { out.append(c); if (mark) decimals++ }
            (c == ',' || c == '.') && !mark -> { mark = true; out.append(',') }
        }
    }
    return out.toString()
}
