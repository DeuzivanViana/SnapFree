@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.snapfree.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.snapfree.app.ui.components.Reveal
import com.snapfree.app.ui.components.pressScale
import com.snapfree.app.util.LinkCheck
import com.snapfree.app.util.LinkDetector
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfree.app.R
import com.snapfree.app.data.LoginSite
import com.snapfree.app.ui.components.explainError
import com.snapfree.app.ui.login.LoginActivity
import com.snapfree.app.engine.EngineState
import com.snapfree.app.engine.FormatCatalog
import com.snapfree.app.engine.FormatItem
import com.snapfree.app.engine.MediaInfo
import com.snapfree.app.ui.components.MetaPill
import com.snapfree.app.ui.components.SelectableRow
import com.snapfree.app.ui.components.ShimmerBox
import com.snapfree.app.ui.components.Thumbnail
import com.snapfree.app.util.Formatters

@Composable
fun HomeScreen(viewModel: HomeViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val engine by viewModel.engineState.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    // After a successful in-app login, analyze the same link again with the new cookies.
    val loginLauncher = rememberLauncherForActivityResult(LoginActivity.Contract()) { ok -> if (ok) viewModel.analyze(force = true) }
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        EngineBanner(engine)

        // --- Link input ------------------------------------------------------------------------
        // No analyze button: the view model starts by itself once the text is a complete link.
        val complete = state.link is LinkCheck.Complete
        val borderColor by animateColorAsState(
            if (complete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
            tween(300),
            label = "fieldBorder",
        )
        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.home_url_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            trailingIcon = {
                AnimatedContent(
                    targetState = state.url.isEmpty(),
                    transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.6f)) },
                    label = "trailing",
                ) { empty ->
                    if (empty) {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { text ->
                                viewModel.onPaste(text)
                                if (LinkDetector.check(text) is LinkCheck.Complete) focus.clearFocus()
                            }
                        }) { Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.action_paste)) }
                    } else {
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); viewModel.analyze() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = borderColor,
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        LinkStatus(
            link = state.link,
            phase = state.phase,
            onAnalyzeAnyway = { focus.clearFocus(); viewModel.analyze() },
        )

        // Hide the keyboard once a result arrives, so the download button is visible right away.
        LaunchedEffect(state.phase is HomePhase.Loaded) {
            if (state.phase is HomePhase.Loaded) focus.clearFocus()
        }

        Spacer(Modifier.height(16.dp))

        // --- Result area -----------------------------------------------------------------------
        AnimatedContent(
            targetState = state.phase,
            // A new result for another link animates in again; option changes on the same one do not.
            contentKey = { phase -> if (phase is HomePhase.Loaded) phase.info.url else phase::class },
            transitionSpec = {
                (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) { it / 10 })
                    .togetherWith(fadeOut(tween(160)) + scaleOut(targetScale = 0.98f, animationSpec = tween(160)))
                    .using(SizeTransform(clip = false))
            },
            label = "phase",
        ) { phase ->
            when (phase) {
                HomePhase.Idle -> IdleHint()
                HomePhase.Loading -> LoadingCard(updating)
                is HomePhase.Error -> ErrorCard(
                    message = phase.message,
                    url = state.url,
                    loggedIn = loggedIn,
                    onRetry = { viewModel.analyze(force = true) },
                    onLogin = { loginLauncher.launch(it) },
                )
                is HomePhase.Loaded -> LoadedContent(
                    info = phase.info,
                    selection = state.selection,
                    onSelect = viewModel::select,
                    onDownload = viewModel::download,
                )
            }
        }
    }
}

