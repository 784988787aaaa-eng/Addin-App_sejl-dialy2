package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.domain.LedgerCalculator
import com.example.domain.ProductDailyStockStats
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * مولد ملفات PDF لكشف الحساب اليومي الأفقي (Landscape) مع التفاف النص وتفصيل الوحدات الكبرى والصغرى وتذييل اسم المستخدم
 */
object DailyLedgerPdfGenerator {

    private fun wrapTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        if (paint.measureText(clean) <= maxWidth) return listOf(clean)

        val words = clean.split(Regex("\\s+"))
        if (words.size == 1) return listOf(clean)

        val lines = mutableListOf<String>()
        var currentLine = words[0]
        for (i in 1 until words.size) {
            val candidate = "$currentLine ${words[i]}"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                lines.add(currentLine)
                currentLine = words[i]
            }
        }
        lines.add(currentLine)
        return lines
    }

    fun generateDailySheetPdfFile(
        context: Context,
        dateStr: String,
        dayName: String,
        products: List<ProductEntity>,
        dayInvoices: List<SaleInvoiceEntity>,
        stockStats: Map<String, ProductDailyStockStats>,
        userName: String = ""
    ): File? {
        return try {
            val effectiveUser = userName.ifBlank {
                context.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
                    .getString("user_name", "") ?: ""
            }.trim()

            val rowsPerPage = 20
            val totalPages = ceil(dayInvoices.size.coerceAtLeast(1) / rowsPerPage.toDouble()).toInt().coerceAtLeast(1)

            val pageWidth = 1190
            val pageHeight = 842

            val pdfDocument = PdfDocument()
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.1f
                color = AndroidColor.parseColor("#334155")
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.BLACK
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val prodCols = products.take(16)
            val marginX = 14f
            val usableWidth = pageWidth - (marginX * 2)

            val colM = 28f
            val colName = 126f
            val colInvoiceTotal = 56f
            val colPaid = 68f
            val colRem = 56f
            val remainingForProds = usableWidth - (colM + colName + colInvoiceTotal + colPaid + colRem)
            val colProdW = remainingForProds / prodCols.size.coerceAtLeast(1)

            fun drawWrappedCell(
                canvas: Canvas,
                rightX: Float,
                topY: Float,
                w: Float,
                h: Float,
                bgHex: Int,
                text: String,
                txtColor: Int = AndroidColor.BLACK,
                baseFontSize: Float = 9.0f
            ) {
                val rect = RectF(rightX - w, topY, rightX, topY + h)
                fillPaint.color = bgHex
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, strokePaint)

                if (text.isNotBlank()) {
                    textPaint.color = txtColor
                    var fontSize = baseFontSize
                    textPaint.textSize = fontSize
                    val availW = (w - 4f).coerceAtLeast(16f)

                    var lines = wrapTextIntoLines(text, textPaint, availW)
                    while ((lines.size > 2 || lines.any { textPaint.measureText(it) > availW }) && fontSize > 6.5f) {
                        fontSize -= 0.5f
                        textPaint.textSize = fontSize
                        lines = wrapTextIntoLines(text, textPaint, availW)
                    }

                    val lineHeight = fontSize + 2f
                    val totalTextHeight = lines.size * lineHeight
                    var startBaselineY = rect.centerY() - (totalTextHeight / 2f) + fontSize - 1f

                    lines.forEach { line ->
                        canvas.drawText(line, rect.centerX(), startBaselineY, textPaint)
                        startBaselineY += lineHeight
                    }
                }
            }

            val fin = LedgerCalculator.computeDailyFinancialTotals(dayInvoices)
            val userHandShortLabel = if (effectiveUser.isNotEmpty()) "بيد المستخدم ($effectiveUser)" else "بيد المستخدم"

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                fillPaint.color = AndroidColor.WHITE
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint)

                textPaint.color = AndroidColor.parseColor("#0F172A")
                textPaint.textSize = 14.5f
                val pageBadge = if (totalPages > 1) "   (صفحة ${pageIndex + 1} من $totalPages)" else ""
                canvas.drawText(
                    "السجل اليومي — كشف حساب: اليوم: $dayName   |   التاريخ: $dateStr م$pageBadge",
                    pageWidth / 2f,
                    24f,
                    textPaint
                )

                val startY = 34f
                val headerRowH = 29f
                val dataRowH = 31.0f
                val rightEdge = pageWidth - marginX
                var curY = startY

                // Top Row 1: وارد بضاعة جديد
                var curX = rightEdge
                drawWrappedCell(canvas, curX, curY, colM, headerRowH, AndroidColor.parseColor("#CBD5E1"), "")
                curX -= colM
                drawWrappedCell(canvas, curX, curY, colName, headerRowH, AndroidColor.parseColor("#CBD5E1"), "وارد بضاعة جديد", AndroidColor.BLACK, 9.5f)
                curX -= colName
                prodCols.forEach { p ->
                    val suppliedDisp = stockStats[p.key]?.suppliedDisplay ?: ""
                    drawWrappedCell(canvas, curX, curY, colProdW, headerRowH, AndroidColor.parseColor("#E2E8F0"), suppliedDisp, AndroidColor.parseColor("#065F46"), 8.5f)
                    curX -= colProdW
                }
                drawWrappedCell(
                    canvas, curX, curY, colInvoiceTotal + colPaid + colRem, headerRowH,
                    AndroidColor.parseColor("#ECFDF5"),
                    "نقد الفواتير: ${fin.totalCashReceived.cleanNum()}",
                    AndroidColor.parseColor("#065F46"), 8.0f
                )
                curY += headerRowH

                // Top Row 2: إجمالي المتبقي
                curX = rightEdge
                drawWrappedCell(canvas, curX, curY, colM, headerRowH, AndroidColor.parseColor("#CBD5E1"), "م")
                curX -= colM
                drawWrappedCell(canvas, curX, curY, colName, headerRowH, AndroidColor.parseColor("#CBD5E1"), "إجمالي المتبقي", AndroidColor.BLACK, 9.5f)
                curX -= colName
                prodCols.forEach { p ->
                    val prevRemDisp = stockStats[p.key]?.prevRemainingDisplay ?: ""
                    drawWrappedCell(canvas, curX, curY, colProdW, headerRowH, p.colorBgHex.toInt(), prevRemDisp, p.colorTextHex.toInt(), 8.5f)
                    curX -= colProdW
                }
                drawWrappedCell(
                    canvas, curX, curY, colInvoiceTotal + colPaid + colRem, headerRowH,
                    AndroidColor.parseColor("#FFFBEB"),
                    "سداد دين سابق: ${fin.totalPrevDebtNotified.cleanNum()}",
                    AndroidColor.parseColor("#92400E"), 8.0f
                )
                curY += headerRowH

                // Top Row 3: ترويسة الأعمدة
                curX = rightEdge
                drawWrappedCell(canvas, curX, curY, colM, headerRowH, AndroidColor.parseColor("#CBD5E1"), "")
                curX -= colM
                drawWrappedCell(canvas, curX, curY, colName, headerRowH, AndroidColor.parseColor("#CBD5E1"), "الإســــــــــــــــــــــــم", AndroidColor.BLACK, 10f)
                curX -= colName
                prodCols.forEach { p ->
                    drawWrappedCell(canvas, curX, curY, colProdW, headerRowH, p.colorBgHex.toInt(), p.name, p.colorTextHex.toInt(), 9.0f)
                    curX -= colProdW
                }
                drawWrappedCell(canvas, curX, curY, colInvoiceTotal, headerRowH, AndroidColor.parseColor("#FEF3C7"), "إجمالي الفاتورة", AndroidColor.parseColor("#92400E"), 8.5f)
                curX -= colInvoiceTotal
                drawWrappedCell(canvas, curX, curY, colPaid, headerRowH, AndroidColor.parseColor("#F1F5F9"), "مسلم", AndroidColor.BLACK, 9.5f)
                curX -= colPaid
                drawWrappedCell(canvas, curX, curY, colRem, headerRowH, AndroidColor.parseColor("#DBEAFE"), "المتبقي", AndroidColor.parseColor("#1E3A8A"), 9.5f)
                curY += headerRowH

                // Data Rows
                val startOffset = pageIndex * rowsPerPage
                for (r in 0 until rowsPerPage) {
                    val globalIndex = startOffset + r
                    val inv = dayInvoices.getOrNull(globalIndex)
                    val decodedItemsMap = if (inv != null) decodeInvoiceItemsMap(inv.itemsJson) else emptyMap()
                    val acc = if (inv != null) LedgerCalculator.computeUnifiedInvoiceAccounting(inv) else null

                    curX = rightEdge
                    drawWrappedCell(canvas, curX, curY, colM, dataRowH, AndroidColor.parseColor("#CBD5E1"), "${globalIndex + 1}", AndroidColor.BLACK, 9.5f)
                    curX -= colM
                    drawWrappedCell(canvas, curX, curY, colName, dataRowH, AndroidColor.parseColor("#E2E8F0"), inv?.customerName ?: "", AndroidColor.BLACK, 9.5f)
                    curX -= colName

                    prodCols.forEach { p ->
                        val item = decodedItemsMap[p.key]
                        drawWrappedCell(canvas, curX, curY, colProdW, dataRowH, p.colorBgHex.toInt(), item?.unitDisplay ?: "", p.colorTextHex.toInt(), 8.5f)
                        curX -= colProdW
                    }

                    drawWrappedCell(
                        canvas, curX, curY, colInvoiceTotal, dataRowH,
                        AndroidColor.parseColor("#FFFBEB"),
                        if (acc != null && acc.netInvoiceSubtotal > 0) acc.netInvoiceSubtotal.cleanNum() else "",
                        AndroidColor.parseColor("#92400E"), 9.0f
                    )
                    curX -= colInvoiceTotal

                    val paidText = acc?.paidCellText ?: ""
                    drawWrappedCell(canvas, curX, curY, colPaid, dataRowH, AndroidColor.WHITE, paidText, AndroidColor.parseColor("#047857"), 8.5f)
                    curX -= colPaid

                    val hasRem = acc != null && acc.remainingOnInvoice > 0
                    val remBg = if (hasRem) AndroidColor.parseColor("#FEF3C7") else AndroidColor.parseColor("#DBEAFE")
                    val remTxtColor = if (hasRem) AndroidColor.parseColor("#DC2626") else AndroidColor.parseColor("#1E3A8A")
                    drawWrappedCell(canvas, curX, curY, colRem, dataRowH, remBg, if (acc != null) acc.remainingOnInvoice.cleanNum() else "", remTxtColor, 9.0f)
                    curY += dataRowH
                }

                // Bottom Row 1: إجمالي البيع
                curX = rightEdge
                drawWrappedCell(canvas, curX, curY, colM, headerRowH, AndroidColor.WHITE, "")
                curX -= colM
                drawWrappedCell(canvas, curX, curY, colName, headerRowH, AndroidColor.parseColor("#DCFCE7"), "إجمالي البيع", AndroidColor.BLACK, 9.5f)
                curX -= colName
                prodCols.forEach { p ->
                    val soldDisp = stockStats[p.key]?.soldDisplay ?: ""
                    drawWrappedCell(canvas, curX, curY, colProdW, headerRowH, AndroidColor.parseColor("#ECFDF5"), soldDisp, AndroidColor.parseColor("#047857"), 8.5f)
                    curX -= colProdW
                }
                drawWrappedCell(canvas, curX, curY, colInvoiceTotal, headerRowH, AndroidColor.parseColor("#FEF3C7"), if (fin.totalInvoicesSubtotal > 0) fin.totalInvoicesSubtotal.cleanNum() else "0", AndroidColor.parseColor("#92400E"), 8.5f)
                curX -= colInvoiceTotal
                val totalPaidSummaryText = if (fin.totalPrevDebtNotified > 0) {
                    "نقد: ${fin.totalCashReceived.cleanNum()} | سابق: ${fin.totalPrevDebtNotified.cleanNum()}"
                } else {
                    fin.totalCashReceived.cleanNum()
                }
                drawWrappedCell(canvas, curX, curY, colPaid, headerRowH, AndroidColor.parseColor("#ECFDF5"), totalPaidSummaryText, AndroidColor.parseColor("#047857"), 8.0f)
                curX -= colPaid
                drawWrappedCell(canvas, curX, curY, colRem, headerRowH, AndroidColor.parseColor("#FEF3C7"), fin.totalInvoicesRemaining.cleanNum(), AndroidColor.parseColor("#DC2626"), 8.5f)
                curY += headerRowH

                // Bottom Row 2: متبقي اليوم
                curX = rightEdge
                drawWrappedCell(canvas, curX, curY, colM, headerRowH, AndroidColor.WHITE, "")
                curX -= colM
                drawWrappedCell(canvas, curX, curY, colName, headerRowH, AndroidColor.parseColor("#DCFCE7"), "متبقي اليوم", AndroidColor.BLACK, 9.5f)
                curX -= colName
                prodCols.forEach { p ->
                    val endRemDisp = stockStats[p.key]?.endRemainingDisplay ?: "0 ${p.majorUnit}"
                    drawWrappedCell(canvas, curX, curY, colProdW, headerRowH, AndroidColor.parseColor("#ECFDF5"), endRemDisp, AndroidColor.parseColor("#065F46"), 8.5f)
                    curX -= colProdW
                }
                drawWrappedCell(
                    canvas, curX, curY, colInvoiceTotal + colPaid + colRem, headerRowH,
                    AndroidColor.parseColor("#DCFCE7"),
                    "إجمالي $userHandShortLabel: ${fin.totalCashInUserHand.cleanNum()} | ديون اليوم: ${fin.totalInvoicesRemaining.cleanNum()}",
                    AndroidColor.parseColor("#0F172A"), 7.8f
                )
                curY += headerRowH

                pdfDocument.finishPage(page)
            }

            val outFile = File(context.cacheDir, "Daily_Ledger_$dateStr.pdf")
            FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
