package com.example.ui

import com.example.data.*
import com.example.domain.LedgerCalculator
import com.example.domain.WhatsAppMessageFormatter
import com.example.util.*
import org.json.JSONObject

data class RestoredCountersResult(
    val salesCounter: Int,
    val supplyCounter: Int,
    val managerPhone: String,
    val userName: String,
    val activeOpenSheetDate: String = "",
    val closedSheetDates: Set<String> = emptySet()
)

/**
 * العمليات التنفيذية لقاعدة البيانات وحفظ وتعديل الفواتير والتوريدات واستعادة أو تهيئة البيانات
 * مرتبطة كلياً بالمحرك المحاسبي الموحد (LedgerCalculator) ومُهيأة للعمل مع ملايين المعاملات
 */
object TradeRepositoryOperations {

    suspend fun createAndPersistSaleInvoice(
        dao: SmartTradeDao,
        invNum: String,
        dt: ArabicDateTimeInfo,
        custName: String,
        custPhone: String,
        currentCart: List<CartItemUi>,
        netInvoiceTotal: Double,
        prevDebt: Double,
        discount: Double,
        paid: Double,
        remaining: Double,
        currency: String,
        userName: String = "",
        invoiceNote: String = "",
        ledgerSheetDate: String = dt.date
    ): SaleInvoiceEntity {
        val cleanNote = invoiceNote.trim()
        val effectiveSheetDate = ledgerSheetDate.trim().ifBlank { dt.date }
        val records = currentCart.map {
            InvoiceItemRecord(
                key = it.productKey,
                name = it.productName,
                unitDisplay = it.unitDisplay,
                majorQty = it.majorQty,
                minorQty = it.minorQty,
                unitPrice = it.majorUnitPrice,
                total = it.total
            )
        }
        val itemsJson = encodeInvoiceItemsJson(records)

        val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = LedgerCalculator.sumRecordTotals(records),
            discount = discount,
            paidCash = paid,
            previousDebtPaid = prevDebt,
            fallbackGrossSubtotalIfNoItems = LedgerCalculator.addMoney(netInvoiceTotal, discount)
        )

        val allProds = dao.getAllProducts().associateBy { it.key }.toMutableMap()
        val modifiedProds = mutableListOf<ProductEntity>()
        currentCart.forEach { cartItem ->
            val prod = allProds[cartItem.productKey]
            if (prod != null) {
                val factor = if (prod.factor > 0.0) prod.factor else 2.0
                val hasMinor = prod.minorUnit.isNotBlank() && factor > 0.0
                val soldEq = LedgerCalculator.computeMajorEquivalent(
                    majorQty = cartItem.majorQty,
                    minorQty = cartItem.minorQty,
                    factor = factor,
                    hasMinor = hasMinor
                )
                val newStock = LedgerCalculator.subtractStock(prod.stock, soldEq).coerceAtLeast(0.0)
                val updatedProd = prod.copy(stock = newStock)
                allProds[cartItem.productKey] = updatedProd
                modifiedProds.add(updatedProd)
            }
        }
        if (modifiedProds.isNotEmpty()) {
            dao.upsertProducts(modifiedProds)
        }

        val existingCustomer = if (custName.isNotBlank() && custName != "عميل نقدي") {
            dao.getCustomerByName(custName)
        } else null
        val effectivePhone = ContactPickerHelper.normalizePhoneDigits(custPhone)
            .ifBlank { existingCustomer?.phone.orEmpty() }

        val customerMsg = LedgerCalculator.buildCustomerWhatsAppMessage(
            invNum = invNum,
            dayName = dt.dayName,
            date = dt.date,
            time = dt.time12h,
            customerName = custName,
            items = records,
            subtotal = acc.netInvoiceSubtotal,
            prevDebt = acc.previousDebtPaid,
            discount = acc.discount,
            paid = acc.paidCash,
            remaining = acc.remainingOnInvoice,
            currency = currency,
            userName = userName,
            notes = cleanNote
        )
        val managerMsg = LedgerCalculator.buildManagerInvoiceWhatsAppMessage(
            invNum = invNum,
            dayName = dt.dayName,
            date = dt.date,
            time = dt.time12h,
            customerName = custName,
            items = records,
            subtotal = acc.netInvoiceSubtotal,
            prevDebt = acc.previousDebtPaid,
            discount = acc.discount,
            paid = acc.paidCash,
            remaining = acc.remainingOnInvoice,
            currency = currency,
            updatedProductsMap = allProds,
            userName = userName,
            notes = cleanNote
        )

