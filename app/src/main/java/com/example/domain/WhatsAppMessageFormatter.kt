package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.data.SupplyBatchEntity
import com.example.util.InvoiceItemRecord
import com.example.util.cleanNum
import com.example.util.decodeInvoiceItemsJson
import kotlin.math.abs

/**
 * منسق رسائل الواتساب الرسمية والمباشرة المرتبط كلياً بالمحرك المحاسبي الموحد (LedgerCalculator)
 */
object WhatsAppMessageFormatter {

    /**
     * تنظيف أي نص من العبارات الترحيبية أو الصياغات القديمة وتوحيد عبارة "سداد دين سابق" وإرفاق تذييل اسم المستخدم إن وُجد
     */
    fun sanitizeDirectMessage(rawText: String, userName: String = ""): String {
        val greetingPrefixes = listOf(
            "حياك الله",
            "حضرة المدير",
            "يا حضرة المدير",
            "مرحبا",
            "مرحباً",
            "السلام عليكم",
            "تحية طيبة",
            "عزيزي العميل",
            "إليك تفاصيل",
            "اليك تفاصيل"
        )
        val cleanUser = userName.trim()
        val cleanedLines = rawText
            .lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("بواسطة:") ||
                    greetingPrefixes.any { prefix ->
                        trimmed.startsWith(prefix) || trimmed.contains("حياك الله") || trimmed.contains("حضرة المدير")
                    }
            }
            .map { line ->
                val normalized = line
                    .replace("مسلم دين سابق (إشعار):", "سداد دين سابق:")
                    .replace("إشعار مسلم دين سابق:", "سداد دين سابق:")
                    .replace("إشعار دين سابق:", "سداد دين سابق:")
                    .replace("إجمالي الديون السابقة المستلمة (إشعار):", "إجمالي سداد الديون السابقة:")
                val trimmed = normalized.trim()
                val withEmoji = when {
                    trimmed.startsWith("فاتورة رقم:") -> "🔴 $trimmed"
                    trimmed.startsWith("توريد بضاعة رقم:") -> "🟢 $trimmed"
                    else -> normalized
                }
                if ((trimmed.startsWith("مسلم دين سابق") || trimmed.startsWith("سداد دين سابق")) &&
                    trimmed.endsWith("دين سابق")
                ) {
                    trimmed.removeSuffix("دين سابق").trimEnd()
                } else {
                    withEmoji
                }
            }
            .toMutableList()

        while (cleanedLines.isNotEmpty() && cleanedLines.last().trim().isEmpty()) {
            cleanedLines.removeAt(cleanedLines.lastIndex)
        }

        val alreadyHasUserHandMention = cleanedLines.any {
            it.contains("بيد المستخدم") || it.contains("كشف حساب اليومية")
        }
        if (cleanUser.isNotEmpty() && !alreadyHasUserHandMention) {
            cleanedLines.add("بواسطة: $cleanUser")
        }

