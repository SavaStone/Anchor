package app.opora.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.window.Dialog
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.sp
import app.opora.ui.theme.OporaIconSize
import app.opora.R
import kotlinx.coroutines.launch
import app.opora.domain.moodLevel
import app.opora.domain.dayMonth
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.theme.moodColor
import app.opora.ui.theme.onMoodColor
import java.time.LocalDate

@Composable
fun OporaCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = OporaSpacing.cardPadding,
    content: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.extraLarge
    val base = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = base,
            shape = shape,
            color = color,
            tonalElevation = 0.dp
        ) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Surface(
            modifier = base,
            shape = shape,
            color = color,
            tonalElevation = 0.dp
        ) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier.fillMaxWidth()) {
        if (onBack != null) {
            // Иконка «Назад» — ведущий элемент управления: центрируем её
            // по первой строке заголовка тем же правилом, что и inline-иконки.
            IconTextRow(
                title = title,
                leadingSize = 48.dp,
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                titleStyle = MaterialTheme.typography.headlineLarge,
                titleHeading = true,
                gap = OporaSpacing.xs,
                trailing = { TitleActions(actions) },
                trailingFirstLine = true
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                TitleActions(actions)
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = if (onBack != null) 52.dp else 0.dp)
                    .padding(top = OporaSpacing.xs)
            )
        }
    }
}

/**
 * Действия заголовка — ряд с зазором из сетки. Ширина ограничена, чтобы
 * длинный статус («Сохранение…») обрезался, а кнопка удаления оставалась
 * на месте и не пересекалась с текстом.
 */