        val invoiceEntity = SaleInvoiceEntity(
            id = "INV_${dt.timestamp}",
            invoiceNumber = invNum,
            marker = "🔴",
            customerName = custName,
            customerPhone = effectivePhone,
            subtotal = acc.netInvoiceSubtotal,
            previousDebt = acc.previousDebtPaid,
            isPrevDebtPaidModel = acc.previousDebtPaid > 0.0,
            discount = acc.discount,
            totalDue = acc.netInvoiceSubtotal,
            paid = acc.paidCash,
            remaining = acc.remainingOnInvoice,
            suppliedGoodsNote = cleanNote,
            dayName = dt.dayName,
            date = effectiveSheetDate,
            time = dt.time12h,
            timestamp = dt.timestamp,
            itemsJson = itemsJson,
            whatsappText = customerMsg,
            managerWhatsappText = managerMsg
        )

        dao.insertSaleInvoice(invoiceEntity)
        if (custName.isNotBlank() && custName != "عميل نقدي") {
            dao.upsertCustomer(CustomerEntity(custName, effectivePhone, acc.remainingOnInvoice, dt.timestamp))
            if (effectivePhone.isNotBlank()) {
                dao.updateCustomerPhoneByName(custName, effectivePhone)
            }
        }
        return invoiceEntity
    }

    suspend fun savePickedCustomerContact(
        dao: SmartTradeDao,
        customerName: String,
        customerPhone: String
    ) {
        val cleanName = customerName.trim()
        val cleanPhone = ContactPickerHelper.normalizePhoneDigits(customerPhone)
        if (cleanName.isBlank()) return
        val existing = dao.getCustomerByName(cleanName)
        dao.upsertCustomer(
            CustomerEntity(
                name = cleanName,
                phone = cleanPhone,
                lastRemainingDebt = existing?.lastRemainingDebt ?: 0.0,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (cleanPhone.isNotBlank()) {
            dao.updateCustomerPhoneByName(cleanName, cleanPhone)
        }
    }

    suspend fun updateCustomerContactInfo(
        dao: SmartTradeDao,
        invoice: SaleInvoiceEntity,
        newCustomerName: String,
        newCustomerPhone: String,
        currency: String,
        userName: String
    ) {
        val oldName = invoice.customerName
        val cleanName = newCustomerName.trim().ifBlank { oldName }
        val cleanPhone = ContactPickerHelper.normalizePhoneDigits(newCustomerPhone)
        val prodsMap = dao.getAllProducts().associateBy { it.key }

        if (oldName == cleanName && oldName.isNotBlank() && oldName != "عميل نقدي") {
            // تحديث سريع مفهرس لرقم الهاتف لجميع فواتير العميل دون استهلاك الذاكرة
            dao.updateCustomerPhoneByName(cleanName, cleanPhone)
            dao.upsertCustomer(CustomerEntity(cleanName, cleanPhone, invoice.remaining, System.currentTimeMillis()))
            return
        }

        val targetInvoices = if (oldName.isNotBlank() && oldName != "عميل نقدي") {
            dao.getSalesInvoicesByCustomerName(oldName).ifEmpty { listOf(invoice) }
        } else {
            listOf(invoice)
        }

        val updatedInvoices = targetInvoices.map { inv ->
            val items = decodeInvoiceItemsJson(inv.itemsJson)
            val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(inv)
            val actualDate = inv.actualRecordDate()
            val customerMsg = LedgerCalculator.buildCustomerWhatsAppMessage(
                inv.invoiceNumber, inv.dayName, actualDate, inv.time,
                cleanName, items, acc.netInvoiceSubtotal, acc.previousDebtPaid, acc.discount, acc.paidCash, acc.remainingOnInvoice, currency, userName, inv.suppliedGoodsNote
            )
            val managerMsg = LedgerCalculator.buildManagerInvoiceWhatsAppMessage(
                inv.invoiceNumber, inv.dayName, actualDate, inv.time,
                cleanName, items, acc.netInvoiceSubtotal, acc.previousDebtPaid, acc.discount, acc.paidCash, acc.remainingOnInvoice, currency, prodsMap, userName, inv.suppliedGoodsNote
            )
            inv.copy(
                customerName = cleanName,
                customerPhone = cleanPhone,
                whatsappText = customerMsg,
                managerWhatsappText = managerMsg
            )
        }
        dao.insertSaleInvoices(updatedInvoices)

        if (oldName != cleanName && oldName.isNotBlank()) {
            dao.deleteCustomerByName(oldName)
        }
        if (cleanName.isNotBlank() && cleanName != "عميل نقدي") {
            dao.upsertCustomer(CustomerEntity(cleanName, cleanPhone, invoice.remaining, System.currentTimeMillis()))
        }
    }

    suspend fun updateExistingSaleInvoice(
        dao: SmartTradeDao,
        invoice: SaleInvoiceEntity,
        newCustomerName: String,
        newPaid: Double,
        newPrevDebt: Double,
        newDiscount: Double,
        currency: String,
        userName: String,
        newNote: String = invoice.suppliedGoodsNote
    ) {
        val cleanNote = newNote.trim()
        val items = decodeInvoiceItemsJson(invoice.itemsJson)
        val rawTotal = LedgerCalculator.sumRecordTotals(items)
        val fallbackGross = LedgerCalculator.addMoney(invoice.subtotal, invoice.discount)
        val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = rawTotal,
            discount = newDiscount,
            paidCash = newPaid,
            previousDebtPaid = newPrevDebt,
            fallbackGrossSubtotalIfNoItems = fallbackGross
        )
        val cleanCust = newCustomerName.trim().ifBlank { invoice.customerName }
        val existingCust = dao.getCustomerByName(cleanCust)
        val effectivePhone = invoice.customerPhone.ifBlank { existingCust?.phone.orEmpty() }
        val prodsMap = dao.getAllProducts().associateBy { it.key }

        val actualDate = invoice.actualRecordDate()
        val customerMsg = LedgerCalculator.buildCustomerWhatsAppMessage(
            invoice.invoiceNumber, invoice.dayName, actualDate, invoice.time,
            cleanCust, items, acc.netInvoiceSubtotal, acc.previousDebtPaid, acc.discount, acc.paidCash, acc.remainingOnInvoice, currency, userName, cleanNote
        )
        val managerMsg = LedgerCalculator.buildManagerInvoiceWhatsAppMessage(
            invoice.invoiceNumber, invoice.dayName, actualDate, invoice.time,
            cleanCust, items, acc.netInvoiceSubtotal, acc.previousDebtPaid, acc.discount, acc.paidCash, acc.remainingOnInvoice, currency, prodsMap, userName, cleanNote
        )

        val updated = invoice.copy(
            customerName = cleanCust,
            customerPhone = effectivePhone,
            subtotal = acc.netInvoiceSubtotal,
            totalDue = acc.netInvoiceSubtotal,
            paid = acc.paidCash,
            previousDebt = acc.previousDebtPaid,
            isPrevDebtPaidModel = acc.previousDebtPaid > 0.0,
            discount = acc.discount,
            remaining = acc.remainingOnInvoice,
            suppliedGoodsNote = cleanNote,
            whatsappText = customerMsg,
            managerWhatsappText = managerMsg
        )
        dao.insertSaleInvoice(updated)
        if (cleanCust.isNotBlank() && cleanCust != "عميل نقدي") {
            dao.upsertCustomer(CustomerEntity(cleanCust, effectivePhone, acc.remainingOnInvoice, System.currentTimeMillis()))
        }
    }

    suspend fun createAndPersistSupplyBatch(
        dao: SmartTradeDao,
        prod: ProductEntity,
        batchNum: String,
        dt: ArabicDateTimeInfo,
        majorQty: Double,
        cleanMin: Double,
        notes: String,
        userName: String = "",
        ledgerSheetDate: String = dt.date
    ): SupplyBatchEntity {
        val effectiveSheetDate = ledgerSheetDate.trim().ifBlank { dt.date }
        val supplyAcc = LedgerCalculator.computeUnifiedSupplyAccounting(
            product = prod,
            enteredMajor = majorQty,
            enteredMinor = cleanMin,
            basePrevStock = prod.stock
        )

        val waText = LedgerCalculator.buildManagerSupplyWhatsAppMessage(
            batchNum = batchNum,
            dayName = dt.dayName,
            date = dt.date,
            time = dt.time12h,
            product = prod,
            prevStock = supplyAcc.prevStock,
            enteredDisplay = supplyAcc.enteredDisplay,
            cumulativeStock = supplyAcc.cumulativeStock,
            notes = notes.trim(),
            userName = userName
        )
        val batch = SupplyBatchEntity(
            id = "SUP_${dt.timestamp}",
            batchNumber = batchNum,
            marker = "🟢",
            productKey = prod.key,
            productName = prod.name,
            enteredMajor = supplyAcc.normalizedMajor,
            enteredMinor = supplyAcc.normalizedMinor,
            enteredDisplay = supplyAcc.enteredDisplay,
            addedMajorEquivalent = supplyAcc.addedMajorEquivalent,
            prevStock = supplyAcc.prevStock,
            cumulativeStock = supplyAcc.cumulativeStock,
            majorUnit = prod.majorUnit,
            dayName = dt.dayName,
            date = effectiveSheetDate,
            time = dt.time12h,
            timestamp = dt.timestamp,
            notes = notes.trim(),
            whatsappText = waText
        )
        dao.insertSupplyBatch(batch)
        dao.upsertProduct(prod.copy(stock = supplyAcc.cumulativeStock))
        return batch
    }

    suspend fun updateExistingSupplyBatch(
        dao: SmartTradeDao,
        batch: SupplyBatchEntity,
        newMajorQty: Double,
        newMinorQty: Double,
        newNotes: String,
        userName: String
    ) {
        val prod = dao.getAllProducts().find { it.key == batch.productKey } ?: return
        val supplyAcc = LedgerCalculator.computeUnifiedSupplyAccounting(
            product = prod,
            enteredMajor = newMajorQty,
            enteredMinor = newMinorQty,
            basePrevStock = batch.prevStock
        )
        val deltaEq = LedgerCalculator.subtractStock(supplyAcc.addedMajorEquivalent, batch.addedMajorEquivalent)
        val updatedProd = prod.copy(stock = LedgerCalculator.addStock(prod.stock, deltaEq))
        dao.upsertProduct(updatedProd)

        val actualDate = batch.actualRecordDate()
        val waText = LedgerCalculator.buildManagerSupplyWhatsAppMessage(
            batch.batchNumber, batch.dayName, actualDate, batch.time,
            updatedProd, supplyAcc.prevStock, supplyAcc.enteredDisplay, supplyAcc.cumulativeStock, newNotes.trim(), userName
        )
        val updatedBatch = batch.copy(
            enteredMajor = supplyAcc.normalizedMajor,
            enteredMinor = supplyAcc.normalizedMinor,
            enteredDisplay = supplyAcc.enteredDisplay,
            addedMajorEquivalent = supplyAcc.addedMajorEquivalent,
            cumulativeStock = supplyAcc.cumulativeStock,
            notes = newNotes.trim(),
            whatsappText = waText
        )
        dao.insertSupplyBatch(updatedBatch)
    }

    suspend fun deleteInvoiceAndRestoreStock(dao: SmartTradeDao, invoice: SaleInvoiceEntity) {
        val allProds = dao.getAllProducts().associateBy { it.key }.toMutableMap()
        val modifiedProds = mutableListOf<ProductEntity>()
        decodeInvoiceItemsJson(invoice.itemsJson).forEach { item ->
            val prod = allProds[item.key]
            if (prod != null) {
                val factor = if (prod.factor > 0.0) prod.factor else 2.0
                val hasMinor = prod.minorUnit.isNotBlank() && factor > 0.0
                val soldEq = LedgerCalculator.computeMajorEquivalent(item.majorQty, item.minorQty, factor, hasMinor)
                val updatedProd = prod.copy(stock = LedgerCalculator.addStock(prod.stock, soldEq))
                allProds[item.key] = updatedProd
                modifiedProds.add(updatedProd)
            }
        }
        if (modifiedProds.isNotEmpty()) {
            dao.upsertProducts(modifiedProds)
        }
        dao.deleteSaleInvoiceById(invoice.id)
    }

    suspend fun deleteSupplyAndRevertStock(dao: SmartTradeDao, batch: SupplyBatchEntity) {
        val prod = dao.getAllProducts().find { it.key == batch.productKey }
        if (prod != null) {
            dao.upsertProduct(prod.copy(stock = LedgerCalculator.subtractStock(prod.stock, batch.addedMajorEquivalent)))
        }
        dao.deleteSupplyBatchById(batch.id)
    }

    suspend fun resetAllDatabaseData(dao: SmartTradeDao) {
        dao.deleteAllSalesInvoices()
        dao.deleteAllSupplyBatches()
        dao.deleteAllCustomers()
        dao.deleteAllProducts()
        dao.upsertProducts(getInitialPaperCommodities())
    }

    suspend fun parseAndRestoreBackupJson(
        dao: SmartTradeDao,
        jsonStr: String,
        defaultSalesCounter: Int,
        defaultSupplyCounter: Int,
        defaultManagerPhone: String,
        defaultUserName: String
    ): RestoredCountersResult {
        val root = JSONObject(jsonStr)
        root.optJSONArray("products")?.let { prodArr ->
            val list = (0 until prodArr.length()).map { i ->
                val o = prodArr.getJSONObject(i)
                ProductEntity(
                    key = o.getString("key"), name = o.getString("name"),
                    majorUnit = o.optString("majorUnit", "كيلو"), minorUnit = o.optString("minorUnit", ""),
                    factor = o.optDouble("factor", 2.0), colorBgHex = o.optLong("colorBgHex", 0xFFF8FAFC),
                    colorTextHex = o.optLong("colorTextHex", 0xFF1E293B), borderHex = o.optLong("borderHex", 0xFFCBD5E1),
                    price = LedgerCalculator.roundMoney(o.optDouble("price", 0.0)),
                    stock = LedgerCalculator.roundStock(o.optDouble("stock", 0.0)),
                    openingStock = LedgerCalculator.roundStock(o.optDouble("openingStock", 0.0)),
                    sortOrder = o.optInt("sortOrder", i + 1)
                )
            }
            dao.upsertProducts(list)
        }

        root.optJSONArray("customers")?.let { custArr ->
            val list = (0 until custArr.length()).map { i ->
                val o = custArr.getJSONObject(i)
                CustomerEntity(
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    lastRemainingDebt = LedgerCalculator.roundMoney(o.optDouble("lastRemainingDebt", 0.0)),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
            dao.upsertCustomers(list)
        }

        root.optJSONArray("invoices")?.let { invArr ->
            val list = (0 until invArr.length()).map { i ->
                val o = invArr.getJSONObject(i)
                val itemsJsonStr = o.optString("itemsJson", "[]")
                val items = decodeInvoiceItemsJson(itemsJsonStr)
                val rawSum = LedgerCalculator.sumRecordTotals(items)
                val rawSub = o.optDouble("subtotal", 0.0)
                val rawDisc = o.optDouble("discount", 0.0)
                val rawPaid = o.optDouble("paid", 0.0)
                val rawPrev = o.optDouble("previousDebt", 0.0)
                val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(
                    rawItemsTotal = rawSum,
                    discount = rawDisc,
                    paidCash = rawPaid,
                    previousDebtPaid = rawPrev,
                    fallbackGrossSubtotalIfNoItems = LedgerCalculator.addMoney(rawSub, rawDisc)
                )
                SaleInvoiceEntity(
                    id = o.getString("id"), invoiceNumber = o.getString("invoiceNumber"),
                    customerName = o.getString("customerName"), customerPhone = o.optString("customerPhone", ""),
                    subtotal = acc.netInvoiceSubtotal, previousDebt = acc.previousDebtPaid,
                    isPrevDebtPaidModel = acc.previousDebtPaid > 0.0, discount = acc.discount,
                    totalDue = acc.netInvoiceSubtotal, paid = acc.paidCash,
                    remaining = acc.remainingOnInvoice, suppliedGoodsNote = o.optString("suppliedGoodsNote", ""),
                    dayName = o.optString("dayName", "اليوم"), date = o.getString("date"),
                    time = o.optString("time", ""), timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    itemsJson = itemsJsonStr, whatsappText = o.optString("whatsappText", ""),
                    managerWhatsappText = o.optString("managerWhatsappText", "")
                )
            }
            list.chunked(500).forEach { chunk ->
                dao.insertSaleInvoices(chunk)
            }
        }

        root.optJSONArray("supplies")?.let { supArr ->
            val list = (0 until supArr.length()).map { i ->
                val o = supArr.getJSONObject(i)
                SupplyBatchEntity(
                    id = o.getString("id"), batchNumber = o.getString("batchNumber"),
                    productKey = o.getString("productKey"), productName = o.getString("productName"),
                    enteredMajor = LedgerCalculator.roundStock(o.optDouble("enteredMajor", 0.0)),
                    enteredMinor = LedgerCalculator.roundStock(o.optDouble("enteredMinor", 0.0)),
                    enteredDisplay = o.optString("enteredDisplay", ""),
                    addedMajorEquivalent = LedgerCalculator.roundStock(o.optDouble("addedMajorEquivalent", 0.0)),
                    prevStock = LedgerCalculator.roundStock(o.optDouble("prevStock", 0.0)),
                    cumulativeStock = LedgerCalculator.roundStock(o.optDouble("cumulativeStock", 0.0)),
                    majorUnit = o.optString("majorUnit", "كيلو"), dayName = o.optString("dayName", "اليوم"),
                    date = o.getString("date"), time = o.optString("time", ""),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    notes = o.optString("notes", ""), whatsappText = o.optString("whatsappText", "")
                )
            }
            list.chunked(500).forEach { chunk ->
                dao.insertSupplyBatches(chunk)
            }
        }

        val restoredClosedSet = mutableSetOf<String>()
        root.optJSONArray("closedSheetDates")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) restoredClosedSet.add(s)
            }
        }

        return RestoredCountersResult(
            salesCounter = root.optInt("salesCounter", defaultSalesCounter),
            supplyCounter = root.optInt("supplyCounter", defaultSupplyCounter),
            managerPhone = root.optString("managerPhone", defaultManagerPhone),
            userName = root.optString("userName", defaultUserName),
            activeOpenSheetDate = root.optString("activeOpenSheetDate", ""),
            closedSheetDates = restoredClosedSet
        )
    }

    suspend fun upgradeLegacyMessagesToDirectFormat(dao: SmartTradeDao, currency: String, userName: String) {
        val prodsMap = dao.getAllProducts().associateBy { it.key }
        val supplies = dao.getRecentSuppliesForBackup(500)
        val updatedSupplies = supplies.mapNotNull { s ->
            val prod = prodsMap[s.productKey]
            val actualDate = s.actualRecordDate()
            val directMsg = if (prod != null) {
                LedgerCalculator.buildManagerSupplyWhatsAppMessage(
                    s.batchNumber, s.dayName, actualDate, s.time, prod, s.prevStock, s.enteredDisplay, s.cumulativeStock, s.notes, userName
                )
            } else {
                WhatsAppMessageFormatter.sanitizeDirectMessage(s.whatsappText, userName)
            }
            if (directMsg != s.whatsappText) s.copy(whatsappText = directMsg) else null
        }
        if (updatedSupplies.isNotEmpty()) dao.insertSupplyBatches(updatedSupplies)

        val invoices = dao.getRecentSalesInvoicesForBackup(500)
        val updatedInvoices = invoices.mapNotNull { inv ->
            val items = decodeInvoiceItemsJson(inv.itemsJson)
            val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(inv)
            val actualDate = inv.actualRecordDate()
            val cleanCustomerMsg = LedgerCalculator.buildCustomerWhatsAppMessage(
                inv.invoiceNumber, inv.dayName, actualDate, inv.time, inv.customerName, items,
                acc.netInvoiceSubtotal, acc.previousDebtPaid, acc.discount, acc.paidCash, acc.remainingOnInvoice, currency, userName, inv.suppliedGoodsNote
            )
            val cleanManagerMsg = LedgerCalculator.buildManagerInvoiceWhatsAppMessage(
                inv.invoiceNumber, inv.dayName, actualDate, inv.time, inv.customerName, items,
                acc.netInvoiceSubtotal, acc.previousDebtPaid, acc.discount, acc.paidCash, acc.remainingOnInvoice, currency, prodsMap, userName, inv.suppliedGoodsNote
            )
            if (cleanCustomerMsg != inv.whatsappText || cleanManagerMsg != inv.managerWhatsappText) {
                inv.copy(
                    subtotal = acc.netInvoiceSubtotal,
                    totalDue = acc.netInvoiceSubtotal,
                    paid = acc.paidCash,
                    previousDebt = acc.previousDebtPaid,
                    discount = acc.discount,
                    remaining = acc.remainingOnInvoice,
                    whatsappText = cleanCustomerMsg,
                    managerWhatsappText = cleanManagerMsg
                )
            } else null
        }
        if (updatedInvoices.isNotEmpty()) dao.insertSaleInvoices(updatedInvoices)
    }
}
