package uz.kassiron.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KOption
import uz.kassiron.app.ui.kit.KSegmented
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
    var tab by rememberSaveable { mutableStateOf(0) }
    val type = if (tab == 0) "product" else "expense"
    val categories by dbState<List<DbRow>?>(null, type) {
        App.db.query(
            """SELECT c.uuid, c.name,
                      (SELECT COUNT(*) FROM products p WHERE p.category_id = c.uuid) +
                      (SELECT COUNT(*) FROM expenses e WHERE e.category_id = c.uuid) AS use_count
               FROM categories c WHERE c.type = ? ORDER BY c.name COLLATE NOCASE""",
            type,
        )
    }
    // null = closed; "" = new; uuid = rename.
    var editing by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<DbRow?>(null) }
    var mergeFrom by remember { mutableStateOf<DbRow?>(null) }
    var askMerge by remember { mutableStateOf<Pair<DbRow, DbRow>?>(null) }
    var askDelete by remember { mutableStateOf<DbRow?>(null) }
    // Renaming onto a name another category has: (the one renamed, the one with that name).
    var askTaken by remember { mutableStateOf<Pair<DbRow, DbRow>?>(null) }

    fun countText(n: Int) = ctx.getString(if (type == "product") R.string.products_count else R.string.categories_expenses_count, n)
    fun byUuid(uuid: String) = categories.orEmpty().firstOrNull { it.str("uuid") == uuid }
    fun merge(from: DbRow, into: DbRow) {
        scope.launch {
            runCatching { io { ProductService.mergeCategory(from.str("uuid"), into.str("uuid")) } }
                .onSuccess { snackbar.showSnackbar(ctx.getString(R.string.categories_merged)) }
                .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
        }
    }

    KScreen(
        snackbar = snackbar,
        topBar = { KTopBar(stringResource(R.string.categories_title), onBack = { nav.popBackStack() }) },
        fab = { KFab(Icons.Outlined.Add, { editing = ""; editName = ""; error = null }, stringResource(R.string.categories_add)) },
    ) {
        KSegmented(
            listOf(KOption(stringResource(R.string.categories_tab_products)), KOption(stringResource(R.string.categories_tab_expenses))),
            tab, { tab = it }, Modifier.fillMaxWidth().padding(horizontal = Edge).padding(bottom = 10.dp),
        )
        val list = categories
        when {
            list == null -> Unit
            list.isEmpty() -> KEmpty(
                Icons.Outlined.Category, stringResource(R.string.categories_empty_title),
                stringResource(if (type == "product") R.string.categories_empty_text else R.string.categories_empty_text_exp),
                action = stringResource(R.string.categories_add), onAction = { editing = ""; editName = ""; error = null },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Edge, end = Edge, top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.str("uuid") }) { c ->
                    val n = c.long("use_count").toInt()
                    KRow(
                        title = c.str("name"),
                        trailing = { KTag(countText(n), if (n > 0) KTagKind.Ok else KTagKind.Neutral) },
                        onClick = { actionsFor = c },
                    )
                }
            }
        }
    }

    actionsFor?.let { c ->
        KSheet({ actionsFor = null }) {
            KSheetTitle(c.str("name"), { actionsFor = null })
            KSub(countText(c.long("use_count").toInt()))
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionRow(Icons.Outlined.Edit, stringResource(R.string.categories_action_rename), null) {
                    actionsFor = null; editing = c.str("uuid"); editName = c.str("name"); error = null
                }
                ActionRow(Icons.Outlined.MergeType, stringResource(R.string.categories_action_merge), stringResource(R.string.categories_action_merge_sub)) {
                    actionsFor = null; mergeFrom = c
                }
                ActionRow(
                    Icons.Outlined.DeleteOutline, stringResource(R.string.categories_action_delete),
                    stringResource(if (type == "product") R.string.categories_action_delete_sub else R.string.categories_action_delete_sub_exp), danger = true,
                ) { actionsFor = null; askDelete = c }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { target ->
        NameSheet(
            title = stringResource(if (target.isEmpty()) R.string.categories_add else R.string.categories_rename),
            label = stringResource(R.string.categories_name),
            placeholder = stringResource(if (type == "product") R.string.categories_name_ph else R.string.categories_name_ph_exp),
            value = editName,
            error = error,
            onChange = { editName = it; error = null },
            onDismiss = { editing = null },
            onSave = {
                scope.launch {
                    runCatching {
                        io { if (target.isEmpty()) ProductService.findOrCreateCategory(editName, type) else ProductService.renameCategory(target, editName) }
                    }.onSuccess { editing = null; snackbar.showSnackbar(ctx.getString(R.string.categories_saved)) }
                        .onFailure { e ->
                            val other = (e as? ProductException)?.takeIf { it.code == ProductException.NAME_TAKEN }?.arg?.let(::byUuid)
                            val self = byUuid(target)
                            if (other != null && self != null) { editing = null; askTaken = self to other } else error = ctx.productError(e)
                        }
                }
            },
        )
    }

    mergeFrom?.let { from ->
        val others = categories.orEmpty().filter { it.str("uuid") != from.str("uuid") }
        KSheet({ mergeFrom = null }) {
            KSheetTitle(stringResource(R.string.categories_action_merge), { mergeFrom = null })
            KSub(stringResource(R.string.categories_merge_pick, from.str("name")))
            Spacer(Modifier.height(12.dp))
            if (others.isEmpty()) {
                KSub(stringResource(R.string.categories_merge_none))
                Spacer(Modifier.height(16.dp))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(others, key = { it.str("uuid") }) { o ->
                        KRow(
                            title = o.str("name"),
                            trailing = { KSub(countText(o.long("use_count").toInt())) },
                            onClick = { mergeFrom = null; askMerge = from to o },
                        )
                    }
                }
            }
        }
    }

    askMerge?.let { (from, into) ->
        val n = from.long("use_count").toInt()
        KConfirm(
            title = stringResource(R.string.categories_merge_title),
            text = when {
                n == 0 -> stringResource(R.string.categories_merge_text_empty, from.str("name"), into.str("name"))
                type == "product" -> stringResource(R.string.categories_merge_text_products, from.str("name"), n, into.str("name"))
                else -> stringResource(R.string.categories_merge_text_expenses, from.str("name"), n, into.str("name"))
            },
            confirm = stringResource(R.string.categories_merge_confirm),
            dismiss = stringResource(R.string.common_cancel),
            onDismiss = { askMerge = null },
            onConfirm = { askMerge = null; merge(from, into) },
        )
    }

    askTaken?.let { (self, other) ->
        KConfirm(
            title = stringResource(R.string.categories_taken_title, other.str("name")),
            text = stringResource(R.string.categories_taken_text, other.str("name"), self.str("name")),
            confirm = stringResource(R.string.categories_merge_confirm),
            dismiss = stringResource(R.string.common_cancel),
            onDismiss = { askTaken = null },
            onConfirm = { askTaken = null; merge(self, other) },
        )
    }

    askDelete?.let { c ->
        val n = c.long("use_count").toInt()
        KConfirm(
            title = stringResource(R.string.categories_delete_title, c.str("name")),
            text = when {
                n == 0 -> stringResource(R.string.categories_delete_text_empty)
                type == "product" -> stringResource(R.string.categories_delete_text_products, n)
                else -> stringResource(R.string.categories_delete_text_expenses, n)
            },
            confirm = stringResource(R.string.common_delete),
            dismiss = stringResource(R.string.common_cancel),
            danger = true,
            onDismiss = { askDelete = null },
            onConfirm = {
                askDelete = null
                scope.launch {
                    runCatching { io { ProductService.deleteCategory(c.str("uuid")) } }
                        .onSuccess { snackbar.showSnackbar(ctx.getString(R.string.categories_deleted)) }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
        )
    }
}

/** One line of an actions sheet: icon tile, title, optional hint. */
@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit) {
    KRow(
        title = title,
        leading = {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (danger) K.c.redBg else K.c.s2), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (danger) K.c.red else K.c.tx, modifier = Modifier.size(19.dp))
            }
        },
        subtitle = sub?.let { { KSub(it) } },
        titleColor = if (danger) K.c.red else K.c.tx,
        onClick = onClick,
    )
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
