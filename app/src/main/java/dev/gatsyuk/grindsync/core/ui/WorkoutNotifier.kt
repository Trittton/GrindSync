package dev.gatsyuk.grindsync.core.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat

/**
 * Silent, ongoing notification while a workout is live (user feature):
 * a visible reminder that a session is running, tap returns to the app.
 * No notification permission -> quietly does nothing.
 */
object WorkoutNotifier {

    private const val CHANNEL_ID = "active_workout"
    private const val NOTIFICATION_ID = 1

    fun showActiveWorkout(context: Context, workoutName: String) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active workout",
                NotificationManager.IMPORTANCE_LOW, // silent, no heads-up
            ).apply { description = "Shown while a training session is in progress" },
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Workout in progress")
            .setContentText(workoutName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun hasPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
