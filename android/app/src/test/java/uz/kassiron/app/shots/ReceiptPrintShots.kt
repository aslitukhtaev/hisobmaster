package uz.kassiron.app.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.core.Keys
import uz.kassiron.app.print.BluetoothPrinter
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import uz.kassiron.app.ui.till.ReceiptContent
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/**
 * The receipt with the shop's logo and QR (website: Profil -> Do'kon sozlamalari):
 * the receipt screen, and the printed receipt — the ESC/POS bytes drawn back as
 * the paper would come out (receipt_print_58.png / _80.png).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class ReceiptPrintShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var savedSettings: String? = null

    @Before fun setUp() {
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
        savedSettings = App.kv.get(Keys.SHOP_SETTINGS)
        App.kv.set(
            Keys.SHOP_SETTINGS,
            JSONObject().put("receipt_logo", dataUri(logo())).put("receipt_qr", dataUri(qr())).toString(),
        )
    }

    @After fun tearDown() {
        App.kv.set(Keys.SHOP_SETTINGS, savedSettings ?: "{}")
        App.kv.set(Keys.PRINTER_WIDTH, null)
        ThemeMode.set("system")
    }

    private fun firstSale() = App.db.queryOne("SELECT uuid FROM sales ORDER BY created_at, rowid LIMIT 1")!!.str("uuid")

    @Test fun receipt_images_screen() {
        val data = Receipts.load(App.db, firstSale())
        assertNotNull(data!!.logo)
        assertNotNull(data.qr)
        ThemeMode.set("light")
        rule.setContent { KassirTheme { ReceiptContent(rememberNavController(), true, data) } }
        repeat(3) { rule.waitForIdle(); Thread.sleep(250) }
        captureScreenRoboImage(File(Shots.outDir, "receipt_images_light.png").path, RoborazziOptions())
    }

    @Test fun receipt_print() {
        val context = rule.activity
        val data = Receipts.load(App.db, firstSale())!!
        for (mm in listOf(58, 80)) {
            App.kv.set(Keys.PRINTER_WIDTH, mm.toString())
            val bytes = BluetoothPrinter.receiptBytes(context, data)
            val paper = Paper(if (mm == 80) 576 else 384, if (mm == 80) 48 else 32)
            paper.run(bytes)
            assertEquals("logo + QR as rasters", 2, paper.pictures)
            File(Shots.outDir, "receipt_print_$mm.png").outputStream().use { paper.bitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Shared as text: no picture lines, no "not fiscal" note.
        val text = Receipts.text(context, data)
        assertFalse(text.contains("iskal"))
        assertTrue(text.contains("rahmat") || text.contains("Спасибо"))
    }

    private fun logo(): Bitmap {
        val b = Bitmap.createBitmap(400, 140, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        c.drawRoundRect(10f, 10f, 130f, 130f, 24f, 24f, p)
        p.textSize = 72f; p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("SHOP", 150f, 100f, p)
        return b
    }

    private fun qr(): Bitmap {
        val n = 21
        val b = Bitmap.createBitmap(n * 10, n * 10, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK }
        for (y in 0 until n) for (x in 0 until n) {
            val finder = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
            val on = if (finder) (x % 6 == 0 || y % 6 == 0 || (x % 7 in 2..4 && y % 7 in 2..4)) else (x * 7 + y * 13) % 3 == 0
            if (on) c.drawRect(x * 10f, y * 10f, x * 10f + 10, y * 10f + 10, p)
        }
        return b
    }

    private fun dataUri(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    /** Just enough of an ESC/POS printer to draw what the receipt bytes print. */
    private class Paper(val dots: Int, val cols: Int) {
        private val rows = ArrayList<Bitmap>()
        var pictures = 0
        private var center = false
        private var bold = false
        private var big = false
        private val text = StringBuilder()
        private val cell = dots / cols

        fun run(b: ByteArray) {
            var i = 0
            fun u(k: Int) = b[k].toInt() and 0xFF
            while (i < b.size) {
                val c = u(i)
                when {
                    c == 0x1B && u(i + 1) == 0x40 -> i += 2
                    c == 0x1B && u(i + 1) == 0x74 -> i += 3
                    c == 0x1B && u(i + 1) == 0x61 -> { center = u(i + 2) == 1; i += 3 }
                    c == 0x1B && u(i + 1) == 0x45 -> { bold = u(i + 2) == 1; i += 3 }
                    c == 0x1D && u(i + 1) == 0x21 -> { big = u(i + 2) != 0; i += 3 }
                    c == 0x1B && u(i + 1) == 0x64 -> { repeat(u(i + 2)) { blank() }; i += 3 }
                    c == 0x1D && u(i + 1) == 0x56 -> i += 4
                    c == 0x1D && u(i + 1) == 0x76 && u(i + 2) == 0x30 -> {
                        val xb = u(i + 4) + u(i + 5) * 256
                        val h = u(i + 6) + u(i + 7) * 256
                        val bmp = Bitmap.createBitmap(dots, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        val left = if (center) (dots - xb * 8) / 2 else 0
                        for (y in 0 until h) for (x in 0 until xb * 8) {
                            if ((u(i + 8 + y * xb + x / 8) shr (7 - x % 8)) and 1 == 1 && left + x in 0 until dots) {
                                bmp.setPixel(left + x, y, Color.BLACK)
                            }
                        }
                        rows += bmp
                        if (rowsSinceLastPicture) pictures++
                        rowsSinceLastPicture = false
                        i += 8 + xb * h
                    }
                    c == 0x0A -> { flush(); rowsSinceLastPicture = true; i++ }
                    else -> { text.append(c.toChar()); i++ }
                }
            }
        }

        private var rowsSinceLastPicture = true

        private fun blank() { rows += Bitmap.createBitmap(dots, cell * 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) } }

        private fun flush() {
            val s = text.toString(); text.clear()
            val scale = if (big) 2 else 1
            val h = cell * 2 * scale
            val bmp = Bitmap.createBitmap(dots, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
                textSize = cell * 1.65f * scale
            }
            // Spread the characters on the printer's grid (one cell each).
            val w = s.length * cell * scale
            val left = if (center) (dots - w) / 2f else 0f
            s.forEachIndexed { k, ch -> Canvas(bmp).drawText(ch.toString(), left + k * cell * scale, h * 0.78f, p) }
            rows += bmp
        }

        fun bitmap(): Bitmap {
            val margin = 24
            val out = Bitmap.createBitmap(dots + margin * 2, rows.sumOf { it.height } + margin * 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(Color.WHITE)
            var y = margin.toFloat()
            for (r in rows) { c.drawBitmap(r, margin.toFloat(), y, null); y += r.height }
            return out
        }
    }
}
