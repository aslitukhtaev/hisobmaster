package uz.kassiron.app.ui.till

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.theme.K

/** "Savat 2", or the name the cashier gave it, or the customer's name. */
@Composable
internal fun cartTitle(tab: CartTab): String =
    tab.name ?: tab.customerName ?: stringResource(R.string.till_cart_n, tab.number)

/**
 * The till's carts as tabs: tap to switch, long-press for the menu, "+ Yangi"
 * to start another while this one waits. Each tab shows how much is in it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CartTabs(
    tabs: List<CartTab>,
    canAdd: Boolean,
    onSwitch: (Int) -> Unit,
    onNew: () -> Unit,
    onMenu: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Edge, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(14.dp)
        tabs.forEach { t ->
            Column(
                Modifier.widthIn(min = 80.dp, max = 160.dp).height(46.dp).clip(shape)
                    .background(if (t.active) K.c.primary else K.c.s1)
                    .border(1.dp, if (t.active) K.c.primary else K.c.bd, shape)
                    .combinedClickable(onClick = { onSwitch(t.index) }, onLongClick = { onMenu(t.index) })
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    cartTitle(t), style = K.t.chip, color = if (t.active) K.c.onPrimary else K.c.tx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (t.count == 0) stringResource(R.string.till_cart_tab_empty)
                    else stringResource(R.string.till_cart_tab_sum, t.count, Money.format(t.total, false)),
                    style = K.t.small.copy(fontSize = 11.sp), color = if (t.active) K.c.onPrimary else K.c.mu,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canAdd) {
            Row(
                Modifier.height(46.dp).clip(shape).border(1.dp, K.c.bd, shape).background(K.c.s1)
                    .combinedClickable(onClick = onNew).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val label = stringResource(R.string.till_cart_new)
                Icon(Icons.Outlined.Add, contentDescription = label, tint = K.c.tx)
                // With three carts or more the word no longer fits beside them: the plus alone.
                if (tabs.size < 3) Text(label, style = K.t.chip, color = K.c.tx, maxLines = 1)
            }
        }
    }
}

/** Rename or close one cart. */
@Composable
internal fun CartMenuSheet(tab: CartTab, onRename: (String) -> Unit, onClose: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(tab.name.orEmpty()) }
    KSheet(onDismiss = onDismiss) {
        KSheetTitle(cartTitle(tab), onDismiss)
        KField(
            name, { name = it.take(20) }, label = stringResource(R.string.till_cart_name),
            placeholder = stringResource(R.string.till_cart_n, tab.number), onDone = { onRename(name) },
        )
        Spacer(Modifier.height(12.dp))
        KButton(stringResource(R.string.common_save), { onRename(name) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KButton(stringResource(R.string.till_cart_close), onClose, Modifier.fillMaxWidth(), kind = KButtonKind.Danger)
    }
}
