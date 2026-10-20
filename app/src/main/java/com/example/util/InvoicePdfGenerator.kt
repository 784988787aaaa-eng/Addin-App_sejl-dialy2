package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.SaleInvoiceEntity
import com.example.domain.LedgerCalculator
import com.example.domain.WhatsAppMessageFormatter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * مولد ومشارك وماسح طباعة ملفات PDF للفواتير والكشوفات المطبوعة (مرتبط بالمحرك المحاسبي الموحد LedgerCalculator)
 */
object InvoicePdfGenerator {

    fun generateInvoicePdfFile(
        context: Context,
        invoice: SaleInvoiceEntity,
        currency: String,
        userName: String = ""
    ): File? {
        return try {
            val effectiveUser = userName.ifBlank {
                context.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
                    .getString("user_name", "") ?: ""
            }.trim()

            val items = decodeInvoiceItemsJson(invoice.itemsJson)
            val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(invoice)
            val pageWidth = 595
            val pageHeight = (520 + items.size * 30).coerceAtLeast(660)

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#475569")
                textSize = 11f
            }

            bgPaint.color = AndroidColor.parseColor("#141E2E")
            canvas.drawRoundRect(RectF(24f, 20f, (pageWidth - 24).toFloat(), 100f), 12f, 12f, bgPaint)

            textPaint.color = AndroidColor.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 18f
            canvas.drawText("فاتورة رقم: ${invoice.invoiceNumber}", pageWidth / 2f, 52f, textPaint)

            textPaint.textSize = 12f
            textPaint.color = AndroidColor.parseColor("#6EE7B7")
            canvas.drawText("اليوم: ${invoice.dayName}   |   التاريخ: ${invoice.date} م   |   الوقت: ${invoice.time}", pageWidth / 2f, 80f, textPaint)

            bgPaint.color = AndroidColor.parseColor("#F8FAFC")
            canvas.drawRoundRect(RectF(24f, 112f, (pageWidth - 24).toFloat(), 156f), 10f, 10f, bgPaint)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = AndroidColor.parseColor("#CBD5E1")
            }
            canvas.drawRoundRect(RectF(24f, 112f, (pageWidth - 24).toFloat(), 156f), 10f, 10f, borderPaint)

            textPaint.color = AndroidColor.parseColor("#0F172A")
            textPaint.textSize = 13f
            textPaint.textAlign = Paint.Align.RIGHT
            val customerHeaderText = if (invoice.customerPhone.isNotBlank()) {
                "العميل: ${invoice.customerName}   |   الهاتف: ${invoice.customerPhone}"
            } else {
                "العميل: ${invoice.customerName}"
            }
            canvas.drawText(customerHeaderText, (pageWidth - 40).toFloat(), 139f, textPaint)

            var y = 170f
            bgPaint.color = AndroidColor.parseColor("#E2E8F0")
            canvas.drawRect(24f, y, (pageWidth - 24).toFloat(), y + 28f, bgPaint)

