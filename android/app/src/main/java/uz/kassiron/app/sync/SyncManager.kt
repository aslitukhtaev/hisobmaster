package uz.kassiron.app.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.BuildConfig
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Kv
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Schema
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** What the UI shows about syncing. */
data class SyncState(
    val running: Boolean = false,
    val lastSyncAt: Long = 0,          // epoch millis, 0 = never
    val error: String? = null,         // last error code (network, unauthorized, shop_blocked...)
    val pendingSales: Long = 0,        // sales not on the server yet
    val pendingTotal: Long = 0,        // any change not on the server yet (debt payments, purchases, products...)
    val rejected: Long = 0,            // changes the server refused (running total)
    val blockedReason: String? = null, // device_revoked | shop_blocked | offline_too_long: the app must stop selling
)

/**
 * PUBLIC CONTRACT used by every screen — keep these signatures.
 *
 * Push then pull (see [SyncEngine]), never two runs at once. Runs:
 * WorkManager every 15 minutes with a network; a one-off ~3 s after
 * [requestSync] (debounced); every ~60 s while an activity is on screen;
 * and on demand ([syncNow], e.g. the "sync now" button).
 */
class SyncManager(private val context: Context, private val db: Db, private val kv: Kv) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _downloaded = MutableStateFlow<Int?>(null)

    /** Rows received so far while the shop is being downloaded (activation); null otherwise. */
    val downloaded: StateFlow<Int?> = _downloaded.asStateFlow()

    private val api = Api()
    private val engine = SyncEngine(db, kv, api)
    // A background sync that fails (no database any more, a bug) is logged, never a crash of the app.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "background sync failed", e) },
    )
    private val lock = Mutex()

    /** Someone asked for a sync while one was running: run once more right after. */
    @Volatile private var again = false
    @Volatile private var lastOk = false
    private var debounce: Job? = null
    private var foreground: Job? = null
    private var startedActivities = 0

    private val activated: Boolean get() = kv.get(Keys.DEVICE_SECRET) != null

    /** Called once from App.init: schedules periodic background sync. */
    fun start() {
        // Read synchronously: the first screen is chosen from this state.
        _state.value = readState()

        // WorkManager's first use is slow (it opens its own database): off the main thread.
        scope.launch {
            runCatching {
                val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(networkConstraint())
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
            }.onFailure { Log.w(TAG, "periodic sync not scheduled", it) }
        }

        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(foregroundWatcher)

        // Pending sales count follows every local write.
        scope.launch {
            db.changes.collect {
                val pending = runCatching { Outbox.pendingSales(db) }.getOrDefault(_state.value.pendingSales)
                _state.update { it.copy(pendingSales = pending) }
            }
        }
    }

    /** Drops a requested sync that hasn't started yet (tests: it must not run on the next test's database). */
    /** Re-reads the state shown on screens (after something outside a sync changed it). */
    fun refreshState() {
        _state.value = readState()
    }

    fun cancelPending() {
        debounce?.cancel()
        debounce = null
    }

    /** Stops everything this manager runs (tests: each test gets a new app, database and manager). */
    fun shutdown() {
        scope.coroutineContext[Job]?.cancelChildren()
    }

    /** Ask for a sync soon (after a sale, on app open, pull-to-refresh). Never blocks. */
    fun requestSync() {
        debounce?.cancel()
        debounce = scope.launch {
            delay(DEBOUNCE_MS)
            if (!activated) return@launch
            if (lock.isLocked) {
                again = true // the running sync picks the new changes up
                return@launch
            }
            runCatching {
                val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(networkConstraint())
                    .apply {
                        // Before Android 12 an expedited job is a foreground
                        // service (needs a notification); there a plain job is
                        // just as quick when the app is open.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        }
                    }
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.KEEP, request)
            }.onFailure {
                Log.w(TAG, "one-off sync not enqueued", it)
                syncNow()
            }
        }
    }

    /**
     * Runs a sync now (or waits for the one already running) and returns
     * whether it succeeded. Never throws: the outcome is in [state].
     */
    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        if (!activated) return@withContext false
        if (!lock.tryLock()) {
            again = true
            lock.withLock { }
            return@withContext lastOk
        }
        try {
            var ok: Boolean
            do {
                again = false
                ok = runLocked()
            } while (again && ok)
            lastOk = ok
            ok
        } finally {
            lock.unlock()
        }
    }

    private fun runLocked(): Boolean {
        _state.update { it.copy(running = true) }
        try {
            engine.push()
            engine.pull()
            succeeded()
            return true
        } catch (e: SyncException) {
            if (e.code in BLOCKING) kv.set(Keys.BLOCKED_REASON, e.code)
            kv.set(Keys.LAST_SYNC_ERROR, e.code)
            if (e.code != "network") Log.w(TAG, "sync failed: ${e.code} ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            kv.set(Keys.LAST_SYNC_ERROR, "client_error")
            return false
        } finally {
            _state.value = readState()
        }
    }

    private fun succeeded() {
        kv.set(Keys.LAST_SYNC_AT, System.currentTimeMillis().toString())
        kv.set(Keys.LAST_SYNC_ERROR, null)
        kv.set(Keys.BLOCKED_REASON, null)
        // The signed-in user may have been blocked or had permissions changed on the website.
        runCatching { App.session.restore() }
    }

    /**
     * Activates this phone for the owner's shop and downloads it.
     * Throws [SyncException] with a code (login_failed, owner_only, network...).
     */
    suspend fun activate(login: String, password: String, deviceName: String) {
        activateWith(JSONObject().put("login", login.trim()).put("password", password), deviceName)
    }

    /**
     * Activates with a one-time 6-digit code the owner gets on the website
     * (Sozlamalar → Kompyuterlar → "Telefonni ulash") — for owners who sign
     * in with Google/Telegram and have no password. Same errors as [activate]
     * plus code_invalid.
     */
    suspend fun activateWithCode(code: String, deviceName: String) {
        activateWith(JSONObject().put("pair_code", code.filter { it.isDigit() }), deviceName)
    }

    private suspend fun activateWith(credentials: JSONObject, deviceName: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            _state.update { it.copy(running = true) }
            try {
                // A fresh identity for every attempt (the server refuses a uuid it has seen).
                val deviceUuid = Ids.new()
                credentials
                    .put("device_uuid", deviceUuid)
                    .put("fingerprint", fingerprint())
                    .put("name", deviceName.trim().ifEmpty { defaultDeviceName() })
                    .put("app_version", BuildConfig.VERSION_NAME)

                val response = api.post("/api/device/activate", credentials, auth = null)
                val device = response.optJSONObject("device")
                val secret = response.optString("secret")
                if (device == null || secret.isBlank()) throw SyncException("server_error", "no credentials")

                db.write {
                    wipe()
                    kv.set(Keys.DEVICE_UUID, device.optString("uuid").ifBlank { deviceUuid })
                    kv.set(Keys.DEVICE_CODE, device.optString("code"))
                    kv.set(Keys.DEVICE_SECRET, secret)
                    kv.set(Keys.RECEIPT_SEQ, "0")
                    kv.set(Keys.PUSHED_UPTO, "0")
                    engine.storeShopState(response)
                    engine.storeLicense(response)
                }
                runCatching { App.session.signOut() }
                download()
            } finally {
                _downloaded.value = null
                _state.value = readState()
            }
        }
    }

    /**
     * Continues the first download after it was interrupted (activation went
     * through but the connection dropped). Throws [SyncException].
     */
    suspend fun resumeDownload() = withContext(Dispatchers.IO) {
        lock.withLock {
            _state.update { it.copy(running = true) }
            try {
                download()
            } finally {
                _downloaded.value = null
                _state.value = readState()
            }
        }
    }

    private fun download() {
        _downloaded.value = 0
        try {
            engine.pull { _downloaded.value = it }
        } catch (e: SyncException) {
            kv.set(Keys.LAST_SYNC_ERROR, e.code)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            kv.set(Keys.LAST_SYNC_ERROR, "client_error")
            throw SyncException("client_error", e.message)
        }
        succeeded()
        lastOk = true
    }

    /** True when the first download finished (the shop's data is all here). */
    val downloadComplete: Boolean get() = activated && kv.get(Keys.LAST_SYNC_AT) != null

    /** Forgets the shop on this phone (wipes local data). */
    suspend fun deactivate() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                db.write { wipe() }
                runCatching { App.session.signOut() }
                runCatching { WorkManager.getInstance(context).cancelUniqueWork(ONE_OFF_WORK) }
                lastOk = false
                _state.value = readState()
            }
        }
    }

    /** Every table of shop data, the outbox and the device state (but the phone's own preferences). */
    private fun Db.wipe() {
        for (table in Schema.TABLES) exec("DELETE FROM ${table.name}")
        exec("DELETE FROM sync_outbox")
        exec(
            "DELETE FROM kv WHERE key NOT IN (?, ?, ?, ?, ?)",
            Keys.LANG, Keys.PRINTER_ADDRESS, Keys.PRINTER_WIDTH, "theme", "printer_charset",
        )
    }

    // ------------------------------------------------------------ state

    private fun readState(): SyncState = runCatching {
        SyncState(
            running = false,
            lastSyncAt = kv.long(Keys.LAST_SYNC_AT),
            error = kv.get(Keys.LAST_SYNC_ERROR),
            pendingSales = Outbox.pendingSales(db),
            pendingTotal = Outbox.pendingTotal(db),
            rejected = kv.long(Keys.SYNC_REJECTED),
            blockedReason = if (!activated) null else kv.get(Keys.BLOCKED_REASON) ?: offlineReason(),
        )
    }.getOrElse {
        Log.e(TAG, "sync state unreadable", it)
        SyncState()
    }

    /**
     * Like the desktop app: a phone that hasn't reached the server for the
     * shop's offline_days stops selling until it syncs (a switched-off phone
     * or a blocked shop can't keep working forever).
     */
    private fun offlineReason(): String? {
        val last = kv.long(Keys.LAST_SYNC_AT)
        if (last <= 0) return null
        val days = kv.shop().optInt("offline_days", 7).coerceAtLeast(1)
        val now = System.currentTimeMillis()
        val expiredByClock = now - last > days * DAY_MS
        val validUntil = licenseValidUntil()
        val serverNow = now / 1000 + kv.long(Keys.SERVER_TIME_OFFSET)
        val expiredByLicense = validUntil > 0 && serverNow > validUntil
        return if (expiredByClock || expiredByLicense) OFFLINE_TOO_LONG else null
    }

    /** valid_until (server unix time) of the license token, 0 when unknown. */
    private fun licenseValidUntil(): Long = runCatching {
        val token = kv.get(Keys.LICENSE) ?: return 0
        val payload = token.substringBefore('.')
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
        JSONObject(json).optLong("valid_until", 0)
    }.getOrDefault(0)

    private fun refreshOffline() {
        val reason = runCatching { if (!activated) null else kv.get(Keys.BLOCKED_REASON) ?: offlineReason() }.getOrNull()
        _state.update { it.copy(blockedReason = reason) }
    }

    // ------------------------------------------------------------ helpers

    /** sha256 hex of ANDROID_ID + package name: the same phone gives the same value after a reinstall. */
    private fun fingerprint(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest((androidId + context.packageName).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun networkConstraint() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sync every minute while the app is on screen. */
    private val foregroundWatcher = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            if (startedActivities == 1 && foreground?.isActive != true) {
                foreground = scope.launch {
                    delay(FIRST_FOREGROUND_DELAY_MS)
                    while (isActive) {
                        refreshOffline()
                        syncNow()
                        delay(FOREGROUND_INTERVAL_MS)
                    }
                }
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0) {
                foreground?.cancel()
                foreground = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    companion object {
        private const val TAG = "KassirSync"
        private const val PERIODIC_WORK = "kassiron-sync-periodic"
        private const val ONE_OFF_WORK = "kassiron-sync-now"
        private const val DEBOUNCE_MS = 3_000L
        private const val FIRST_FOREGROUND_DELAY_MS = 1_500L
        private const val FOREGROUND_INTERVAL_MS = 60_000L
        private const val DAY_MS = 86_400_000L
        const val OFFLINE_TOO_LONG = "offline_too_long"
        private val BLOCKING = setOf("device_revoked", "shop_blocked")

        /** The phone's model, e.g. "Samsung SM-A515F": the default device name. */
        fun defaultDeviceName(): String {
            val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = Build.MODEL.orEmpty()
            return (if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model").trim().take(100)
        }
    }
}

class SyncException(val code: String, message: String? = null) : Exception(message ?: code)
