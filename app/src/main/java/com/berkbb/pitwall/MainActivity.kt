package com.berkbb.pitwall

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import java.util.Locale

val AccentColor = Color(0xFF7C4DFF)
val BackgroundDark = Color(0xFF111318)
val CardDark = Color(0xFF1C1F26)
val TextGray = Color(0xFF949498)
val TextWhite = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {

    private val viewModel: UpdaterViewModel by viewModels()

    companion object {
        private const val PREFS_NAME = "f1tv_updater_prefs"
        private const val KEY_SELECTED_LANGUAGE = "selected_language"
    }

    private fun getSavedLanguage(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_LANGUAGE, null)
    }

    private fun saveLanguagePreference(lang: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_SELECTED_LANGUAGE, lang) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedLang = getSavedLanguage()
        val systemLang = Locale.getDefault().language
        val initialLang = LocaleHelper.resolveInitialLanguage(savedLang, systemLang)

        setContent {
            var currentLanguage by remember {
                mutableStateOf(initialLang)
            }

            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val localizedConfiguration = remember(currentLanguage, configuration) {
                Configuration(configuration).apply {
                    setLocale(Locale.forLanguageTag(currentLanguage))
                }
            }
            val localizedContext = remember(context, localizedConfiguration) {
                context.createConfigurationContext(localizedConfiguration)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext
            ) {
                PitwallScreen(
                    viewModel = viewModel,
                    currentLanguage = currentLanguage,
                    onToggleLanguage = {
                        val newLang = LocaleHelper.getNextLanguage(currentLanguage)
                        saveLanguagePreference(newLang)
                        // Durum metinleri kaynak ID'si olarak tutulduğu için yeniden çizim yeterli; ağ çağrısı gerekmez
                        currentLanguage = newLang
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}

@Composable
fun PitwallScreen(
    viewModel: UpdaterViewModel,
    currentLanguage: String,
    onToggleLanguage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(48.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(32.dp)
                                .background(AccentColor, RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.header_title),
                            color = TextWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                    val model = android.os.Build.MODEL
                    val product = android.os.Build.PRODUCT
                    val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm"
                    Text(
                        text = stringResource(R.string.header_subtitle, manufacturer, model, product, abi),
                        color = TextGray,
                        fontSize = 16.sp
                    )
                    (uiState.selfUpdate as? SelfUpdateStatus.Available)?.let { update ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.self_update_badge, update.latest),
                            color = Color(0xFFB39DDB),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Butonlar: Info, Dil Değiştirme ve Canlı F1 TV Aç
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bilgi Butonu
                    TvActionButton(
                        text = stringResource(R.string.btn_info),
                        backgroundColor = CardDark,
                        focusedBorderColor = Color(0xFF64B5F6),
                        onClick = { showInfoDialog = true }
                    )

                    // Dil Butonu (EN / TR / RO)
                    TvActionButton(
                        text = stringResource(R.string.btn_language, currentLanguage.uppercase(Locale.ROOT)),
                        backgroundColor = CardDark,
                        focusedBorderColor = Color(0xFF00ADB5),
                        onClick = onToggleLanguage
                    )

                    // F1 TV Başlat Butonu
                    TvActionButton(
                        text = if (uiState.isInstalled) stringResource(R.string.btn_launch_f1) else stringResource(R.string.btn_not_installed),
                        backgroundColor = CardDark,
                        focusedBorderColor = TextWhite,
                        enabled = uiState.isInstalled,
                        onClick = { viewModel.launchF1TV() }
                    )
                }
            }

            // Info Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InfoCard(
                    title = stringResource(R.string.card_installed_version),
                    value = if (uiState.isInstalled) uiState.installedVersion else stringResource(R.string.btn_not_installed),
                    valueColor = if (uiState.isInstalled) TextWhite else Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )

                InfoCard(
                    title = stringResource(R.string.card_latest_version),
                    value = uiState.latestVersion.ifEmpty { stringResource(R.string.status_not_checked) },
                    modifier = Modifier.weight(1f)
                )

                val statusColor = when {
                    uiState.isSignatureConflict -> Color(0xFFE53935)
                    !uiState.isInstalled -> Color(0xFFFF9800)
                    uiState.isUpdateAvailable -> AccentColor
                    else -> Color(0xFF4CAF50)
                }

                val statusText = when {
                    uiState.isLoading -> stringResource(R.string.status_checking)
                    // statusArg tipi (Int / String) ilgili metnin %1$d / %1$s biçimiyle eşleşir
                    else -> uiState.statusArg?.let { arg -> stringResource(uiState.statusResId, arg) }
                        ?: stringResource(uiState.statusResId)
                }

                InfoCard(
                    title = stringResource(R.string.card_status),
                    value = statusText,
                    valueColor = statusColor,
                    isLoading = uiState.isLoading,
                    modifier = Modifier.weight(1.2f)
                )
            }

            // Progress Bar if Downloading
            if (uiState.isDownloading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { uiState.downloadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentColor,
                        trackColor = CardDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.status_downloading, uiState.downloadProgress),
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }

            // Bottom Actions
            val cancelFocusRequester = remember { FocusRequester() }
            LaunchedEffect(uiState.isDownloading) {
                // İndirme başlayınca ana buton devre dışı kalır; odağı İptal butonuna taşı
                if (uiState.isDownloading) runCatching { cancelFocusRequester.requestFocus() }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvActionButton(
                    text = if (uiState.isLoading) stringResource(R.string.status_checking) else stringResource(R.string.btn_check_updates),
                    backgroundColor = CardDark,
                    focusedBorderColor = Color(0xFFE0E0E0),
                    enabled = !uiState.isLoading && !uiState.isDownloading,
                    isLoading = uiState.isLoading,
                    onClick = { viewModel.checkForUpdates() }
                )

                if (uiState.isSignatureConflict && uiState.isInstalled) {
                    // Farklı imza anahtarıyla kurulu F1 TV önce kaldırılmalı
                    TvActionButton(
                        text = stringResource(R.string.btn_uninstall_f1),
                        backgroundColor = AccentColor,
                        focusedBorderColor = Color(0xFFFFD700),
                        onClick = { viewModel.uninstallF1TV() }
                    )
                } else if (!uiState.isInstalled || uiState.isUpdateAvailable || uiState.downloadUrl.isNotEmpty()) {
                    val buttonText = when {
                        uiState.isDownloading -> stringResource(R.string.btn_downloading)
                        !uiState.isInstalled -> stringResource(R.string.btn_download_install)
                        uiState.isUpdateAvailable -> stringResource(R.string.btn_download_update)
                        else -> stringResource(R.string.btn_reinstall)
                    }

                    TvActionButton(
                        text = buttonText,
                        backgroundColor = AccentColor,
                        focusedBorderColor = Color(0xFFFFD700),
                        enabled = !uiState.isDownloading && uiState.downloadUrl.isNotEmpty(),
                        isLoading = uiState.isDownloading,
                        onClick = { viewModel.downloadAndInstall() }
                    )

                    if (uiState.isDownloading) {
                        TvActionButton(
                            text = stringResource(R.string.btn_cancel_download),
                            backgroundColor = CardDark,
                            focusedBorderColor = Color(0xFFE0E0E0),
                            modifier = Modifier.focusRequester(cancelFocusRequester),
                            onClick = { viewModel.cancelDownload() }
                        )
                    }
                }
            }
        }

        if (showInfoDialog) {
            AboutWindow(
                selfUpdate = uiState.selfUpdate,
                selfUpdateResult = uiState.selfUpdateResult,
                selfInstall = uiState.selfInstall,
                onCheckSelfUpdate = { viewModel.checkSelfUpdate() },
                onInstallSelfUpdate = { viewModel.installSelfUpdate() },
                onDismissSelfUpdateResult = { viewModel.dismissSelfUpdateResult() },
                onClose = { showInfoDialog = false }
            )
        }
    }
}

