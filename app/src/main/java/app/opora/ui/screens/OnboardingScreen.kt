@file:OptIn(ExperimentalFoundationApi::class)

package app.opora.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.ui.components.IconTextRow
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appName = context.getString(R.string.app_name)
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun finish() {
        if (!accepted) {
            error = context.getString(R.string.onboarding_accept_required)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onDone(name)
    }

    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage(appName)
                1 -> FeaturesPage()
                else -> PrivacyPage(
                    appName = appName,
                    name = name,
                    onNameChange = { name = it },
                    accepted = accepted,
                    onAcceptedChange = {
                        accepted = it
                        if (it) error = ""
                    }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clearAndSetSemantics {
                    contentDescription = context.getString(
                        R.string.onboarding_page_of,
                        pager.currentPage + 1,
                        3
                    )
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (pager.currentPage == index) 22.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pager.currentPage == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        if (error.isNotBlank() && pager.currentPage == 2) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }
        if (pager.currentPage == 2) {
            Text(
                stringResource(R.string.onboarding_permission_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = {
                if (pager.currentPage < 2) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else {
                    finish()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (pager.currentPage < 2) R.string.onboarding_next
                    else R.string.onboarding_start
                )
            )
        }
    }
}

@Composable
private fun WelcomePage(appName: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // После удаления декоративных смайликов контент стал коротким и
        // прижимался к верхнему краю — центрируем его в доступной высоте.
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(74.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(OporaSpacing.xl))
        Text(
            stringResource(R.string.onboarding_welcome_title, appName),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(OporaSpacing.s))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeaturesPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.onboarding_features_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_features_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(OporaSpacing.xl))
        Feature(
            Icons.Outlined.Mood,
            stringResource(R.string.onboarding_feature_mood_title),
            stringResource(R.string.onboarding_feature_mood_body)
        )
        Feature(
            Icons.Outlined.Medication,
            stringResource(R.string.onboarding_feature_meds_title),
            stringResource(R.string.onboarding_feature_meds_body)
        )
        Feature(
            Icons.Outlined.AutoStories,
            stringResource(R.string.onboarding_feature_journal_title),
            stringResource(R.string.onboarding_feature_journal_body)
        )
        Spacer(Modifier.height(8.dp))
        Feature(
            Icons.Outlined.Lock,
            stringResource(R.string.onboarding_feature_privacy_title),
            stringResource(R.string.onboarding_feature_privacy_body)
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    IconTextRow(
        leadingSize = 46.dp,
        leading = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
        firstLineStyle = MaterialTheme.typography.titleMedium,
        // Крупный значок-иллюстрация — отдельная роль: центр по всему блоку.
        firstLine = false,
        gap = 12.dp,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacyPage(
    appName: String,
    name: String,
    onNameChange: (String) -> Unit,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.onboarding_privacy_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(OporaSpacing.l))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions.Default
        )
        Spacer(Modifier.height(OporaSpacing.l))
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = accepted,
                    role = Role.Checkbox,
                    onValueChange = onAcceptedChange
                ),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(checked = accepted, onCheckedChange = null)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    stringResource(R.string.onboarding_consent_title, appName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.onboarding_consent_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(OporaSpacing.listGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(OporaIconSize.inline)
            )
            Spacer(Modifier.width(OporaSpacing.s))
            Text(
                stringResource(R.string.onboarding_export_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