@Composable
private fun TitleActions(actions: @Composable RowScope.() -> Unit) {
    BoxWithConstraints {
        Row(
            Modifier.widthIn(max = maxWidth * 0.6f),
            horizontalArrangement = Arrangement.spacedBy(OporaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/**
 * Одна и та же дата-пилюля в дневнике и в отметке настроения: иконка,
 * день с месяцем и явная подпись «Изменить».
 */
@Composable
fun DateChip(
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateLabel = date.dayMonth(locale)
    val changeDateLabel = stringResource(R.string.common_change_date_a11y, dateLabel)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.semantics { contentDescription = changeDateLabel }
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = OporaSpacing.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(OporaIconSize.inline),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(OporaSpacing.s))
            Text(dateLabel, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(OporaSpacing.s))
            Text(
                stringResource(R.string.action_edit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun BrandMark(size: Dp = 84.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}

@Composable
fun MoodFace(
    level: Int,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val faceColor = moodColor(level)
    val ink = onMoodColor(level)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "moodScale"
    )
    val label = stringResource(moodLevel(level).labelRes)
    val a11yLabel = stringResource(R.string.mood_a11y, label)
    val levelLabel = stringResource(R.string.a11y_level_of_five, level.coerceIn(1, 5))
    val targetSize = size.coerceAtLeast(48.dp)
    // Интерактивная лунка: selectable даёт Role.RadioButton, состояние selected
    // и корректное поведение TalkBack. Без onClick лицо декоративно.
    val faceOnClick = onClick
    val interaction = if (faceOnClick != null) {
        Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = faceOnClick)
            .semantics {
                contentDescription = a11yLabel
                stateDescription = levelLabel
            }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(
        modifier
            .size(targetSize)
            .scale(scale)
            .clip(CircleShape)
            .background(faceColor)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) ink else faceColor,
                shape = CircleShape
            )
            .then(interaction)
    ) {
        Canvas(Modifier.size(targetSize)) {
            val w = this.size.width
            val h = this.size.height
            val line = w * 0.048f
            val cap = StrokeCap.Round
            val eye = w * 0.032f

            fun mouth(startX: Float, startY: Float, bend: Float, endX: Float, endY: Float) {
                val path = Path().apply {
                    moveTo(w * startX, h * startY)
                    quadraticTo(w * 0.5f, h * bend, w * endX, h * endY)
                }
                drawPath(path, ink, style = Stroke(width = line, cap = cap))
            }

            when (level.coerceIn(1, 5)) {
                1 -> {
                    // Глубоко опущенные уголки рта — без бровей, чтобы лицо
                    // не выглядело злым.
                    drawCircle(ink, eye, Offset(w * 0.36f, h * 0.45f))
                    drawCircle(ink, eye, Offset(w * 0.64f, h * 0.45f))
                    mouth(0.32f, 0.72f, 0.54f, 0.68f, 0.72f)
                }
                2 -> {
                    drawCircle(ink, eye, Offset(w * 0.36f, h * 0.45f))
                    drawCircle(ink, eye, Offset(w * 0.64f, h * 0.45f))
                    mouth(0.34f, 0.69f, 0.60f, 0.66f, 0.69f)
                }
                3 -> {
                    drawCircle(ink, eye, Offset(w * 0.36f, h * 0.44f))
                    drawCircle(ink, eye, Offset(w * 0.64f, h * 0.44f))
                    drawLine(
                        ink, Offset(w * 0.36f, h * 0.67f), Offset(w * 0.64f, h * 0.67f),
                        strokeWidth = line, cap = cap
                    )
                }
                4 -> {
                    drawCircle(ink, eye, Offset(w * 0.36f, h * 0.44f))
                    drawCircle(ink, eye, Offset(w * 0.64f, h * 0.44f))
                    mouth(0.33f, 0.64f, 0.75f, 0.67f, 0.64f)
                }
                else -> {
                    // Прищуренные глаза и открытая улыбка — самый заметный уровень.
                    val leftEye = Path().apply {
                        moveTo(w * 0.28f, h * 0.45f)
                        quadraticTo(w * 0.35f, h * 0.36f, w * 0.42f, h * 0.45f)
                    }
                    val rightEye = Path().apply {
                        moveTo(w * 0.58f, h * 0.45f)
                        quadraticTo(w * 0.65f, h * 0.36f, w * 0.72f, h * 0.45f)
                    }
                    drawPath(leftEye, ink, style = Stroke(width = line, cap = cap))
                    drawPath(rightEye, ink, style = Stroke(width = line, cap = cap))
                    val smile = Path().apply {
                        moveTo(w * 0.29f, h * 0.60f)
                        quadraticTo(w * 0.5f, h * 0.84f, w * 0.71f, h * 0.60f)
                        quadraticTo(w * 0.5f, h * 0.70f, w * 0.29f, h * 0.60f)
                        close()
                    }
                    drawPath(smile, ink)
                }
            }
        }
    }
}

@Composable
fun MoodDot(level: Int?, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val color = if (level == null) MaterialTheme.colorScheme.outlineVariant else moodColor(level)
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun RingProgress(
    percent: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit = {}
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val noDataLabel = stringResource(R.string.common_no_data)
    val clamped = percent?.coerceIn(0, 100)
    val animated by animateFloatAsState(
        targetValue = (clamped ?: 0) / 100f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "ring"
    )
    Box(
        modifier
            .size(size)
            .semantics {
                val value = clamped
                if (value != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(value / 100f, 0f..1f)
                } else {
                    contentDescription = noDataLabel
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (clamped != null) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(OporaIconSize.list))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null
) {
    Surface(modifier, shape = CircleShape, color = color) {
        Row(
            Modifier.padding(horizontal = OporaSpacing.m, vertical = OporaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(OporaIconSize.compact), tint = contentColor)
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

/**
 * Контейнер экрана с учётом клавиатуры. В edge-to-edge `adjustResize`
 * не уменьшает окно, поэтому нижние поля и кнопка сохранения уезжали под IME.
 */
@Composable
fun ImeAwareBox(
    modifier: Modifier = Modifier,
    imeInsets: WindowInsets = WindowInsets.ime,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier.fillMaxSize().windowInsetsPadding(imeInsets),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Hides the IME of the window that owns this composition. A dialog lives in its
 * own window, so the activity's SoftwareKeyboardController may target the wrong
 * window; the view's window token is always the right one.
 */
@Composable
fun rememberKeyboardHider(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            val imm = view.context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}

/**
 * Back handling shared by every form: while the keyboard is open the first
 * back hides it and leaves the form in place; the next back performs the
 * screen's own close action. This keeps predictive back predictable because
 * the dialog never handles back itself.
 */
@Composable
fun ImeBackHandler(onBack: () -> Unit) {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    ImeBackHandler(imeVisible = imeVisible, onBack = onBack)
}

@Composable
private fun ImeBackHandler(imeVisible: Boolean, onBack: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler {
        if (imeVisible) keyboard?.hide() else onBack()
    }
}

private class FormDialogScrollState {
    val scroll = ScrollState(0)
    var focused: BringIntoViewRequester? = null

    suspend fun revealFocused() {
        focused?.bringIntoView()
    }
}

private val LocalFormDialogScroll = staticCompositionLocalOf<FormDialogScrollState?> { null }

/** Tests need to emulate an IME without a real keyboard window. */
@VisibleForTesting
val LocalFormDialogImeVisible = staticCompositionLocalOf<Boolean?> { null }

/**
 * Marks a text field inside [FormDialog] so the form can scroll it above the
 * keyboard both when it gains focus and when the IME finishes appearing.
 */
@Composable
fun Modifier.formDialogField(): Modifier {
    val state = LocalFormDialogScroll.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focus ->
            if (focus.isFocused) {
                state.focused = requester
                scope.launch { requester.bringIntoView() }
            }
        }
}

/**
 * Full-screen form on phones and a centred card on tablets.
 *
 * The dialog window is edge-to-edge and does not fit system windows, so the
 * content itself applies IME and navigation-bar insets. The action row is
 * outside the scrolling area: it is always reachable above the keyboard, and
 * every button keeps a 48dp touch target.
 */
@Composable
fun FormDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    imeInsets: WindowInsets = WindowInsets.ime,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val override = LocalFormDialogImeVisible.current
    val imeVisible = override ?: (imeInsets.getBottom(density) > 0)
    val scrollState = remember { FormDialogScrollState() }
    val hideKeyboard = rememberKeyboardHider()

    // The dialog lives in its own window on Android 13+, so a plain BackHandler
    // registered with the activity is not reached by the system back gesture.
    // Let the dialog's own back callback run and hide the IME before closing.
    fun handleBack() {
        if (imeVisible) hideKeyboard() else onDismissRequest()
    }

    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            // Insets animate; wait for the new viewport before revealing.
            withFrameNanos { }
            scrollState.revealFocused()
        }
    }

    Dialog(
        onDismissRequest = ::handleBack,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val wide = maxWidth >= 600.dp
            Surface(
                modifier = if (wide) {
                    Modifier.widthIn(max = 560.dp).fillMaxHeight(0.92f)
                } else {
                    Modifier.fillMaxSize()
                },
                shape = if (wide) MaterialTheme.shapes.extraLarge else RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                CompositionLocalProvider(LocalFormDialogScroll provides scrollState) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(imeInsets)
                            .navigationBarsPadding()
                            .then(if (wide) Modifier else Modifier.statusBarsPadding())
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = 20.dp,
                                bottom = 12.dp
                            )
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(scrollState.scroll)
                                .padding(horizontal = 24.dp)
                        ) {
                            content()
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.End
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissLabel != null) {
                                androidx.compose.material3.TextButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text(dismissLabel) }
                            }
                            Button(
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text(confirmLabel) }
                        }
                    }
                }
            }
        }
    }
}

