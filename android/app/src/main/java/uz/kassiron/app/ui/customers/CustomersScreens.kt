package uz.kassiron.app.ui.customers

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// STUB — replaced by the agent that owns ui/customers (see ARCHITECTURE.md). Keep the signatures.

@Composable
fun CustomersScreen(nav: NavHostController) { Text("CustomersScreen") }

@Composable
fun QuickDebtsScreen(nav: NavHostController) { Text("QuickDebtsScreen") }

@Composable
fun CustomerDetailScreen(nav: NavHostController, uuid: String) { Text("CustomerDetailScreen $uuid") }

@Composable
fun CustomerEditScreen(nav: NavHostController, uuid: String) { Text("CustomerEditScreen $uuid") }
