package uz.kassiron.app.ui.sales

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// STUB — replaced by the agent that owns ui/sales (see ARCHITECTURE.md). Keep the signatures.

@Composable
fun SalesScreen(nav: NavHostController) { Text("SalesScreen") }

@Composable
fun SaleDetailScreen(nav: NavHostController, uuid: String) { Text("SaleDetailScreen $uuid") }

@Composable
fun RefundScreen(nav: NavHostController, uuid: String) { Text("RefundScreen $uuid") }
