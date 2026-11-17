package uz.kassiron.app

import android.app.Application
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Kv
import uz.kassiron.app.core.Session
import uz.kassiron.app.sync.SyncManager

/**
 * The app's single set of shared objects. Anywhere: `App.db`, `App.kv`,
 * `App.session`, `App.sync`.
 */
class KassirApp : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }
}

object App {
    lateinit var app: Application
        private set
    lateinit var db: Db
        private set
    lateinit var kv: Kv
        private set
    lateinit var session: Session
        private set
    lateinit var sync: SyncManager
        private set

    fun init(application: Application) {
        app = application
        db = Db(application)
        kv = Kv(db)
        session = Session(db, kv)
        uz.kassiron.app.ui.theme.ThemeMode.set(kv.get("theme") ?: "system")
        session.restore()
        sync = SyncManager(application, db, kv)
        sync.start()
    }

    /** Activated: this phone has device credentials for a shop. */
    val isActivated: Boolean get() = kv.get(uz.kassiron.app.core.Keys.DEVICE_SECRET) != null
}
