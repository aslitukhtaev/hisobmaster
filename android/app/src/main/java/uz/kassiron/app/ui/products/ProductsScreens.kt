package uz.kassiron.app.ui.products

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// STUB — replaced by the agent that owns ui/products (see ARCHITECTURE.md). Keep the signatures.

@Composable
fun ProductsScreen(nav: NavHostController) { Text("ProductsScreen") }

@Composable
fun QuickProductsScreen(nav: NavHostController) { Text("QuickProductsScreen") }

@Composable
fun CategoriesScreen(nav: NavHostController) { Text("CategoriesScreen") }

@Composable
fun PurchasesScreen(nav: NavHostController) { Text("PurchasesScreen") }

@Composable
fun PurchaseNewScreen(nav: NavHostController) { Text("PurchaseNewScreen") }

@Composable
fun SuppliersScreen(nav: NavHostController) { Text("SuppliersScreen") }

@Composable
fun PurchaseDetailScreen(nav: NavHostController, uuid: String) { Text("PurchaseDetailScreen $uuid") }
