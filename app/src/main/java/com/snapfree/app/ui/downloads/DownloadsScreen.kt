@file:OptIn(ExperimentalMaterial3Api::class)

package com.snapfree.app.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfree.app.R
import com.snapfree.app.data.DownloadEntity
import com.snapfree.app.data.DownloadStatus
import com.snapfree.app.ui.components.EmptyState
import com.snapfree.app.ui.components.SectionTitle
import com.snapfree.app.ui.components.Thumbnail
import com.snapfree.app.util.Formatters

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.nav_downloads)) },
            actions = {
                AnimatedVisibility(visible = state.history.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearFinished) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.action_clear_history))
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loaded && state.active.isEmpty() && state.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.DownloadDone,
                    title = stringResource(R.string.downloads_empty_title),
                    message = stringResource(R.string.downloads_empty_message),
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.active.isNotEmpty()) {
                item(key = "header_active") {
                    SectionTitle(stringResource(R.string.downloads_active, state.active.size), Modifier.animateItem())
                }
                items(state.active, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        modifier = Modifier.animateItem(),
                        onCancel = { viewModel.cancel(item) },
                        onRetry = {}, onRemove = {}, onDeleteFile = {},
                    )
                }
            }
            if (state.history.isNotEmpty()) {
                item(key = "header_history") {
                    SectionTitle(stringResource(R.string.downloads_history), Modifier.animateItem())
                }
                items(state.history, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        modifier = Modifier.animateItem(),
                        onCancel = {},
                        onRetry = { viewModel.retry(item) },
                        onRemove = { viewModel.remove(item) },
                        onDeleteFile = { viewModel.deleteFile(context, item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = {
            when (item.status) {
                DownloadStatus.COMPLETED -> openFile(context, item)
                DownloadStatus.FAILED -> showError = !showError
                else -> Unit
            }
        },
    ) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Thumbnail(item.thumbnailUrl, Modifier.size(width = 104.dp, height = 64.dp))
                    if (item.status == DownloadStatus.COMPLETED) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(item.formatLabel, item.source).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    StatusLine(item)
                }
                if (item.status.isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                } else {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (item.status == DownloadStatus.COMPLETED) {
                                MenuItem(stringResource(R.string.action_open), Icons.AutoMirrored.Outlined.OpenInNew) { menuOpen = false; openFile(context, item) }
                                MenuItem(stringResource(R.string.action_share), Icons.Outlined.Share) { menuOpen = false; shareFile(context, item) }
                            } else {
                                MenuItem(stringResource(R.string.action_retry), Icons.Outlined.Refresh) { menuOpen = false; onRetry() }
                            }
                            MenuItem(stringResource(R.string.action_remove_history), Icons.Outlined.RemoveCircleOutline) { menuOpen = false; onRemove() }
                            if (item.status == DownloadStatus.COMPLETED) {
                                MenuItem(stringResource(R.string.action_delete_file), Icons.Outlined.Delete) { menuOpen = false; onDeleteFile() }
                            }
                        }
                    }
                }
            }

            if (item.status.isActive) {
                Spacer(Modifier.height(10.dp))
                val progress by animateFloatAsState(item.progress, animationSpec = tween(450), label = "progress")
                if (item.status == DownloadStatus.RUNNING && item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }

            AnimatedVisibility(visible = showError && item.error != null) {
                val explanation = com.snapfree.app.ui.components.explainError(item.error, item.url)
                Column(Modifier.padding(top = 10.dp)) {
                    Text(
                        stringResource(explanation.title) + ". " + stringResource(explanation.hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        item.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(item: DownloadEntity) {
    val text = when (item.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.status_queued)
        DownloadStatus.RUNNING -> listOf(
            if (item.progress > 0f) "${(item.progress * 100).toInt()}%" else stringResource(R.string.status_starting),
            item.speed.orEmpty(),
            Formatters.eta(item.etaSeconds).let { if (it.isEmpty()) "" else stringResource(R.string.status_eta, it) },
        ).filter { it.isNotBlank() }.joinToString(" · ")
        DownloadStatus.PROCESSING -> stringResource(R.string.status_processing)
        DownloadStatus.COMPLETED -> listOf(
            Formatters.size(item.fileSize),
            item.completedAt?.let { Formatters.dateTime(it) }.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" · ")
        DownloadStatus.FAILED -> stringResource(R.string.status_failed)
        DownloadStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.status == DownloadStatus.FAILED) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp).padding(end = 2.dp),
            )
            Spacer(Modifier.size(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (item.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

private fun openFile(context: Context, item: DownloadEntity) {
    val uri = item.fileUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, item.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, item: DownloadEntity) {
    val uri = item.fileUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