            textPaint.textSize = 12f
            textPaint.color = AndroidColor.parseColor("#1E293B")
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("م", (pageWidth - 36).toFloat(), y + 19f, textPaint)
            canvas.drawText("الصنف", (pageWidth - 75).toFloat(), y + 19f, textPaint)
            canvas.drawText("الكمية والوحدة", (pageWidth - 230).toFloat(), y + 19f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الإجمالي", 40f, y + 19f, textPaint)

            y += 28f
            items.forEachIndexed { idx, item ->
                if (idx % 2 == 1) {
                    bgPaint.color = AndroidColor.parseColor("#F8FAFC")
                    canvas.drawRect(24f, y, (pageWidth - 24).toFloat(), y + 28f, bgPaint)
                }
                canvas.drawLine(24f, y + 28f, (pageWidth - 24).toFloat(), y + 28f, borderPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = AndroidColor.parseColor("#1E293B")
                canvas.drawText("${idx + 1}", (pageWidth - 36).toFloat(), y + 19f, subTextPaint.apply { textAlign = Paint.Align.RIGHT })
                canvas.drawText(item.name, (pageWidth - 75).toFloat(), y + 19f, textPaint)
                canvas.drawText(item.unitDisplay, (pageWidth - 230).toFloat(), y + 19f, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = AndroidColor.parseColor("#047857")
                canvas.drawText("${item.total.cleanNum()} $currency", 40f, y + 19f, textPaint)
                y += 28f
            }

            y += 18f
            val summaryRows = mutableListOf<Pair<String, String>>()
            if (acc.discount > 0) {
                if (acc.rawItemsTotal > 0) {
                    summaryRows.add("إجمالي الأصناف (قبل الخصم):" to "${acc.rawItemsTotal.cleanNum()} $currency")
                }
                summaryRows.add("الخصم:" to "${acc.discount.cleanNum()} $currency")
            }
            summaryRows.add("إجمالي الفاتورة:" to "${acc.netInvoiceSubtotal.cleanNum()} $currency")
            summaryRows.add("المسلم نقداً:" to "${acc.paidCash.cleanNum()} $currency")
            summaryRows.add("المتبقي من الفاتورة:" to "${acc.remainingOnInvoice.cleanNum()} $currency")
            if (acc.previousDebtPaid > 0) {
                summaryRows.add("سداد دين سابق:" to "${acc.previousDebtPaid.cleanNum()} $currency")
                if (acc.paidCash > 0) {
                    summaryRows.add("إجمالي المسلم الكلي:" to "${acc.totalInUserHand.cleanNum()} $currency")
                }
            }

            summaryRows.forEach { (label, valStr) ->
                val isRem = label.contains("المتبقي") && acc.remainingOnInvoice > 0
                bgPaint.color = if (isRem) AndroidColor.parseColor("#FEF3C7") else AndroidColor.parseColor("#F1F5F9")
                canvas.drawRoundRect(RectF(24f, y, (pageWidth - 24).toFloat(), y + 26f), 6f, 6f, bgPaint)
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = AndroidColor.parseColor("#0F172A")
                canvas.drawText(label, (pageWidth - 40).toFloat(), y + 18f, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = if (isRem) AndroidColor.parseColor("#B45309") else AndroidColor.parseColor("#047857")
                canvas.drawText(valStr, 40f, y + 18f, textPaint)
                y += 30f
            }

            if (effectiveUser.isNotEmpty()) {
                y += 8f
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = 11.5f
                textPaint.color = AndroidColor.parseColor("#334155")
                canvas.drawText("بواسطة: $effectiveUser", pageWidth / 2f, y + 14f, textPaint)
            }

            pdfDocument.finishPage(page)
            val outFile = File(context.cacheDir, "Invoice_${invoice.invoiceNumber}.pdf")
            FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sharePdfFile(context: Context, pdfFile: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة ملف PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePdfToManagerWhatsApp(context: Context, pdfFile: File, managerPhone: String, caption: String, userName: String = "") {
        try {
            val effectiveUser = userName.ifBlank {
                context.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
                    .getString("user_name", "") ?: ""
            }.trim()
            val isDailyLedger = caption.contains("كشف حساب اليومية") || pdfFile.name.startsWith("Daily_Ledger_")
            val finalCaption = if (!isDailyLedger && effectiveUser.isNotEmpty() && !caption.contains("بواسطة:")) {
                "$caption\nبواسطة: $effectiveUser"
            } else {
                caption
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val formattedPhone = WhatsAppMessageFormatter.formatInternationalPhone(managerPhone)
            val packagesToTry = listOf("com.whatsapp", "com.whatsapp.w4b")

            for (pkg in packagesToTry) {
                try {
                    val waPdfIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, finalCaption)
                        if (formattedPhone.isNotEmpty()) {
                            putExtra("jid", "$formattedPhone@s.whatsapp.net")
                        }
                        setPackage(pkg)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(waPdfIntent)
                    return
                } catch (_: Exception) {
                }
            }

            sharePdfFile(context, pdfFile, finalCaption)
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر مشاركة السجل المطبوع", Toast.LENGTH_SHORT).show()
        }
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(pdfFile.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destination?.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            val landscapeAttrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .build()
            printManager.print(jobName, adapter, landscapeAttrs)
        } catch (e: Exception) {
            sharePdfFile(context, pdfFile, jobName)
        }
    }
}
