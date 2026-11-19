package uz.kassiron.app.shots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.ui.products.CategoriesScreen
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import java.io.File

/** The categories screen's actions (rename / merge / delete) and the service behind them. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class CategoriesShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() {
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
    }

    private val show = mutableStateOf(true)

    @After fun tearDown() {
        // Leave nothing running (local reads) into the next test, whose database is new.
        show.value = false
        settle()
        // The services ask for a sync 3 s later (SyncManager's debounce): let it run on this database, not the next test's.
        Thread.sleep(3_500)
        ThemeMode.set("system")
    }

    private fun uuid(name: String) = App.db.queryOne("SELECT uuid FROM categories WHERE name = ?", name)?.str("uuid")

    private fun settle() = repeat(3) { rule.waitForIdle(); Thread.sleep(250) }

    private fun capture(name: String) {
        for (mode in listOf("light", "dark")) {
            ThemeMode.set(mode)
            settle()
            captureScreenRoboImage(File(Shots.outDir, "${name}_$mode.png").path, RoborazziOptions())
        }
    }

    private fun open() {
        ThemeMode.set("light")
        rule.setContent { if (show.value) KassirTheme { CategoriesScreen(rememberNavController()) } }
        settle()
    }

    @Test fun categories_actions() {
        open()
        rule.onNodeWithText("Ichimliklar").performClick()
        capture("categories_actions")
    }

    @Test fun categories_delete_confirm() {
        open()
        rule.onNodeWithText("Ichimliklar").performClick()
        settle()
        rule.onNodeWithText("O‘chirish").performClick()
        capture("categories_delete_confirm")
    }

    @Test fun categories_merge_pick() {
        open()
        rule.onNodeWithText("Ichimliklar").performClick()
        settle()
        rule.onNodeWithText("Boshqasiga qo‘shish").performClick()
        capture("categories_merge_pick")
    }

    @Test fun mergeMovesProductsAndQueuesUpsertsBeforeDelete() {
        val drinks = uuid("Ichimliklar")!!
        val food = uuid("Oziq-ovqat")!!
        App.db.exec("DELETE FROM sync_outbox")
        val moved = ProductService.mergeCategory(drinks, food)
        assertEquals(3, moved)
        assertNull(uuid("Ichimliklar"))
        assertEquals(0L, App.db.scalarLong("SELECT COUNT(*) FROM products WHERE category_id = ?", drinks))
        val ops = App.db.query("SELECT tbl, op FROM sync_outbox WHERE tbl IN ('products', 'categories') ORDER BY rowid").map { it.str("tbl") + ":" + it.str("op") }
        assertEquals(listOf("products:upsert", "products:upsert", "products:upsert", "categories:delete"), ops)
    }

    @Test fun deleteKeepsProductsWithoutCategory() {
        val drinks = uuid("Ichimliklar")!!
        ProductService.deleteCategory(drinks)
        assertNull(uuid("Ichimliklar"))
        assertEquals(0L, App.db.scalarLong("SELECT COUNT(*) FROM products WHERE category_id = ?", drinks))
        assertEquals(1L, App.db.scalarLong("SELECT COUNT(*) FROM products WHERE name = 'Coca-Cola 1.5L' AND category_id IS NULL"))
    }

    @Test fun namesCompareTrimmedAndCaseInsensitive() {
        val drinks = uuid("Ichimliklar")!!
        assertEquals(drinks, ProductService.findOrCreateCategory("  ichimliklar  "))
        val cyr = ProductService.findOrCreateCategory("Ичимлик  сув")
        assertEquals(cyr, ProductService.findOrCreateCategory("ичимлик сув"))
        val e = runCatching { ProductService.renameCategory(cyr, "ICHIMLIKLAR") }.exceptionOrNull() as ProductException
        assertEquals(ProductException.NAME_TAKEN, e.code)
        assertEquals(drinks, e.arg)
    }
}
