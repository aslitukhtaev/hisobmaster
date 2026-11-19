package uz.kassiron.app.shots

import androidx.lifecycle.SavedStateHandle
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uz.kassiron.app.App
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.SearchText
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.Customers
import uz.kassiron.app.domain.ExpenseService
import uz.kassiron.app.sync.Api
import uz.kassiron.app.sync.SyncEngine
import uz.kassiron.app.ui.till.TillData
import uz.kassiron.app.ui.till.TillViewModel
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The fixes from the Android audit (rejected changes, clock, cart, search, phone lookups). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class AuditFixesShots {
    @Before fun setUp() {
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
        Time.setServerOffset(0)
    }

    @After fun tearDown() {
        Time.setServerOffset(0)
        // The services ask for a sync 3 s later: let it run on this database, not the next test's.
        Thread.sleep(3_500)
    }

    // ------------------------------------------------------------- 2. clock

    @Test fun timeFollowsTheServerClock() {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        Time.setServerOffset(86_400) // the phone is a day behind
        val stamped = utc.parse(Time.now())!!.time
        assertTrue("now() should be about a day ahead of the phone", kotlin.math.abs(stamped - (System.currentTimeMillis() + 86_400_000)) < 5_000)
        Time.setServerOffset(400L * 86_400) // nonsense: ignored
        assertEquals(0L, Time.offsetMillis)
    }

    // ------------------------------------------------------------- 8. search

    @Test fun searchIgnoresCaseAndApostrophesInCyrillicAndUzbek() {
        val p = App.db.write {
            val u = Ids.new()
            insert("products", mapOf("uuid" to u, "name" to "Go‘sht Мясо", "unit" to "kg", "cost_price" to 1.0, "sell_price" to 2.0, "stock_qty" to 5.0, "status" to "active", "created_at" to Time.now(), "updated_at" to Time.now()))
            exec("DELETE FROM sync_outbox")
            u
        }
        assertTrue(TillData.search("go'sht").any { it.uuid == p })
        assertTrue(TillData.search("мясо").any { it.uuid == p })
        assertEquals("go'sht", SearchText.key("  Go‘SHT "))
    }

    @Test fun expenseCategoryIsOneWhateverTheCase() {
        val a = App.db.write { ExpenseService.findOrCreateCategory(this, "Коммунальные") }
        val b = App.db.write { ExpenseService.findOrCreateCategory(this, "коммунальные") }
        assertEquals(a, b)
    }

    // ------------------------------------------------------------- 10. phone lookups

    @Test fun findOrCreateFindsTheSamePhoneInAnotherFormat() {
        val existing = Shots.customers["Vali aka"]!! // +998909998877
        val found = App.db.write { Customers.findOrCreate(this, "Vali", "90 999 88 77") }
        assertEquals(existing, found)
    }

    // ------------------------------------------------------------- 4 + 3. till cart

    @Test fun cartSurvivesTheProcessBeingKilled() {
        val handle = SavedStateHandle()
        val first = TillViewModel(handle)
        val non = TillData.search("Non").first { it.name == "Non" }
        first.add(non, null, 3.0)
        val again = TillViewModel(handle) // same saved state, new process
        assertEquals(1, again.cart.size)
        assertEquals(3.0, again.cart[0].qty, 0.0001)
        assertEquals(non.uuid, again.cart[0].productUuid)
    }

    @Test fun cartPricesFollowAPriceChangeFromTheServer() {
        val vm = TillViewModel()
        val non = TillData.search("Non").first { it.name == "Non" }
        vm.add(non, null, 2.0)
        App.db.write { exec("UPDATE products SET sell_price = 4000 WHERE uuid = ?", non.uuid); exec("DELETE FROM sync_outbox") }
        vm.applyFresh(vm.cart.map { it.key to TillData.fresh(it) })
        assertEquals(4000.0, vm.cart[0].price, 0.001)
        assertEquals(8000.0, vm.subtotal, 0.001)
        assertFalse(vm.cart[0].unavailable)

        App.db.write { exec("UPDATE products SET status = 'inactive' WHERE uuid = ?", non.uuid); exec("DELETE FROM sync_outbox") }
        vm.applyFresh(vm.cart.map { it.key to TillData.fresh(it) })
        assertTrue(vm.cart[0].unavailable)
        App.db.write { exec("UPDATE products SET status = 'active', sell_price = 3500 WHERE uuid = ?", non.uuid); exec("DELETE FROM sync_outbox") }
    }

    // ------------------------------------------------------------- 1 + 7. sync

    @Test fun refusedChangesAreCountedAndEmptyPullsDontRefreshScreens() {
        // A tiny stand-in for the server: answers push and pull with fixed JSON.
        val socket = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            while (!socket.isClosed) {
                val c = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    c.use { conn ->
                        val input = conn.getInputStream().bufferedReader()
                        val requestLine = input.readLine() ?: return@use
                        var length = 0
                        while (true) {
                            val h = input.readLine() ?: break
                            if (h.isEmpty()) break
                            if (h.lowercase().startsWith("content-length:")) length = h.substringAfter(':').trim().toInt()
                        }
                        repeat(length) { input.read() }
                        val body = if (requestLine.contains("/api/sync/push")) {
                            JSONObject().put("applied_seq", 99).put("rejected", 2).put("server_time", System.currentTimeMillis() / 1000)
                        } else {
                            JSONObject().put("changes", org.json.JSONArray()).put("cursor", 1).put("more", false)
                                .put("shop", JSONObject(App.kv.get(Keys.SHOP)!!)).put("settings", JSONObject(App.kv.get(Keys.SHOP_SETTINGS) ?: "{}"))
                        }.toString().toByteArray()
                        val out = conn.getOutputStream()
                        val crlf = "\r\n"
                        out.write(("HTTP/1.1 200 OK" + crlf + "Content-Type: application/json" + crlf + "Content-Length: " + body.size + crlf + "Connection: close" + crlf + crlf).toByteArray())
                        out.write(body)
                        out.flush()
                    }
                }
            }
        }
        try {
            App.kv.set(Keys.SYNC_REJECTED, null)
            App.db.write { Outbox.upsert(this, "customers", Shots.customers["Karim aka"]!!) }
            val engine = SyncEngine(App.db, App.kv, Api("http://127.0.0.1:" + socket.localPort))
            engine.push()
            assertEquals(2L, App.kv.long(Keys.SYNC_REJECTED))
            assertEquals(0L, Outbox.pendingTotal(App.db))

            // A second identical pull: nothing changed, so screens aren't told to re-read.
            engine.pull()
            val before = App.db.changes.value
            engine.pull()
            assertEquals(before, App.db.changes.value)
        } finally {
            socket.close()
            serverThread.join(1000)
            App.kv.set(Keys.SYNC_REJECTED, null)
        }
    }

    @Test fun freshReadsAVariantsOwnPrice() {
        val non = Shots.products["Non"]!!
        val v = App.db.write {
            val u = Ids.new()
            insert("product_variants", mapOf("uuid" to u, "product_id" to non, "variant_label" to "Katta", "sell_price" to 5000.0, "stock_qty" to 4.0, "status" to "active", "created_at" to Time.now()))
            exec("DELETE FROM sync_outbox")
            u
        }
        val vm = TillViewModel()
        val p = TillData.search("Non").first { it.uuid == non }
        val variant = TillData.variants(p).first { it.uuid == v }
        vm.add(p, variant, 1.0)
        val f = TillData.fresh(vm.cart[0])
        assertNotNull(f)
        assertEquals(5000.0, f!!.retail, 0.001)
        App.db.write { exec("DELETE FROM product_variants WHERE uuid = ?", v); exec("DELETE FROM sync_outbox") }
    }
}
