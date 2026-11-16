package app.opora.ui.theme

import app.opora.R

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Огонёк серии приёма. В светлой теме берём более тёмный оранжевый:
 * исходный #E07A2F давал 2,3:1 на светлых контейнерах при норме 3:1 для иконок.
 */
val FireColorLight = Color(0xFFA8450A)
val FireColorDark = Color(0xFFE07A2F)

/** Чернила поверх огонька: на светлом оранжевом — белые, на тёмном — почти чёрные. */
val OnFireColorLight = Color(0xFFFFFFFF)
val OnFireColorDark = Color(0xFF1A1006)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2F6653),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F1D5),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF092017),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFF7FBF7),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF7FBF7),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color(0xFF2F6653),
    surfaceDim = Color(0xFFD7DCD7),
    surfaceBright = Color(0xFFF7FBF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F6F2),
    surfaceContainer = Color(0xFFEBF0EB),
    surfaceContainerHigh = Color(0xFFE5EAE5),
    surfaceContainerHighest = Color(0xFFDFE4DF),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF1EC),
    inversePrimary = Color(0xFF99D5B8)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF99D5B8),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF14513E),
    onPrimaryContainer = Color(0xFFB4F1D5),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA5CDDE),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C2),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF99D5B8),
    surfaceDim = Color(0xFF0F1511),
    surfaceBright = Color(0xFF353B37),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632),
    inverseSurface = Color(0xFFDFE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF2F6653)
)

private val OporaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val OporaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

data class MoodPalette(
    val one: Color,
    val two: Color,
    val three: Color,
    val four: Color,
    val five: Color,
    val onOne: Color,
    val onTwo: Color,
    val onThree: Color,
    val onFour: Color,
    val onFive: Color
)

// Приглушённые тона: насыщенная заливка читается как «выбранный» вариант,
// поэтому цвет палитры спокойный, а смысл несут штрихи и форма лица.
// Красный → жёлтый → синий → светло-зелёный → зелёный: цвет несёт смысл,
// а иконка на заливке остаётся контрастной (≥4.5:1 для мелких штрихов).
val LightMoodPalette = MoodPalette(
    one = Color(0xFFE7B4B6),
    two = Color(0xFFE8D3A0),
    three = Color(0xFFB8CBE2),
    four = Color(0xFFCBDDAE),
    five = Color(0xFFA9C7B4),
    onOne = Color(0xFF4A1D20),
    onTwo = Color(0xFF3F330F),
    onThree = Color(0xFF1D3047),
    onFour = Color(0xFF2C3A16),
    onFive = Color(0xFF173528)
)

val DarkMoodPalette = MoodPalette(
    one = Color(0xFF8E5C60),
    two = Color(0xFF7F6C3B),
    three = Color(0xFF566F8C),
    four = Color(0xFF5C7548),
    five = Color(0xFF507564),
    onOne = Color(0xFFFBEDEE),
    onTwo = Color(0xFFFFF5DA),
    onThree = Color(0xFFEAF2FB),
    onFour = Color(0xFFF1F7E8),
    onFive = Color(0xFFEAF6EF)
)

val LocalMoodPalette = staticCompositionLocalOf { LightMoodPalette }
val LocalIsDark = staticCompositionLocalOf { false }

/** Огонёк в активной теме: светлый вариант темнее, тёмный — теплее. */
@Composable
fun fireColor(): Color = if (LocalIsDark.current) FireColorDark else FireColorLight

@Composable
fun onFireColor(): Color = if (LocalIsDark.current) OnFireColorDark else OnFireColorLight

fun MoodPalette.colorFor(level: Int): Color = when (level) {
    1 -> one
    2 -> two
    3 -> three
    4 -> four
    else -> five
}

fun MoodPalette.onColorFor(level: Int): Color = when (level) {
    1 -> onOne
    2 -> onTwo
    3 -> onThree
    4 -> onFour
    else -> onFive
}

@Composable
fun moodColor(level: Int): Color = LocalMoodPalette.current.colorFor(level)

@Composable
fun onMoodColor(level: Int): Color = LocalMoodPalette.current.onColorFor(level)

@Composable
fun OporaTheme(
    themeMode: String = "system",
    // По умолчанию — статичная зелёная схема; динамический цвет включает переключатель в настройках.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val palette = if (dark) DarkMoodPalette else LightMoodPalette

    CompositionLocalProvider(
        LocalMoodPalette provides palette,
        LocalIsDark provides dark
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OporaTypography,
            shapes = OporaShapes,
            content = content
        )
    }
}

/**
 * Палитра лекарств — данные пользователя, но сами значения и контрастные
 * «чернила» для галочки живут в теме, а не в экране редактора.
 */
data class MedicationSwatch(val value: Long, @androidx.annotation.StringRes val labelRes: Int)

val MedicationSwatches: List<MedicationSwatch> = listOf(
    MedicationSwatch(0xFF4F7CD1, R.string.color_blue),
    MedicationSwatch(0xFF4E8C77, R.string.color_green),
    MedicationSwatch(0xFFB4544E, R.string.color_red),
    MedicationSwatch(0xFFC4774A, R.string.color_orange),
    MedicationSwatch(0xFFB99A44, R.string.color_yellow),
    MedicationSwatch(0xFF7B6BB5, R.string.color_purple),
    MedicationSwatch(0xFFB05A8E, R.string.color_pink),
    MedicationSwatch(0xFF4E8C9B, R.string.color_teal),
    MedicationSwatch(0xFF8A6B4F, R.string.color_brown),
    MedicationSwatch(0xFF5F6B72, R.string.color_gray)
)

val DefaultMedicationColor: Long = MedicationSwatches.first().value

/**
 * Контрастная галочка поверх произвольного цвета лекарства. Выбираем из
 * чёрного и белого по фактическому контрасту, а не по «светлый/тёмный»:
 * жёлтый и оранжевый требуют тёмных чернил, красный и серый — светлых.
 */
fun medicationOnColor(value: Long): Color {
    val background = Color(value).luminance()
    val blackContrast = (background + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (background + 0.05f)
    return if (blackContrast >= whiteContrast) Color(0xFF000000) else Color(0xFFFFFFFF)
}
