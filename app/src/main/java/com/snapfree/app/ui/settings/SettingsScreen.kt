@file:OptIn(ExperimentalMaterial3Api::class)

package com.snapfree.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfree.app.BuildConfig
import com.snapfree.app.R
import com.snapfree.app.data.DefaultMode
import com.snapfree.app.data.SaveLocation
import com.snapfree.app.data.ThemeMode
import com.snapfree.app.data.LoginSite
import com.snapfree.app.data.UpdateChannel
import com.snapfree.app.ui.login.LoginActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.outlined.AccountCircle
import com.snapfree.app.engine.EngineState
import com.snapfree.app.engine.FormatCatalog
import com.snapfree.app.engine.UpdateResult
import com.snapfree.app.ui.components.GroupCard
import com.snapfree.app.ui.components.IconBadge
import com.snapfree.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/** Which dialog is currently open on the settings screen. */
private enum class SettingDialog {
    THEME, LANGUAGE, DEFAULT_MODE, CONTAINER, HEIGHT, AUDIO_FORMAT, BITRATE, LOCATION, SUBFOLDER,
    CONCURRENT, FRAGMENTS, SUB_LANGS, PROXY, RATE_LIMIT, USER_AGENT, COOKIES, EXTRA_ARGS, CHANNEL,
}

