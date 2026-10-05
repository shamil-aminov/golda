package sh.aminov.golda.ui

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.Row
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import android.Manifest
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.aminov.golda.R
import sh.aminov.golda.data.Repo
import sh.aminov.golda.data.VoiceOutcome
import sh.aminov.golda.data.VoiceRecorder
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.label
import sh.aminov.golda.domain.tr
import kotlin.math.sqrt

enum class VoiceState { Idle, Recording, Thinking }

/** [level] is how loud the microphone hears it right now, 0..1. */
class VoiceControl(val state: VoiceState, val toggle: () -> Unit, val level: () -> Float = { 0f })

/**
 * Tap to talk, tap again when done. Results (also of notes understood later
 * from the offline queue) arrive through [Repo.voice] and end up as snackbars;
 * "хочу купить X" goes to [onConsider].
 */
@Composable
fun rememberVoice(
    repo: Repo,
    data: AppData,
    snackbar: SnackbarHostState,
    requests: ReceiveChannel<Unit>,
    onConsider: (List<VoiceAction.Consider>) -> Unit,
): VoiceControl {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val haptics = LocalHapticFeedback.current
    var state by remember { mutableStateOf(VoiceState.Idle) }

    // The microphone never outlives the screen. Leaving the app mid-recording (or the screen going
    // away) drops the half-said note instead of keeping it: it was never confirmed with a second tap,
    // in the background Android records only silence anyway, and a kept half would be booked later
    // beside whatever the user enters again by hand.
    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (state == VoiceState.Recording) {
            recorder.cancel()
            state = VoiceState.Idle
            scope.launch { snackbar.showSnackbar(tr("Запись прервана — приложение свернули. Ничего не записано", "Recording stopped when the app was left. Nothing was saved")) }
        }
    }

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { state = VoiceState.Recording }
            .onFailure { scope.launch { snackbar.showSnackbar(tr("Микрофон занят или недоступен", "The microphone is busy or unavailable")) } }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else scope.launch { snackbar.showSnackbar(tr("Без доступа к микрофону голос не работает", "Voice needs microphone access")) }
    }

    fun toggle() {
        when (state) {
            VoiceState.Idle -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
            }
            VoiceState.Recording -> {
                val file = recorder.stop()
                if (file == null) {
                    state = VoiceState.Idle
                } else {
                    state = VoiceState.Thinking
                    scope.launch {
                        repo.understand(file)
                        state = VoiceState.Idle
                    }
                }
            }
            VoiceState.Thinking -> Unit
        }
    }

    LaunchedEffect(requests) {
        for (request in requests) if (state == VoiceState.Idle) toggle()
    }

    // The collector below lives as long as the screen; it must read today's accounts, not the ones it
    // started with, or a lari purchase is announced in rubles because its account wasn't loaded yet.
    val latest by rememberUpdatedState(data)
    LaunchedEffect(repo) {
        repo.voice.collect { outcome ->
            when (outcome) {
                is VoiceOutcome.Done -> {
                    // Every "хочу купить" of the note, one after another.
                    if (outcome.considering.isNotEmpty()) onConsider(outcome.considering)
                    if (outcome.recorded.isNotEmpty()) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        val text = outcome.recorded.joinToString(" · ") { (_, draft) -> describe(draft, latest) } +
                            (outcome.comment?.let { "\n$it" } ?: "") +
                            (if (outcome.misunderstood) "\n" + tr("Часть записи не разобрать — допиши через «+»", "Part of the note was unclear; add it with “+”") else "")
                        val prefix = if (outcome.late) tr("Из отложенного: ", "From a saved note: ") else ""
                        val result = snackbar.showSnackbar(prefix + text, actionLabel = tr("Отменить", "Undo"), duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) repo.undo(outcome.undo)
                    } else if (outcome.misunderstood && outcome.transcript.isNotBlank() && outcome.considering.isEmpty()) {
                        // A blank transcript is a stray tap: nothing to say about it.
                        snackbar.showSnackbar(tr("Не разобрать: «${outcome.transcript}». Можно записать через «+»", "Couldn’t make out “${outcome.transcript}”. Add it with “+”"), duration = SnackbarDuration.Long)
                    }
                }
                is VoiceOutcome.Waiting -> snackbar.showSnackbar(outcome.reason, duration = SnackbarDuration.Long)
                is VoiceOutcome.Failed -> snackbar.showSnackbar(outcome.reason, duration = SnackbarDuration.Long)
            }
        }
    }

    return VoiceControl(state, ::toggle, recorder::level)
}

private fun describe(draft: Draft, data: AppData): String {
    val code = draft.purchaseCurrency ?: data.accountById[draft.accountId]?.currency ?: "RUB"
    val title = draft.note.ifBlank { draft.categoryId?.let { data.categoryById[it]?.label() } ?: tr("Записано", "Saved") }
    return "$title ${Fmt.amount(draft.purchaseAmountMinor ?: draft.amountMinor, code)}"
}

