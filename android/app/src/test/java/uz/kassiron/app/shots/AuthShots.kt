package uz.kassiron.app.shots

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.ui.auth.ActivationContent
import uz.kassiron.app.ui.auth.BlockedContent
import uz.kassiron.app.ui.auth.DownloadContent
import uz.kassiron.app.ui.auth.LoginScreen
import uz.kassiron.app.ui.settings.MoreScreen
import uz.kassiron.app.ui.settings.SettingsScreen

/** Sign-in, blocked, "Ko‘proq" and settings screens (mockups 1–5, 14, 29, 33). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class AuthShots {
    /**
     * Robolectric gives every test a fresh database, but Shots' "seeded" flag is
     * static and survives between tests: reset it so each test seeds its own shop.
     */
    @Before fun freshShop() {
        if (App.db.scalar("SELECT COUNT(*) FROM users")?.toString()?.toLongOrNull() ?: 0L == 0L) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
    }

    private fun signedOut() {
        Shots.seed(signIn = false)
        App.session.signOut()
    }

    private fun activation(name: String, tab: Int, login: String = "", password: String = "", code: String = "", error: String? = null) {
        signedOut()
        Shots.capture(name) {
            ActivationContent(tab, {}, login, {}, password, {}, code, {}, error, {})
        }
    }

    @Test fun activationEmpty() = activation("auth_activation_empty", 0)
    @Test fun activationLogin() = activation("auth_activation_login", 0, login = "ali", password = "secret12")
    @Test fun activationWrongPassword() = activation("auth_activation_wrong", 0, login = "ali", password = "x", error = "login_failed")
    @Test fun activationNoLogin() = activation("auth_activation_nologin", 0, error = "fill_login")
    @Test fun activationNetwork() = activation("auth_activation_network", 0, login = "ali", password = "secret12", error = "network")
    @Test fun activationCode() = activation("auth_activation_code", 1, code = "7222")
    @Test fun activationCodeError() = activation("auth_activation_code_error", 1, code = "722201", error = "code_invalid")

    @Test fun download() {
        signedOut()
        Shots.capture("auth_download") { DownloadContent("Baraka market", 1240, 8, 8 / 18f) }
    }

    @Test fun connecting() {
        signedOut()
        Shots.capture("auth_connecting") { DownloadContent("", null, 0, 0f) }
    }

    @Test fun login() {
        signedOut()
        Shots.capture("auth_login") { nav -> LoginScreen(nav) }
    }

    @Test fun loginPassword() {
        signedOut()
        Shots.capture("auth_login_password") { nav -> LoginScreen(nav, preselect = Shots.owner) }
    }

    private fun blocked(name: String, reason: String, pending: Long = 0, error: String? = null) {
        signedOut()
        Shots.capture(name) { BlockedContent(reason, 7, pending, false, error, {}, {}, {}, {}) }
    }

    @Test fun blockedShop() = blocked("auth_blocked_shop", "shop_blocked")
    @Test fun blockedRevoked() = blocked("auth_blocked_revoked", "device_revoked", pending = 2)
    @Test fun blockedOffline() = blocked("auth_blocked_offline", "offline_too_long", error = "network")

    @Test fun more() {
        Shots.seed()
        Shots.capture("auth_more") { nav -> MoreScreen(nav) }
    }

    @Test fun moreCashier() {
        Shots.seed()
        App.session.signIn(Shots.cashier)
        Shots.capture("auth_more_cashier") { nav -> MoreScreen(nav) }
    }

    @Test fun settings() {
        Shots.seed()
        Shots.capture("auth_settings") { nav -> SettingsScreen(nav) }
    }

    /** Changes the server refused: said on the sync card and above the till. */
    @Test fun sync_rejected() {
        Shots.seed()
        App.kv.set(uz.kassiron.app.core.Keys.SYNC_REJECTED, "3")
        App.sync.refreshState()
        try {
            Shots.capture("settings_sync_rejected") { nav -> SettingsScreen(nav) }
            Shots.capture("till_sync_rejected") { nav -> uz.kassiron.app.ui.till.TillContent(nav, uz.kassiron.app.ui.till.TillViewModel()) }
        } finally {
            App.kv.set(uz.kassiron.app.core.Keys.SYNC_REJECTED, null)
            App.sync.refreshState()
        }
    }
}
