package uz.kassiron.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.mindrot.jbcrypt.BCrypt
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.resetTo

/** The message for an activation error code (SyncException.code). */
@Composable
fun activationErrorText(code: String): String = when (code) {
    "login_failed" -> stringResource(R.string.auth_err_login_failed)
    "login_locked" -> stringResource(R.string.auth_err_login_locked)
    "owner_only" -> stringResource(R.string.auth_err_owner_only)
    "code_invalid" -> stringResource(R.string.auth_err_code_invalid)
    "network" -> stringResource(R.string.auth_err_network)
    "shop_blocked" -> stringResource(R.string.auth_err_shop_blocked)
    "invalid_request" -> stringResource(R.string.auth_err_invalid_request)
    "fill" -> stringResource(R.string.auth_fill_fields)
    "code_short" -> stringResource(R.string.auth_code_short)
    else -> stringResource(R.string.auth_err_generic, code)
}

/** The message for a background sync error code (SyncState.error / blockedReason). */
@Composable
fun syncErrorText(code: String): String = when (code) {
    "network" -> stringResource(R.string.sync_err_network)
    "unauthorized" -> stringResource(R.string.sync_err_unauthorized)
    "device_revoked" -> stringResource(R.string.sync_err_device_revoked)
    "shop_blocked" -> stringResource(R.string.sync_err_shop_blocked)
    "server_error" -> stringResource(R.string.sync_err_server_error)
    "client_error" -> stringResource(R.string.sync_err_client_error)
    "offline_too_long" -> stringResource(R.string.sync_err_offline_too_long)
    else -> stringResource(R.string.sync_err_other, code)
}

/** A round avatar with the person's initials, colored by their name. */
@Composable
fun InitialsAvatar(name: String, size: Dp = 56.dp) {
    val palette = listOf(
        Color(0xFF3A7300), Color(0xFF1D4ED8), Color(0xFFB45309), Color(0xFF7C3AED),
        Color(0xFF0F766E), Color(0xFFBE123C), Color(0xFF4D7C0F), Color(0xFF0369A1),
    )
    val color = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/**
 * PHP's password_hash() (bcrypt "$2y$") checked with jBCrypt, which only
 * knows the "$2a$" prefix — the algorithm is the same.
 */
fun checkPassword(password: String, hash: String?): Boolean {
    if (hash.isNullOrBlank() || password.isEmpty()) return false
    val normalized = if (hash.startsWith("\$2y\$") || hash.startsWith("\$2b\$")) "\$2a\$" + hash.substring(4) else hash
    return runCatching { BCrypt.checkpw(password, normalized) }.getOrDefault(false)
}

/** Where a signed-in user starts: the till if they sell, otherwise the dashboard. */
fun NavHostController.goHome() {
    val user = App.session.user.value
    resetTo(
        when {
            user == null -> Routes.LOGIN
            user.can("sales") -> Routes.TILL
            else -> Routes.HOME
        },
    )
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
