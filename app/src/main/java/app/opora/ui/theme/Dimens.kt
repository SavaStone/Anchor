package app.opora.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Единая сетка приложения: 4 / 8 / 12 / 16 / 24.
 * Семантические токены для каркаса экрана и карточек.
 */
object OporaSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp

    val screenGutter = l
    val screenTop = l
    val listGap = m
    val cardPadding = l
    val screenBottom = xl

    /** Место под FAB, чтобы последняя карточка не уходила под кнопку. */
    val fabClearance = 112.dp

    /** Место под нижней панелью сохранения в редакторах. */
    val editorBottomClearance = 128.dp
}

/** Одинаковые размеры иконок в одинаковых ролях. */
object OporaIconSize {
    val compact = 16.dp
    val inline = 18.dp
    val list = 20.dp
    val row = 22.dp
    val regular = 24.dp
}
