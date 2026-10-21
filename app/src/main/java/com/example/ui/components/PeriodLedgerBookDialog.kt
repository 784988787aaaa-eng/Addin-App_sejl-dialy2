package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.SmartTradeViewModel
import com.example.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

private enum class BookPostAction {
    NONE,
    SHARE_PDF,
    PRINT_PDF,
    SHARE_MANAGER_WHATSAPP
}

/**
 * نافذة تجميع ودمج جميع السجلات اليومية بمدة محددة (أسبوع / شهر / عام / من تاريخ إلى تاريخ)
 * في كتاب PDF واحد مفهرس ومرقم بدقة متناهية مع دعم الإلغاء الآمن في أي لحظة دون توقف التطبيق
 */
@Composable
fun PeriodLedgerBookDialog(
    viewModel: SmartTradeViewModel,
    activeDates: List<String>,
    initialSelectedDate: String,
    managerPhone: String,
    userName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val todayStr = remember { getCurrentDateTimeArabic().date }
    val currentYear = remember(todayStr) { todayStr.take(4).toIntOrNull() ?: 2026 }
    val currentMonth = remember(todayStr) { todayStr.drop(5).take(2).toIntOrNull() ?: 10 }

    // 0 = فترات سريعة (أسبوع / شهر / عام) | 1 = تحديد شهر معين | 2 = من تاريخ إلى تاريخ
    var selectionTab by remember { mutableIntStateOf(0) }
    var selectedPresetKey by remember { mutableStateOf("week") }

    // إعدادات تبويب الشهر المحدد
    var pickedYear by remember { mutableIntStateOf(currentYear) }
    var pickedMonth by remember { mutableIntStateOf(currentMonth) }

    // إعدادات الفترة الفعلية (من تاريخ -> إلى تاريخ)
    var startDate by remember {
        mutableStateOf(shiftDateString(initialSelectedDate.ifBlank { todayStr }, -6))
    }
    var endDate by remember {
        mutableStateOf(initialSelectedDate.ifBlank { todayStr })
    }

    // ترتيب السجلات داخل الكتاب: true = من الأقدم إلى الأحدث (تسلسل دفتر محاسبي)، false = من الأحدث إلى الأقدم
    var ascendingOrder by remember { mutableStateOf(true) }

    // معاينة حية لعدد الأيام والفواتير والصفحات
    var preview by remember { mutableStateOf<PeriodLedgerBookPreview?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // حالة التجهيز والإلغاء الآمن
    var progressState by remember { mutableStateOf(PeriodLedgerBookProgress()) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    // نافذة اختيار التاريخ المخصص (من / إلى)
    var pickingDateTarget by remember { mutableStateOf<String?>(null) } // "start" or "end"

    // السنوات المتاحة من السجلات الفعلية + السنة الحالية والسابقة
    val availableYears = remember(activeDates, currentYear) {
        val set = linkedSetOf(currentYear, currentYear - 1)
        activeDates.forEach { d ->
            d.take(4).toIntOrNull()?.let { set.add(it) }
        }
        set.sortedDescending()
    }

    // خريطة عدد الأيام النشطة لكل شهر في السنة المختارة
    val activeDaysPerMonthInPickedYear = remember(activeDates, pickedYear) {
        val prefix = String.format(Locale.US, "%04d-", pickedYear)
        val map = HashMap<Int, Int>()
        activeDates.forEach { d ->
            if (d.startsWith(prefix) && d.length >= 7) {
                val m = d.substring(5, 7).toIntOrNull()
                if (m != null) {
                    map[m] = (map[m] ?: 0) + 1
                }
            }
        }
        map
    }

    // تحديث المعاينة تلقائياً عند تغير الفترة أو الترتيب
    LaunchedEffect(startDate, endDate, ascendingOrder) {
        isLoadingPreview = true
        preview = try {
            viewModel.computePeriodBookPreview(startDate, endDate, ascendingOrder)
        } catch (_: Exception) {
            null
        }
        isLoadingPreview = false
        // إذا تغيرت الفترة نمسح الملف الجاهز السابق حتى يتم توليد الكتاب المطابق للفترة الجديدة
        if (!progressState.isGenerating) {
            progressState = PeriodLedgerBookProgress()
        }
    }

    fun cancelOngoingGeneration() {
        val job = generationJob
        if (job != null && job.isActive) {
            job.cancel()
        }
        generationJob = null
        progressState = progressState.copy(
            isGenerating = false,
            wasCancelled = true,
            generatedFile = null,
            errorMessage = null
        )
    }

    // ضمان إلغاء العملية بأمان عند إغلاق النافذة
    DisposableEffect(Unit) {
        onDispose {
            generationJob?.cancel()
        }
    }

    fun executeGenerateBook(postAction: BookPostAction) {
        val currentPreview = preview
        if (currentPreview == null || currentPreview.activeDaysCount == 0) {
            Toast.makeText(context, "لا توجد سجلات يومية مسجلة في هذه الفترة لدمجها", Toast.LENGTH_SHORT).show()
            return
        }

        // إذا كان الكتاب جاهزاً بالفعل لنفس الفترة، ننفذ الإجراء فوراً دون إعادة التجهيز
        val existingFile = progressState.generatedFile
        if (existingFile != null && existingFile.exists() && !progressState.isGenerating) {
            val bookTitle = "كتاب السجلات اليومية (${currentPreview.startDate} إلى ${currentPreview.endDate})"
            when (postAction) {
                BookPostAction.PRINT_PDF -> PdfAndBackupHelper.printPdfFile(context, existingFile, "Ledger_Book_${currentPreview.startDate}_${currentPreview.endDate}")
                BookPostAction.SHARE_PDF -> PdfAndBackupHelper.sharePdfFile(context, existingFile, bookTitle)
                BookPostAction.SHARE_MANAGER_WHATSAPP -> PdfAndBackupHelper.sharePdfToManagerWhatsApp(
                    context = context,
                    pdfFile = existingFile,
                    managerPhone = managerPhone,
                    caption = "📚 $bookTitle — إجمالي الأيام: ${currentPreview.activeDaysCount} | إجمالي الصفحات: ${currentPreview.totalPagesCount}",
                    userName = userName
                )
                BookPostAction.NONE -> {}
            }
            return
        }

        generationJob?.cancel()
        generationJob = scope.launch {
            progressState = PeriodLedgerBookProgress(
                isGenerating = true,
                currentDayIndex = 0,
                totalDays = currentPreview.activeDaysCount,
                currentPageIndex = 0,
                totalPages = currentPreview.totalPagesCount,
                processedInvoices = 0,
                totalInvoices = currentPreview.totalInvoicesCount
            )
            try {
                val pdfFile = viewModel.generatePeriodLedgerBookPdf(
                    context = context,
                    startDate = currentPreview.startDate,
                    endDate = currentPreview.endDate,
                    ascendingOrder = ascendingOrder,
                    onProgress = { liveProgress ->
                        progressState = liveProgress
                    }
                )
                if (pdfFile != null && pdfFile.exists()) {
                    progressState = PeriodLedgerBookProgress(
                        isGenerating = false,
                        currentDayIndex = currentPreview.activeDaysCount,
                        totalDays = currentPreview.activeDaysCount,
                        currentPageIndex = currentPreview.totalPagesCount,
                        totalPages = currentPreview.totalPagesCount,
                        processedInvoices = currentPreview.totalInvoicesCount,
                        totalInvoices = currentPreview.totalInvoicesCount,
                        generatedFile = pdfFile,
                        wasCancelled = false
                    )
                    val bookTitle = "كتاب السجلات اليومية (${currentPreview.startDate} إلى ${currentPreview.endDate})"
                    when (postAction) {
                        BookPostAction.PRINT_PDF -> PdfAndBackupHelper.printPdfFile(
                            context,
                            pdfFile,
                            "Ledger_Book_${currentPreview.startDate}_${currentPreview.endDate}"
                        )
                        BookPostAction.SHARE_PDF -> PdfAndBackupHelper.sharePdfFile(context, pdfFile, bookTitle)
                        BookPostAction.SHARE_MANAGER_WHATSAPP -> PdfAndBackupHelper.sharePdfToManagerWhatsApp(
                            context = context,
                            pdfFile = pdfFile,
                            managerPhone = managerPhone,
                            caption = "📚 $bookTitle — إجمالي الأيام: ${currentPreview.activeDaysCount} | إجمالي الصفحات: ${currentPreview.totalPagesCount}",
                            userName = userName
                        )
                        BookPostAction.NONE -> {
                            Toast.makeText(context, "تم تجهيز كتاب السجلات الموحد بنجاح ✓", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    progressState = PeriodLedgerBookProgress(
                        isGenerating = false,
                        errorMessage = "تعذر إنشاء ملف الكتاب، يرجى المحاولة مرة أخرى"
                    )
                }
            } catch (_: CancellationException) {
                progressState = PeriodLedgerBookProgress(
                    isGenerating = false,
                    wasCancelled = true
                )
            } catch (e: Exception) {
                progressState = PeriodLedgerBookProgress(
                    isGenerating = false,
                    errorMessage = "حدث خطأ غير متوقع أثناء التجهيز"
                )
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (progressState.isGenerating) {
                cancelOngoingGeneration()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = !progressState.isGenerating
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 720.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. شريط عنوان النافذة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = Color(0xFF312E81),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "تجميع كتاب السجلات اليومية الموحد (PDF)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "دمج كشوفات أسبوع أو شهر أو فترة مخصصة في كتاب واحد مفهرس ومرقم للطباعة",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (progressState.isGenerating) {
                                    cancelOngoingGeneration()
                                }
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 2. تبويبات اختيار أسلوب تحديد المدة
                    Surface(
                        color = Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PeriodModeTabChip(
                                title = "⚡ فترات سريعة",
                                selected = selectionTab == 0,
                                enabled = !progressState.isGenerating,
                                modifier = Modifier.weight(1f),
                                onClick = { selectionTab = 0 }
                            )
                            PeriodModeTabChip(
                                title = "📅 تحديد شهر",
                                selected = selectionTab == 1,
                                enabled = !progressState.isGenerating,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectionTab = 1
                                    val (mStart, mEnd) = getMonthDateRange(pickedYear, pickedMonth)
                                    startDate = mStart
                                    endDate = mEnd
                                }
                            )
                            PeriodModeTabChip(
                                title = "🗓️ من تاريخ إلى تاريخ",
                                selected = selectionTab == 2,
                                enabled = !progressState.isGenerating,
                                modifier = Modifier.weight(1.15f),
                                onClick = { selectionTab = 2 }
                            )
                        }
                    }

                    // 3. محتوى التبويب المختار
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (selectionTab) {
                                0 -> {
                                    Text(
                                        text = "اختر المدة المطلوبة بضغطة واحدة:",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PresetPeriodButton(
                                            title = "سجلات أسبوع",
                                            subtitle = "آخر 7 أيام",
                                            selected = selectedPresetKey == "week",
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                selectedPresetKey = "week"
                                                val base = initialSelectedDate.ifBlank { todayStr }
                                                startDate = shiftDateString(base, -6)
                                                endDate = base
                                            }
                                        )
                                        PresetPeriodButton(
                                            title = "هذا الشهر",
                                            subtitle = "${getArabicMonthName(currentMonth)} $currentYear",
                                            selected = selectedPresetKey == "this_month",
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                selectedPresetKey = "this_month"
                                                val (s, e) = getMonthDateRange(currentYear, currentMonth)
                                                startDate = s
                                                endDate = e
                                            }
                                        )
                                        PresetPeriodButton(
                                            title = "الشهر السابق",
                                            subtitle = run {
                                                val prevM = if (currentMonth == 1) 12 else currentMonth - 1
                                                val prevY = if (currentMonth == 1) currentYear - 1 else currentYear
                                                "${getArabicMonthName(prevM)} $prevY"
                                            },
                                            selected = selectedPresetKey == "prev_month",
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                selectedPresetKey = "prev_month"
                                                val prevM = if (currentMonth == 1) 12 else currentMonth - 1
                                                val prevY = if (currentMonth == 1) currentYear - 1 else currentYear
                                                val (s, e) = getMonthDateRange(prevY, prevM)
                                                startDate = s
                                                endDate = e
                                            }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PresetPeriodButton(
                                            title = "سجل عام كامل",
                                            subtitle = "سنة $currentYear كاملة",
                                            selected = selectedPresetKey == "full_year",
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                selectedPresetKey = "full_year"
                                                startDate = String.format(Locale.US, "%04d-01-01", currentYear)
                                                endDate = String.format(Locale.US, "%04d-12-31", currentYear)
                                            }
                                        )
                                        PresetPeriodButton(
                                            title = "جميع السجلات الشاملة",
                                            subtitle = if (activeDates.isNotEmpty()) "${activeDates.size} يوم مسجل" else "كل الأيام",
                                            selected = selectedPresetKey == "all_records",
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                selectedPresetKey = "all_records"
                                                val minD = activeDates.minOrNull() ?: "2020-01-01"
                                                val maxD = activeDates.maxOrNull() ?: todayStr
                                                startDate = minD
                                                endDate = maxD
                                            }
                                        )
                                    }
                                }

                                1 -> {
                                    // تحديد السنة والشهر
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "السنة:",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F172A)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            availableYears.forEach { yr ->
                                                val isSel = yr == pickedYear
                                                Surface(
                                                    color = if (isSel) Color(0xFF312E81) else Color(0xFFF1F5F9),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF312E81) else Color(0xFFCBD5E1)),
                                                    modifier = Modifier.clickable(enabled = !progressState.isGenerating) {
                                                        pickedYear = yr
                                                        val (s, e) = getMonthDateRange(yr, pickedMonth)
                                                        startDate = s
                                                        endDate = e
                                                    }
                                                ) {
                                                    Text(
                                                        text = "$yr",
                                                        color = if (isSel) Color.White else Color(0xFF1E293B),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "اختر الشهر المطلوب لتجميع سجلاته:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )

                                    // شبكة الأشهر الـ 12 (3 صفوف × 4 أشهر)
                                    for (rowIdx in 0 until 3) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            for (colIdx in 1..4) {
                                                val m = rowIdx * 4 + colIdx
                                                val isSelectedMonth = m == pickedMonth
                                                val activeDaysInMonth = activeDaysPerMonthInPickedYear[m] ?: 0
                                                Surface(
                                                    color = when {
                                                        isSelectedMonth -> Color(0xFF2563EB)
                                                        activeDaysInMonth > 0 -> Color(0xFFECFDF5)
                                                        else -> Color(0xFFF8FAFC)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        when {
                                                            isSelectedMonth -> Color(0xFF1D4ED8)
                                                            activeDaysInMonth > 0 -> Color(0xFF6EE7B7)
                                                            else -> Color(0xFFE2E8F0)
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable(enabled = !progressState.isGenerating) {
                                                            pickedMonth = m
                                                            val (s, e) = getMonthDateRange(pickedYear, m)
                                                            startDate = s
                                                            endDate = e
                                                        }
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = "$m - ${getArabicMonthName(m)}",
                                                            color = if (isSelectedMonth) Color.White else Color(0xFF0F172A),
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Black,
                                                            maxLines = 1
                                                        )
                                                        Text(
                                                            text = if (activeDaysInMonth > 0) "$activeDaysInMonth يوم مسجل" else "فارغ",
                                                            color = when {
                                                                isSelectedMonth -> Color(0xFFDBEAFE)
                                                                activeDaysInMonth > 0 -> Color(0xFF047857)
                                                                else -> Color(0xFF94A3B8)
                                                            },
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                2 -> {
                                    // تحديد دقيق من تاريخ إلى تاريخ
                                    Text(
                                        text = "حدد تاريخ البداية وتاريخ النهاية للكتاب:",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DateRangeEndpointCard(
                                            label = "من تاريخ (البداية)",
                                            dateStr = startDate,
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onStepDay = { delta ->
                                                startDate = shiftDateString(startDate, delta)
                                            },
                                            onOpenPicker = { pickingDateTarget = "start" }
                                        )
                                        DateRangeEndpointCard(
                                            label = "إلى تاريخ (النهاية)",
                                            dateStr = endDate,
                                            enabled = !progressState.isGenerating,
                                            modifier = Modifier.weight(1f),
                                            onStepDay = { delta ->
                                                endDate = shiftDateString(endDate, delta)
                                            },
                                            onOpenPicker = { pickingDateTarget = "end" }
                                        )
                                    }

                                    if (activeDates.isNotEmpty()) {
                                        Text(
                                            text = "أو اختر سريعاً من تواريخ السجلات المحفوظة:",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            activeDates.take(30).forEach { dStr ->
                                                val isEndpoint = dStr == startDate || dStr == endDate
                                                Surface(
                                                    color = if (isEndpoint) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(
                                                        0.8.dp,
                                                        if (isEndpoint) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                                                    ),
                                                    modifier = Modifier.clickable(enabled = !progressState.isGenerating) {
                                                        if (dStr < startDate) {
                                                            startDate = dStr
                                                        } else if (dStr > endDate) {
                                                            endDate = dStr
                                                        } else {
                                                            startDate = dStr
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = "${getDayNameFromDateStr(dStr)} $dStr",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isEndpoint) Color(0xFF1E3A8A) else Color(0xFF334155),
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // خيار ترتيب السجلات داخل الكتاب
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ترتيب السجلات في الكتاب:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF334155)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    SortOrderChip(
                                        text = "من الأقدم للأحدث (متسلسل)",
                                        selected = ascendingOrder,
                                        enabled = !progressState.isGenerating,
                                        onClick = { ascendingOrder = true }
                                    )
                                    SortOrderChip(
                                        text = "من الأحدث للأقدم",
                                        selected = !ascendingOrder,
                                        enabled = !progressState.isGenerating,
                                        onClick = { ascendingOrder = false }
                                    )
                                }
                            }
                        }
                    }

                    // 4. بطاقة المعاينة الدقيقة للكتاب قبل التجهيز
                    val cleanStart = minOf(startDate, endDate)
                    val cleanEnd = maxOf(startDate, endDate)
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📖 ملخص الكتاب المجمّع للفترة:",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38BDF8)
                                )
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "من $cleanStart إلى $cleanEnd",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BookPreviewMetricBox(
                                    label = "السجلات اليومية الفعالة",
                                    value = if (isLoadingPreview) "..." else "${preview?.activeDaysCount ?: 0} يوم",
                                    valueColor = Color(0xFF6EE7B7),
                                    modifier = Modifier.weight(1f)
                                )
                                BookPreviewMetricBox(
                                    label = "إجمالي الفواتير المشمولة",
                                    value = if (isLoadingPreview) "..." else "${preview?.totalInvoicesCount ?: 0} فاتورة",
                                    valueColor = Color(0xFFFDE047),
                                    modifier = Modifier.weight(1f)
                                )
                                BookPreviewMetricBox(
                                    label = "إجمالي صفحات الكتاب",
                                    value = if (isLoadingPreview) "..." else "${preview?.totalPagesCount ?: 0} صفحة",
                                    valueColor = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "✦ كل يوم يحتفظ بتفاصيله وحساباته المستقلة، وإذا امتد سجل اليوم لأكثر من صفحة تظهر شارة فنية (❖ ورقة اليوم: 1 من 2) في سطر العنوان منفصلةً عن الترقيم العام للكتاب.",
                                fontSize = 8.8.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // 5. شريط التقدم الحي وزر الإلغاء الآمن أثناء التجهيز
                    AnimatedVisibility(
                        visible = progressState.isGenerating || progressState.wasCancelled || progressState.generatedFile != null || progressState.errorMessage != null
                    ) {
                        Surface(
                            color = when {
                                progressState.isGenerating -> Color(0xFFEFF6FF)
                                progressState.wasCancelled -> Color(0xFFFFFBEB)
                                progressState.errorMessage != null -> Color(0xFFFFF1F2)
                                else -> Color(0xFFECFDF5)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    progressState.isGenerating -> Color(0xFF93C5FD)
                                    progressState.wasCancelled -> Color(0xFFFCD34D)
                                    progressState.errorMessage != null -> Color(0xFFFECDD3)
                                    else -> Color(0xFF6EE7B7)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                if (progressState.isGenerating) {
                                    val fraction = if (progressState.totalPages > 0) {
                                        (progressState.currentPageIndex.toFloat() / progressState.totalPages.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    val percent = (fraction * 100).toInt()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⏳ جاري انتظام وتجهيز صفحات الكتاب بدقة...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1E3A8A)
                                        )
                                        Surface(
                                            color = Color(0xFF1D4ED8),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "$percent%",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        color = Color(0xFF2563EB),
                                        trackColor = Color(0xFFDBEAFE),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "السجلات الجاهزة: ${progressState.currentDayIndex} من ${progressState.totalDays} يوم (${progressState.currentDayName} ${progressState.currentDateStr})",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "الصفحات: ${progressState.currentPageIndex} / ${progressState.totalPages}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }

                                    Button(
                                        onClick = { cancelOngoingGeneration() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(9.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .testTag("cancel_period_book_button")
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "إلغاء عملية التجهيز فوراً بأمان",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                } else if (progressState.wasCancelled) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "تم إلغاء عملية التجهيز في المنتصف بأمان تام — التطبيق جاهز ويمكنك البدء من جديد في أي وقت.",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                } else if (progressState.errorMessage != null) {
                                    Text(
                                        text = progressState.errorMessage ?: "",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBE123C)
                                    )
                                } else if (progressState.generatedFile != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "الكتاب جاهز تماماً (${progressState.totalDays} سجل يومي | ${progressState.totalPages} صفحة مرقمة بدقة).",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. أزرار التجهيز والطباعة والمشاركة
                    val hasRecords = (preview?.activeDaysCount ?: 0) > 0
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { executeGenerateBook(BookPostAction.PRINT_PDF) },
                                enabled = hasRecords && !progressState.isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(42.dp)
                                    .testTag("print_period_book_button")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "طباعة الكتاب (PDF)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { executeGenerateBook(BookPostAction.SHARE_PDF) },
                                enabled = hasRecords && !progressState.isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("share_period_book_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "مشاركة الكتاب PDF",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = { executeGenerateBook(BookPostAction.SHARE_MANAGER_WHATSAPP) },
                            enabled = hasRecords && !progressState.isGenerating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("whatsapp_manager_period_book_button")
                        ) {
                            Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "إرسال كتاب السجلات المطبوع (PDF) إلى واتساب المدير مباشرة",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // نافذة اختيار تاريخ دقيق (سنة / شهر / يوم) عند الضغط على بطاقة (من تاريخ) أو (إلى تاريخ)
    pickingDateTarget?.let { target ->
        val currentVal = if (target == "start") startDate else endDate
        ArabicDateSelectorPopup(
            title = if (target == "start") "تحديد تاريخ البداية (من تاريخ)" else "تحديد تاريخ النهاية (إلى تاريخ)",
            initialDateStr = currentVal,
            availableYears = availableYears,
            onConfirmDate = { chosen ->
                if (target == "start") {
                    startDate = chosen
                } else {
                    endDate = chosen
                }
                pickingDateTarget = null
            },
            onDismiss = { pickingDateTarget = null }
        )
    }
}

@Composable
private fun PeriodModeTabChip(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF1E293B) else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = title,
                color = if (selected) Color.White else Color(0xFF334155),
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PresetPeriodButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = if (selected) Color(0xFF1E3A8A) else Color(0xFF0F172A),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = if (selected) Color(0xFF2563EB) else Color(0xFF64748B),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DateRangeEndpointCard(
    label: String,
    dateStr: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onStepDay: (Int) -> Unit,
    onOpenPicker: () -> Unit
) {
    val dayName = remember(dateStr) { getDayNameFromDateStr(dateStr) }
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onOpenPicker() }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0F172A)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable(enabled = enabled) { onStepDay(-1) }
                ) {
                    Text(
                        text = "- يوم",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = Color(0xFFDBEAFE),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable(enabled = enabled) { onOpenPicker() }
                ) {
                    Text(
                        text = "تغيير",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E3A8A),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable(enabled = enabled) { onStepDay(1) }
                ) {
                    Text(
                        text = "+ يوم",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortOrderChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(0.8.dp, if (selected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFF334155),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BookPreviewMetricBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * منتقي تاريخ عربي سريع وأنيق (سنة / شهر / يوم) لتحديد تاريخ البداية أو النهاية بدقة
 */
@Composable
private fun ArabicDateSelectorPopup(
    title: String,
    initialDateStr: String,
    availableYears: List<Int>,
    onConfirmDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = remember(initialDateStr) { initialDateStr.split("-") }
    var selYear by remember(initialDateStr) { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var selMonth by remember(initialDateStr) { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 10) }
    var selDay by remember(initialDateStr) { mutableIntStateOf(parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31) ?: 1) }

    val maxDaysInMonth = remember(selYear, selMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selYear)
            set(Calendar.MONTH, selMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val effectiveDay = selDay.coerceIn(1, maxDaysInMonth)
    val composedDate = String.format(Locale.US, "%04d-%02d-%02d", selYear, selMonth, effectiveDay)

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )

                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${getDayNameFromDateStr(composedDate)}  $composedDate م",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E3A8A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    // اختيار السنة
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Text("السنة:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        availableYears.forEach { y ->
                            val isSel = y == selYear
                            Surface(
                                color = if (isSel) Color(0xFF1E3A8A) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { selYear = y }
                            ) {
                                Text(
                                    text = "$y",
                                    color = if (isSel) Color.White else Color(0xFF1E293B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // اختيار الشهر
                    Text("الشهر:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        for (m in 1..12) {
                            val isSel = m == selMonth
                            Surface(
                                color = if (isSel) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { selMonth = m }
                            ) {
                                Text(
                                    text = "$m ${getArabicMonthName(m)}",
                                    color = if (isSel) Color.White else Color(0xFF1E293B),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // اختيار اليوم
                    Text("اليوم:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val rows = (maxDaysInMonth + 6) / 7
                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (c in 1..7) {
                                    val d = r * 7 + c
                                    if (d <= maxDaysInMonth) {
                                        val isSel = d == effectiveDay
                                        Surface(
                                            color = if (isSel) Color(0xFF0D9488) else Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(0.7.dp, if (isSel) Color(0xFF0D9488) else Color(0xFFCBD5E1)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selDay = d }
                                        ) {
                                            Text(
                                                text = "$d",
                                                color = if (isSel) Color.White else Color(0xFF0F172A),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 5.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onConfirmDate(composedDate) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("✓ اعتماد التاريخ", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
