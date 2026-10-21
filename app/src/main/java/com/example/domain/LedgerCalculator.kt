package com.example.domain

import com.example.data.DateProductSupplyProjection
import com.example.data.FinancialSummaryTuple
import com.example.data.InvoiceDateItemsProjection
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.data.SupplyAggregatedStockProjection
import com.example.data.SupplyBatchEntity
import com.example.data.SupplyStockProjection
import com.example.util.InvoiceItemRecord
import com.example.util.cleanNum
import com.example.util.decodeInvoiceItemsJson
import com.example.util.formatCombinedUnitsArabic
import com.example.util.formatQtyWithUnitArabic
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProductDailyStockStats(
    val productKey: String,
    val productName: String,
    val suppliedToday: Double,     // الصف الأول: وارد بضاعة جديد (لنفس الصنف حصراً في هذا اليوم)
    val prevDayRemaining: Double,  // الصف الثاني: إجمالي المتبقي من اليوم السابق (يعبأ تلقائياً)
    val soldToday: Double,         // الصف السفلي 1: إجمالي المباع اليوم لهذا الصنف
    val endOfDayRemaining: Double, // الصف السفلي 2: متبقي اليوم = (الوارد + المتبقي السابق) - المباع
    val suppliedDisplay: String,
    val prevRemainingDisplay: String,
    val soldDisplay: String,
    val endRemainingDisplay: String
)

data class DailyFinancialTotals(
    val totalInvoicesSubtotal: Double,          // إجمالي قيمة الفواتير المباعة (الصافي بعد الخصم)
    val totalCashReceived: Double,              // إجمالي النقد المستلم من الفواتير
    val totalPrevDebtNotified: Double,          // إجمالي سداد الديون السابقة المستلمة
    val totalCashInUserHand: Double,            // المبلغ الإجمالي الكلي بيد المستخدم (النقد + سداد دين سابق)
    val totalInvoicesRemaining: Double,         // إجمالي المتبقي (الديون على العملاء)
    val totalDiscountGiven: Double = 0.0,       // إجمالي الخصم الممنوح
    val matchedInvoicesCount: Int = 0           // إجمالي عدد الفواتير المطابقة
)

/**
 * النموذج الحسابي الموحد والدقيق لكل فاتورة في جميع الشاشات والنوافذ والرسائل والتقارير والسجل
 */
data class UnifiedInvoiceAccounting(
    val rawItemsTotal: Double,      // إجمالي الأصناف قبل الخصم
    val discount: Double,           // الخصم المعتمد
    val netInvoiceSubtotal: Double, // صافي إجمالي الفاتورة بعد الخصم
    val paidCash: Double,           // المسلم نقداً من الفاتورة
    val previousDebtPaid: Double,   // سداد دين سابق
    val remainingOnInvoice: Double, // المتبقي من الفاتورة = (صافي الفاتورة - المسلم نقداً)
    val totalInUserHand: Double,    // إجمالي المبلغ المستلم بيد المستخدم من هذه العملية = (المسلم نقداً + سداد دين سابق)
    val paidCellText: String        // نص خلية "مسلم" الموحد في الكشوفات والبطاقات
)

/**
 * النموذج الحسابي الموحد لحركة التوريد والمخزون التراكمي
 */
data class UnifiedSupplyAccounting(
    val normalizedMajor: Double,
    val normalizedMinor: Double,
    val enteredDisplay: String,
    val addedMajorEquivalent: Double,
    val prevStock: Double,
    val cumulativeStock: Double,
    val prevStockDisplay: String,
    val cumulativeStockDisplay: String
)

/**
 * المحرك المحاسبي الموحد والدقيق للمخزون والوحدات الكبرى/الصغرى والحسابات المالية
 * يعتمد على BigDecimal لضمان الدقة المحاسبية التامة 100% وخوارزميات O(N) أحادية المرور للعمل مع ملايين المعاملات
 */
object LedgerCalculator {

