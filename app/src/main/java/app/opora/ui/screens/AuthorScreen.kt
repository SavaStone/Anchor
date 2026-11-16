package app.opora.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.ui.components.InfoRow
import app.opora.ui.components.OporaCard
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing

/**
 * Автор приложения: спокойная визитка без аналитики и без встроенного
 * браузера. Ссылки открываются системным обработчиком, поэтому человек сам
 * выбирает приложение и остаётся в знакомой среде.
 */
@Composable
fun AuthorScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf<String?>(null) }

    fun open(url: String) {
        linkError = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.exceptionOrNull()?.let { context.getString(R.string.author_link_error, url) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = OporaSpacing.screenGutter,
                end = OporaSpacing.screenGutter,
                top = OporaSpacing.screenTop,
                bottom = OporaSpacing.screenBottom
            )
    ) {
        ScreenTitle(title = stringResource(R.string.author_title), onBack = onBack)
        Spacer(Modifier.height(OporaSpacing.xl))
        AuthorHero()

        Spacer(Modifier.height(OporaSpacing.xl))
        SectionHeader(stringResource(R.string.author_section_links))
        Spacer(Modifier.height(OporaSpacing.s))
        OporaCard(contentPadding = OporaSpacing.l) {
            Column(verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)) {
                AuthorLinkRow(
                    icon = Icons.Outlined.PlayCircle,
                    tint = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.author_youtube_title),
                    subtitle = stringResource(R.string.author_youtube_subtitle),
                    onClickLabel = stringResource(R.string.author_open_youtube)
                ) {
                    open("https://www.youtube.com/@SavaStone")
                }
                AuthorLinkRow(
                    icon = Icons.Outlined.Code,
                    tint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.author_github_title),
                    subtitle = stringResource(R.string.author_github_subtitle),
                    onClickLabel = stringResource(R.string.author_open_github)
                ) {
                    open("https://github.com/SavaStone")
                }
            }
        }

        linkError?.let { message ->
            Spacer(Modifier.height(OporaSpacing.m))
            OporaCard(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }

        Spacer(Modifier.height(OporaSpacing.xl))
        Text(
            stringResource(R.string.author_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Шапка визитки без портрета: короткая акцентная черта, имя, роль и одна
 * строка о приложении. Левый край совпадает с заголовком экрана, поэтому
 * блок остаётся собранным и на крупном системном шрифте.
 */
@Composable
private fun AuthorHero() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        )
        Spacer(Modifier.height(OporaSpacing.m))
        Text(
            stringResource(R.string.author_name),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.author_role),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(OporaSpacing.s))
        Text(
            stringResource(R.string.author_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthorLinkRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit
) {
    InfoRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tint = tint,
        trailing = {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(OporaIconSize.inline)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    )
}
