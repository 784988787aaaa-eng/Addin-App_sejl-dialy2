package com.example

import com.example.data.FinancialSummaryTuple
import com.example.data.InvoiceDateItemsProjection
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.data.SupplyAggregatedStockProjection
import com.example.domain.LedgerCalculator
import com.example.domain.WhatsAppMessageFormatter
import com.example.util.InvoiceItemRecord
import com.example.util.encodeInvoiceItemsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnifiedAccountingAndScalabilityTest {

    private val sampleProduct = ProductEntity(
        key = "khadra",
        name = "خضرة",
        majorUnit = "فراسلة",
        minorUnit = "ثمن",
        factor = 8.0,
        price = 4000.0,
        stock = 20.0,
        colorBgHex = 0xFFECFDF5,
        colorTextHex = 0xFF065F46,
        borderHex = 0xFF6EE7B7,
        sortOrder = 0
    )

    @Test
    fun `test minor unit carry-over normalization and exact line total`() {
        // 1 فراسلة و 10 أثمان (معامل 8) => يجب أن تتوحد تلقائياً إلى 2 فراسلة و 2 ثمن (2.25 فراسلة)
        val (normMaj, normMin) = LedgerCalculator.normalizeMajorMinorPair(
            majorQty = 1.0,
            minorQty = 10.0,
            factor = 8.0,
            hasMinor = true
        )
        assertEquals(2.0, normMaj, 0.0001)
        assertEquals(2.0, normMin, 0.0001)

        val eq = LedgerCalculator.computeMajorEquivalent(normMaj, normMin, 8.0, true)
        assertEquals(2.25, eq, 0.0001)

        val total = LedgerCalculator.computeLineTotal(normMaj, normMin, 8.0, true, 4000.0)
        assertEquals(9000.0, total, 0.001)
    }

    @Test
    fun `test unified invoice accounting across POS, entity, WhatsApp messages, and ledger cells`() {
        val items = listOf(
            InvoiceItemRecord(
                key = "khadra",
                name = "خضرة",
                majorQty = 2.0,
                minorQty = 4.0,
                unitDisplay = "2 فراسلة + 4 ثمن",
                unitPrice = 4000.0,
                total = 10000.0
            )
        )
        val itemsJson = encodeInvoiceItemsJson(items)

        val accFromRaw = LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = LedgerCalculator.sumRecordTotals(items),
            discount = 500.0,
            paidCash = 6000.0,
            previousDebtPaid = 1500.0
        )

        assertEquals(10000.0, accFromRaw.rawItemsTotal, 0.001)
        assertEquals(500.0, accFromRaw.discount, 0.001)
        assertEquals(9500.0, accFromRaw.netInvoiceSubtotal, 0.001)
        assertEquals(6000.0, accFromRaw.paidCash, 0.001)
        assertEquals(1500.0, accFromRaw.previousDebtPaid, 0.001)
        assertEquals(3500.0, accFromRaw.remainingOnInvoice, 0.001)
        assertEquals(7500.0, accFromRaw.totalInUserHand, 0.001)
        assertEquals("6000 + 1500 سداد دين سابق", accFromRaw.paidCellText)

        val savedEntity = SaleInvoiceEntity(
            id = "inv_1001",
            invoiceNumber = "A1001",
            dayName = "الثلاثاء",
            date = "2026/10/20",
            time = "10:00 ص",
            customerName = "أحمد صالح",
            customerPhone = "777123456",
            itemsJson = itemsJson,
            subtotal = accFromRaw.netInvoiceSubtotal,
            discount = accFromRaw.discount,
            previousDebt = accFromRaw.previousDebtPaid,
            totalDue = accFromRaw.netInvoiceSubtotal,
            paid = accFromRaw.paidCash,
            remaining = accFromRaw.remainingOnInvoice,
            whatsappText = "",
            managerWhatsappText = "",
            timestamp = 1000L
        )

        val accFromEntity = LedgerCalculator.computeUnifiedInvoiceAccounting(savedEntity)
        assertEquals(accFromRaw, accFromEntity)

        val customerMsg = WhatsAppMessageFormatter.buildCustomerWhatsAppMessage(
            invNum = savedEntity.invoiceNumber,
            dayName = savedEntity.dayName,
            date = savedEntity.date,
            time = savedEntity.time,
            customerName = savedEntity.customerName,
            items = items,
            subtotal = savedEntity.subtotal,
            prevDebt = savedEntity.previousDebt,
            discount = savedEntity.discount,
            paid = savedEntity.paid,
            remaining = savedEntity.remaining,
            currency = "ر.ي",
            userName = "محمد"
        )
        assertTrue(customerMsg.contains("إجمالي الفاتورة: 9500 ر.ي"))
        assertTrue(customerMsg.contains("المسلم نقداً: 6000 ر.ي"))
        assertTrue(customerMsg.contains("المتبقي من الفاتورة: 3500 ر.ي"))
        assertTrue(customerMsg.contains("سداد دين سابق: 1500 ر.ي"))
        assertTrue(customerMsg.contains("إجمالي المسلم الكلي: 7500 ر.ي"))
    }

    @Test
    fun `test unified supply accounting and daily stock rollback invariant`() {
        // المخزون السابق 10 فراسلة، وتم توريد 4 فراسلة و 12 ثمن (= 5 فراسلة و 4 ثمن = 5.5 فراسلة)
        val supAcc = LedgerCalculator.computeUnifiedSupplyAccounting(
            product = sampleProduct,
            enteredMajor = 4.0,
            enteredMinor = 12.0,
            basePrevStock = 10.0
        )
        assertEquals(5.0, supAcc.normalizedMajor, 0.0001)
        assertEquals(4.0, supAcc.normalizedMinor, 0.0001)
        assertEquals(5.5, supAcc.addedMajorEquivalent, 0.0001)
        assertEquals(15.5, supAcc.cumulativeStock, 0.0001)
        assertTrue(supAcc.enteredDisplay.contains("5 فراسل") && supAcc.enteredDisplay.contains("4"))

        // التحقق من معادلة المخزون اليومية: (السابق + الوارد - المباع = المتبقي نهاية اليوم)
        val currentProduct = sampleProduct.copy(stock = 20.0)
        val aggSupplies = listOf(
            SupplyAggregatedStockProjection(
                productKey = "khadra",
                suppliedOnDate = 5.0,
                suppliedAfterDate = 4.0
            )
        )
        val invProjections = listOf(
            InvoiceDateItemsProjection(
                date = "2026/10/20",
                itemsJson = encodeInvoiceItemsJson(
                    listOf(
                        InvoiceItemRecord(
                            key = "khadra",
                            name = "خضرة",
                            majorQty = 3.0,
                            minorQty = 4.0,
                            unitDisplay = "3 فراسلة + 4 ثمن",
                            unitPrice = 4000.0,
                            total = 14000.0
                        )
                    )
                )
            ),
            InvoiceDateItemsProjection(
                date = "2026/10/21",
                itemsJson = encodeInvoiceItemsJson(
                    listOf(
                        InvoiceItemRecord(
                            key = "khadra",
                            name = "خضرة",
                            majorQty = 1.0,
                            minorQty = 4.0,
                            unitDisplay = "1 فراسلة + 4 ثمن",
                            unitPrice = 4000.0,
                            total = 6000.0
                        )
                    )
                )
            )
        )

        val stats = LedgerCalculator.computeDailyStockStatsFromAggregatedSupplies(
            targetDate = "2026/10/20",
            products = listOf(currentProduct),
            invoiceProjectionsFromDateOnwards = invProjections,
            aggregatedSupplies = aggSupplies
        )["khadra"]!!

        assertEquals(16.0, stats.prevDayRemaining, 0.0001)
        assertEquals(5.0, stats.suppliedToday, 0.0001)
        assertEquals(3.5, stats.soldToday, 0.0001)
        assertEquals(17.5, stats.endOfDayRemaining, 0.0001)
        // المعادلة المحاسبية الذهبية:
        assertEquals(
            stats.endOfDayRemaining,
            LedgerCalculator.subtractStock(LedgerCalculator.addStock(stats.prevDayRemaining, stats.suppliedToday), stats.soldToday),
            0.0001
        )
    }

    @Test
    fun `test high volume precision and speed with 50000 transactions`() {
        var runningCash = 0.0
        var runningPrevDebt = 0.0
        for (i in 1..50_000) {
            runningCash = LedgerCalculator.addMoney(runningCash, 0.1)
            runningPrevDebt = LedgerCalculator.addMoney(runningPrevDebt, 0.2)
        }
        assertEquals(5000.0, runningCash, 0.0001)
        assertEquals(10000.0, runningPrevDebt, 0.0001)

        val summary = LedgerCalculator.computeFinancialTotalsFromTuple(
            FinancialSummaryTuple(
                totalInvoicesSubtotal = 15000000.0,
                totalDiscountGiven = 250000.0,
                totalCashReceived = runningCash,
                totalPrevDebtNotified = runningPrevDebt,
                totalInvoicesRemaining = 9995000.0,
                matchedCount = 50_000
            )
        )
        assertEquals(15000.0, summary.totalCashInUserHand, 0.0001)
        assertEquals(50_000, summary.matchedInvoicesCount)
    }

    @Test
    fun `test multi day ledger book stock stats continuity across period`() {
        val currentProduct = ProductEntity(
            key = "khadra",
            name = "خضرة",
            majorUnit = "كيلو",
            minorUnit = "",
            factor = 2.0,
            colorBgHex = 0xFFDCFCE7,
            colorTextHex = 0xFF15803D,
            borderHex = 0xFF86EFAC,
            price = 5000.0,
            stock = 20.0
        )
        val dates = listOf("2026-10-19", "2026-10-20", "2026-10-21")
        val supplies = listOf(
            com.example.data.DateProductSupplyProjection("2026-10-19", "khadra", 10.0),
            com.example.data.DateProductSupplyProjection("2026-10-20", "khadra", 5.0),
            com.example.data.DateProductSupplyProjection("2026-10-21", "khadra", 8.0)
        )
        val invoices = listOf(
            InvoiceDateItemsProjection(
                date = "2026-10-19",
                itemsJson = encodeInvoiceItemsJson(
                    listOf(InvoiceItemRecord("khadra", "خضرة", "4 كيلو", 4.0, 0.0, 5000.0, 20000.0))
                )
            ),
            InvoiceDateItemsProjection(
                date = "2026-10-20",
                itemsJson = encodeInvoiceItemsJson(
                    listOf(InvoiceItemRecord("khadra", "خضرة", "3 كيلو", 3.0, 0.0, 5000.0, 15000.0))
                )
            ),
            InvoiceDateItemsProjection(
                date = "2026-10-21",
                itemsJson = encodeInvoiceItemsJson(
                    listOf(InvoiceItemRecord("khadra", "خضرة", "6 كيلو", 6.0, 0.0, 5000.0, 30000.0))
                )
            )
        )

        val multiStats = LedgerCalculator.computeMultiDayStockStatsMap(
            targetDates = dates,
            products = listOf(currentProduct),
            invoiceProjectionsFromMinDateOnwards = invoices,
            supplyProjectionsByDateFromMinDateOnwards = supplies
        )

        val d1 = multiStats["2026-10-19"]!!["khadra"]!!
        val d2 = multiStats["2026-10-20"]!!["khadra"]!!
        val d3 = multiStats["2026-10-21"]!!["khadra"]!!

        // متبقي نهاية كل يوم ينتقل بدقة 100% كـ (إجمالي المتبقي السابق) لليوم التالي في الكتاب
        assertEquals(d1.endOfDayRemaining, d2.prevDayRemaining, 0.0001)
        assertEquals(d2.endOfDayRemaining, d3.prevDayRemaining, 0.0001)
        assertEquals(20.0, d3.endOfDayRemaining, 0.0001)
    }

    @Test
    fun `test invoice notes appear in customer manager and daily ledger messages like supplies`() {
        val prod = ProductEntity(
            key = "khadra",
            name = "خضرة",
            majorUnit = "كيلو",
            minorUnit = "",
            factor = 2.0,
            colorBgHex = 0xFFDCFCE7,
            colorTextHex = 0xFF15803D,
            borderHex = 0xFF86EFAC,
            price = 6800.0,
            stock = 19.0
        )
        val items = listOf(InvoiceItemRecord("khadra", "خضرة", "1 كيلو", 1.0, 0.0, 6800.0, 6800.0))
        val noteText = "تسليم مع السائق محمد"

        val customerMsg = LedgerCalculator.buildCustomerWhatsAppMessage(
            invNum = "A1005",
            dayName = "الأربعاء",
            date = "2026-10-21",
            time = "5:00 م",
            customerName = "منصور قطينه",
            items = items,
            subtotal = 6800.0,
            prevDebt = 0.0,
            discount = 0.0,
            paid = 6800.0,
            remaining = 0.0,
            currency = "ر.ي",
            userName = "أنور",
            notes = noteText
        )
        assertTrue(customerMsg.contains("ملاحظات: $noteText"))

        val managerMsg = LedgerCalculator.buildManagerInvoiceWhatsAppMessage(
            invNum = "A1005",
            dayName = "الأربعاء",
            date = "2026-10-21",
            time = "5:00 م",
            customerName = "منصور قطينه",
            items = items,
            subtotal = 6800.0,
            prevDebt = 0.0,
            discount = 0.0,
            paid = 6800.0,
            remaining = 0.0,
            currency = "ر.ي",
            updatedProductsMap = mapOf("khadra" to prod),
            userName = "أنور",
            notes = noteText
        )
        assertTrue(managerMsg.contains("ملاحظات: $noteText"))
    }
}