/** A tab of the floating toolbar: the outline icon, the filled one when it is the current tab, and its name. */
class ToolbarTab(@DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int, val name: String)

/**
 * The one bar at the bottom of every tab: an M3 Expressive floating toolbar, centred, floating over
 * the content. The tabs as icons (filled and on the picked colour when current), a hairline, "+" to
 * add by hand, and the mic as the toolbar's own FAB, the biggest mark of all, in the hero's lavender.
 * While the mic listens it shows the voice-reactive cookie with the stop square; while the note is
 * worked out, the loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoldaToolbar(
    tabs: List<ToolbarTab>,
    selected: Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    voice: VoiceControl,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // The one action to take: gold, like every main action.
    val mic = actionColor()
    val onMic = onActionColor()
    val label = when (voice.state) {
        VoiceState.Idle -> tr("Сказать", "Say it")
        VoiceState.Recording -> tr("Слушаю. Нажми, когда закончишь", "Listening. Tap when done")
        VoiceState.Thinking -> tr("Разбираю…", "Working it out…")
    }
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            // A card in light; in dark a step up, or it melts into the cards it floats over.
            toolbarContainerColor = if (isDark()) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
            toolbarContentColor = scheme.onSurfaceVariant,
            fabContainerColor = mic,
            fabContentColor = onMic,
        ),
        floatingActionButton = {
            // Round like the bar beside it, and as tall, so the main action is also the easiest target.
            FloatingActionButton(
                onClick = voice.toggle,
                modifier = Modifier.size(ToolbarHeight).semantics { contentDescription = label },
                shape = CircleShape,
                containerColor = mic,
                contentColor = onMic,
            ) {
                when (voice.state) {
                    VoiceState.Thinking -> LoadingIndicator(Modifier.size(36.dp), color = onMic)
                    VoiceState.Recording -> Listening(voice.level, fill = onMic, ink = mic)
                    VoiceState.Idle -> Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(32.dp))
                }
            }
        },
    ) {
        // One pill rides under the tabs at the pager's exact position, so it follows the finger during
        // a swipe and glides on a tap. Each icon takes the pill's ink and fills as the pill reaches it.
        val picked = pickedColor()
        val onPicked = onPickedColor()
        val idle = scheme.onSurfaceVariant
        Box {
            Box(
                Modifier
                    .offset { IntOffset((position() * ToolbarItemWidth.toPx()).roundToInt(), 0) }
                    .size(ToolbarItemWidth, ToolbarItemHeight)
                    .background(picked, CircleShape),
            )
            Row {
                tabs.forEachIndexed { index, tab ->
                    val nearness = (1f - abs(position() - index)).coerceIn(0f, 1f)
                    IconButton(
                        onClick = { onSelect(index) },
                        // Wider and taller than the 40 dp default: the bar is used with a thumb, often on the move.
                        modifier = Modifier.size(ToolbarItemWidth, ToolbarItemHeight).semantics {
                            role = Role.Tab
                            this.selected = index == selected
                        },
                    ) {
                        Icon(
                            painterResource(if (nearness > 0.5f) tab.selectedIcon else tab.icon),
                            tab.name,
                            Modifier.size(26.dp),
                            tint = lerp(idle, onPicked, nearness),
                        )
                    }
                }
            }
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = Gap.xs), color = scheme.outlineVariant)
        IconButton(onClick = onAdd, Modifier.size(ToolbarItemWidth, ToolbarItemHeight)) { Icon(painterResource(R.drawable.ic_add), tr("Записать вручную", "Add by hand"), Modifier.size(26.dp)) }
    }
}

private val ToolbarItemWidth = 58.dp
private val ToolbarItemHeight = 48.dp
private val ToolbarHeight = 64.dp

/** A circle that keeps turning into a nine-sided cookie and back, with the stop square on it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Listening(level: () -> Float, fill: Color, ink: Color) {
    // Silence is a circle; the louder the voice, the more of a cookie it becomes.
    var loudness by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            // Speech peaks sit well under full scale; the square root lifts quiet talk into view.
            loudness = sqrt((level() * 3f).coerceIn(0f, 1f))
            delay(60)
        }
    }
    val progress by animateFloatAsState(loudness, MaterialTheme.motionScheme.fastSpatialSpec(), label = "voice")
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val turn by rememberInfiniteTransition(label = "listening")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "turn")
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = morph.toPath(progress.coerceIn(0f, 1f))
            path.transform(Matrix().apply { scale(size.width, size.height) })
            rotate(turn) { drawPath(path, fill) }
        }
        Icon(painterResource(R.drawable.ic_stop), null, Modifier.size(20.dp), tint = ink)
    }
}