/** One line under the field that tells, as the user types or pastes, what SnapFree understood. */
@Composable
private fun LinkStatus(link: LinkCheck, phase: HomePhase, onAnalyzeAnyway: () -> Unit) {
    val status: LinkStatusUi? = when {
        link is LinkCheck.Empty -> null
        link is LinkCheck.Incomplete -> if (link.looksLikeLink || link.site != null) LinkStatusUi.Incomplete else LinkStatusUi.NotUrl
        link is LinkCheck.Complete -> when (phase) {
            HomePhase.Loading -> LinkStatusUi.Analyzing(link.label)
            is HomePhase.Loaded -> LinkStatusUi.Ready(link.label)
            is HomePhase.Error -> LinkStatusUi.Failed(link.label)
            HomePhase.Idle -> LinkStatusUi.Detected(link.label)
        }
        else -> null
    }
    AnimatedVisibility(
        visible = status != null,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
    ) {
        // Remember the last status so the exit animation still has something to show.
        var shown by remember { mutableStateOf(status ?: LinkStatusUi.Incomplete) }
        if (status != null) shown = status
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 })
            },
            label = "linkStatus",
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        ) { current ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    when (current) {
                        is LinkStatusUi.Analyzing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        is LinkStatusUi.Detected -> PulsingDot()
                        is LinkStatusUi.Ready -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        is LinkStatusUi.Failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        LinkStatusUi.Incomplete, LinkStatusUi.NotUrl ->
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    when (current) {
                        is LinkStatusUi.Analyzing -> stringResource(R.string.link_analyzing, current.label)
                        is LinkStatusUi.Detected -> stringResource(R.string.link_detected, current.label)
                        is LinkStatusUi.Ready -> stringResource(R.string.link_ready, current.label)
                        is LinkStatusUi.Failed -> stringResource(R.string.link_failed, current.label)
                        LinkStatusUi.Incomplete -> stringResource(R.string.link_incomplete)
                        LinkStatusUi.NotUrl -> stringResource(R.string.link_not_url)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current is LinkStatusUi.Ready || current is LinkStatusUi.Analyzing) MaterialTheme.colorScheme.onSurface else muted,
                    modifier = Modifier.weight(1f),
                )
                if (current == LinkStatusUi.Incomplete) {
                    TextButton(onClick = onAnalyzeAnyway, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(stringResource(R.string.action_analyze_anyway), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private sealed interface LinkStatusUi {
    data object NotUrl : LinkStatusUi
    data object Incomplete : LinkStatusUi
    data class Detected(val label: String) : LinkStatusUi
    data class Analyzing(val label: String) : LinkStatusUi
    data class Ready(val label: String) : LinkStatusUi
    data class Failed(val label: String) : LinkStatusUi
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale",
    )
    Box(
        Modifier
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface)
    )
}

@Composable
private fun EngineBanner(engine: EngineState) {
    AnimatedVisibility(
        visible = engine !is EngineState.Ready,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (engine is EngineState.Failed) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    if (engine is EngineState.Failed) stringResource(R.string.engine_failed, engine.message)
                    else stringResource(R.string.engine_initializing),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun IdleHint() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Reveal(0) { Text(
            stringResource(R.string.home_supported),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("YouTube", "Instagram", "TikTok", "X / Twitter", "Facebook", "Vimeo", "Reddit", "+1000").forEachIndexed { i, name ->
                Reveal(40 + i * 35) { MetaPill(name) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Reveal(340) { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.home_tip_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_tip_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } }
    }
}

@Composable
private fun LoadingCard(updating: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(visible = updating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.home_updating_engine),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = MaterialTheme.shapes.large,
        )
        ShimmerBox(Modifier.fillMaxWidth(0.85f).height(22.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(16.dp))
        Spacer(Modifier.height(4.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(60.dp), shape = MaterialTheme.shapes.large)
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(40.dp), shape = MaterialTheme.shapes.medium)
        ShimmerBox(Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium)
    }
}

@Composable
private fun ErrorCard(
    message: String,
    url: String,
    loggedIn: Set<LoginSite>,
    onRetry: () -> Unit,
    onLogin: (LoginSite) -> Unit,
) {
    val explanation = explainError(message, url)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(explanation.title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(explanation.hint), style = MaterialTheme.typography.bodyMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                explanation.loginSite?.let { site ->
                    Button(onClick = { onLogin(site) }, shape = MaterialTheme.shapes.medium) {
                        val name = LoginActivity.siteName(site)
                        Text(
                            if (site in loggedIn) stringResource(R.string.action_login_again, name)
                            else stringResource(R.string.action_login, name)
                        )
                    }
                }
                OutlinedButton(onClick = onRetry, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    info: MediaInfo,
    selection: Selection,
    onSelect: ((Selection) -> Selection) -> Unit,
    onDownload: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Media card
        Reveal(0) {
            Box {
                Thumbnail(
                    url = info.thumbnailUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = MaterialTheme.shapes.large,
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    info.source?.let { MetaPill(it) }
                    Formatters.duration(info.durationSeconds).takeIf { it.isNotEmpty() }?.let { MetaPill(it) }
                }
            }
        }
        Reveal(70) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                info.uploader?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Download button right under the result; its label follows the options chosen below.
        Reveal(140) {
            DownloadButton(
                label = formatLabel(info, selection),
                enabled = selection.tab != OptionTab.FORMATS || selection.customFormatId != null,
                onDownload = onDownload,
            )
        }

        Reveal(210) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.label_download_options),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OptionTabs(info, selection, onSelect)
            }
        }

        Reveal(280) {
            AnimatedContent(
                targetState = selection.tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { if (forward) -it / 6 else it / 6 })
                },
                label = "tab",
                modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            ) { tab ->
                when (tab) {
                    OptionTab.VIDEO -> VideoOptions(info, selection, onSelect)
                    OptionTab.AUDIO -> AudioOptions(selection, onSelect)
                    OptionTab.FORMATS -> FormatOptions(info.formats, selection, onSelect)
                }
            }
        }
    }
}

