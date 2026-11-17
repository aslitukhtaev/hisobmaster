package uz.kassiron.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import uz.kassiron.app.App

/**
 * Background sync run by WorkManager: every 15 minutes with a network, and
 * once soon after App.sync.requestSync(). The app process (and App.init) is
 * always up when this runs, so it just asks the one SyncManager to run.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        App.sync.syncNow()
        // Offline or a server error: the next periodic run tries again; no
        // WorkManager retry storm (the outbox keeps everything safe meanwhile).
        return Result.success()
    }
}
