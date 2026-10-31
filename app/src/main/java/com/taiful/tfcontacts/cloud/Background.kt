package com.taiful.tfcontacts.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.taiful.tfcontacts.MainActivity
import com.taiful.tfcontacts.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Checks the GitHub Releases page for a newer TF Contacts. */
object Updates {
    const val DOWNLOAD_URL = "https://github.com/taiful666/TF-Contacts/releases/download/latest/TF-Contacts.apk"
    private const val API = "https://api.github.com/repos/taiful666/TF-Contacts/releases/tags/latest"

    fun installedBuild(c: Context): Long = try {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Exception) {
        0
    }

    /** Returns the newest build number on GitHub, or null if it can't be checked. */
    fun latestBuild(): Long? = try {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        conn.disconnect()
        val name = JSONObject(text).optString("name")
        Regex("build (\\d+)").find(name)?.groupValues?.get(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    fun openDownload(c: Context) {
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

object Notify {
    private const val CH_UPDATE = "tf_updates"
    private const val CH_BACKUP = "tf_backup"

    private fun channel(c: Context, id: String, name: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun update(c: Context, build: Long) {
        try {
            channel(c, CH_UPDATE, "App updates")
            val pi = PendingIntent.getActivity(
                c, 10, Intent(Intent.ACTION_VIEW, Uri.parse(Updates.DOWNLOAD_URL)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = android.app.Notification.Builder(c, CH_UPDATE)
                .setSmallIcon(R.drawable.ic_call_small)
                .setContentTitle("TF Contacts update available")
                .setContentText("New version (build $build) is ready. Tap to download and install.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            c.getSystemService(NotificationManager::class.java).notify(5501, n)
        } catch (_: Exception) {
        }
    }

    fun reconnect(c: Context) {
        try {
            channel(c, CH_BACKUP, "Google Drive backup")
            val pi = PendingIntent.getActivity(
                c, 11, Intent(c, MainActivity::class.java).putExtra("openTools", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = android.app.Notification.Builder(c, CH_BACKUP)
                .setSmallIcon(R.drawable.ic_call_small)
                .setContentTitle("Google Drive backup paused")
                .setContentText("Tap to connect your Gmail again so backups can continue.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            c.getSystemService(NotificationManager::class.java).notify(5502, n)
        } catch (_: Exception) {
        }
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val latest = Updates.latestBuild() ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("tf", Context.MODE_PRIVATE)
        if (latest > Updates.installedBuild(applicationContext) && prefs.getLong("notifiedBuild", 0) < latest) {
            Notify.update(applicationContext, latest)
            prefs.edit().putLong("notifiedBuild", latest).apply()
        }
        return Result.success()
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (DriveBackup.email(applicationContext) == null) return Result.success()
        return try {
            DriveBackup.backup(applicationContext)
            Result.success()
        } catch (e: DriveBackup.NeedsSignIn) {
            Notify.reconnect(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object Background {
    fun schedule(c: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniquePeriodicWork(
            "tf_update_check6", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS).setConstraints(net).build()
        )
        wm.enqueueUniquePeriodicWork(
            "tf_drive_backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).setConstraints(net).build()
        )
    }
}