private enum class AppLanguage(val tag: String) { SYSTEM(""), ENGLISH("en"), PORTUGUESE_BR("pt-BR") }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, contentPadding: PaddingValues) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    val hasCookies by viewModel.hasCookies.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val loginLauncher = rememberLauncherForActivityResult(LoginActivity.Contract()) { }
    var logoutSite by remember { mutableStateOf<LoginSite?>(null) }
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }

    // Report yt-dlp update results with a short toast.
    val updatedMsg = stringResource(R.string.ytdlp_updated)
    val upToDateMsg = stringResource(R.string.ytdlp_up_to_date)
    val failedMsg = stringResource(R.string.ytdlp_update_failed)
    LaunchedEffect(updateResult) {
        val result = updateResult ?: return@LaunchedEffect
        val text = when (result) {
            is UpdateResult.Updated -> "$updatedMsg ${result.version.orEmpty()}"
            is UpdateResult.AlreadyUpToDate -> upToDateMsg
            is UpdateResult.Failed -> "$failedMsg: ${result.message}"
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.consumeUpdateResult()
    }

    val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags().let { tags ->
        when {
            tags.startsWith("pt") -> AppLanguage.PORTUGUESE_BR
            tags.startsWith("en") -> AppLanguage.ENGLISH
            else -> AppLanguage.SYSTEM
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.nav_settings)) },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            // --- Appearance ----------------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_appearance))
            GroupCard {
                ValueRow(Icons.Outlined.DarkMode, stringResource(R.string.setting_theme), themeLabel(s.themeMode)) { dialog = SettingDialog.THEME }
                Divider()
                SwitchRow(Icons.Outlined.Contrast, stringResource(R.string.setting_pure_black), stringResource(R.string.setting_pure_black_desc), s.pureBlack) {
                    viewModel.edit { setPureBlack(it) }
                }
                Divider()
                ValueRow(Icons.Outlined.Language, stringResource(R.string.setting_language), languageLabel(currentLanguage)) { dialog = SettingDialog.LANGUAGE }
            }

            // --- Formats -------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_formats))
            GroupCard {
                ValueRow(Icons.Outlined.Tune, stringResource(R.string.setting_default_mode), modeLabel(s.defaultMode)) { dialog = SettingDialog.DEFAULT_MODE }
                Divider()
                ValueRow(Icons.Outlined.Movie, stringResource(R.string.setting_video_container), s.videoContainer.uppercase()) { dialog = SettingDialog.CONTAINER }
                Divider()
                ValueRow(Icons.Outlined.HighQuality, stringResource(R.string.setting_video_quality), heightLabel(s.maxHeight)) { dialog = SettingDialog.HEIGHT }
                Divider()
                ValueRow(Icons.Outlined.AudioFile, stringResource(R.string.setting_audio_format), audioLabel(s.audioFormat)) { dialog = SettingDialog.AUDIO_FORMAT }
                Divider()
                ValueRow(Icons.Outlined.Tag, stringResource(R.string.setting_audio_bitrate), "${s.audioBitrate} kbps") { dialog = SettingDialog.BITRATE }
            }

            // --- Post-processing -----------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_processing))
            GroupCard {
                SwitchRow(Icons.Outlined.Info, stringResource(R.string.setting_embed_metadata), stringResource(R.string.setting_embed_metadata_desc), s.embedMetadata) {
                    viewModel.edit { setEmbedMetadata(it) }
                }
                Divider()
                SwitchRow(Icons.Outlined.Image, stringResource(R.string.setting_embed_thumbnail), stringResource(R.string.setting_embed_thumbnail_desc), s.embedThumbnail) {
                    viewModel.edit { setEmbedThumbnail(it) }
                }
                Divider()
                SwitchRow(Icons.Outlined.Subtitles, stringResource(R.string.setting_embed_subs), stringResource(R.string.setting_embed_subs_desc), s.embedSubtitles) {
                    viewModel.edit { setEmbedSubtitles(it) }
                }
                if (s.embedSubtitles) {
                    Divider()
                    ValueRow(Icons.Outlined.Language, stringResource(R.string.setting_sub_langs), s.subtitleLanguages) { dialog = SettingDialog.SUB_LANGS }
                }
                Divider()
                SwitchRow(Icons.Outlined.ContentCut, stringResource(R.string.setting_sponsorblock), stringResource(R.string.setting_sponsorblock_desc), s.sponsorBlock) {
                    viewModel.edit { setSponsorBlock(it) }
                }
            }

            // --- Storage & queue -----------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_downloads))
            GroupCard {
                ValueRow(Icons.Outlined.Folder, stringResource(R.string.setting_location), locationLabel(s.saveLocation)) { dialog = SettingDialog.LOCATION }
                Divider()
                ValueRow(Icons.Outlined.FolderOpen, stringResource(R.string.setting_subfolder), s.subfolder.ifBlank { "—" }) { dialog = SettingDialog.SUBFOLDER }
                Divider()
                ValueRow(Icons.Outlined.Layers, stringResource(R.string.setting_concurrent), s.concurrentDownloads.toString()) { dialog = SettingDialog.CONCURRENT }
                Divider()
                ValueRow(Icons.Outlined.Bolt, stringResource(R.string.setting_fragments), s.concurrentFragments.toString()) { dialog = SettingDialog.FRAGMENTS }
                Divider()
                SwitchRow(Icons.Outlined.Wifi, stringResource(R.string.setting_wifi_only), stringResource(R.string.setting_wifi_only_desc), s.wifiOnly) {
                    viewModel.edit { setWifiOnly(it) }
                }
            }

            // --- Accounts ------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_accounts))
            GroupCard {
                LoginSite.entries.forEachIndexed { index, site ->
                    if (index > 0) Divider()
                    val isIn = site in loggedIn
                    ValueRow(
                        Icons.Outlined.AccountCircle,
                        LoginActivity.siteName(site),
                        stringResource(if (isIn) R.string.account_logged_in else R.string.account_logged_out),
                    ) {
                        if (isIn) logoutSite = site else loginLauncher.launch(site)
                    }
                }
            }
            Text(
                stringResource(R.string.accounts_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            // --- Network -------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_network))
            GroupCard {
                ValueRow(Icons.Outlined.Cookie, stringResource(R.string.setting_cookies_manual),
                    if (hasCookies) stringResource(R.string.setting_cookies_loaded) else stringResource(R.string.setting_cookies_none)) {
                    dialog = SettingDialog.COOKIES
                }
                Divider()
                SwitchRow(Icons.Outlined.Person, stringResource(R.string.setting_use_cookies), stringResource(R.string.setting_use_cookies_manual_desc), s.useCookies && hasCookies, enabled = hasCookies) {
                    viewModel.edit { setUseCookies(it) }
                }
                Divider()
                ValueRow(Icons.Outlined.Public, stringResource(R.string.setting_proxy), s.proxy.ifBlank { stringResource(R.string.value_none) }) { dialog = SettingDialog.PROXY }
                Divider()
                ValueRow(Icons.Outlined.Speed, stringResource(R.string.setting_rate_limit), s.rateLimit.ifBlank { stringResource(R.string.value_unlimited) }) { dialog = SettingDialog.RATE_LIMIT }
                Divider()
                ValueRow(Icons.Outlined.DataSaverOn, stringResource(R.string.setting_user_agent), s.userAgent.ifBlank { stringResource(R.string.value_default) }) { dialog = SettingDialog.USER_AGENT }
            }

            // --- yt-dlp --------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_engine))
            GroupCard {
                val version = (engine as? EngineState.Ready)?.version
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating && engine is EngineState.Ready) { viewModel.updateYtDlp() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Outlined.SystemUpdate)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_update_ytdlp), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (engine) {
                                is EngineState.Ready -> stringResource(R.string.ytdlp_version, version ?: "?")
                                is EngineState.Initializing -> stringResource(R.string.engine_initializing)
                                is EngineState.Failed -> stringResource(R.string.engine_failed, (engine as EngineState.Failed).message)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedContent(targetState = updating, label = "updating") { busy ->
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.action_update), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Divider()
                ValueRow(Icons.Outlined.NewReleases, stringResource(R.string.setting_update_channel), channelLabel(s.updateChannel)) { dialog = SettingDialog.CHANNEL }
                Divider()
                SwitchRow(Icons.Outlined.Update, stringResource(R.string.setting_auto_update), stringResource(R.string.setting_auto_update_desc_v2), s.autoUpdateYtDlp) {
                    viewModel.edit { setAutoUpdateYtDlp(it) }
                }
                Divider()
                ValueRow(Icons.Outlined.Terminal, stringResource(R.string.setting_extra_args), s.extraArgs.ifBlank { stringResource(R.string.value_none) }) { dialog = SettingDialog.EXTRA_ARGS }
            }

            // --- About ---------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.settings_about))
            GroupCard {
                ValueRow(Icons.Outlined.Info, stringResource(R.string.app_name), stringResource(R.string.about_version, BuildConfig.VERSION_NAME), onClick = null)
            }
            Text(
                stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }

    // --- Dialogs ---------------------------------------------------------------------------------
    logoutSite?.let { site ->
        AlertDialog(
            onDismissRequest = { logoutSite = null },
            title = { Text(stringResource(R.string.account_logout_title, LoginActivity.siteName(site))) },
            text = { Text(stringResource(R.string.account_logout_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(site); logoutSite = null }) { Text(stringResource(R.string.action_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { logoutSite = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    val close = { dialog = null }
    when (dialog) {
        SettingDialog.THEME -> ChoiceDialog(stringResource(R.string.setting_theme), ThemeMode.entries, s.themeMode, { themeLabel(it) }, close) {
            viewModel.edit { setThemeMode(it) }
        }
        SettingDialog.LANGUAGE -> ChoiceDialog(stringResource(R.string.setting_language), AppLanguage.entries, currentLanguage, { languageLabel(it) }, close) {
            val locales = if (it == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(it.tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
        SettingDialog.DEFAULT_MODE -> ChoiceDialog(stringResource(R.string.setting_default_mode), DefaultMode.entries, s.defaultMode, { modeLabel(it) }, close) {
            viewModel.edit { setDefaultMode(it) }
        }
        SettingDialog.CONTAINER -> ChoiceDialog(stringResource(R.string.setting_video_container), FormatCatalog.videoContainers, s.videoContainer, { it.uppercase() }, close) {
            viewModel.edit { setVideoContainer(it) }
        }
        SettingDialog.HEIGHT -> ChoiceDialog(stringResource(R.string.setting_video_quality), FormatCatalog.heights, s.maxHeight, { heightLabel(it) }, close) {
            viewModel.edit { setMaxHeight(it) }
        }
        SettingDialog.AUDIO_FORMAT -> ChoiceDialog(stringResource(R.string.setting_audio_format), FormatCatalog.audioFormats, s.audioFormat, { audioLabel(it) }, close) {
            viewModel.edit { setAudioFormat(it) }
        }
        SettingDialog.BITRATE -> ChoiceDialog(stringResource(R.string.setting_audio_bitrate), FormatCatalog.audioBitrates, s.audioBitrate, { "$it kbps" }, close) {
            viewModel.edit { setAudioBitrate(it) }
        }
        SettingDialog.LOCATION -> ChoiceDialog(stringResource(R.string.setting_location), SaveLocation.entries, s.saveLocation, { locationLabel(it) }, close) {
            viewModel.edit { setSaveLocation(it) }
        }
        SettingDialog.CHANNEL -> ChoiceDialog(stringResource(R.string.setting_update_channel), UpdateChannel.entries, s.updateChannel, { channelLabel(it) }, close) {
            viewModel.edit { setUpdateChannel(it) }
        }
        SettingDialog.SUBFOLDER -> TextInputDialog(
            title = stringResource(R.string.setting_subfolder), initial = s.subfolder, placeholder = "SnapFree",
            supportingText = stringResource(R.string.setting_subfolder_desc), onDismiss = close,
        ) { viewModel.edit { setSubfolder(it) } }
        SettingDialog.CONCURRENT -> SliderDialog(stringResource(R.string.setting_concurrent), s.concurrentDownloads, 1..5, close) {
            viewModel.edit { setConcurrentDownloads(it) }
        }
        SettingDialog.FRAGMENTS -> SliderDialog(stringResource(R.string.setting_fragments), s.concurrentFragments, 1..16, close) {
            viewModel.edit { setConcurrentFragments(it) }
        }
        SettingDialog.SUB_LANGS -> TextInputDialog(
            title = stringResource(R.string.setting_sub_langs), initial = s.subtitleLanguages, placeholder = "en,pt",
            supportingText = stringResource(R.string.setting_sub_langs_desc), onDismiss = close,
        ) { viewModel.edit { setSubtitleLanguages(it) } }
        SettingDialog.PROXY -> TextInputDialog(
            title = stringResource(R.string.setting_proxy), initial = s.proxy, placeholder = "socks5://127.0.0.1:1080",
            supportingText = stringResource(R.string.setting_proxy_desc), keyboardType = KeyboardType.Uri, onDismiss = close,
        ) { viewModel.edit { setProxy(it) } }
        SettingDialog.RATE_LIMIT -> TextInputDialog(
            title = stringResource(R.string.setting_rate_limit), initial = s.rateLimit, placeholder = "2M",
            supportingText = stringResource(R.string.setting_rate_limit_desc), onDismiss = close,
        ) { viewModel.edit { setRateLimit(it) } }
        SettingDialog.USER_AGENT -> TextInputDialog(
            title = stringResource(R.string.setting_user_agent), initial = s.userAgent, placeholder = "Mozilla/5.0 …",
            onDismiss = close,
        ) { viewModel.edit { setUserAgent(it) } }
        SettingDialog.EXTRA_ARGS -> TextInputDialog(
            title = stringResource(R.string.setting_extra_args), initial = s.extraArgs, placeholder = "--no-part --geo-bypass",
            supportingText = stringResource(R.string.setting_extra_args_desc), monospace = true, onDismiss = close,
        ) { viewModel.edit { setExtraArgs(it) } }
        SettingDialog.COOKIES -> CookiesDialog(viewModel, hasCookies, close)
        null -> Unit
    }
}

@Composable
private fun CookiesDialog(viewModel: SettingsViewModel, hasCookies: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = viewModel.readCookies() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_cookies)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.cookies_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("# Netscape HTTP Cookie File\n.instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\t…") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = MaterialTheme.shapes.medium,
                )
                Row {
                    TextButton(onClick = { importer.launch(arrayOf("text/*", "application/octet-stream")) }) {
                        Text(stringResource(R.string.action_import_file))
                    }
                    if (hasCookies) {
                        TextButton(onClick = { viewModel.clearCookies(); onDismiss() }) {
                            Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.saveCookies(text); onDismiss() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

// --- Rows ----------------------------------------------------------------------------------------

@Composable
private fun ValueRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// --- Labels --------------------------------------------------------------------------------------

@Composable
private fun themeLabel(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun languageLabel(language: AppLanguage) = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.PORTUGUESE_BR -> "Português (Brasil)"
}

@Composable
private fun modeLabel(mode: DefaultMode) = stringResource(if (mode == DefaultMode.VIDEO) R.string.tab_video else R.string.tab_audio)

@Composable
private fun heightLabel(height: Int) = if (height == 0) stringResource(R.string.quality_best) else "${height}p"

@Composable
private fun audioLabel(format: String) = if (format == "best") stringResource(R.string.audio_original) else format.uppercase()

@Composable
private fun locationLabel(location: SaveLocation) = stringResource(
    if (location == SaveLocation.DOWNLOADS) R.string.location_downloads else R.string.location_media
)

@Composable
private fun channelLabel(channel: UpdateChannel) = stringResource(
    if (channel == UpdateChannel.STABLE) R.string.channel_stable else R.string.channel_nightly
)
