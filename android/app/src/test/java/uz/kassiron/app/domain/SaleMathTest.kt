package uz.kassiron.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.print.Text
import uz.kassiron.app.ui.till.quickCash

/** Sale::create's money rules, reproduced by SaleMath. */
class SaleMathTest {
    private val eps = 0.0001

    @Test fun cashExact() {
        val s = SaleMath.split(25_000.0, 25_000.0, 0.0)
        assertEquals(25_000.0, s.naqd, eps)
        assertEquals(0.0, s.qarz, eps)
        assertNull(s.cashReceived)
        assertEquals(0.0, s.change, eps)
        assertEquals("naqd", s.paymentType)
        assertEquals(25_000.0, s.paidAmount, eps)
    }

    @Test fun cashWithChange() {
        val s = SaleMath.split(23_400.0, 50_000.0, 0.0)
        assertEquals(23_400.0, s.naqd, eps)
        assertEquals(50_000.0, s.cashReceived!!, eps)
        assertEquals(26_600.0, s.change, eps)
        assertEquals(0.0, s.qarz, eps)
        assertEquals("naqd", s.paymentType)
    }

    @Test fun cardIsCappedAndAppliedFirst() {
        val s = SaleMath.split(10_000.0, 5_000.0, 12_000.0)
        assertEquals(10_000.0, s.karta, eps)
        assertEquals(0.0, s.naqd, eps)
        // All the cash handed over is change: the card already paid everything.
        assertEquals(5_000.0, s.cashReceived!!, eps)
        assertEquals(5_000.0, s.change, eps)
        assertEquals("karta", s.paymentType)
    }

    @Test fun splitCardAndCash() {
        val s = SaleMath.split(30_000.0, 20_000.0, 15_000.0)
        assertEquals(15_000.0, s.karta, eps)
        assertEquals(15_000.0, s.naqd, eps)
        assertEquals(5_000.0, s.change, eps)
        assertEquals("aralash", s.paymentType)
        assertEquals(30_000.0, s.paidAmount, eps)
    }

    @Test fun restBecomesDebt() {
        val s = SaleMath.split(30_000.0, 10_000.0, 0.0)
        assertEquals(10_000.0, s.naqd, eps)
        assertEquals(20_000.0, s.qarz, eps)
        assertEquals(10_000.0, s.paidAmount, eps)
        assertEquals("aralash", s.paymentType)
        assertEquals("qarz", SaleMath.split(30_000.0, 0.0, 0.0).paymentType)
    }

    @Test fun negativeInputsAreZero() {
        val s = SaleMath.split(1_000.0, -5.0, -3.0)
        assertEquals(0.0, s.naqd, eps)
        assertEquals(0.0, s.karta, eps)
        assertEquals(1_000.0, s.qarz, eps)
    }

    @Test fun zeroTotalIsNaqd() {
        assertEquals("naqd", SaleMath.paymentTypeLabel(0.0, 0.0, 0.0))
    }

    @Test fun discountRules() {
        assertEquals(1_000.0, SaleMath.checkDiscount(1_000.0, 5_000.0), eps)
        assertEquals(0.0, SaleMath.checkDiscount(0.0, 0.0), eps)
        expect("discount_too_large") { SaleMath.checkDiscount(5_000.0, 5_000.0) }
        expect("discount_too_large") { SaleMath.checkDiscount(4_999.996, 5_000.0) }
        expect("invalid_discount") { SaleMath.checkDiscount(-1.0, 5_000.0) }
        expect("invalid_discount") { SaleMath.checkDiscount(Double.NaN, 5_000.0) }
    }

    @Test fun lineSubtotalRounds() {
        assertEquals(4_166.63, SaleMath.lineSubtotal(12_500.0, 0.33333), 0.0)
        assertEquals(1.01, SaleMath.round2(1.005), 0.0)
        assertEquals(-1.01, SaleMath.round2(-1.005), 0.0)
        assertEquals(0.3, SaleMath.lineSubtotal(0.1, 3.0), eps)
    }

    @Test fun receiptNumber() {
        assertEquals("K3-000145", SaleMath.receiptNo("K3", 145))
        assertEquals("K-000001", SaleMath.receiptNo(null, 1))
        assertEquals("K12-1234567", SaleMath.receiptNo("K12", 1_234_567))
    }

    @Test fun quickCashChips() {
        assertEquals(listOf(23_400.0, 24_000.0, 25_000.0, 30_000.0, 50_000.0, 100_000.0), quickCash(23_400.0))
        assertEquals(listOf(50_000.0, 100_000.0, 200_000.0), quickCash(50_000.0))
        assertEquals(emptyList<Double>(), quickCash(0.0))
    }

    @Test fun printerText() {
        assertEquals("O'zbek g'alla", Text.toAscii("O‘zbek g‘alla"))
        assertEquals("Privet, Shkola", Text.toAscii("Привет, Школа"))
        assertEquals("Jami        15 000", Receipts.twoCols("Jami", "15 000", 18))
        assertEquals(listOf("abc def", "ghi"), Receipts.wrap("abc def ghi", 7))
    }

    private fun expect(code: String, block: () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: SaleException) {
            assertEquals(code, e.code)
        }
    }
}
