package app.opora.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opora.BuildConfig
import app.opora.R
import app.opora.ui.components.IconTextRow
import app.opora.ui.components.MenuIcon
import app.opora.ui.components.OporaCard
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        ScreenTitle(title = stringResource(R.string.about_title), onBack = onBack)
        Spacer(Modifier.height(OporaSpacing.m))
        Text(
            stringResource(R.string.about_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                R.string.about_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(OporaSpacing.xl))
        SectionHeader(stringResource(R.string.about_section_privacy))
        Spacer(Modifier.height(OporaSpacing.s))
        OporaCard {
            Column(verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)) {
                PrivacyRow(
                    Icons.Outlined.Lock,
                    stringResource(R.string.about_privacy_accounts_title),
                    stringResource(R.string.about_privacy_accounts_body)
                )
                PrivacyRow(
                    Icons.Outlined.PhoneAndroid,
                    stringResource(R.string.about_privacy_local_title),
                    stringResource(R.string.about_privacy_local_body)
                )
                PrivacyRow(
                    Icons.Outlined.CloudSync,
                    stringResource(R.string.about_privacy_server_title),
                    stringResource(R.string.about_privacy_server_body)
                )
            }
        }

        Spacer(Modifier.height(OporaSpacing.xl))
        SectionHeader(stringResource(R.string.about_section_repo))
        Spacer(Modifier.height(OporaSpacing.s))
        RepositoryCard()

        Spacer(Modifier.height(OporaSpacing.xl))
        SectionHeader(stringResource(R.string.about_section_important))
        Spacer(Modifier.height(OporaSpacing.s))
        OporaCard(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                Text(
                    stringResource(R.string.about_disclaimer_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(OporaSpacing.s))
                Text(
                    stringResource(R.string.about_disclaimer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(OporaSpacing.xl))
        Text(
            stringResource(R.string.about_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.about_author_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.about_credits),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacyRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    IconTextRow(
        icon = icon,
        title = title,
        subtitle = body,
        titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        tint = MaterialTheme.colorScheme.primary
    )
}

private const val REPOSITORY_URL = "https://github.com/SavaStone/Anchor"

/**
 * Репозиторий открывается системным обработчиком: приложение не встраивает
 * браузер, а человек сам выбирает, чем открыть ссылку. Если обработчика нет,
 * под карточкой появляется спокойное объяснение вместо молчаливого нажатия.
 */
@Composable
private fun RepositoryCard() {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf<String?>(null) }

    fun openRepository() {
        linkError = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, REPOSITORY_URL.toUri()))
        }.exceptionOrNull()?.let { context.getString(R.string.author_link_error, REPOSITORY_URL) }
    }

    Column {
        OporaCard(onClick = { openRepository() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MenuIcon(
                    icon = Icons.Outlined.Code,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 42.dp,
                    iconSize = OporaIconSize.list
                )
                Spacer(Modifier.width(OporaSpacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.about_repo_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.about_repo_url),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(OporaSpacing.s))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(OporaIconSize.inline)
                )
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
    }
}
