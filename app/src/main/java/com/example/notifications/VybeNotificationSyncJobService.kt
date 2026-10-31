package com.example.notifications

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.repository.VybeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Native Android JobService for periodic background notification sync.
 *
 * Runs automatically under Android's OS JobScheduler when the app is NOT OPEN,
 * ensuring unread notifications, mentions, LINKUP requests, and direct messages
 * are surfaced in the Android status bar even if Firebase Cloud Messaging is delayed.
 */
class VybeNotificationSyncJobService : JobService() {

  private val jobScope = CoroutineScope(Dispatchers.IO + Job())

  companion object {
    private const val TAG = "VybeSyncJob"
    const val JOB_ID_PERIODIC = 94821
    const val JOB_ID_IMMEDIATE = 94822

    fun schedule(context: Context) {
      try {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val component = ComponentName(context, VybeNotificationSyncJobService::class.java)

        val periodicBuilder = JobInfo.Builder(JOB_ID_PERIODIC, component)
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setPersisted(true)

        // Periodic minimum interval is 15 minutes on Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          periodicBuilder.setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L)
        } else {
          periodicBuilder.setPeriodic(15 * 60 * 1000L)
        }

        val result = scheduler.schedule(periodicBuilder.build())
        Log.i(TAG, "Scheduled periodic background notification sync job (result=$result)")
      } catch (e: Throwable) {
        Log.w(TAG, "Periodic sync job schedule notice: ${e.message}")
      }
    }

    fun scheduleQuickCheck(context: Context, delayMs: Long = 10_000L) {
      try {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val component = ComponentName(context, VybeNotificationSyncJobService::class.java)

        val oneShot = JobInfo.Builder(JOB_ID_IMMEDIATE, component)
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setMinimumLatency(delayMs)
          .setOverrideDeadline(delayMs + 30_000L)
          .build()

        scheduler.schedule(oneShot)
        Log.d(TAG, "Scheduled immediate background sync check in ${delayMs / 1000}s")
      } catch (e: Throwable) {
        Log.w(TAG, "Quick check sync schedule notice: ${e.message}")
      }
    }
  }

  override fun onStartJob(params: JobParameters?): Boolean {
    Log.d(TAG, "Background sync job started: id=${params?.jobId}")
    jobScope.launch {
      try {
        VybeNotificationHelper.initNotificationChannels(applicationContext)
        VybeRepository.get().syncBackgroundNotifications(applicationContext)
      } catch (e: Throwable) {
        Log.w(TAG, "Background sync job execution notice: ${e.message}")
      } finally {
        jobFinished(params, false)
      }
    }
    return true
  }

  override fun onStopJob(params: JobParameters?): Boolean {
    Log.d(TAG, "Background sync job cancelled: id=${params?.jobId}")
    jobScope.cancel()
    return false
  }
}
