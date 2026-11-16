package uz.kassiron.app.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Kv

/** What the UI shows about syncing. */
data class SyncState(
    val running: Boolean = false,
    val lastSyncAt: Long = 0,          // epoch millis, 0 = never
    val error: String? = null,         // last error code (network, unauthorized, shop_blocked...)
    val pendingSales: Long = 0,        // sales not on the server yet
    val blockedReason: String? = null, // device_revoked | shop_blocked: the app must stop selling
)

/**
 * PUBLIC CONTRACT used by every screen — keep these signatures.
 * The implementation is owned by the sync work (see ARCHITECTURE.md).
 */
class SyncManager(private val context: Context, private val db: Db, private val kv: Kv) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Called once from App.init: schedules periodic background sync. */
    fun start() {}

    /** Ask for a sync soon (after a sale, on app open, pull-to-refresh). Never blocks. */
    fun requestSync() {}

    /**
     * Activates this phone for the owner's shop and downloads it.
     * Throws [SyncException] with a code (login_failed, owner_only, network...).
     */
    suspend fun activate(login: String, password: String, deviceName: String) {
        throw SyncException("not_implemented")
    }

    /**
     * Activates with a one-time 6-digit code the owner gets on the website
     * (Sozlamalar → Kompyuterlar → "Telefonni ulash") — for owners who sign
     * in with Google/Telegram and have no password. Same errors as [activate]
     * plus code_invalid.
     */
    suspend fun activateWithCode(code: String, deviceName: String) {
        throw SyncException("not_implemented")
    }

    /** Forgets the shop on this phone (wipes local data). */
    suspend fun deactivate() {}
}

class SyncException(val code: String, message: String? = null) : Exception(message ?: code)
