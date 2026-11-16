package uz.kassiron.app.ui.till

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// STUB — replaced by the agent that owns ui/till (see ARCHITECTURE.md). Keep the signatures.

@Composable
fun TillScreen(nav: NavHostController) { Text("TillScreen") }

@Composable
fun ReceiptScreen(nav: NavHostController, uuid: String) { Text("ReceiptScreen $uuid") }