/**
 * Full-screen About window, as in AynaCast: title with the accent bar, a close button in the
 * top-right corner and another at the bottom. A separate window keeps D-pad focus inside it, and
 * Back closes it.
 */
@Composable
fun AboutWindow(
    selfUpdate: SelfUpdateStatus,
    selfUpdateResult: SelfUpdateStatus?,
    selfInstall: SelfInstallState,
    onCheckSelfUpdate: () -> Unit,
    onInstallSelfUpdate: () -> Unit,
    onDismissSelfUpdateResult: () -> Unit,
    onClose: () -> Unit
) {
    val closeFocusRequester = remember { FocusRequester() }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            runCatching { closeFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            // Title bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(32.dp)
                        .background(AccentColor, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.dialog_about_title),
                    color = TextWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                CloseIconButton(
                    contentDescription = stringResource(R.string.btn_close),
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    InfoCard(
                        title = stringResource(R.string.dialog_developer_label).trimEnd(':'),
                        value = stringResource(R.string.dialog_developer_name),
                        height = 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                    InfoCard(
                        title = stringResource(R.string.dialog_website_label).trimEnd(':'),
                        value = stringResource(R.string.dialog_website_url),
                        valueColor = Color(0xFF64B5F6),
                        height = 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    InfoCard(
                        title = stringResource(R.string.dialog_license_label).trimEnd(':'),
                        value = stringResource(R.string.dialog_license_name),
                        valueColor = Color(0xFF81C784),
                        height = 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                    InfoCard(
                        title = stringResource(R.string.dialog_patch_label).trimEnd(':'),
                        value = stringResource(R.string.dialog_patch_author),
                        height = 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                    val model = android.os.Build.MODEL
                    val product = android.os.Build.PRODUCT
                    val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm"
                    InfoCard(
                        title = stringResource(R.string.dialog_version_label).trimEnd(':'),
                        value = "v${BuildConfig.VERSION_NAME}",
                        valueColor = Color(0xFFB39DDB),
                        height = 96.dp,
                        modifier = Modifier.weight(1f)
                    )
                    InfoCard(
                        title = stringResource(R.string.dialog_device_label).trimEnd(':'),
                        value = stringResource(R.string.header_subtitle, manufacturer, model, product, abi),
                        height = 96.dp,
                        modifier = Modifier.weight(2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer beside the Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dialog_disclaimer),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
                val checkingSelf = selfUpdate is SelfUpdateStatus.Checking
                TvActionButton(
                    text = stringResource(if (checkingSelf) R.string.btn_checking_app_updates else R.string.btn_check_app_updates),
                    backgroundColor = CardDark,
                    focusedBorderColor = Color(0xFFE0E0E0),
                    enabled = !checkingSelf,
                    onClick = onCheckSelfUpdate
                )
                TvActionButton(
                    text = stringResource(R.string.btn_view_license),
                    backgroundColor = CardDark,
                    focusedBorderColor = Color(0xFFE0E0E0),
                    onClick = { showLicenseDialog = true }
                )
                TvActionButton(
                    text = stringResource(R.string.btn_close),
                    backgroundColor = AccentColor,
                    focusedBorderColor = Color(0xFFFFD700),
                    modifier = Modifier.focusRequester(closeFocusRequester),
                    onClick = onClose
                )
            }
        }

        if (showLicenseDialog) {
            LicenseWindow(onClose = { showLicenseDialog = false })
        }
        if (selfUpdateResult != null) {
            SelfUpdateResultWindow(
                result = selfUpdateResult,
                install = selfInstall,
                onInstall = onInstallSelfUpdate,
                onClose = onDismissSelfUpdateResult
            )
        }
    }
}

/**
 * Result of a manual "Check for Updates" in About: up to date, no public release, GitHub
 * unreachable, or a newer version that can download and install itself ("Update Now").
 * Full-screen like About; Later/OK or Back closes it.
 */
@Composable
fun SelfUpdateResultWindow(
    result: SelfUpdateStatus,
    install: SelfInstallState,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }
    val canInstall = result is SelfUpdateStatus.Available && result.apk != null
    val (title, message, color) = when (result) {
        is SelfUpdateStatus.UpToDate -> Triple(
            stringResource(R.string.self_update_up_to_date_title),
            stringResource(R.string.self_update_up_to_date, result.current),
            Color(0xFF81C784)
        )
        is SelfUpdateStatus.Available -> Triple(
            stringResource(R.string.self_update_available_title),
            if (result.apk != null) stringResource(R.string.self_update_available_install, result.latest, result.current)
            else stringResource(R.string.self_update_available, result.latest, result.current, SELF_RELEASES_PAGE),
            Color(0xFFB39DDB)
        )
        is SelfUpdateStatus.Unavailable -> Triple(
            stringResource(R.string.self_update_failed_title),
            stringResource(R.string.self_update_unavailable),
            Color(0xFFFFB74D)
        )
        else -> Triple(
            stringResource(R.string.self_update_failed_title),
            stringResource(R.string.self_update_offline),
            Color(0xFFFFB74D)
        )
    }
    val busy = install is SelfInstallState.Downloading || install is SelfInstallState.WaitingForConfirmation
    val installLine: Pair<String, Color>? = when (install) {
        is SelfInstallState.Downloading -> stringResource(R.string.status_downloading, install.progress) to TextWhite
        is SelfInstallState.WaitingForConfirmation -> stringResource(R.string.self_update_confirm) to Color(0xFF81C784)
        is SelfInstallState.Failed -> (
            if (install.arg != null) stringResource(install.messageRes, install.arg) else stringResource(install.messageRes)
        ) to Color(0xFFFFB74D)
        else -> null
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            runCatching { primaryFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(horizontal = 120.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                color = TextWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = TextGray,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (installLine != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = installLine.first,
                    color = installLine.second,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (canInstall) {
                    TvActionButton(
                        text = stringResource(R.string.btn_self_update_now),
                        backgroundColor = AccentColor,
                        focusedBorderColor = Color(0xFFFFD700),
                        enabled = !busy,
                        isLoading = install is SelfInstallState.Downloading,
                        modifier = Modifier.focusRequester(primaryFocusRequester),
                        onClick = onInstall
                    )
                    TvActionButton(
                        text = stringResource(R.string.btn_later),
                        backgroundColor = CardDark,
                        focusedBorderColor = Color(0xFFE0E0E0),
                        onClick = onClose
                    )
                } else {
                    TvActionButton(
                        text = stringResource(R.string.btn_ok),
                        backgroundColor = AccentColor,
                        focusedBorderColor = Color(0xFFFFD700),
                        modifier = Modifier.focusRequester(primaryFocusRequester),
                        onClick = onClose
                    )
                }
            }
        }
    }
}

/**
 * Full-screen read-only License window displaying the GNU General Public License v3.0.
 * Scrollable with D-pad, traps focus, and closes via Close button or Back key.
 */
@Composable
fun LicenseWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val closeFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val licenseText = remember(context) {
        runCatching {
            context.assets.open("LICENSE").bufferedReader().use { it.readText() }
        }.getOrDefault("GNU General Public License v3.0\n\nhttps://www.gnu.org/licenses/gpl-3.0.txt")
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            runCatching { closeFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(48.dp)
                // The text itself is not focusable: D-pad Up/Down scrolls it and only moves focus
                // between the buttons once the text has reached its start or end.
                .onPreviewKeyEvent { event ->
                    val step = when (event.key) {
                        Key.DirectionDown -> if (scrollState.canScrollForward) 300f else null
                        Key.DirectionUp -> if (scrollState.canScrollBackward) -300f else null
                        else -> null
                    } ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        scope.launch { scrollState.animateScrollBy(step) }
                    }
                    true
                }
        ) {
            // Title bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(32.dp)
                        .background(AccentColor, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.dialog_license_title),
                    color = TextWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                CloseIconButton(
                    contentDescription = stringResource(R.string.btn_close),
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Read-only scrollable license text container
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp)
                ) {
                    Text(
                        text = licenseText,
                        color = TextWhite,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvActionButton(
                    text = stringResource(R.string.btn_close),
                    backgroundColor = AccentColor,
                    focusedBorderColor = Color(0xFFFFD700),
                    modifier = Modifier.focusRequester(closeFocusRequester),
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun CloseIconButton(contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isFocused) Color(0xFFE0E0E0) else CardDark)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✕",
            color = if (isFocused) BackgroundDark else TextWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextWhite,
    isLoading: Boolean = false,
    height: Dp = 140.dp
) {
    Card(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = TextGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentColor,
                        strokeWidth = 2.5.dp
                    )
                }
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun TvActionButton(
    text: String,
    backgroundColor: Color,
    focusedBorderColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    // Button zaten odaklanabilir; ek .focusable() ikinci bir odak durağı yaratıyordu
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) focusedBorderColor else backgroundColor,
            contentColor = if (isFocused) BackgroundDark else TextWhite,
            disabledContainerColor = backgroundColor.copy(alpha = 0.6f),
            disabledContentColor = TextWhite.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .height(52.dp)
            .then(
                if (isFocused) Modifier.border(3.dp, focusedBorderColor, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = if (isFocused) BackgroundDark else TextWhite,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