/** Единое состояние загрузки: спиннер с описанием для TalkBack. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.common_loading)
    Box(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = loadingLabel }
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = OporaSpacing.xl, horizontal = OporaSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(OporaSpacing.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Единое состояние ошибки: тот же контейнер и ритм, что у EmptyState. */
@Composable
fun ErrorState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = OporaSpacing.xl, horizontal = OporaSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(Modifier.height(OporaSpacing.l))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(OporaSpacing.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(OporaSpacing.l))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Подтверждение опасного или необратимого действия.
 *
 * По умолчанию диалог закрывается сразу после нажатия подтверждения — это подходит
 * для синхронных действий. Для асинхронных операций (бэкап, удаление файла) передайте
 * [dismissOnConfirm] = false, закройте диалог сами после успеха и держите его открытым
 * при ошибке: так сбой не выглядит как успех.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    dismissLabel: String = stringResource(R.string.common_cancel),
    destructive: Boolean = true,
    confirmEnabled: Boolean = true,
    dismissOnConfirm: Boolean = true,
    icon: ImageVector? = null,
    errorMessage: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        // Длинное предупреждение при крупном шрифте не должно обрезаться:
        // текст прокручивается, кнопки остаются на месте.
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text)
                if (errorMessage != null) {
                    Spacer(Modifier.height(OporaSpacing.s))
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    if (dismissOnConfirm) onDismiss()
                },
                enabled = confirmEnabled,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

