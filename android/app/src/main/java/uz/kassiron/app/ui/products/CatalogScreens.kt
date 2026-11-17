package uz.kassiron.app.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KFab
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSearch
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KTopBar

// ====================================================================
// Categories
// ====================================================================

@Composable
fun CategoriesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val categories by dbState<List<DbRow>?>(null) {
        App.db.query(
            """SELECT c.uuid, c.name, (SELECT COUNT(*) FROM products p WHERE p.category_id = c.uuid) AS product_count
               FROM categories c WHERE c.type = 'product' ORDER BY c.name COLLATE NOCASE"""
        )
    }
    // null = closed; "" = new; uuid = rename.
    var editing by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    KScreen(
        snackbar = snackbar,
        topBar = { KTopBar(stringResource(R.string.categories_title), onBack = { nav.popBackStack() }) },
        fab = { KFab(Icons.Outlined.Add, { editing = ""; editName = ""; error = null }, stringResource(R.string.categories_add)) },
    ) {
        val list = categories
        when {
            list == null -> Unit
            list.isEmpty() -> KEmpty(
                Icons.Outlined.Category, stringResource(R.string.categories_empty_title), stringResource(R.string.categories_empty_text),
                action = stringResource(R.string.categories_add), onAction = { editing = ""; editName = "" },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Edge, end = Edge, top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.str("uuid") }) { c ->
                    val n = c.long("product_count").toInt()
                    KRow(
                        title = c.str("name"),
                        trailing = { KTag(stringResource(R.string.products_count, n), if (n > 0) KTagKind.Ok else KTagKind.Neutral) },
                        onClick = { editing = c.str("uuid"); editName = c.str("name"); error = null },
                    )
                }
            }
        }
    }

    editing?.let { target ->
        NameSheet(
            title = stringResource(if (target.isEmpty()) R.string.categories_add else R.string.categories_rename),
            label = stringResource(R.string.categories_name),
            placeholder = stringResource(R.string.categories_name_ph),
            value = editName,
            error = error,
            onChange = { editName = it; error = null },
            onDismiss = { editing = null },
            onSave = {
                scope.launch {
                    runCatching {
                        io { if (target.isEmpty()) ProductService.findOrCreateCategory(editName) else ProductService.renameCategory(target, editName) }
                    }.onSuccess { editing = null; snackbar.showSnackbar(ctx.getString(R.string.categories_saved)) }
                        .onFailure { error = ctx.productError(it) }
                }
            },
        )
    }
}

// ====================================================================
// Suppliers
// ====================================================================

private data class SupplierDraft(val uuid: String?, val name: String = "", val phone: String = "", val address: String = "", val note: String = "")

@Composable
fun SuppliersScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val suppliers by dbState<List<DbRow>?>(null) { ProductService.suppliers(App.db) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<SupplierDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val shown = remember(suppliers, query) {
        val q = query.trim().lowercase()
        suppliers.orEmpty().filter { q.isEmpty() || it.str("name").lowercase().contains(q) || it.str("phone").contains(q) }
    }

    KScreen(
        snackbar = snackbar,
        topBar = { KTopBar(stringResource(R.string.suppliers_title), onBack = { nav.popBackStack() }) },
        fab = { KFab(Icons.Outlined.Add, { draft = SupplierDraft(null); error = null }, stringResource(R.string.suppliers_add)) },
    ) {
        val list = suppliers
        if (list != null && list.size > 6) {
            KSearch(query, { query = it }, stringResource(R.string.suppliers_search))
            Spacer(Modifier.height(10.dp))
        }
        when {
            list == null -> Unit
            list.isEmpty() -> KEmpty(
                Icons.Outlined.LocalShipping, stringResource(R.string.suppliers_empty_title), stringResource(R.string.suppliers_empty_text),
                action = stringResource(R.string.suppliers_add), onAction = { draft = SupplierDraft(null) },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Edge, end = Edge, top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.str("uuid") }) { s ->
                    val sub = listOf(showPhone(s.str("phone")), s.str("address")).filter { it.isNotBlank() }.joinToString(" · ")
                    KRow(
                        title = s.str("name"),
                        leading = { KAvatar(s.str("name")) },
                        subtitle = if (sub.isNotEmpty() || s.str("note").isNotBlank()) ({
                            if (sub.isNotEmpty()) KSub(sub)
                            if (s.str("note").isNotBlank()) KSub(s.str("note"))
                        }) else null,
                        onClick = {
                            draft = SupplierDraft(s.str("uuid"), s.str("name"), s.str("phone"), s.str("address"), s.str("note"))
                            error = null
                        },
                    )
                }
            }
        }
    }

    draft?.let { d ->
        KSheet({ draft = null }) {
            KSheetTitle(stringResource(if (d.uuid == null) R.string.suppliers_add else R.string.suppliers_edit), { draft = null })
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KField(d.name, { draft = d.copy(name = it) }, label = stringResource(R.string.suppliers_name), placeholder = stringResource(R.string.suppliers_name_ph))
                KField(
                    d.phone, { draft = d.copy(phone = it); error = null }, label = stringResource(R.string.suppliers_phone),
                    placeholder = "+998 90 123 45 67", keyboard = KeyboardType.Phone, error = error,
                )
                KField(d.address, { draft = d.copy(address = it) }, label = stringResource(R.string.suppliers_address), placeholder = stringResource(R.string.suppliers_address_ph))
                KField(d.note, { draft = d.copy(note = it) }, label = stringResource(R.string.suppliers_note), placeholder = stringResource(R.string.suppliers_note_ph), singleLine = false)
                KButton(
                    stringResource(R.string.common_save),
                    {
                        scope.launch {
                            runCatching { io { ProductService.saveSupplier(d.uuid, d.name, d.phone, d.address, d.note) } }
                                .onSuccess { draft = null; snackbar.showSnackbar(ctx.getString(if (d.uuid == null) R.string.suppliers_created else R.string.suppliers_updated)) }
                                .onFailure { error = ctx.productError(it) }
                        }
                    },
                    Modifier.fillMaxWidth().padding(top = 2.dp), enabled = d.name.isNotBlank(),
                )
            }
        }
    }
}
