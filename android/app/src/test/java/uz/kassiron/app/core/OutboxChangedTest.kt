package uz.kassiron.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Outbox.changed(): an edit sends only the columns it really changed, so a
 * stale copy of the others (an optom price set meanwhile on the website) is
 * never pushed over the server's.
 */
class OutboxChangedTest {
    private val existing = Row(
        mapOf(
            "name" to "Cola 1l", "sell_price" to 15000.0, "cost_price" to 12000L,
            "wholesale_price" to null, "barcode" to "2000000012704", "updated_at" to "2026-11-24 10:00:00",
        ),
    )

    @Test fun onlyTheEditedColumns() {
        val values = mapOf(
            "name" to "Cola 1l", "sell_price" to 16000.0, "cost_price" to 12000.0,
            "wholesale_price" to null, "barcode" to "2000000012704", "updated_at" to "2026-11-25 09:00:00",
        )
        assertEquals(listOf("sell_price", "updated_at"), Outbox.changed(existing, values))
    }

    @Test fun numbersCompareByValue() {
        // 12000 (Long from SQLite) and 12000.0 (Double from the form) are the same price.
        assertEquals(emptyList<String>(), Outbox.changed(existing, mapOf("cost_price" to 12000.0)))
        assertEquals(emptyList<String>(), Outbox.changed(existing, mapOf("sell_price" to "15000")))
    }

    @Test fun clearingAndSettingAreChanges() {
        assertEquals(listOf("wholesale_price"), Outbox.changed(existing, mapOf("wholesale_price" to 11000.0)))
        assertEquals(listOf("barcode"), Outbox.changed(existing, mapOf("barcode" to null)))
    }
}
