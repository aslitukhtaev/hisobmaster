package uz.kassiron.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.mindrot.jbcrypt.BCrypt
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.resetTo
import uz.kassiron.app.ui.theme.K

/** The message for an activation error code (SyncException.code or a local check). */
@Composable
fun activationErrorText(code: String): String = when (code) {
    "login_failed" -> stringResource(R.string.auth_err_login_failed)
    "login_locked" -> stringResource(R.string.auth_err_login_locked)
    "owner_only" -> stringResource(R.string.auth_err_owner_only)
    "code_invalid" -> stringResource(R.string.auth_err_code_invalid)
    "network" -> stringResource(R.string.auth_err_network)
    "shop_blocked" -> stringResource(R.string.auth_err_shop_blocked)
    "invalid_request" -> stringResource(R.string.auth_err_invalid_request)
    "fill_login" -> stringResource(R.string.auth_err_fill_login)
    "fill_password" -> stringResource(R.string.auth_err_fill_password)
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

/** The KassirON word mark: the dark logo on the light theme, the light one on the dark theme. */
@Composable
fun KassirLogo(height: Dp = 50.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (K.c.dark) R.drawable.logo_light else R.drawable.logo_dark),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.height(height),
    )
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
