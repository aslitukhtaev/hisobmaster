package uz.kassiron.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uz.kassiron.app.core.Keys
import uz.kassiron.app.ui.AppNav
import uz.kassiron.app.ui.theme.KassirTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** The app's language (uz / ru) from settings, whatever the phone's language is. */
    override fun attachBaseContext(base: Context) {
        val lang = runCatching { App.kv.get(Keys.LANG) }.getOrNull() ?: "uz"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KassirTheme {
                AppNav()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        App.sync.requestSync()
    }
}
