package uz.kassiron.app.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.ui.auth.ActivationScreen
import uz.kassiron.app.ui.auth.BlockedScreen
import uz.kassiron.app.ui.auth.LoginScreen
import uz.kassiron.app.ui.customers.CustomerDetailScreen
import uz.kassiron.app.ui.customers.CustomerEditScreen
import uz.kassiron.app.ui.customers.CustomersScreen
import uz.kassiron.app.ui.customers.QuickDebtsScreen
import uz.kassiron.app.ui.expenses.ExpenseEditScreen
import uz.kassiron.app.ui.expenses.ExpensesScreen
import uz.kassiron.app.ui.products.CategoriesScreen
import uz.kassiron.app.ui.products.ProductEditScreen
import uz.kassiron.app.ui.products.ProductsScreen
import uz.kassiron.app.ui.products.PurchaseDetailScreen
import uz.kassiron.app.ui.products.PurchaseNewScreen
import uz.kassiron.app.ui.products.PurchasesScreen
import uz.kassiron.app.ui.products.QuickProductsScreen
import uz.kassiron.app.ui.products.SuppliersScreen
import uz.kassiron.app.ui.reports.AttendanceScreen
import uz.kassiron.app.ui.reports.DashboardScreen
import uz.kassiron.app.ui.reports.ReportsScreen
import uz.kassiron.app.ui.sales.RefundScreen
import uz.kassiron.app.ui.sales.SaleDetailScreen
import uz.kassiron.app.ui.sales.SalesScreen
import uz.kassiron.app.ui.settings.MoreScreen
import uz.kassiron.app.ui.settings.PrinterScreen
import uz.kassiron.app.ui.settings.SettingsScreen
import uz.kassiron.app.ui.till.ReceiptScreen
import uz.kassiron.app.ui.till.TillScreen

/**
 * Every screen's route. Screens navigate with `nav.navigate(Routes.x(...))`
 * and go back with `nav.popBackStack()`. Ids in routes are row uuids;
 * "new" means a new row.
 */
object Routes {
    const val ACTIVATION = "activation"
    const val LOGIN = "login"
    const val BLOCKED = "blocked"

    const val TILL = "till"
    fun receipt(saleUuid: String) = "receipt/$saleUuid"

    const val HOME = "home"                  // dashboard
    const val SALES = "sales"
    fun sale(uuid: String) = "sales/$uuid"
    fun refund(saleUuid: String) = "sales/$saleUuid/refund"

    const val PRODUCTS = "products"
    /** [barcode]: prefill for a new product (an unknown code scanned at the till). */
    fun productEdit(uuid: String, barcode: String? = null) = "products/edit/$uuid" + (barcode?.let { "?barcode=$it" } ?: "")
    const val QUICK_PRODUCTS = "products/quick"
    const val CATEGORIES = "categories"
    const val PURCHASES = "purchases"
    const val PURCHASE_NEW = "purchases/new"
    fun purchase(uuid: String) = "purchases/$uuid"
    const val SUPPLIERS = "suppliers"

    const val CUSTOMERS = "customers"
    fun customer(uuid: String) = "customers/$uuid"
    fun customerEdit(uuid: String) = "customers/edit/$uuid"
    const val QUICK_DEBTS = "customers/quick-debts"

    const val EXPENSES = "expenses"
    fun expenseEdit(uuid: String) = "expenses/edit/$uuid"

    const val REPORTS = "reports"
    const val ATTENDANCE = "attendance"

    const val MORE = "more"
    const val SETTINGS = "settings"
    const val PRINTER = "printer"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val permission: String?)

