package com.snapfree.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.snapfree.app.MainActivity
import com.snapfree.app.R
import java.util.UUID

object Notifications {
    const val CHANNEL_PROGRESS = "downloads_progress"
    const val CHANNEL_RESULT = "downloads_result"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.notification_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.notification_channel_result),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    /** Stable notification id per download, derived from its UUID. */
    fun idFor(downloadId: String): Int = downloadId.hashCode()

    fun progress(
        context: Context,
        downloadId: String,
        title: String,
        progress: Float,
        text: String?,
        indeterminate: Boolean,
    ) = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setProgress(100, (progress * 100).toInt(), indeterminate)
        .setContentIntent(openAppIntent(context))
        .addAction(
            0,
            context.getString(R.string.action_cancel),
            WorkManager.getInstance(context).createCancelPendingIntent(UUID.fromString(downloadId)),
        )
        .build()

    fun completed(context: Context, downloadId: String, title: String, uri: Uri, mimeType: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context, idFor(downloadId), view,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            context, downloadId,
            NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_completed))
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(pending),
        )
    }

    fun failed(context: Context, downloadId: String, title: String, error: String?) {
        notify(
            context, downloadId,
            NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_failed))
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n${error.orEmpty()}"))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context)),
        )
    }

    private fun notify(context: Context, downloadId: String, builder: NotificationCompat.Builder) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            // Offset the id so the result does not get removed together with the foreground notification.
            manager.notify(idFor(downloadId) + 1, builder.build())
        } catch (_: SecurityException) {
            // Notification permission revoked; nothing to do.
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
