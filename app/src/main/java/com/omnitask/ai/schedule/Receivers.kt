package com.omnitask.ai.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.omnitask.ai.R
import com.omnitask.ai.actions.ActionExecutor
import com.omnitask.ai.actions.ActionParser
import com.omnitask.ai.data.Schedule
import com.omnitask.ai.data.Store

/** Fires when a scheduled task is due: runs the actions and shows the results. */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("schedule_id") ?: return
        val schedule: Schedule = Store.loadSchedules(ctx).firstOrNull { it.id == id } ?: return
        val actions = ActionParser.fromJson(schedule.actionsJson)
        val results = if (actions != null) ActionExecutor.executeAll(ctx, actions) else emptyList()

        // Re-arm for the next occurrence
        Scheduler.armNext(ctx, schedule)

        showNotification(ctx, schedule, results)
    }

    private fun showNotification(ctx: Context, schedule: Schedule, results: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Scheduled tasks", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val text = if (results.isEmpty()) "Task ran" else results.joinToString("\n")
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("OmniTask: ${schedule.label}")
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(schedule.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // notifications not granted - execution already happened, ignore
        }
    }

    companion object {
        private const val CHANNEL_ID = "scheduled_tasks"
    }
}

/** Re-arms all schedules after the phone restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.rearmAll(ctx)
        }
    }
}
