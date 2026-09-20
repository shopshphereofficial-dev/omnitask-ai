package com.omnitask.ai.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.omnitask.ai.data.Schedule
import com.omnitask.ai.data.Store
import java.util.Calendar

/**
 * Arms and manages scheduled tasks using AlarmManager. Each schedule re-arms
 * itself after firing, which keeps it reliable across Doze mode.
 */
object Scheduler {

    private fun pending(ctx: Context, id: String): PendingIntent {
        val intent = Intent(ctx, ScheduleReceiver::class.java).putExtra("schedule_id", id)
        return PendingIntent.getBroadcast(
            ctx,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun armNext(ctx: Context, s: Schedule) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, s.id)
        val trigger = nextTrigger(s)
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(ctx: Context, id: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, id))
    }

    fun rearmAll(ctx: Context) {
        Store.loadSchedules(ctx).forEach { armNext(ctx, it) }
    }

    private fun nextTrigger(s: Schedule): Long {
        if (s.intervalMinutes != null && s.intervalMinutes > 0) {
            return System.currentTimeMillis() + s.intervalMinutes * 60_000L
        }
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.set(Calendar.HOUR_OF_DAY, s.hour ?: 7)
        target.set(Calendar.MINUTE, s.minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        val allowed = s.days
        if (allowed != null && allowed.isNotEmpty()) {
            var i = 0
            while (i < 8) {
                val dow = target.get(Calendar.DAY_OF_WEEK)
                if (target.timeInMillis > now.timeInMillis && allowed.contains(dow)) {
                    return target.timeInMillis
                }
                target.add(Calendar.DAY_OF_YEAR, 1)
                i++
            }
        }
        while (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
