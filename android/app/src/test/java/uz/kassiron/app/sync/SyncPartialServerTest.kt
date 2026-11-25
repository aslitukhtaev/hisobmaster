package uz.kassiron.app.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uz.kassiron.app.App
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Time
import java.security.MessageDigest

/**
 * Against a real KassirON server (a local test copy): a phone whose copy of a
 * product lacks the optom price set on the website edits the sell price — only
 * the sell price goes up, the server keeps the optom price and the phone gets it.
 *
 * Runs only when KASSIRON_TEST_SERVER (and _LOGIN, _PASSWORD) are set, e.g.
 *   KASSIRON_TEST_SERVER=http://127.0.0.1:8099 ./gradlew testDebugUnitTest --tests '*SyncPartialServerTest*'
 * The server's product named KASSIRON_TEST_PRODUCT (default "Cola 1l") must have an optom price.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncPartialServerTest {
    @Test fun staleOptomPriceIsNotPushedOverTheServers() {
        val server = System.getenv("KASSIRON_TEST_SERVER")
        assumeTrue("no test server", !server.isNullOrBlank())
        val api = Api(server!!)
        val db = App.db
        val kv = App.kv

        val fingerprint = MessageDigest.getInstance("SHA-256").digest("robolectric".toByteArray()).joinToString("") { "%02x".format(it) }
        val response = api.post(
            "/api/device/activate",
            JSONObject()
                .put("login", System.getenv("KASSIRON_TEST_LOGIN"))
                .put("password", System.getenv("KASSIRON_TEST_PASSWORD"))
                .put("device_uuid", Ids.new())
                .put("fingerprint", fingerprint)
                .put("name", "Robolectric")
                .put("app_version", "2.3.2"),
            auth = null,
        )
        kv.set(Keys.DEVICE_UUID, response.getJSONObject("device").getString("uuid"))
        kv.set(Keys.DEVICE_SECRET, response.getString("secret"))
        val engine = SyncEngine(db, kv, api)
        engine.pull()

        val name = System.getenv("KASSIRON_TEST_PRODUCT") ?: "Cola 1l"
        val product = db.queryOne("SELECT * FROM products WHERE name = ?", name)!!
        val uuid = product.str("uuid")
        val optom = product.dblOrNull("wholesale_price")
        assumeTrue("the server's product needs an optom price", optom != null)

        // This phone's copy goes stale: it never got the optom price.
        db.exec("UPDATE products SET wholesale_price = NULL WHERE uuid = ?", uuid)
        val stale = db.queryOne("SELECT * FROM products WHERE uuid = ?", uuid)!!

        // An edit on the phone: the sell price.
        val newPrice = product.dbl("sell_price") + 1000
        db.write {
            val values = mapOf("sell_price" to newPrice, "updated_at" to Time.now())
            update("products", uuid, values)
            Outbox.upsert(this, "products", uuid, Outbox.changed(stale, values))
        }
        engine.push()
        engine.pull()

        val after = db.queryOne("SELECT * FROM products WHERE uuid = ?", uuid)!!
        assertEquals(newPrice, after.dbl("sell_price"), 0.001)
        // The server kept its optom price (a whole-row push would have cleared it) and sent it back.
        // A pull only sends what changed since the cursor: the sell price edit did, so the row came whole.
        assertEquals(optom!!, after.dblOrNull("wholesale_price") ?: -1.0, 0.001)
    }
}