/** Единое размещение и отступы снекбаров; FAB-экраны поднимают хост выше. */
@Composable
fun OporaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    aboveFab: Boolean = false
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(
            start = OporaSpacing.l,
            end = OporaSpacing.l,
            bottom = if (aboveFab) OporaSpacing.fabClearance else OporaSpacing.l
        )
    )
}


/**
 * Единое правило для строк «иконка + текст».
 *
 * По умолчанию ведущая иконка центрируется по ПЕРВОЙ строке заголовка, а не
 * по центру всего многострочного блока: так иконка стоит на одном уровне
 * с текстом, а не «висит» между строками. Для крупных иллюстраций
 * (например, блок «Всё важное — рядом» в онбординге) роль другая —
 * `firstLine = false`, они центрируются по всему текстовому блоку.
 *
 * Размер иконки берётся из типографики заголовка, отступ — единый.
 * Хвостовые элементы (шеврон, ⋮) — это действия, они центрируются по строке.
 */
@Composable
fun IconTextRow(
    leadingSize: Dp,
    leading: @Composable () -> Unit,
    firstLineStyle: TextStyle,
    modifier: Modifier = Modifier,
    firstLine: Boolean = true,
    gap: Dp = OporaSpacing.s,
    trailing: (@Composable () -> Unit)? = null,
    trailingCentered: Boolean = true,
    trailingFirstLine: Boolean = false,
    trailingSize: Dp = leadingSize,
    content: @Composable ColumnScope.() -> Unit
) {
    // Высота ПЕРВОЙ строки по реальным метрикам шрифта, а не по
    // TextStyle.lineHeight: Compose для однострочного текста использует
    // метрики глифа, и lineHeight=18sp у bodySmall даёт строку ~14.1dp.
    // Иначе иконка садится ниже текста на 2–17dp в зависимости от масштаба.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val firstLineHeight = remember(firstLineStyle, measurer, density) {
        val measured = measurer.measure(
            text = AnnotatedString("Ag"),
            style = firstLineStyle,
            maxLines = 1,
            softWrap = false
        )
        with(density) {
            (measured.getLineBottom(0) - measured.getLineTop(0)).toDp()
        }
    }
    val leadingOffset = if (firstLine) (firstLineHeight - leadingSize) / 2 else 0.dp
    val trailingOffset = if (trailingFirstLine) (firstLineHeight - trailingSize) / 2 else 0.dp
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = if (firstLine) Alignment.Top else Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .offset(y = leadingOffset)
                .size(leadingSize)
                .testTag(IconTextRowTags.LEADING),
            contentAlignment = Alignment.Center
        ) {
            leading()
        }
        Spacer(Modifier.width(gap))
        Column(Modifier.weight(1f).testTag(IconTextRowTags.BLOCK), content = content)
        if (trailing != null) {
            val trailingModifier = when {
                trailingFirstLine -> Modifier.offset(y = trailingOffset)
                trailingCentered -> Modifier.align(Alignment.CenterVertically)
                else -> Modifier
            }
            Box(
                trailingModifier.testTag(IconTextRowTags.TRAILING),
                contentAlignment = Alignment.Center
            ) { trailing() }
        }
    }
}

