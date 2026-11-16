package uz.kassiron.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Who is signed in on this device (a row of the synced `users` table). */
data class User(
    val uuid: String,
    val fullName: String,
    val login: String,
    val role: String,              // owner | employee
    val permissions: Set<String>,  // sales, discount, products, prices, customers, expenses, reports
) {
    val isOwner: Boolean get() = role == "owner"

    /** The website's Auth::can(): the owner may do everything. */
    fun can(permission: String): Boolean = isOwner || permission in permissions

    companion object {
        fun from(row: Row): User = User(
            uuid = row.str("uuid"),
            fullName = row.str("full_name"),
            login = row.str("login"),
            role = row.str("role"),
            permissions = runCatching {
                val arr = JSONArray(row.strOrNull("permissions_json") ?: "[]")
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }.getOrDefault(emptySet()),
        )
    }
}

/**
 * The signed-in user. Sign-in itself (password check against the synced
 * bcrypt hash) lives in the auth screens; this only remembers who it is.
 */
class Session(private val db: Db, private val kv: Kv) {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** Reads the remembered user at startup (a blocked or removed user is signed out). */
    fun restore() {
        val uuid = kv.get(Keys.CURRENT_USER) ?: return
        val row = db.queryOne("SELECT * FROM users WHERE uuid = ? AND status = 'active'", uuid)
        _user.value = row?.let(User::from)
        if (row == null) kv.set(Keys.CURRENT_USER, null)
    }

    fun signIn(userUuid: String) {
        kv.set(Keys.CURRENT_USER, userUuid)
        restore()
    }

    fun signOut() {
        kv.set(Keys.CURRENT_USER, null)
        _user.value = null
    }

    /** The signed-in user; screens behind sign-in may rely on it. */
    fun current(): User = _user.value ?: error("not signed in")

    fun can(permission: String): Boolean = _user.value?.can(permission) == true
}
