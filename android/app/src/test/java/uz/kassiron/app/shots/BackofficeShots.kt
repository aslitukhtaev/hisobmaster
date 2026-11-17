package uz.kassiron.app.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.Attendance
import uz.kassiron.app.domain.CartLine
import uz.kassiron.app.domain.Debts
import uz.kassiron.app.domain.SaleService
import uz.kassiron.app.sync.SyncState
import uz.kassiron.app.ui.customers.AmountSheetContent
import uz.kassiron.app.ui.customers.CustomerDetailScreen
import uz.kassiron.app.ui.customers.CustomerEditScreen
import uz.kassiron.app.ui.customers.CustomersScreen
import uz.kassiron.app.ui.customers.QuickDebtsScreen
import uz.kassiron.app.ui.expenses.ExpenseEditScreen
import uz.kassiron.app.ui.expenses.ExpensesScreen
import uz.kassiron.app.ui.reports.AttendanceScreen
import uz.kassiron.app.ui.reports.DashboardScreen
import uz.kassiron.app.ui.reports.ReportsScreen
import uz.kassiron.app.ui.sales.RefundScreen
import uz.kassiron.app.ui.sales.SaleDetailScreen
import uz.kassiron.app.ui.sales.SalesScreen
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import java.io.File

/**
 * Every back-office screen (mockups 13, 19–28) in both themes:
 *   KASSIRON_ANDROID_WORK=$HOME/.kassiron-android/work-backoffice bash android/build.sh shots '*BackofficeShots*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class BackofficeShots {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() {
        // Robolectric gives every test a fresh app (and database) but keeps Shots' static
        // "seeded" flag: seed again when this test's database is empty.
        if (App.db.scalarLong("SELECT COUNT(*) FROM users") == 0L) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
    }

    /** Sales made in a test ask for a sync ~3 s later: let it finish while this test's database is still open. */
    @After fun drainSync() = Thread.sleep(3500)

    private fun settle() {
        repeat(4) { Thread.sleep(120); rule.waitForIdle() }
    }

    private fun shot(name: String, act: () -> Unit = {}, content: @Composable (NavHostController) -> Unit) {
        ThemeMode.set("light")
        rule.setContent { KassirTheme { content(rememberNavController()) } }
        settle()
        act()
        settle()
        for (mode in listOf("light", "dark")) {
            ThemeMode.set(mode)
            settle()
            rule.onRoot().captureRoboImage(File(Shots.outDir, "${name}_$mode.png").path)
        }
        ThemeMode.set("system")
    }

    private fun firstSale(): String = App.db.queryOne("SELECT uuid FROM sales ORDER BY receipt_no LIMIT 1")!!.str("uuid")
    private val vali get() = Shots.customers["Vali aka"]!!

    /** Vali: an old debt, a payment, a sale on credit — the ledger of mockup 23. */
    private fun valiHistory() {
        App.db.write { Debts.record(this, vali, null, Debts.TOLOV, 20000.0, Shots.owner) }
        SaleService.create(listOf(CartLine(Shots.products["Shakar"]!!, null, 1.0), CartLine(Shots.products["Un 1 kg"]!!, null, 1.0)), 0.0, 0.0, 0.0, vali)
    }

    // ---------------------------------------------------------------- Bosh

    @Test fun dashboard() {
        App.db.write { exec("UPDATE products SET low_stock_threshold = 5 WHERE name LIKE 'Choy%'") }
        shot("bo_dashboard") { DashboardScreen(it) }
    }

    @Test fun dashboardOffline() {
        App.db.write { exec("UPDATE products SET low_stock_threshold = 5 WHERE name LIKE 'Choy%' OR name LIKE 'Tuxum%'") }
        Attendance.clockIn(Shots.owner)
        val f = App.sync.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (f.get(App.sync) as MutableStateFlow<SyncState>).value = SyncState(error = "network", pendingSales = 3)
        shot("bo_dashboard_offline") { DashboardScreen(it) }
    }

    @Test fun dashboardCashier() {
        App.session.signIn(Shots.cashier)
        shot("bo_dashboard_cashier") { DashboardScreen(it) }
    }

    // ---------------------------------------------------------------- Sotuvlar

    @Test fun sales() {
        SaleService.create(listOf(CartLine(Shots.products["Coca-Cola 1.5L"]!!, null, 2.0)), 0.0, 25000.0, 0.0, null)
        valiHistory()
        shot("bo_sales") { SalesScreen(it) }
    }

    @Test fun salesEmpty() = shot("bo_sales_empty", act = { rule.onNode(hasText("Kecha")).performClick() }) { SalesScreen(it) }

    @Test fun saleDetail() = shot("bo_sale_detail") { SaleDetailScreen(it, firstSale()) }

    @Test fun refund() {
        val uuid = firstSale()
        shot("bo_refund", act = { rule.onAllNodes(hasText("+"))[0].performClick() }) { RefundScreen(it, uuid) }
    }

    @Test fun refundEmpty() {
        val uuid = firstSale()
        shot("bo_refund_none") { RefundScreen(it, uuid) }
    }

    // ---------------------------------------------------------------- Qarzdorlar

    @Test fun customers() = shot("bo_customers") { CustomersScreen(it) }

    @Test fun customersAll() = shot("bo_customers_all", act = { rule.onNode(hasText("Hammasi")).performClick() }) { CustomersScreen(it) }

    @Test fun customersSearchEmpty() = shot("bo_customers_empty", act = { rule.onAllNodes(hasSetTextAction())[0].performTextInput("Zafar") }) { CustomersScreen(it) }

    @Test fun customerDetail() {
        valiHistory()
        shot("bo_customer_detail") { CustomerDetailScreen(it, vali) }
    }

    @Test fun paymentSheet() {
        valiHistory()
        val balance = App.db.let { Debts.balance(it, vali) }
        shot("bo_payment_sheet") {
            Box(Modifier.fillMaxSize()) {
                CustomerDetailScreen(it, vali)
                FakeSheet { AmountSheetContent(debt = false, balance = balance, creditLimit = null, busy = false, onClose = {}, initial = "50 000") {} }
            }
        }
    }

    @Test fun debtSheet() {
        App.db.write { exec("UPDATE customers SET credit_limit = 120000 WHERE uuid = ?", vali) }
        shot("bo_debt_sheet") {
            Box(Modifier.fillMaxSize()) {
                CustomerDetailScreen(it, vali)
                FakeSheet { AmountSheetContent(debt = true, balance = 100000.0, creditLimit = 120000.0, busy = false, onClose = {}, initial = "30 000") {} }
            }
        }
    }

    @Test fun customerNew() = shot("bo_customer_new") { CustomerEditScreen(it, "new") }

    @Test fun customerEdit() {
        App.db.write { exec("UPDATE customers SET note = 'Bozordagi qo‘shni', credit_limit = 200000, debt_due_date = ? WHERE uuid = ?", Time.addDays(Time.today(), 7), vali) }
        shot("bo_customer_edit") { CustomerEditScreen(it, vali) }
    }

    @Test fun quickDebts() = shot("bo_quick_debts", act = {
        val cells = rule.onAllNodes(hasSetTextAction())
        cells[0].performTextInput("Karim aka"); cells[1].performTextInput("90 123 45 67"); cells[2].performTextInput("80 000")
        cells[3].performTextInput("Dilfuza"); cells[5].performTextInput("35 000")
    }) { QuickDebtsScreen(it) }

    @Test fun quickDebtsError() = shot("bo_quick_debts_error", act = {
        val cells = rule.onAllNodes(hasSetTextAction())
        cells[0].performTextInput("Karim aka"); cells[1].performTextInput("12 34"); cells[2].performTextInput("80 000")
        rule.onNode(hasText("1 ta qarzni saqlash")).performClick()
    }) { QuickDebtsScreen(it) }

    // ---------------------------------------------------------------- Xarajatlar

    @Test fun expenses() {
        App.db.write {
            val cat = uz.kassiron.app.domain.ExpenseService.findOrCreateCategory(this, "Transport")
            insert("expenses", mapOf("uuid" to uz.kassiron.app.core.Ids.new(), "category_id" to cat, "amount" to 70000.0, "description" to "Yetkazib berish", "expense_date" to Time.today(), "created_by" to Shots.owner, "created_at" to Time.now()))
            val cat2 = uz.kassiron.app.domain.ExpenseService.findOrCreateCategory(this, "Boshqa")
            insert("expenses", mapOf("uuid" to uz.kassiron.app.core.Ids.new(), "category_id" to cat2, "amount" to 50000.0, "description" to null, "expense_date" to Time.today(), "created_by" to Shots.owner, "created_at" to Time.now()))
        }
        shot("bo_expenses") { ExpensesScreen(it) }
    }

    @Test fun expensesEmpty() {
        App.db.write { exec("DELETE FROM expenses") }
        shot("bo_expenses_empty") { ExpensesScreen(it) }
    }

    @Test fun expenseNew() = shot("bo_expense_new") { ExpenseEditScreen(it, "new") }

    @Test fun expenseNewCategory() = shot("bo_expense_new_category", act = { rule.onNode(hasText("+ Yangi")).performClick() }) { ExpenseEditScreen(it, "new") }

    @Test fun expenseEdit() {
        val uuid = App.db.queryOne("SELECT uuid FROM expenses LIMIT 1")!!.str("uuid")
        shot("bo_expense_edit") { ExpenseEditScreen(it, uuid) }
    }

    // ---------------------------------------------------------------- Hisobotlar, Davomat

    @Test fun reports() {
        SaleService.create(listOf(CartLine(Shots.products["Coca-Cola 1.5L"]!!, null, 2.0)), 0.0, 25000.0, 0.0, null)
        valiHistory()
        shot("bo_reports") { ReportsScreen(it) }
    }

    @Test fun attendance() {
        Attendance.clockIn(Shots.owner)
        Attendance.clockIn(Shots.cashier)
        shot("bo_attendance") { AttendanceScreen(it) }
    }

    @Test fun attendanceOff() = shot("bo_attendance_off") { AttendanceScreen(it) }
}

/** The kit's KSheet is a dialog window (not captured): the same look drawn in place. */
@Composable
private fun FakeSheet(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(K.c.s1).padding(horizontal = 14.dp).padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(4.dp)).background(K.c.bd))
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