        return cleanedLines.joinToString("\n").trim()
    }

    fun formatInternationalPhone(rawPhone: String): String {
        val normalized = com.example.util.ContactPickerHelper.normalizePhoneDigits(rawPhone)
        val cleanPhone = normalized.replace(Regex("[^0-9]"), "")
        if (cleanPhone.isEmpty()) return ""
        return when {
            cleanPhone.startsWith("00") -> cleanPhone.substring(2)
            cleanPhone.length == 10 && cleanPhone.startsWith("07") -> "967${cleanPhone.substring(1)}"
            cleanPhone.length == 9 && cleanPhone.startsWith("7") -> "967$cleanPhone"
            else -> cleanPhone
        }
    }

    /**
     * رسالة واتساب رسمية ومباشرة للعميل معتمدة على الحساب الموحد (UnifiedInvoiceAccounting) مع إيموجي الفاتورة الأحمر 🔴
     */
    fun buildCustomerWhatsAppMessage(
        invNum: String,
        dayName: String,
        date: String,
        time: String,
        customerName: String,
        items: List<InvoiceItemRecord>,
        subtotal: Double,
        prevDebt: Double,
        discount: Double,
        paid: Double,
        remaining: Double,
        currency: String,
        userName: String = ""
    ): String = buildString {
        val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = LedgerCalculator.sumRecordTotals(items),
            discount = discount,
            paidCash = paid,
            previousDebtPaid = prevDebt,
            fallbackGrossSubtotalIfNoItems = LedgerCalculator.addMoney(subtotal, discount)
        )

        appendLine("🔴 فاتورة رقم: $invNum")
        appendLine("العميل: $customerName")
        appendLine("اليوم: $dayName | التاريخ: $date | الوقت: $time")
        appendLine("────────────────")
        if (items.isNotEmpty()) {
            items.forEach { item ->
                appendLine("• ${item.name}: ${item.unitDisplay} (${item.total.cleanNum()} $currency)")
            }
            appendLine("────────────────")
        }
        if (acc.discount > 0) {
            if (acc.rawItemsTotal > 0) {
                appendLine("إجمالي الأصناف: ${acc.rawItemsTotal.cleanNum()} $currency")
            }
            appendLine("الخصم: ${acc.discount.cleanNum()} $currency")
        }
        appendLine("إجمالي الفاتورة: ${acc.netInvoiceSubtotal.cleanNum()} $currency")
        appendLine("المسلم نقداً: ${acc.paidCash.cleanNum()} $currency")
        appendLine("المتبقي من الفاتورة: ${acc.remainingOnInvoice.cleanNum()} $currency")
        if (acc.previousDebtPaid > 0) {
            appendLine("سداد دين سابق: ${acc.previousDebtPaid.cleanNum()} $currency")
            if (acc.paidCash > 0) {
                appendLine("إجمالي المسلم الكلي: ${acc.totalInUserHand.cleanNum()} $currency")
            }
        }
        val cleanUser = userName.trim()
        if (cleanUser.isNotEmpty()) {
            appendLine("────────────────")
            appendLine("بواسطة: $cleanUser")
        }
    }.trim()

    /**
     * رسالة واتساب مباشرة للمدير معتمدة على الحساب الموحد (UnifiedInvoiceAccounting) ومتبقي المخزون الدقيق مع إيموجي الفاتورة الأحمر 🔴
     */
    fun buildManagerInvoiceWhatsAppMessage(
        invNum: String,
        dayName: String,
        date: String,
        time: String,
        customerName: String,
        items: List<InvoiceItemRecord>,
        subtotal: Double,
        prevDebt: Double,
        discount: Double,
        paid: Double,
        remaining: Double,
        currency: String,
        updatedProductsMap: Map<String, ProductEntity>,
        userName: String = ""
    ): String = buildString {
        val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = LedgerCalculator.sumRecordTotals(items),
            discount = discount,
            paidCash = paid,
            previousDebtPaid = prevDebt,
            fallbackGrossSubtotalIfNoItems = LedgerCalculator.addMoney(subtotal, discount)
        )

        appendLine("🔴 فاتورة رقم: $invNum")
        appendLine("العميل: $customerName")
        appendLine("اليوم: $dayName | التاريخ: $date | الوقت: $time")
        appendLine("────────────────")
        if (items.isNotEmpty()) {
            items.forEach { item ->
                appendLine("• ${item.name}: ${item.unitDisplay} (${item.total.cleanNum()} $currency)")
            }
            appendLine("────────────────")
        }
        if (acc.discount > 0) {
            if (acc.rawItemsTotal > 0) {
                appendLine("إجمالي الأصناف: ${acc.rawItemsTotal.cleanNum()} $currency")
            }
            appendLine("الخصم: ${acc.discount.cleanNum()} $currency")
        }
        appendLine("إجمالي الفاتورة: ${acc.netInvoiceSubtotal.cleanNum()} $currency")
        appendLine("المسلم نقداً: ${acc.paidCash.cleanNum()} $currency")
        appendLine("المتبقي من الفاتورة: ${acc.remainingOnInvoice.cleanNum()} $currency")
        if (acc.previousDebtPaid > 0) {
            appendLine("سداد دين سابق: ${acc.previousDebtPaid.cleanNum()} $currency")
            if (acc.paidCash > 0) {
                appendLine("إجمالي المستلم بيد المستخدم: ${acc.totalInUserHand.cleanNum()} $currency")
            }
        }

        if (items.isNotEmpty()) {
            appendLine("────────────────")
            appendLine("المتبقي في المخزن من الأصناف المباعة:")
            items.map { it.key }.distinct().forEach { key ->
                val prod = updatedProductsMap[key]
                if (prod != null) {
                    val stockFormatted = LedgerCalculator.formatStockMajorMinorArabic(prod.stock, prod)
                    appendLine("• ${prod.name}: $stockFormatted")
                }
            }
        }
        val cleanUser = userName.trim()
        if (cleanUser.isNotEmpty()) {
            appendLine("────────────────")
            appendLine("بواسطة: $cleanUser")
        }
    }.trim()

    /**
     * رسالة التوريد المباشرة لواتساب المدير مع إيموجي التوريد الأخضر 🟢 وتذييل اسم المستخدم
     */
    fun buildManagerSupplyWhatsAppMessage(
        batchNum: String,
        dayName: String,
        date: String,
        time: String,
        product: ProductEntity,
        prevStock: Double,
        enteredDisplay: String,
        cumulativeStock: Double,
        notes: String,
        userName: String = ""
    ): String = buildString {
        val prevFormatted = LedgerCalculator.formatStockMajorMinorArabic(prevStock, product)
        val totalFormatted = LedgerCalculator.formatStockMajorMinorArabic(cumulativeStock, product)
        appendLine("🟢 توريد بضاعة رقم: $batchNum")
        appendLine("اليوم: $dayName | التاريخ: $date | الوقت: $time")
        appendLine("────────────────")
        appendLine("الصنف: ${product.name}")
        appendLine("العدد السابق المتوفر: $prevFormatted")
        appendLine("العدد المورد: $enteredDisplay")
        appendLine("الإجمالي الكلي الحالي: $totalFormatted")
        if (notes.isNotBlank()) {
            appendLine("ملاحظات: $notes")
        }
        val cleanUser = userName.trim()
        if (cleanUser.isNotEmpty()) {
            appendLine("────────────────")
            appendLine("بواسطة: $cleanUser")
        }
    }.trim()

    /**
     * تقرير كشف الحساب اليومي المباشر المعتمد كلياً على الحساب الموحد (LedgerCalculator)
     */
    fun buildDailyLedgerWhatsAppReport(
        dateStr: String,
        dayName: String,
        products: List<ProductEntity>,
        dayInvoices: List<SaleInvoiceEntity>,
        daySupplies: List<SupplyBatchEntity>,
        stockStats: Map<String, ProductDailyStockStats>,
        currency: String,
        userName: String = ""
    ): String = buildString {
        val fin = LedgerCalculator.computeDailyFinancialTotals(dayInvoices)
        val cleanUser = userName.trim()
        val userHandLabel = if (cleanUser.isNotEmpty()) "المبلغ الإجمالي الكلي بيد المستخدم ($cleanUser)" else "المبلغ الإجمالي الكلي بيد المستخدم"

        appendLine("كشف حساب اليومية")
        appendLine("اليوم: $dayName | التاريخ: $dateStr م")
        appendLine("════════════════")
        if (daySupplies.isNotEmpty()) {
            appendLine("وارد بضاعة جديد اليوم:")
            daySupplies.forEach { s ->
                val prod = products.find { it.key == s.productKey }
                val prevDisp = if (prod != null) LedgerCalculator.formatStockMajorMinorArabic(s.prevStock, prod) else "${s.prevStock.cleanNum()} ${s.majorUnit}"
                val cumDisp = if (prod != null) LedgerCalculator.formatStockMajorMinorArabic(s.cumulativeStock, prod) else "${s.cumulativeStock.cleanNum()} ${s.majorUnit}"
                appendLine("• ${s.productName} [${s.batchNumber}]: سابق ($prevDisp) + وارد (${s.enteredDisplay}) = الإجمالي ($cumDisp)")
            }
            appendLine("────────────────")
        }
        if (dayInvoices.isNotEmpty()) {
            appendLine("حركة الفواتير (${dayInvoices.size}):")
            dayInvoices.forEachIndexed { i, inv ->
                val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(inv)
                val itemsSummary = decodeInvoiceItemsJson(inv.itemsJson).joinToString(" + ") { "${it.name} (${it.unitDisplay})" }
                appendLine("${i + 1}. ${inv.customerName} [${inv.invoiceNumber}]")
                if (itemsSummary.isNotBlank()) appendLine("   الأصناف: $itemsSummary")
                appendLine("   إجمالي الفاتورة: ${acc.netInvoiceSubtotal.cleanNum()} | مسلم: ${acc.paidCellText} | المتبقي: ${acc.remainingOnInvoice.cleanNum()} $currency")
            }
            appendLine("════════════════")
            appendLine("ملخص الحسابات اليومية:")
            appendLine("• إجمالي فواتير اليوم: ${fin.totalInvoicesSubtotal.cleanNum()} $currency")
            if (fin.totalDiscountGiven > 0) {
                appendLine("• إجمالي الخصومات الممنوحة: ${fin.totalDiscountGiven.cleanNum()} $currency")
            }
            appendLine("• إجمالي النقد المستلم (فواتير اليوم): ${fin.totalCashReceived.cleanNum()} $currency")
            appendLine("• إجمالي سداد الديون السابقة: ${fin.totalPrevDebtNotified.cleanNum()} $currency")
            appendLine("• $userHandLabel: ${fin.totalCashInUserHand.cleanNum()} $currency")
            appendLine("• إجمالي المتبقي (ديون اليوم): ${fin.totalInvoicesRemaining.cleanNum()} $currency")
        }

        val activeStockItems = products.filter { p ->
            val st = stockStats[p.key]
            st != null && (abs(st.suppliedToday) > 0.001 || abs(st.prevDayRemaining) > 0.001 || abs(st.soldToday) > 0.001 || abs(st.endOfDayRemaining) > 0.001)
        }
        if (activeStockItems.isNotEmpty()) {
            appendLine("────────────────")
            appendLine("حركة ومتبقي الأصناف (بالوحدة الكبرى والصغرى):")
            activeStockItems.forEach { p ->
                val st = stockStats[p.key] ?: return@forEach
                val supDisp = st.suppliedDisplay.ifBlank { "0 ${p.majorUnit}" }
                val prevDisp = st.prevRemainingDisplay.ifBlank { "0 ${p.majorUnit}" }
                val soldDisp = st.soldDisplay.ifBlank { "0 ${p.majorUnit}" }
                val remDisp = st.endRemainingDisplay.ifBlank { "0 ${p.majorUnit}" }
                appendLine("• ${p.name}: وارد ($supDisp) + سابق ($prevDisp) - مباع ($soldDisp) = متبقي اليوم: $remDisp")
            }
        }
    }.trim()

    fun openWhatsAppDirectOrShare(context: Context, text: String, rawPhone: String = "", userName: String = "") {
        val effectiveUser = userName.ifBlank {
            context.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
                .getString("user_name", "") ?: ""
        }
        val directText = sanitizeDirectMessage(text, effectiveUser)
        try {
            val formattedPhone = formatInternationalPhone(rawPhone)
            if (formattedPhone.isNotEmpty()) {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(directText)}")
                val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waIntent)
                return
            }
        } catch (_: Exception) {
        }

        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, directText)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(sendIntent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, directText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallback, "مشاركة عبر واتساب").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر فتح واتساب", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
