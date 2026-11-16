package uz.kassiron.app.ui.expenses

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// STUB — replaced by the agent that owns ui/expenses (see ARCHITECTURE.md). Keep the signatures.

@Composable
fun ExpensesScreen(nav: NavHostController) { Text("ExpensesScreen") }

@Composable
fun ExpenseEditScreen(nav: NavHostController, uuid: String) { Text("ExpenseEditScreen $uuid") }