    /**
     * تقريب مالي دقيق (منزلتين عشريتين بنظام HALF_UP) لمنع أي انحراف عشري
     */
    fun roundMoney(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        if (abs(value) < 0.0001) return 0.0
        return try {
            BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * تقريب كميات المخزون والوحدات بدقة 4 منازل عشرية لمنع تراكم فروقات الكسور
     */
    fun roundStock(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        if (abs(value) < 0.00005) return 0.0
        return try {
            BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toDouble()
        } catch (_: Exception) {
            0.0
        }
    }

    fun addMoney(a: Double, b: Double): Double {
        return try {
            BigDecimal.valueOf(roundMoney(a))
                .add(BigDecimal.valueOf(roundMoney(b)))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        } catch (_: Exception) {
            roundMoney(a + b)
        }
    }

    fun subtractMoneyNonNegative(a: Double, b: Double): Double {
        return try {
            BigDecimal.valueOf(roundMoney(a))
                .subtract(BigDecimal.valueOf(roundMoney(b)))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        } catch (_: Exception) {
            roundMoney((a - b).coerceAtLeast(0.0))
        }
    }

    fun addStock(a: Double, b: Double): Double {
        val cleanA = if (a.isNaN() || a.isInfinite()) 0.0 else a
        val cleanB = if (b.isNaN() || b.isInfinite()) 0.0 else b
        return try {
            BigDecimal.valueOf(cleanA)
                .add(BigDecimal.valueOf(cleanB))
                .setScale(4, RoundingMode.HALF_UP)
                .toDouble()
        } catch (_: Exception) {
            roundStock(cleanA + cleanB)
        }
    }

    fun subtractStock(a: Double, b: Double): Double {
        val cleanA = if (a.isNaN() || a.isInfinite()) 0.0 else a
        val cleanB = if (b.isNaN() || b.isInfinite()) 0.0 else b
        return try {
            BigDecimal.valueOf(cleanA)
                .subtract(BigDecimal.valueOf(cleanB))
                .setScale(4, RoundingMode.HALF_UP)
                .toDouble()
        } catch (_: Exception) {
            roundStock(cleanA - cleanB)
        }
    }

    /**
     * توحيد وترحيل الكسور من الوحدة الصغرى إلى الكبرى عند تجاوز معامل التحويل
     * (مثال: 2 حبة بمعامل 2 تتحول تلقائياً إلى 1 كيلو، أو 9 أثمان بمعامل 8 تتحول إلى 1 فراسلة و 1 ثمن)
     */
    fun normalizeMajorMinorPair(
        majorQty: Double,
        minorQty: Double,
        factor: Double,
        hasMinor: Boolean
    ): Pair<Double, Double> {
        val cleanMaj = if (majorQty.isNaN() || majorQty < 0.0) 0.0 else roundStock(majorQty)
        val cleanMin = if (!hasMinor || minorQty.isNaN() || minorQty <= 0.0) 0.0 else roundStock(minorQty)
        val cleanFactor = if (factor > 0.0 && !factor.isNaN()) factor else 2.0
        if (!hasMinor || cleanFactor <= 1.0 || cleanMin < cleanFactor) {
            return cleanMaj to cleanMin
        }
        return try {
            val minBd = BigDecimal.valueOf(cleanMin)
            val facBd = BigDecimal.valueOf(cleanFactor)
            val carryMajor = minBd.divideToIntegralValue(facBd)
            val remMinor = minBd.remainder(facBd).setScale(4, RoundingMode.HALF_UP)
            val newMaj = BigDecimal.valueOf(cleanMaj).add(carryMajor).setScale(4, RoundingMode.HALF_UP).toDouble()
            val newMin = if (remMinor.abs().toDouble() < 0.0001) 0.0 else remMinor.toDouble()
            newMaj to newMin
        } catch (_: Exception) {
            cleanMaj to cleanMin
        }
    }

    /**
     * حساب المكافئ بالوحدة الكبرى بدقة عالية من (الوحدة الكبرى + الوحدة الصغرى / معامل التحويل)
     */
    fun computeMajorEquivalent(
        majorQty: Double,
        minorQty: Double,
        factor: Double,
        hasMinor: Boolean
    ): Double {
        val cleanMaj = if (majorQty.isNaN() || majorQty < 0.0) 0.0 else majorQty
        val cleanMin = if (!hasMinor || minorQty.isNaN() || minorQty <= 0.0) 0.0 else minorQty
        val cleanFactor = if (factor > 0.0 && !factor.isNaN()) factor else 2.0
        if (cleanMaj == 0.0 && cleanMin == 0.0) return 0.0

        return try {
            val majBd = BigDecimal.valueOf(cleanMaj)
            val minBd = if (cleanMin > 0.0) {
                BigDecimal.valueOf(cleanMin).divide(BigDecimal.valueOf(cleanFactor), 8, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
            majBd.add(minBd).setScale(6, RoundingMode.HALF_UP).toDouble()
        } catch (_: Exception) {
            roundStock(cleanMaj + (if (cleanMin > 0.0) cleanMin / cleanFactor else 0.0))
        }
    }

    /**
     * حساب إجمالي سطر الصنف بدقة محاسبية موحدة
     */
    fun computeLineTotal(
        majorQty: Double,
        minorQty: Double,
        factor: Double,
        hasMinor: Boolean,
        majorUnitPrice: Double
    ): Double {
        val cleanPrice = if (majorUnitPrice.isNaN() || majorUnitPrice <= 0.0) 0.0 else majorUnitPrice
        if (cleanPrice == 0.0) return 0.0
        val cleanMaj = if (majorQty.isNaN() || majorQty < 0.0) 0.0 else majorQty
        val cleanMin = if (!hasMinor || minorQty.isNaN() || minorQty <= 0.0) 0.0 else minorQty
        val cleanFactor = if (factor > 0.0 && !factor.isNaN()) factor else 2.0
        if (cleanMaj == 0.0 && cleanMin == 0.0) return 0.0

        return try {
            val majBd = BigDecimal.valueOf(cleanMaj)
            val minBd = if (cleanMin > 0.0) {
                BigDecimal.valueOf(cleanMin).divide(BigDecimal.valueOf(cleanFactor), 8, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
            val eqBd = majBd.add(minBd)
            eqBd.multiply(BigDecimal.valueOf(cleanPrice)).setScale(2, RoundingMode.HALF_UP).toDouble()
        } catch (_: Exception) {
            val eq = computeMajorEquivalent(cleanMaj, cleanMin, cleanFactor, hasMinor)
            roundMoney(eq * cleanPrice)
        }
    }

    /**
     * جمع إجماليات الأصناف بدقة BigDecimal
     */
    fun sumRecordTotals(items: List<InvoiceItemRecord>): Double {
        if (items.isEmpty()) return 0.0
        var sumBd = BigDecimal.ZERO
        for (item in items) {
            val t = if (item.total.isNaN() || item.total.isInfinite()) 0.0 else item.total
            sumBd = sumBd.add(BigDecimal.valueOf(t))
        }
        return sumBd.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * الحساب الموحد الشامل للفاتورة (يُستخدم في شاشة البيع، حفظ الفاتورة، تعديل الفاتورة، النوافذ، السجل، التقارير، والرسائل)
     */
    fun computeUnifiedInvoiceAccounting(
        rawItemsTotal: Double,
        discount: Double,
        paidCash: Double,
        previousDebtPaid: Double,
        fallbackGrossSubtotalIfNoItems: Double = 0.0
    ): UnifiedInvoiceAccounting {
        val gross = roundMoney(if (rawItemsTotal > 0.0) rawItemsTotal else fallbackGrossSubtotalIfNoItems.coerceAtLeast(0.0))
        val cleanDiscount = roundMoney(discount.coerceAtLeast(0.0))
        val netSubtotal = subtractMoneyNonNegative(gross, cleanDiscount)
        val cleanPaid = roundMoney(paidCash.coerceAtLeast(0.0))
        val cleanPrevDebt = roundMoney(previousDebtPaid.coerceAtLeast(0.0))
        val remaining = subtractMoneyNonNegative(netSubtotal, cleanPaid)
        val inUserHand = addMoney(cleanPaid, cleanPrevDebt)
        val paidText = formatPaidCellValues(cleanPaid, cleanPrevDebt)

        return UnifiedInvoiceAccounting(
            rawItemsTotal = gross,
            discount = cleanDiscount,
            netInvoiceSubtotal = netSubtotal,
            paidCash = cleanPaid,
            previousDebtPaid = cleanPrevDebt,
            remainingOnInvoice = remaining,
            totalInUserHand = inUserHand,
            paidCellText = paidText
        )
    }

    /**
     * استخراج الحساب الموحد من كيان فاتورة محفوظة
     */
    fun computeUnifiedInvoiceAccounting(invoice: SaleInvoiceEntity): UnifiedInvoiceAccounting {
        val items = decodeInvoiceItemsJson(invoice.itemsJson)
        val rawItemsSum = sumRecordTotals(items)
        val fallbackGross = addMoney(invoice.subtotal, invoice.discount)
        return computeUnifiedInvoiceAccounting(
            rawItemsTotal = rawItemsSum,
            discount = invoice.discount,
            paidCash = invoice.paid,
            previousDebtPaid = invoice.previousDebt,
            fallbackGrossSubtotalIfNoItems = fallbackGross
        )
    }

    /**
     * الحساب الموحد لحركة التوريد (معاينة وحفظ وتعديل)
     */
    fun computeUnifiedSupplyAccounting(
        product: ProductEntity,
        enteredMajor: Double,
        enteredMinor: Double,
        basePrevStock: Double = product.stock
    ): UnifiedSupplyAccounting {
        val hasMinor = product.minorUnit.isNotBlank()
        val factor = if (product.factor > 0.0) product.factor else 2.0
        val (normMaj, normMin) = normalizeMajorMinorPair(enteredMajor, enteredMinor, factor, hasMinor)
        val addedEq = computeMajorEquivalent(normMaj, normMin, factor, hasMinor)
        val cleanPrev = roundStock(basePrevStock)
        val cumulative = addStock(cleanPrev, addedEq)
        val enteredDisp = formatCombinedUnitsArabic(product.majorUnit, product.minorUnit, normMaj, normMin)
        val prevDisp = formatStockMajorMinorArabic(cleanPrev, product)
        val cumDisp = formatStockMajorMinorArabic(cumulative, product)

        return UnifiedSupplyAccounting(
            normalizedMajor = normMaj,
            normalizedMinor = normMin,
            enteredDisplay = enteredDisp,
            addedMajorEquivalent = addedEq,
            prevStock = cleanPrev,
            cumulativeStock = cumulative,
            prevStockDisplay = prevDisp,
            cumulativeStockDisplay = cumDisp
        )
    }

    fun formatStockMajorMinorArabic(totalMajorEquivalent: Double, product: ProductEntity): String {
        if (totalMajorEquivalent.isNaN() || totalMajorEquivalent.isInfinite()) return "0 ${product.majorUnit}"
        val roundedZeroCheck = (totalMajorEquivalent * 1000.0).roundToInt() / 1000.0
        if (roundedZeroCheck == 0.0) {
            return "0 ${product.majorUnit}"
        }

        val isNegative = totalMajorEquivalent < 0.0
        val absMajor = abs(totalMajorEquivalent)
        val majorUnit = product.majorUnit.trim().ifBlank { "كيلو" }
        val minorUnit = product.minorUnit.trim()
        val factor = if (product.factor > 0.0) product.factor else 1.0

        val formatted = if (minorUnit.isNotEmpty() && factor > 1.0) {
            val totalMinorRaw = absMajor * factor
            val totalMinorRounded = (totalMinorRaw * 100.0).roundToInt() / 100.0
            val wholeMajor = ((totalMinorRounded + 0.0001) / factor).toInt()
            val remMinor = ((totalMinorRounded - (wholeMajor * factor)) * 100.0).roundToInt() / 100.0
            val cleanRemMinor = if (remMinor < 0.01) 0.0 else remMinor

            formatCombinedUnitsArabic(
                majorUnit = majorUnit,
                minorUnit = minorUnit,
                majorQty = wholeMajor.toDouble(),
                minorQty = cleanRemMinor
            )
        } else {
            formatQtyWithUnitArabic(absMajor, majorUnit)
        }

        return if (isNegative) "-$formatted" else formatted
    }

    fun getSoldMajorEquivalentInInvoice(invoice: SaleInvoiceEntity, product: ProductEntity): Double {
        val factor = if (product.factor > 0.0) product.factor else 2.0
        val hasMinor = product.minorUnit.isNotBlank() && factor > 0.0
        val items = decodeInvoiceItemsJson(invoice.itemsJson)
        var sum = 0.0
        for (it in items) {
            if (it.key == product.key) {
                sum = addStock(sum, computeMajorEquivalent(it.majorQty, it.minorQty, factor, hasMinor))
            }
        }
        return roundStock(sum)
    }

    /**
     * حساب إحصائيات المخزون اليومية فائقة السرعة عبر تجميعات SQL للتوريدات وإسقاطات الفواتير الخفيفة
     * يضمن المعادلة المحاسبية الصارمة: (المتبقي السابق + وارد اليوم - مباع اليوم = متبقي نهاية اليوم)
     */
    fun computeDailyStockStatsFromAggregatedSupplies(
        targetDate: String,
        products: List<ProductEntity>,
        invoiceProjectionsFromDateOnwards: List<InvoiceDateItemsProjection>,
        aggregatedSupplies: List<SupplyAggregatedStockProjection>
    ): Map<String, ProductDailyStockStats> {
        if (products.isEmpty()) return emptyMap()
        val productMap = products.associateBy { it.key }
        val suppliedOnDateMap = HashMap<String, Double>(products.size * 2)
        val suppliesAfterDateMap = HashMap<String, Double>(products.size * 2)
        val soldOnDateMap = HashMap<String, Double>(products.size * 2)
        val salesAfterDateMap = HashMap<String, Double>(products.size * 2)

        for (sup in aggregatedSupplies) {
            suppliedOnDateMap[sup.productKey] = roundStock(sup.suppliedOnDate)
            suppliesAfterDateMap[sup.productKey] = roundStock(sup.suppliedAfterDate)
        }

        for (inv in invoiceProjectionsFromDateOnwards) {
            if (inv.date < targetDate) continue
            val isOnTargetDate = inv.date == targetDate
            val items = decodeInvoiceItemsJson(inv.itemsJson)
            for (item in items) {
                val p = productMap[item.key] ?: continue
                val factor = if (p.factor > 0.0) p.factor else 2.0
                val hasMinor = p.minorUnit.isNotBlank() && factor > 0.0
                val soldEq = computeMajorEquivalent(item.majorQty, item.minorQty, factor, hasMinor)
                if (soldEq > 0.0) {
                    if (isOnTargetDate) {
                        soldOnDateMap[item.key] = addStock(soldOnDateMap[item.key] ?: 0.0, soldEq)
                    } else {
                        salesAfterDateMap[item.key] = addStock(salesAfterDateMap[item.key] ?: 0.0, soldEq)
                    }
                }
            }
        }

        val result = LinkedHashMap<String, ProductDailyStockStats>(products.size)
        for (p in products) {
            val suppliedOnDate = roundStock(suppliedOnDateMap[p.key] ?: 0.0)
            val suppliesAfterDate = roundStock(suppliesAfterDateMap[p.key] ?: 0.0)
            val soldOnDate = roundStock(soldOnDateMap[p.key] ?: 0.0)
            val salesAfterDate = roundStock(salesAfterDateMap[p.key] ?: 0.0)

            val endOfDayRem = addStock(subtractStock(p.stock, suppliesAfterDate), salesAfterDate)
            val prevDayRem = addStock(subtractStock(endOfDayRem, suppliedOnDate), soldOnDate)

            result[p.key] = ProductDailyStockStats(
                productKey = p.key,
                productName = p.name,
                suppliedToday = suppliedOnDate,
                prevDayRemaining = prevDayRem,
                soldToday = soldOnDate,
                endOfDayRemaining = endOfDayRem,
                suppliedDisplay = if (suppliedOnDate > 0.0001) formatStockMajorMinorArabic(suppliedOnDate, p) else "",
                prevRemainingDisplay = if (abs(prevDayRem) > 0.0005) formatStockMajorMinorArabic(prevDayRem, p) else "",
                soldDisplay = if (soldOnDate > 0.0001) formatStockMajorMinorArabic(soldOnDate, p) else "",
                endRemainingDisplay = if (abs(endOfDayRem) > 0.0005) formatStockMajorMinorArabic(endOfDayRem, p) else "0 ${p.majorUnit}"
            )
        }
        return result
    }

    /**
     * حساب إحصائيات المخزون اليومية بخوارزمية O(N) أحادية المرور عالية السرعة عبر الإسقاطات الخفيفة
     */
    fun computeDailyStockStatsFromProjections(
        targetDate: String,
        products: List<ProductEntity>,
        invoiceProjectionsFromDateOnwards: List<InvoiceDateItemsProjection>,
        supplyProjectionsFromDateOnwards: List<SupplyStockProjection>
    ): Map<String, ProductDailyStockStats> {
        val suppliedOnDateMap = HashMap<String, Double>(products.size * 2)
        val suppliesAfterDateMap = HashMap<String, Double>(products.size * 2)
        for (sup in supplyProjectionsFromDateOnwards) {
            val key = sup.productKey
            if (sup.date == targetDate) {
                suppliedOnDateMap[key] = addStock(suppliedOnDateMap[key] ?: 0.0, sup.addedMajorEquivalent)
            } else if (sup.date > targetDate) {
                suppliesAfterDateMap[key] = addStock(suppliesAfterDateMap[key] ?: 0.0, sup.addedMajorEquivalent)
            }
        }
        val aggregated = products.map { p ->
            SupplyAggregatedStockProjection(
                productKey = p.key,
                suppliedOnDate = suppliedOnDateMap[p.key] ?: 0.0,
                suppliedAfterDate = suppliesAfterDateMap[p.key] ?: 0.0
            )
        }
        return computeDailyStockStatsFromAggregatedSupplies(
            targetDate = targetDate,
            products = products,
            invoiceProjectionsFromDateOnwards = invoiceProjectionsFromDateOnwards,
            aggregatedSupplies = aggregated
        )
    }

    fun computeDailyStockStatsForDate(
        targetDate: String,
        products: List<ProductEntity>,
        invoicesFromDateOnwards: List<SaleInvoiceEntity>,
        suppliesFromDateOnwards: List<SupplyBatchEntity>
    ): Map<String, ProductDailyStockStats> {
        val invProjections = invoicesFromDateOnwards.map { InvoiceDateItemsProjection(it.date, it.itemsJson) }
        val supProjections = suppliesFromDateOnwards.map { SupplyStockProjection(it.productKey, it.date, it.addedMajorEquivalent) }
        return computeDailyStockStatsFromProjections(targetDate, products, invProjections, supProjections)
    }

    /**
     * حساب إحصائيات المخزون اليومية لمجموعة أيام (فترة / أسبوع / شهر / عام كامل) في تمريرة واحدة فائقة السرعة O(N + D*P)
     * يضمن تطابق ترحيل الأرصدة بين الأيام المتتالية بدقة 100% دون تكرار مسح قاعدة البيانات لكل يوم
     */
    fun computeMultiDayStockStatsMap(
        targetDates: List<String>,
        products: List<ProductEntity>,
        invoiceProjectionsFromMinDateOnwards: List<InvoiceDateItemsProjection>,
        supplyProjectionsByDateFromMinDateOnwards: List<DateProductSupplyProjection>
    ): Map<String, Map<String, ProductDailyStockStats>> {
        if (targetDates.isEmpty() || products.isEmpty()) return emptyMap()
        val minDate = targetDates.minOrNull() ?: return emptyMap()
        val targetDateSet = targetDates.toHashSet()
        val productMap = products.associateBy { it.key }

        val allDatesSet = HashSet<String>(targetDates.size * 2)
        allDatesSet.addAll(targetDates)

        val suppliedByDateProduct = HashMap<String, MutableMap<String, Double>>()
        for (sup in supplyProjectionsByDateFromMinDateOnwards) {
            if (sup.date < minDate) continue
            allDatesSet.add(sup.date)
            val dayMap = suppliedByDateProduct.getOrPut(sup.date) { HashMap() }
            dayMap[sup.productKey] = addStock(dayMap[sup.productKey] ?: 0.0, sup.totalSupplied)
        }

        val soldByDateProduct = HashMap<String, MutableMap<String, Double>>()
        for (inv in invoiceProjectionsFromMinDateOnwards) {
            if (inv.date < minDate) continue
            allDatesSet.add(inv.date)
            val items = decodeInvoiceItemsJson(inv.itemsJson)
            if (items.isEmpty()) continue
            val dayMap = soldByDateProduct.getOrPut(inv.date) { HashMap() }
            for (item in items) {
                val p = productMap[item.key] ?: continue
                val factor = if (p.factor > 0.0) p.factor else 2.0
                val hasMinor = p.minorUnit.isNotBlank() && factor > 0.0
                val soldEq = computeMajorEquivalent(item.majorQty, item.minorQty, factor, hasMinor)
                if (soldEq > 0.0) {
                    dayMap[item.key] = addStock(dayMap[item.key] ?: 0.0, soldEq)
                }
            }
        }

        val datesDescending = allDatesSet.sortedDescending()
        val runningSuppliesAfter = HashMap<String, Double>(products.size * 2)
        val runningSalesAfter = HashMap<String, Double>(products.size * 2)
        val resultByDate = HashMap<String, Map<String, ProductDailyStockStats>>(targetDates.size * 2)

        for (d in datesDescending) {
            val daySupMap = suppliedByDateProduct[d]
            val daySoldMap = soldByDateProduct[d]

            if (targetDateSet.contains(d)) {
                val dayStats = LinkedHashMap<String, ProductDailyStockStats>(products.size)
                for (p in products) {
                    val suppliedOnDate = roundStock(daySupMap?.get(p.key) ?: 0.0)
                    val soldOnDate = roundStock(daySoldMap?.get(p.key) ?: 0.0)
                    val suppliesAfterDate = roundStock(runningSuppliesAfter[p.key] ?: 0.0)
                    val salesAfterDate = roundStock(runningSalesAfter[p.key] ?: 0.0)

                    val endOfDayRem = addStock(subtractStock(p.stock, suppliesAfterDate), salesAfterDate)
                    val prevDayRem = addStock(subtractStock(endOfDayRem, suppliedOnDate), soldOnDate)

                    dayStats[p.key] = ProductDailyStockStats(
                        productKey = p.key,
                        productName = p.name,
                        suppliedToday = suppliedOnDate,
                        prevDayRemaining = prevDayRem,
                        soldToday = soldOnDate,
                        endOfDayRemaining = endOfDayRem,
                        suppliedDisplay = if (suppliedOnDate > 0.0001) formatStockMajorMinorArabic(suppliedOnDate, p) else "",
                        prevRemainingDisplay = if (abs(prevDayRem) > 0.0005) formatStockMajorMinorArabic(prevDayRem, p) else "",
                        soldDisplay = if (soldOnDate > 0.0001) formatStockMajorMinorArabic(soldOnDate, p) else "",
                        endRemainingDisplay = if (abs(endOfDayRem) > 0.0005) formatStockMajorMinorArabic(endOfDayRem, p) else "0 ${p.majorUnit}"
                    )
                }
                resultByDate[d] = dayStats
            }

            for (p in products) {
                val suppliedOnDate = daySupMap?.get(p.key) ?: 0.0
                val soldOnDate = daySoldMap?.get(p.key) ?: 0.0
                if (suppliedOnDate != 0.0) {
                    runningSuppliesAfter[p.key] = addStock(runningSuppliesAfter[p.key] ?: 0.0, suppliedOnDate)
                }
                if (soldOnDate != 0.0) {
                    runningSalesAfter[p.key] = addStock(runningSalesAfter[p.key] ?: 0.0, soldOnDate)
                }
            }
        }

        return resultByDate
    }

    /**
     * حساب الملخص المالي اليومي الموحد بدقة BigDecimal
     */
    fun computeDailyFinancialTotals(dayInvoices: List<SaleInvoiceEntity>): DailyFinancialTotals {
        var totalSubtotalBd = BigDecimal.ZERO
        var totalDiscountBd = BigDecimal.ZERO
        var totalCashBd = BigDecimal.ZERO
        var totalPrevDebtBd = BigDecimal.ZERO
        var totalRemBd = BigDecimal.ZERO

        for (inv in dayInvoices) {
            val acc = computeUnifiedInvoiceAccounting(inv)
            totalSubtotalBd = totalSubtotalBd.add(BigDecimal.valueOf(acc.netInvoiceSubtotal))
            totalDiscountBd = totalDiscountBd.add(BigDecimal.valueOf(acc.discount))
            totalCashBd = totalCashBd.add(BigDecimal.valueOf(acc.paidCash))
            totalPrevDebtBd = totalPrevDebtBd.add(BigDecimal.valueOf(acc.previousDebtPaid))
            totalRemBd = totalRemBd.add(BigDecimal.valueOf(acc.remainingOnInvoice))
        }

        val totalSubtotal = totalSubtotalBd.setScale(2, RoundingMode.HALF_UP).toDouble()
        val totalDiscount = totalDiscountBd.setScale(2, RoundingMode.HALF_UP).toDouble()
        val totalCash = totalCashBd.setScale(2, RoundingMode.HALF_UP).toDouble()
        val totalPrevDebt = totalPrevDebtBd.setScale(2, RoundingMode.HALF_UP).toDouble()
        val totalInHand = totalCashBd.add(totalPrevDebtBd).setScale(2, RoundingMode.HALF_UP).toDouble()
        val totalRem = totalRemBd.setScale(2, RoundingMode.HALF_UP).toDouble()

        return DailyFinancialTotals(
            totalInvoicesSubtotal = totalSubtotal,
            totalCashReceived = totalCash,
            totalPrevDebtNotified = totalPrevDebt,
            totalCashInUserHand = totalInHand,
            totalInvoicesRemaining = totalRem,
            totalDiscountGiven = totalDiscount,
            matchedInvoicesCount = dayInvoices.size
        )
    }

    /**
     * تحويل مخرجات استعلام SQL التجميعي إلى النموذج المالي الموحد بدقة عالية
     */
    fun computeFinancialTotalsFromTuple(tuple: FinancialSummaryTuple): DailyFinancialTotals {
        val totalSubtotal = roundMoney(tuple.totalInvoicesSubtotal)
        val totalDiscount = roundMoney(tuple.totalDiscountGiven)
        val totalCash = roundMoney(tuple.totalCashReceived)
        val totalPrevDebt = roundMoney(tuple.totalPrevDebtNotified)
        val totalInHand = addMoney(totalCash, totalPrevDebt)
        val totalRem = roundMoney(tuple.totalInvoicesRemaining)
        return DailyFinancialTotals(
            totalInvoicesSubtotal = totalSubtotal,
            totalCashReceived = totalCash,
            totalPrevDebtNotified = totalPrevDebt,
            totalCashInUserHand = totalInHand,
            totalInvoicesRemaining = totalRem,
            totalDiscountGiven = totalDiscount,
            matchedInvoicesCount = tuple.matchedCount
        )
    }

    /**
     * تنسيق نص خلية "مسلم" بشكل موحد في جميع الواجهات والكشوفات والملفات
     */
    fun formatPaidCellValues(paid: Double, previousDebt: Double): String {
        val cleanPaid = roundMoney(paid)
        val cleanPrevDebt = roundMoney(previousDebt)
        val hasCash = cleanPaid > 0.0
        val hasPrevDebt = cleanPrevDebt > 0.0
        return when {
            hasCash && hasPrevDebt -> "${cleanPaid.cleanNum()} + ${cleanPrevDebt.cleanNum()} سداد دين سابق"
            hasPrevDebt -> "${cleanPrevDebt.cleanNum()} سداد دين سابق"
            hasCash -> cleanPaid.cleanNum()
            else -> "0"
        }
    }

    fun formatPaidCellText(inv: SaleInvoiceEntity): String =
        formatPaidCellValues(inv.paid, inv.previousDebt)

    fun buildCustomerWhatsAppMessage(
        invNum: String, dayName: String, date: String, time: String, customerName: String,
        items: List<InvoiceItemRecord>, subtotal: Double, prevDebt: Double, discount: Double,
        paid: Double, remaining: Double, currency: String, userName: String = ""
    ): String = WhatsAppMessageFormatter.buildCustomerWhatsAppMessage(
        invNum, dayName, date, time, customerName, items, subtotal, prevDebt, discount, paid, remaining, currency, userName
    )

    fun buildManagerInvoiceWhatsAppMessage(
        invNum: String, dayName: String, date: String, time: String, customerName: String,
        items: List<InvoiceItemRecord>, subtotal: Double, prevDebt: Double, discount: Double,
        paid: Double, remaining: Double, currency: String, updatedProductsMap: Map<String, ProductEntity>,
        userName: String = ""
    ): String = WhatsAppMessageFormatter.buildManagerInvoiceWhatsAppMessage(
        invNum, dayName, date, time, customerName, items, subtotal, prevDebt, discount, paid, remaining, currency, updatedProductsMap, userName
    )

    fun buildManagerSupplyWhatsAppMessage(
        batchNum: String, dayName: String, date: String, time: String, product: ProductEntity,
        prevStock: Double, enteredDisplay: String, cumulativeStock: Double, notes: String,
        userName: String = ""
    ): String = WhatsAppMessageFormatter.buildManagerSupplyWhatsAppMessage(
        batchNum, dayName, date, time, product, prevStock, enteredDisplay, cumulativeStock, notes, userName
    )

    fun buildDailyLedgerWhatsAppReport(
        dateStr: String, dayName: String, products: List<ProductEntity>, dayInvoices: List<SaleInvoiceEntity>,
        daySupplies: List<SupplyBatchEntity>, stockStats: Map<String, ProductDailyStockStats>, currency: String,
        userName: String = ""
    ): String = WhatsAppMessageFormatter.buildDailyLedgerWhatsAppReport(
        dateStr, dayName, products, dayInvoices, daySupplies, stockStats, currency, userName
    )
}
