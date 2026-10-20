package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.CustomerEntity
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.data.SupplyBatchEntity
import com.example.domain.ProductDailyStockStats
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * واجهة موحدة لإدارة النسخ الاحتياطي وتوجيه أوامر PDF إلى الوحدات المتخصصة
 */
object PdfAndBackupHelper {

    fun generateInvoicePdfFile(
        context: Context,
        invoice: SaleInvoiceEntity,
        currency: String,
        userName: String = ""
    ): File? = InvoicePdfGenerator.generateInvoicePdfFile(context, invoice, currency, userName)

    fun generateDailySheetPdfFile(
        context: Context,
        dateStr: String,
        dayName: String,
        products: List<ProductEntity>,
        dayInvoices: List<SaleInvoiceEntity>,
        stockStats: Map<String, ProductDailyStockStats>,
        userName: String = ""
    ): File? = DailyLedgerPdfGenerator.generateDailySheetPdfFile(
        context, dateStr, dayName, products, dayInvoices, stockStats, userName
    )

    fun sharePdfFile(context: Context, pdfFile: File, title: String) =
        InvoicePdfGenerator.sharePdfFile(context, pdfFile, title)

    fun sharePdfToManagerWhatsApp(context: Context, pdfFile: File, managerPhone: String, caption: String, userName: String = "") =
        InvoicePdfGenerator.sharePdfToManagerWhatsApp(context, pdfFile, managerPhone, caption, userName)

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) =
        InvoicePdfGenerator.printPdfFile(context, pdfFile, jobName)

    fun buildFullBackupJson(
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        invoices: List<SaleInvoiceEntity>,
        supplies: List<SupplyBatchEntity>,
        salesCounter: Int,
        supplyCounter: Int,
        managerPhone: String,
        connectedAccount: String,
        userName: String = ""
    ): String {
        val root = JSONObject()
        root.put("version", 4)
        root.put("backupTimestamp", System.currentTimeMillis())
        root.put("salesCounter", salesCounter)
        root.put("supplyCounter", supplyCounter)
        root.put("managerPhone", managerPhone)
        root.put("connectedAccount", connectedAccount)
        root.put("userName", userName)

        val prodArr = JSONArray()
        products.forEach { p ->
            prodArr.put(JSONObject().apply {
                put("key", p.key)
                put("name", p.name)
                put("majorUnit", p.majorUnit)
                put("minorUnit", p.minorUnit)
                put("factor", p.factor)
                put("colorBgHex", p.colorBgHex)
                put("colorTextHex", p.colorTextHex)
                put("borderHex", p.borderHex)
                put("price", p.price)
                put("stock", p.stock)
                put("openingStock", p.openingStock)
                put("sortOrder", p.sortOrder)
            })
        }
        root.put("products", prodArr)

        val custArr = JSONArray()
        customers.forEach { c ->
            custArr.put(JSONObject().apply {
                put("name", c.name)
                put("phone", c.phone)
                put("lastRemainingDebt", c.lastRemainingDebt)
                put("updatedAt", c.updatedAt)
            })
        }
        root.put("customers", custArr)

        val invArr = JSONArray()
        invoices.forEach { inv ->
            invArr.put(JSONObject().apply {
                put("id", inv.id)
                put("invoiceNumber", inv.invoiceNumber)
                put("customerName", inv.customerName)
                put("customerPhone", inv.customerPhone)
                put("subtotal", inv.subtotal)
                put("previousDebt", inv.previousDebt)
                put("isPrevDebtPaidModel", inv.isPrevDebtPaidModel)
                put("discount", inv.discount)
                put("totalDue", inv.totalDue)
                put("paid", inv.paid)
                put("remaining", inv.remaining)
                put("suppliedGoodsNote", inv.suppliedGoodsNote)
                put("dayName", inv.dayName)
                put("date", inv.date)
                put("time", inv.time)
                put("timestamp", inv.timestamp)
                put("itemsJson", inv.itemsJson)
                put("whatsappText", inv.whatsappText)
                put("managerWhatsappText", inv.managerWhatsappText)
            })
        }
        root.put("invoices", invArr)

        val supArr = JSONArray()
        supplies.forEach { s ->
            supArr.put(JSONObject().apply {
                put("id", s.id)
                put("batchNumber", s.batchNumber)
                put("productKey", s.productKey)
                put("productName", s.productName)
                put("enteredMajor", s.enteredMajor)
                put("enteredMinor", s.enteredMinor)
                put("enteredDisplay", s.enteredDisplay)
                put("addedMajorEquivalent", s.addedMajorEquivalent)
                put("prevStock", s.prevStock)
                put("cumulativeStock", s.cumulativeStock)
                put("majorUnit", s.majorUnit)
                put("dayName", s.dayName)
                put("date", s.date)
                put("time", s.time)
                put("timestamp", s.timestamp)
                put("notes", s.notes)
                put("whatsappText", s.whatsappText)
            })
        }
        root.put("supplies", supArr)

        return root.toString(2)
    }

    fun saveAutoLocalBackup(context: Context, backupJson: String): File? {
        return try {
            val file = File(context.filesDir, "SmartTradeBook_AutoBackup.json")
            file.writeText(backupJson, Charsets.UTF_8)
            file
        } catch (e: Exception) {
            null
        }
    }

    fun shareCloudBackupFile(context: Context, backupJson: String) {
        try {
            val dateStr = getCurrentDateTimeArabic().date
            val file = File(context.cacheDir, "SmartTradeBook_Backup_$dateStr.json")
            file.writeText(backupJson, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - السجل اليومي ($dateStr)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "رفع وحفظ النسخة الاحتياطية السحابية (Google Drive / الهاتف)").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة ملف النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
        }
    }
}