@Composable
private fun OptionTabs(info: MediaInfo, selection: Selection, onSelect: ((Selection) -> Selection) -> Unit) {
    val tabs = buildList {
        if (info.hasVideo) add(OptionTab.VIDEO)
        add(OptionTab.AUDIO)
        if (info.formats.isNotEmpty()) add(OptionTab.FORMATS)
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selection.tab == tab,
                onClick = { onSelect { it.copy(tab = tab) } },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = selection.tab == tab) {
                        Icon(
                            when (tab) {
                                OptionTab.VIDEO -> Icons.Outlined.Movie
                                OptionTab.AUDIO -> Icons.Outlined.AudioFile
                                OptionTab.FORMATS -> Icons.Outlined.Tune
                            },
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
            ) {
                Text(
                    stringResource(
                        when (tab) {
                            OptionTab.VIDEO -> R.string.tab_video
                            OptionTab.AUDIO -> R.string.tab_audio
                            OptionTab.FORMATS -> R.string.tab_formats
                        }
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Primary action. Springs down when pressed, the format label slides when the choice changes,
 * and after a tap it briefly turns into a check to confirm the download was queued.
 */
@Composable
private fun DownloadButton(label: String, enabled: Boolean, onDownload: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var queued by remember { mutableStateOf(false) }
    LaunchedEffect(queued) {
        if (queued) {
            delay(1800)
            queued = false
        }
    }
    Button(
        onClick = {
            if (!queued) {
                onDownload(label)
                queued = true
            }
        },
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .pressScale(interaction),
        shape = MaterialTheme.shapes.large,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        AnimatedContent(
            targetState = queued,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.55f))) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.8f))
            },
            label = "downloadState",
        ) { done ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.download_added), style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.action_download), style = MaterialTheme.typography.titleMedium)
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInVertically(tween(220)) { it }) togetherWith
                                    (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it })
                            },
                            label = "downloadLabel",
                        ) { text -> Text(text, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatLabel(info: MediaInfo, s: Selection): String = when (s.tab) {
    OptionTab.VIDEO -> "${s.container.uppercase()} · " +
        if (s.height == 0) stringResource(R.string.quality_best) else "${s.height}p"
    OptionTab.AUDIO -> s.audioFormat.uppercase().let {
        if (s.audioFormat == "best") stringResource(R.string.audio_original)
        else if (FormatCatalog.usesBitrate(s.audioFormat)) "$it · ${s.audioBitrate} kbps" else it
    }
    OptionTab.FORMATS -> info.formats.firstOrNull { it.formatId == s.customFormatId }
        ?.let { "${it.ext.uppercase()} · ${describeFormat(it)}" } ?: ""
}

@Composable
private fun describeFormat(f: FormatItem): String = when {
    f.isAudioOnly -> stringResource(R.string.format_audio_only) + if (f.bitrate > 0) " ${f.bitrate}k" else ""
    f.height > 0 -> "${f.height}p" + if (f.fps > 30) "${f.fps}" else ""
    else -> f.note ?: f.formatId
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ChoiceChips(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(label(option), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun VideoOptions(info: MediaInfo, s: Selection, onSelect: ((Selection) -> Selection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionLabel(stringResource(R.string.label_container))
        ChoiceChips(FormatCatalog.videoContainers, s.container, { it.uppercase() }) { c -> onSelect { it.copy(container = c) } }
        Spacer(Modifier.height(8.dp))
        OptionLabel(stringResource(R.string.label_quality))
        SelectableRow(selected = s.height == 0, onClick = { onSelect { it.copy(height = 0) } }) {
            Text(stringResource(R.string.quality_best), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            info.videoQualities.firstOrNull()?.let { Text("${it.height}p", style = MaterialTheme.typography.bodySmall) }
        }
        info.videoQualities.forEach { q ->
            SelectableRow(selected = s.height == q.height, onClick = { onSelect { it.copy(height = q.height) } }) {
                Text(
                    "${q.height}p" + if (q.fps > 30) " ${q.fps}fps" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(qualityTag(q.height), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                Text(
                    Formatters.size(q.estimatedSize).let { if (it.isEmpty()) "" else "~$it" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun qualityTag(height: Int): String = when {
    height >= 4320 -> "8K"
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    else -> "SD"
}

@Composable
private fun AudioOptions(s: Selection, onSelect: ((Selection) -> Selection) -> Unit) {
    val original = stringResource(R.string.audio_original)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionLabel(stringResource(R.string.label_audio_format))
        ChoiceChips(FormatCatalog.audioFormats, s.audioFormat, { if (it == "best") original else it.uppercase() }) { f ->
            onSelect { it.copy(audioFormat = f) }
        }
        AnimatedVisibility(visible = FormatCatalog.usesBitrate(s.audioFormat)) {
            Column {
                Spacer(Modifier.height(16.dp))
                OptionLabel(stringResource(R.string.label_bitrate))
                ChoiceChips(FormatCatalog.audioBitrates.map { it.toString() }, s.audioBitrate.toString(), { "$it kbps" }) { b ->
                    onSelect { it.copy(audioBitrate = b.toInt()) }
                }
            }
        }
    }
}

@Composable
private fun FormatOptions(formats: List<FormatItem>, s: Selection, onSelect: ((Selection) -> Selection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionLabel(stringResource(R.string.label_all_formats))
        formats.forEach { f ->
            SelectableRow(
                selected = s.customFormatId == f.formatId,
                onClick = { onSelect { it.copy(customFormatId = f.formatId) } },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${f.ext.uppercase()} · ${describeFormat(f)}", style = MaterialTheme.typography.titleSmall)
                    val codecs = listOfNotNull(
                        f.vcodec?.takeIf { it != "none" },
                        f.acodec?.takeIf { it != "none" },
                    ).joinToString(" + ")
                    val detail = listOf(
                        "id ${f.formatId}",
                        codecs,
                        if (f.isVideoOnly) stringResource(R.string.format_video_only) else "",
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(Formatters.size(f.size), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
