package br.com.pilovieira.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

object UsageStatsHelper {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageAccess(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /** Total foreground time per package, in milliseconds, within [sinceMillis, untilMillis]. */
    fun getUsageTimeByPackage(
        context: Context,
        sinceMillis: Long,
        untilMillis: Long = System.currentTimeMillis()
    ): Map<String, Long> {
        if (!hasUsageAccess(context)) return emptyMap()
        return runCatching {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usageStatsManager
                .queryAndAggregateUsageStats(sinceMillis, untilMillis)
                .mapValues { (_, stats) -> stats.totalTimeInForeground }
        }.getOrDefault(emptyMap())
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
