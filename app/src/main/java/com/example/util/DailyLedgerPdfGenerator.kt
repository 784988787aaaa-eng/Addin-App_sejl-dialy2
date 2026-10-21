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
import com.example.data.SmartTradeDao
import com.example.domain.LedgerCalculator
import com.example.domain.ProductDailyStockStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * معاينة سريعة ودقيقة لكتاب السجلات المجمّع قبل بدء التجهيز
 */
data class PeriodLedgerBookPreview(
    val startDate: String,
    val endDate: String,
    val activeDaysCount: Int,
    val totalInvoicesCount: Int,
    val totalPagesCount: Int,
    val orderedDates: List<String>,
    val invoiceCountByDate: Map<String, Int>
)

/**
 * حالة التقدم الحية أثناء تجهيز ودمج كتاب السجلات اليومية (PDF)
 */
data class PeriodLedgerBookProgress(
    val isGenerating: Boolean = false,
    val currentDayIndex: Int = 0,
    val totalDays: Int = 0,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val processedInvoices: Int = 0,
    val totalInvoices: Int = 0,
    val currentDateStr: String = "",
    val currentDayName: String = "",
    val generatedFile: File? = null,
    val wasCancelled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * مولد ملفات PDF لكشف الحساب اليومي الأفقي (Landscape) وكتاب السجلات المجمّع (أسبوع / شهر / عام / فترة مخصصة)
 * مع التفاف النص وتفصيل الوحدات الكبرى والصغرى والترقيم الذكي المزدوج وتذييل اسم المستخدم
 */
object DailyLedgerPdfGenerator {

    private const val ROWS_PER_PAGE = 20
    private const val PAGE_WIDTH = 1190
    private const val PAGE_HEIGHT = 842

    private fun wrapTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        if (paint.measureText(clean) <= maxWidth) return listOf(clean)

        val explicitLines = clean.split("\n")
        if (explicitLines.size > 1) {
            return explicitLines.flatMap { sub -> wrapSingleParagraph(sub, paint, maxWidth) }
        }
        return wrapSingleParagraph(clean, paint, maxWidth)
    }

    private fun wrapSingleParagraph(text: String, paint: Paint, maxWidth: Float): List<String> {
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

    /**
     * حساب معاينة كتاب السجلات المجمّع لفترة محددة (عدد الأيام الفعالة، عدد الفواتير، وعدد الصفحات الإجمالي الدقيق)
     */
    suspend fun computePeriodBookPreview(
        dao: SmartTradeDao,
        startDate: String,
        endDate: String,
        ascendingOrder: Boolean = true
    ): PeriodLedgerBookPreview {
        val cleanStart = minOf(startDate, endDate)
        val cleanEnd = maxOf(startDate, endDate)
        val activeDatesAsc = dao.getActiveDatesInRangeSync(cleanStart, cleanEnd)
        val countsList = dao.getInvoiceCountsByDateInRangeSync(cleanStart, cleanEnd)
        val countMap = countsList.associate { it.date to it.invoiceCount }

        val orderedDates = if (ascendingOrder) activeDatesAsc else activeDatesAsc.asReversed()
        var totalInvoices = 0
        var totalPages = 0
        for (d in orderedDates) {
            val cnt = countMap[d] ?: 0
            totalInvoices += cnt
            totalPages += ceil(cnt.coerceAtLeast(1) / ROWS_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)
        }

        return PeriodLedgerBookPreview(
            startDate = cleanStart,
            endDate = cleanEnd,
            activeDaysCount = orderedDates.size,
            totalInvoicesCount = totalInvoices,
            totalPagesCount = totalPages,
            orderedDates = orderedDates,
            invoiceCountByDate = countMap
        )
    }

    /**
     * توليد ملف PDF لكشف حساب يوم واحد
     */
    fun generateDailySheetPdfFile(
        context: Context,
        dateStr: String,
        dayName: String,
        products: List<ProductEntity>,
        dayInvoices: List<SaleInvoiceEntity>,
        stockStats: Map<String, ProductDailyStockStats>,
        userName: String = ""
    ): File? {
        val pdfDocument = PdfDocument()
        return try {
            val effectiveUser = userName.ifBlank {
                context.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
                    .getString("user_name", "") ?: ""
            }.trim()

            val totalPages = ceil(dayInvoices.size.coerceAtLeast(1) / ROWS_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                try {
                    drawDailyLedgerPageOnCanvas(
                        canvas = page.canvas,
                        dateStr = dateStr,
                        dayName = dayName,
                        products = products,
                        dayInvoices = dayInvoices,
                        stockStats = stockStats,
                        effectiveUser = effectiveUser,
                        dayPageIndex = pageIndex,
                        dayTotalPages = totalPages,
                        rowsPerPage = ROWS_PER_PAGE,
                        isBookMode = false,
                        dayOrderIndex = 1,
                        totalDaysInBook = 1,
                        globalBookPageNumber = pageIndex + 1,
                        totalBookPages = totalPages
                    )
                } finally {
                    pdfDocument.finishPage(page)
                }
            }

            val outFile = File(context.cacheDir, "Daily_Ledger_$dateStr.pdf")
            FileOutputStream(outFile).use { pdfDocument.writeTo(it) }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * توليد كتاب PDF موحد ومفهرس يجمع كافة سجلات الفترة المحددة (أسبوع / شهر / عام / من تاريخ إلى تاريخ)
     * مصمم للصمود أمام آلاف المعاملات مع دعم الإلغاء الفوري الآمن في أي لحظة دون توقف أو انهيار
     */
    suspend fun generatePeriodLedgerBookPdfFile(
        context: Context,
        dao: SmartTradeDao,
        startDate: String,
        endDate: String,
        ascendingOrder: Boolean = true,
        userName: String = "",
        onProgress: suspend (PeriodLedgerBookProgress) -> Unit = {}
    ): File? {
        val coroutineCtx = currentCoroutineContext()
        coroutineCtx.ensureActive()

        val effectiveUser = userName.ifBlank {
            context.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
                .getString("user_name", "") ?: ""
        }.trim()

        val preview = computePeriodBookPreview(dao, startDate, endDate, ascendingOrder)
        if (preview.orderedDates.isEmpty()) {
            return null
        }

        val products = dao.getAllProducts()
        coroutineCtx.ensureActive()

        // حساب إحصائيات المخزون لكل يوم في الفترة بتمريرة واحدة فائقة السرعة O(N + D*P)
        val minDate = preview.startDate
        val invoiceProjections = dao.getInvoiceItemsFromDateOnwardsSync(minDate)
        coroutineCtx.ensureActive()
        val supplyProjections = dao.getAggregatedSuppliesByDateFromDateOnwardsSync(minDate)
        coroutineCtx.ensureActive()

        val multiDayStockStats = LedgerCalculator.computeMultiDayStockStatsMap(
            targetDates = preview.orderedDates,
            products = products,
            invoiceProjectionsFromMinDateOnwards = invoiceProjections,
            supplyProjectionsByDateFromMinDateOnwards = supplyProjections
        )
        coroutineCtx.ensureActive()

        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "Ledger_Book_Temp_${System.currentTimeMillis()}.pdf")
        var completedSuccessfully = false

        try {
            var globalPageCounter = 0
            var processedInvoicesSoFar = 0
            val totalDays = preview.orderedDates.size
            val totalExpectedPages = preview.totalPagesCount.coerceAtLeast(1)
            val totalExpectedInvoices = preview.totalInvoicesCount

            for ((dayIdx, dateStr) in preview.orderedDates.withIndex()) {
                coroutineCtx.ensureActive()
                val dayName = getDayNameFromDateStr(dateStr)
                val dayInvoices = dao.getSalesInvoicesByDateSync(dateStr)
                val dayStockStats = multiDayStockStats[dateStr] ?: emptyMap()
                val dayTotalPages = ceil(dayInvoices.size.coerceAtLeast(1) / ROWS_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)

                for (dayPageIdx in 0 until dayTotalPages) {
                    coroutineCtx.ensureActive()
                    globalPageCounter += 1
                    val doneInDay = ((dayPageIdx + 1) * ROWS_PER_PAGE).coerceAtMost(dayInvoices.size)

                    onProgress(
                        PeriodLedgerBookProgress(
                            isGenerating = true,
                            currentDayIndex = dayIdx + 1,
                            totalDays = totalDays,
                            currentPageIndex = globalPageCounter,
                            totalPages = totalExpectedPages,
                            processedInvoices = processedInvoicesSoFar + doneInDay,
                            totalInvoices = totalExpectedInvoices,
                            currentDateStr = dateStr,
                            currentDayName = dayName
                        )
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, globalPageCounter).create()
                    val page = pdfDocument.startPage(pageInfo)
                    try {
                        drawDailyLedgerPageOnCanvas(
                            canvas = page.canvas,
                            dateStr = dateStr,
                            dayName = dayName,
                            products = products,
                            dayInvoices = dayInvoices,
                            stockStats = dayStockStats,
                            effectiveUser = effectiveUser,
                            dayPageIndex = dayPageIdx,
                            dayTotalPages = dayTotalPages,
                            rowsPerPage = ROWS_PER_PAGE,
                            isBookMode = true,
                            dayOrderIndex = dayIdx + 1,
                            totalDaysInBook = totalDays,
                            globalBookPageNumber = globalPageCounter,
                            totalBookPages = totalExpectedPages
                        )
                    } finally {
                        pdfDocument.finishPage(page)
                    }

                    // إتاحة الفرصة لتحديث شريط التقدم والاستجابة الفورية لزر الإلغاء
                    yield()
                }

                processedInvoicesSoFar += dayInvoices.size
            }

            coroutineCtx.ensureActive()
            FileOutputStream(tempFile).use { out ->
                pdfDocument.writeTo(out)
            }
            coroutineCtx.ensureActive()

            val finalFile = File(context.cacheDir, "Daily_Ledger_Book_${preview.startDate}_to_${preview.endDate}.pdf")
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val moved = tempFile.renameTo(finalFile)
            val resultFile = if (moved) finalFile else tempFile
            completedSuccessfully = true
            return resultFile
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {
            }
            if (!completedSuccessfully && tempFile.exists()) {
                try {
                    tempFile.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * رسم صفحة كشف حساب يومي واحدة بدقة متناهية مع لمسة فنية في رأس الجدول تميز بين
     * رقم ورقة اليوم (مثلاً: ورقة اليوم 1 من 2) والترقيم العام لصفحات الكتاب (ص X من Y)
     */
    private fun drawDailyLedgerPageOnCanvas(
        canvas: Canvas,
        dateStr: String,
        dayName: String,
        products: List<ProductEntity>,
        dayInvoices: List<SaleInvoiceEntity>,
        stockStats: Map<String, ProductDailyStockStats>,
        effectiveUser: String,
        dayPageIndex: Int,
        dayTotalPages: Int,
        rowsPerPage: Int,
        isBookMode: Boolean,
        dayOrderIndex: Int,
        totalDaysInBook: Int,
        globalBookPageNumber: Int,
        totalBookPages: Int
    ) {
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
        val usableWidth = PAGE_WIDTH - (marginX * 2)

        val colM = 28f
        val colName = 126f
        val colInvoiceTotal = 56f
        val colPaid = 68f
        val colRem = 56f
        val remainingForProds = usableWidth - (colM + colName + colInvoiceTotal + colPaid + colRem)
        val colProdW = remainingForProds / prodCols.size.coerceAtLeast(1)

        fun drawWrappedCell(
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

        // خلفية الصفحة البيضاء النقية
        fillPaint.color = AndroidColor.WHITE
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), fillPaint)

        // ═══════════════════════════════════════════════════════════════════════════
        // شريط العنوان العلوي مع اللمسة الفنية الذكية لتمييز (ورقة اليوم) عن (ترقيم الكتاب العام)
        // ═══════════════════════════════════════════════════════════════════════════
        val rightEdge = PAGE_WIDTH - marginX
        val headerPillTop = 7f
        val headerPillBottom = 29f

        if (isBookMode) {
            // 1. أقصى اليمين: شارة ترتيب سجل اليوم داخل الكتاب
            val dayOrderPillW = 175f
            val dayOrderRect = RectF(rightEdge - dayOrderPillW, headerPillTop, rightEdge, headerPillBottom)
            fillPaint.color = AndroidColor.parseColor("#ECFDF5")
            canvas.drawRoundRect(dayOrderRect, 6f, 6f, fillPaint)
            val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = AndroidColor.parseColor("#10B981")
            }
            canvas.drawRoundRect(dayOrderRect, 6f, 6f, pillBorderPaint)
            textPaint.color = AndroidColor.parseColor("#065F46")
            textPaint.textSize = 10.0f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "سجل اليوم: $dayOrderIndex من $totalDaysInBook",
                dayOrderRect.centerX(),
                22f,
                textPaint
            )

            // 2. أقصى اليسار: شارة الترقيم العام للكتاب (لا تلتبس أبداً مع صفحات اليوم الواحد)
            val bookPagePillW = 205f
            val bookPageRect = RectF(marginX, headerPillTop, marginX + bookPagePillW, headerPillBottom)
            fillPaint.color = AndroidColor.parseColor("#0F172A")
            canvas.drawRoundRect(bookPageRect, 6f, 6f, fillPaint)
            textPaint.color = AndroidColor.parseColor("#F8FAFC")
            textPaint.textSize = 10.0f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "ترقيم الكتاب العام: ص $globalBookPageNumber من $totalBookPages",
                bookPageRect.centerX(),
                22f,
                textPaint
            )
        }

        // 3. المنتصف (نفس سطر العنوان): عنوان كشف حساب اليوم + لمسة فنية لترقيم ورقة اليوم عند تعدد صفحات اليوم
        val mainTitleText = "السجل اليومي — كشف حساب: اليوم: $dayName   |   التاريخ: $dateStr م"
        textPaint.color = AndroidColor.parseColor("#0F172A")
        textPaint.textSize = if (isBookMode) 13.5f else 14.5f
        textPaint.textAlign = Paint.Align.CENTER

        if (dayTotalPages > 1) {
            // لمسة فنية أنيقة في نفس سطر العنوان بجانب التاريخ مباشرة لبيان (ورقة 1 من 2 / ورقة 2 من 2) الخاصة بهذا اليوم
            val badgeText = "❖ ورقة اليوم: ${dayPageIndex + 1} من $dayTotalPages"
            val titleW = textPaint.measureText(mainTitleText)
            textPaint.textSize = 10.5f
            val badgeTextW = textPaint.measureText(badgeText)
            val badgeW = badgeTextW + 18f
            val gap = 10f
            val totalCenterGroupW = titleW + gap + badgeW
            val groupRightX = (PAGE_WIDTH / 2f) + (totalCenterGroupW / 2f)

            // رسم العنوان الرئيسي على يمين الشارة الفنية (بما يتوافق مع اتجاه القراءة العربي RTL)
            textPaint.textSize = if (isBookMode) 13.5f else 14.5f
            textPaint.color = AndroidColor.parseColor("#0F172A")
            val titleCenterX = groupRightX - (titleW / 2f)
            canvas.drawText(mainTitleText, titleCenterX, 23f, textPaint)

            // رسم الشارة الفنية لورقة اليوم على يسار العنوان مباشرة في نفس السطر
            val badgeRightX = groupRightX - titleW - gap
            val badgeRect = RectF(badgeRightX - badgeW, headerPillTop, badgeRightX, headerPillBottom)
            fillPaint.color = AndroidColor.parseColor("#FEF3C7")
            canvas.drawRoundRect(badgeRect, 11f, 11f, fillPaint)
            val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = AndroidColor.parseColor("#D97706")
            }
            canvas.drawRoundRect(badgeRect, 11f, 11f, badgeStroke)
            textPaint.textSize = 10.0f
            textPaint.color = AndroidColor.parseColor("#92400E")
            canvas.drawText(badgeText, badgeRect.centerX(), 22f, textPaint)
        } else {
            canvas.drawText(mainTitleText, PAGE_WIDTH / 2f, 23f, textPaint)
        }

        val fin = LedgerCalculator.computeDailyFinancialTotals(dayInvoices)
        val userHandShortLabel = if (effectiveUser.isNotEmpty()) "بيد المستخدم ($effectiveUser)" else "بيد المستخدم"

        val startY = 34f
        val headerRowH = 29f
        val dataRowH = 31.0f
        var curY = startY

        // Top Row 1: وارد بضاعة جديد
        var curX = rightEdge
        drawWrappedCell(curX, curY, colM, headerRowH, AndroidColor.parseColor("#CBD5E1"), "")
        curX -= colM
        drawWrappedCell(curX, curY, colName, headerRowH, AndroidColor.parseColor("#CBD5E1"), "وارد بضاعة جديد", AndroidColor.BLACK, 9.5f)
        curX -= colName
        prodCols.forEach { p ->
            val suppliedDisp = stockStats[p.key]?.suppliedDisplay ?: ""
            drawWrappedCell(curX, curY, colProdW, headerRowH, AndroidColor.parseColor("#E2E8F0"), suppliedDisp, AndroidColor.parseColor("#065F46"), 8.5f)
            curX -= colProdW
        }
        drawWrappedCell(
            curX, curY, colInvoiceTotal + colPaid + colRem, headerRowH,
            AndroidColor.parseColor("#ECFDF5"),
            "نقد الفواتير: ${fin.totalCashReceived.cleanNum()}",
            AndroidColor.parseColor("#065F46"), 8.0f
        )
        curY += headerRowH

        // Top Row 2: إجمالي المتبقي
        curX = rightEdge
        drawWrappedCell(curX, curY, colM, headerRowH, AndroidColor.parseColor("#CBD5E1"), "م")
        curX -= colM
        drawWrappedCell(curX, curY, colName, headerRowH, AndroidColor.parseColor("#CBD5E1"), "إجمالي المتبقي", AndroidColor.BLACK, 9.5f)
        curX -= colName
        prodCols.forEach { p ->
            val prevRemDisp = stockStats[p.key]?.prevRemainingDisplay ?: ""
            drawWrappedCell(curX, curY, colProdW, headerRowH, p.colorBgHex.toInt(), prevRemDisp, p.colorTextHex.toInt(), 8.5f)
            curX -= colProdW
        }
        drawWrappedCell(
            curX, curY, colInvoiceTotal + colPaid + colRem, headerRowH,
            AndroidColor.parseColor("#FFFBEB"),
            "سداد دين سابق: ${fin.totalPrevDebtNotified.cleanNum()}",
            AndroidColor.parseColor("#92400E"), 8.0f
        )
        curY += headerRowH

        // Top Row 3: ترويسة الأعمدة
        curX = rightEdge
        drawWrappedCell(curX, curY, colM, headerRowH, AndroidColor.parseColor("#CBD5E1"), "")
        curX -= colM
        drawWrappedCell(curX, curY, colName, headerRowH, AndroidColor.parseColor("#CBD5E1"), "الإســــــــــــــــــــــــم", AndroidColor.BLACK, 10f)
        curX -= colName
        prodCols.forEach { p ->
            drawWrappedCell(curX, curY, colProdW, headerRowH, p.colorBgHex.toInt(), p.name, p.colorTextHex.toInt(), 9.0f)
            curX -= colProdW
        }
        drawWrappedCell(curX, curY, colInvoiceTotal, headerRowH, AndroidColor.parseColor("#FEF3C7"), "إجمالي الفاتورة", AndroidColor.parseColor("#92400E"), 8.5f)
        curX -= colInvoiceTotal
        drawWrappedCell(curX, curY, colPaid, headerRowH, AndroidColor.parseColor("#F1F5F9"), "مسلم", AndroidColor.BLACK, 9.5f)
        curX -= colPaid
        drawWrappedCell(curX, curY, colRem, headerRowH, AndroidColor.parseColor("#DBEAFE"), "المتبقي", AndroidColor.parseColor("#1E3A8A"), 9.5f)
        curY += headerRowH

        // Data Rows (20 rows per page)
        val startOffset = dayPageIndex * rowsPerPage
        for (r in 0 until rowsPerPage) {
            val globalIndex = startOffset + r
            val inv = dayInvoices.getOrNull(globalIndex)
            val decodedItemsMap = if (inv != null) decodeInvoiceItemsMap(inv.itemsJson) else emptyMap()
            val acc = if (inv != null) LedgerCalculator.computeUnifiedInvoiceAccounting(inv) else null

            val customerCellText = when {
                inv == null -> ""
                inv.suppliedGoodsNote.isNotBlank() -> "${inv.customerName}\n(${inv.suppliedGoodsNote.trim()})"
                else -> inv.customerName
            }

            curX = rightEdge
            drawWrappedCell(curX, curY, colM, dataRowH, AndroidColor.parseColor("#CBD5E1"), "${globalIndex + 1}", AndroidColor.BLACK, 9.5f)
            curX -= colM
            drawWrappedCell(curX, curY, colName, dataRowH, AndroidColor.parseColor("#E2E8F0"), customerCellText, AndroidColor.BLACK, 9.5f)
            curX -= colName

            prodCols.forEach { p ->
                val item = decodedItemsMap[p.key]
                drawWrappedCell(curX, curY, colProdW, dataRowH, p.colorBgHex.toInt(), item?.unitDisplay ?: "", p.colorTextHex.toInt(), 8.5f)
                curX -= colProdW
            }

            val invTotalCellText = when {
                acc == null || acc.netInvoiceSubtotal <= 0.0 -> ""
                acc.discount > 0.0 -> "${acc.netInvoiceSubtotal.cleanNum()}\n(خصم: ${acc.discount.cleanNum()})"
                else -> acc.netInvoiceSubtotal.cleanNum()
            }
            drawWrappedCell(
                curX, curY, colInvoiceTotal, dataRowH,
                AndroidColor.parseColor("#FFFBEB"),
                invTotalCellText,
                AndroidColor.parseColor("#92400E"), 9.0f
            )
            curX -= colInvoiceTotal

            val paidText = acc?.paidCellText ?: ""
            drawWrappedCell(curX, curY, colPaid, dataRowH, AndroidColor.WHITE, paidText, AndroidColor.parseColor("#047857"), 8.5f)
            curX -= colPaid

            val hasRem = acc != null && acc.remainingOnInvoice > 0
            val remBg = if (hasRem) AndroidColor.parseColor("#FEF3C7") else AndroidColor.parseColor("#DBEAFE")
            val remTxtColor = if (hasRem) AndroidColor.parseColor("#DC2626") else AndroidColor.parseColor("#1E3A8A")
            drawWrappedCell(curX, curY, colRem, dataRowH, remBg, if (acc != null) acc.remainingOnInvoice.cleanNum() else "", remTxtColor, 9.0f)
            curY += dataRowH
        }

        // Bottom Row 1: إجمالي البيع
        curX = rightEdge
        drawWrappedCell(curX, curY, colM, headerRowH, AndroidColor.WHITE, "")
        curX -= colM
        drawWrappedCell(curX, curY, colName, headerRowH, AndroidColor.parseColor("#DCFCE7"), "إجمالي البيع", AndroidColor.BLACK, 9.5f)
        curX -= colName
        prodCols.forEach { p ->
            val soldDisp = stockStats[p.key]?.soldDisplay ?: ""
            drawWrappedCell(curX, curY, colProdW, headerRowH, AndroidColor.parseColor("#ECFDF5"), soldDisp, AndroidColor.parseColor("#047857"), 8.5f)
            curX -= colProdW
        }
        val totalSubSummaryText = when {
            fin.totalInvoicesSubtotal <= 0.0 -> "0"
            fin.totalDiscountGiven > 0.0 -> "${fin.totalInvoicesSubtotal.cleanNum()}\n(خصم: ${fin.totalDiscountGiven.cleanNum()})"
            else -> fin.totalInvoicesSubtotal.cleanNum()
        }
        drawWrappedCell(curX, curY, colInvoiceTotal, headerRowH, AndroidColor.parseColor("#FEF3C7"), totalSubSummaryText, AndroidColor.parseColor("#92400E"), 8.5f)
        curX -= colInvoiceTotal
        val totalPaidSummaryText = if (fin.totalPrevDebtNotified > 0) {
            "نقد: ${fin.totalCashReceived.cleanNum()} | سابق: ${fin.totalPrevDebtNotified.cleanNum()}"
        } else {
            fin.totalCashReceived.cleanNum()
        }
        drawWrappedCell(curX, curY, colPaid, headerRowH, AndroidColor.parseColor("#ECFDF5"), totalPaidSummaryText, AndroidColor.parseColor("#047857"), 8.0f)
        curX -= colPaid
        drawWrappedCell(curX, curY, colRem, headerRowH, AndroidColor.parseColor("#FEF3C7"), fin.totalInvoicesRemaining.cleanNum(), AndroidColor.parseColor("#DC2626"), 8.5f)
        curY += headerRowH

        // Bottom Row 2: متبقي اليوم
        curX = rightEdge
        drawWrappedCell(curX, curY, colM, headerRowH, AndroidColor.WHITE, "")
        curX -= colM
        drawWrappedCell(curX, curY, colName, headerRowH, AndroidColor.parseColor("#DCFCE7"), "متبقي اليوم", AndroidColor.BLACK, 9.5f)
        curX -= colName
        prodCols.forEach { p ->
            val endRemDisp = stockStats[p.key]?.endRemainingDisplay ?: "0 ${p.majorUnit}"
            drawWrappedCell(curX, curY, colProdW, headerRowH, AndroidColor.parseColor("#ECFDF5"), endRemDisp, AndroidColor.parseColor("#065F46"), 8.5f)
            curX -= colProdW
        }
        drawWrappedCell(
            curX, curY, colInvoiceTotal + colPaid + colRem, headerRowH,
            AndroidColor.parseColor("#DCFCE7"),
            "إجمالي $userHandShortLabel: ${fin.totalCashInUserHand.cleanNum()} | ديون اليوم: ${fin.totalInvoicesRemaining.cleanNum()}",
            AndroidColor.parseColor("#0F172A"), 7.8f
        )
    }
}