@Composable
fun IconTextRow(
    title: String,
    leadingSize: Dp,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    firstLine: Boolean = true,
    titleHeading: Boolean = false,
    gap: Dp = OporaSpacing.s,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
    trailingCentered: Boolean = true,
    trailingFirstLine: Boolean = false,
    trailingSize: Dp = leadingSize
) {
    IconTextRow(
        leadingSize = leadingSize,
        leading = leading,
        firstLineStyle = titleStyle,
        modifier = modifier,
        firstLine = firstLine,
        gap = gap,
        trailing = trailing,
        trailingCentered = trailingCentered,
        trailingFirstLine = trailingFirstLine,
        trailingSize = trailingSize
    ) {
        Text(
            text = title,
            style = titleStyle,
            color = titleColor,
            modifier = Modifier
                .testTag(IconTextRowTags.TITLE)
                .then(if (titleHeading) Modifier.semantics { heading() } else Modifier)
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = subtitleStyle, color = subtitleColor)
        }
    }
}

/** Иконка по типографической шкале: мелкий текст — мелкая иконка. */
fun iconSizeForStyle(style: TextStyle): Dp = when {
    style.fontSize < 13.sp -> OporaIconSize.compact
    style.fontSize < 16.sp -> OporaIconSize.inline
    else -> OporaIconSize.list
}

@Composable
fun IconTextRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = iconSizeForStyle(titleStyle),
    titleHeading: Boolean = false,
    gap: Dp = OporaSpacing.s,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    firstLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    trailingCentered: Boolean = true,
    trailingFirstLine: Boolean = false,
    trailingSize: Dp = iconSize
) {
    IconTextRow(
        title = title,
        leadingSize = iconSize,
        leading = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
        },
        modifier = modifier,
        subtitle = subtitle,
        titleStyle = titleStyle,
        subtitleStyle = subtitleStyle,
        firstLine = firstLine,
        titleHeading = titleHeading,
        gap = gap,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        trailing = trailing,
        trailingCentered = trailingCentered,
        trailingFirstLine = trailingFirstLine,
        trailingSize = trailingSize
    )
}

object IconTextRowTags {
    const val LEADING = "iconTextLeading"
    const val TITLE = "iconTextTitle"
    const val BLOCK = "iconTextBlock"
    const val TRAILING = "iconTextTrailing"
}

/** Единый фон иконки меню: скруглённый квадрат, а не круг/голый значок. */
@Composable
fun MenuIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 38.dp,
    iconSize: Dp = OporaIconSize.list
) {
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null
) {
    IconTextRow(
        title = title,
        leadingSize = 38.dp,
        leading = { MenuIcon(icon, tint) },
        subtitle = subtitle,
        titleStyle = MaterialTheme.typography.titleSmall,
        gap = 12.dp,
        modifier = modifier.padding(vertical = 6.dp),
        firstLine = false,
        trailing = trailing,
        trailingFirstLine = false,
        trailingSize = 24.dp
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    )
}

@Composable
fun BigActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container
    ) {
        Row(Modifier.padding(OporaSpacing.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
                Spacer(Modifier.height(3.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f)
                )
            }
            MenuIcon(
                icon = icon,
                tint = contentColor,
                size = 46.dp,
                iconSize = OporaIconSize.regular
            )
        }
    }
}

/**
 * Главное действие экрана: расширенный FAB с подписью, который сворачивается
 * до иконки при прокрутке. Появление анимировано, чтобы кнопка не «прыгала»
 * на уже отрисованный экран.
 */
@Composable
fun PrimaryFab(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true
) {
    // При крупном шрифте развёрнутый FAB закрывает заголовки и карточки:
    // оставляем иконку, полная подпись остаётся в contentDescription.
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    ExtendedFloatingActionButton(
        text = { Text(text) },
        icon = {
            // Подпись кнопке задаёт contentDescription на самой кнопке:
            // отдельное имя у иконки TalkBack читал бы дважды.
            Icon(Icons.Outlined.Add, contentDescription = null)
        },
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        expanded = expanded && !largeFont
    )
}