private val TABS = listOf(
    Tab(Routes.TILL, R.string.nav_till, Icons.Filled.PointOfSale, "sales"),
    Tab(Routes.SALES, R.string.nav_sales, Icons.Filled.ReceiptLong, "sales"),
    Tab(Routes.PRODUCTS, R.string.nav_products, Icons.Filled.Inventory2, "products"),
    Tab(Routes.CUSTOMERS, R.string.nav_customers, Icons.Filled.People, "customers"),
    Tab(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz, null),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val user by App.session.user.collectAsState()
    val sync by App.sync.state.collectAsState()

    // Decided once: later changes navigate explicitly (resetTo).
    val start = androidx.compose.runtime.remember { when {
        !App.isActivated -> Routes.ACTIVATION
        sync.blockedReason != null -> Routes.BLOCKED
        user == null -> Routes.LOGIN
        user!!.can("sales") -> Routes.TILL
        else -> Routes.HOME
    } }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in TABS.map { it.route } || current == Routes.HOME

    // Sync blocked the phone (revoked, shop blocked, offline too long) or the
    // signed-in user was removed/blocked on the website: leave whatever screen is open.
    androidx.compose.runtime.LaunchedEffect(sync.blockedReason, user, current) {
        if (current == null || current == Routes.ACTIVATION) return@LaunchedEffect
        if (!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { App.isActivated }) return@LaunchedEffect
        if (sync.blockedReason != null && current != Routes.BLOCKED) nav.resetTo(Routes.BLOCKED)
        else if (sync.blockedReason == null && user == null && current != Routes.LOGIN && current != Routes.BLOCKED) nav.resetTo(Routes.LOGIN)
    }

    Scaffold(
        bottomBar = {
            if (showBar && user != null) {
                NavigationBar {
                    TABS.filter { it.permission == null || user!!.can(it.permission) }.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1, softWrap = false, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.ACTIVATION) { ActivationScreen(nav) }
            composable(Routes.LOGIN) { LoginScreen(nav) }
            composable(Routes.BLOCKED) { BlockedScreen(nav) }

            composable(Routes.TILL) { TillScreen(nav) }
            composable("receipt/{id}") { ReceiptScreen(nav, it.arg("id")) }

            composable(Routes.HOME) { DashboardScreen(nav) }
            composable(Routes.SALES) { SalesScreen(nav) }
            composable("sales/{id}") { SaleDetailScreen(nav, it.arg("id")) }
            composable("sales/{id}/refund") { RefundScreen(nav, it.arg("id")) }

            composable(Routes.PRODUCTS) { ProductsScreen(nav) }
            composable(
                "products/edit/{id}?barcode={barcode}",
                arguments = listOf(navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { ProductEditScreen(nav, it.arg("id"), it.arguments?.getString("barcode")) }
            composable(Routes.QUICK_PRODUCTS) { QuickProductsScreen(nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
            composable(Routes.PURCHASES) { PurchasesScreen(nav) }
            composable(Routes.PURCHASE_NEW) { PurchaseNewScreen(nav) }
            composable("purchases/{id}") { PurchaseDetailScreen(nav, it.arg("id")) }
            composable(Routes.SUPPLIERS) { SuppliersScreen(nav) }

            composable(Routes.CUSTOMERS) { CustomersScreen(nav) }
            composable("customers/{id}") { CustomerDetailScreen(nav, it.arg("id")) }
            composable("customers/edit/{id}") { CustomerEditScreen(nav, it.arg("id")) }
            composable(Routes.QUICK_DEBTS) { QuickDebtsScreen(nav) }

            composable(Routes.EXPENSES) { ExpensesScreen(nav) }
            composable("expenses/edit/{id}") { ExpenseEditScreen(nav, it.arg("id")) }

            composable(Routes.REPORTS) { ReportsScreen(nav) }
            composable(Routes.ATTENDANCE) { AttendanceScreen(nav) }

            composable(Routes.MORE) { MoreScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.PRINTER) { PrinterScreen(nav) }
        }
    }
}

private fun androidx.navigation.NavBackStackEntry.arg(name: String): String = arguments?.getString(name) ?: ""

/** Bottom-bar navigation: one copy of each tab, state kept. */
fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** After sign-in / activation: start fresh at [route]. */
fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
