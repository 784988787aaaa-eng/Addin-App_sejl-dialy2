package com.example.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.domain.DailyFinancialTotals
import com.example.domain.ProductDailyStockStats
import com.example.ui.SmartTradeViewModel
import com.example.ui.openWhatsAppDirectOrShare
import com.example.util.PdfAndBackupHelper
import com.example.util.cleanNum
import com.example.util.formatSheetDateDisplay
import com.example.util.getDayNameFromDateStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * شاشة كشف الحساب اليومي المتكامل (تجمع الشريط العلوي الرشيق مع زر إغلاق اليوم وزر مشاركة السجل المطبوع للمدير مباشرة وجدول الكشف ملء الصفحة)
 * تعمل بسلاسة فائقة مع آلاف وملايين المعاملات عبر الحسابات المسبقة في الخلفية (Dispatchers.Default)
 */
@Composable
fun DailyLedgerSheetScreen(
    viewModel: SmartTradeViewModel,
    selectedDate: String,
    activeDates: List<String>,
    products: List<ProductEntity>,
    dayInvoices: List<SaleInvoiceEntity>,
    stockStats: Map<String, ProductDailyStockStats>,
    financialTotals: DailyFinancialTotals,
    currency: String,
    managerPhone: String,
    userName: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    var isLandscape by remember {
        mutableStateOf(activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    }

    val activeOpenSheetDate by viewModel.activeOpenSheetDate.collectAsStateWithLifecycle()
    val dayName = remember(selectedDate) { getDayNameFromDateStr(selectedDate) }
    val displaySelectedDate = remember(selectedDate) { formatSheetDateDisplay(selectedDate) }

    val rowsPerPage = 20
    var extraManualPages by remember(selectedDate) { mutableIntStateOf(0) }
    val basePages = remember(dayInvoices.size) {
        ceil(dayInvoices.size.coerceAtLeast(1) / rowsPerPage.toDouble()).toInt().coerceAtLeast(1)
    }
    val totalPages = basePages + extraManualPages
    var currentPageIndex by remember(selectedDate, totalPages) {
        mutableIntStateOf((basePages - 1).coerceAtLeast(0))
    }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val toolbarScroll = rememberScrollState()
    var showPeriodBookDialog by remember { mutableStateOf(false) }
    var showCloseDayConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        DailyLedgerCompactToolbar(
            dayName = dayName,
            selectedDate = selectedDate,
            displaySelectedDate = displaySelectedDate,
            activeOpenSheetDate = activeOpenSheetDate,
            totalPages = totalPages,
            currentPageIndex = currentPageIndex,
            isLandscape = isLandscape,
            toolbarScroll = toolbarScroll,
            onCloseDayClick = { showCloseDayConfirmDialog = true },
            onPrevDay = { viewModel.shiftSheetDate(-1) },
            onNextDay = { viewModel.shiftSheetDate(1) },
            onJumpToday = { viewModel.setSelectedSheetDate(it) },
            onSelectPage = { currentPageIndex = it },
            onAddPage = {
                extraManualPages += 1
                currentPageIndex = totalPages
            },
            onToggleLandscape = {
                isLandscape = !isLandscape
                activity?.requestedOrientation = if (isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            },
            onOpenPeriodBookDialog = { showPeriodBookDialog = true },
            onSharePrintedLedgerToManager = {
                scope.launch {
                    val pdf = withContext(Dispatchers.IO) {
                        PdfAndBackupHelper.generateDailySheetPdfFile(
                            context = context,
                            dateStr = selectedDate,
                            dayName = dayName,
                            products = products,
                            dayInvoices = dayInvoices,
                            stockStats = stockStats,
                            userName = userName
                        )
                    }
                    if (pdf != null) {
                        PdfAndBackupHelper.sharePdfToManagerWhatsApp(
                            context = context,
                            pdfFile = pdf,
                            managerPhone = managerPhone,
                            caption = "كشف حساب اليومية: $dayName $displaySelectedDate م",
                            userName = ""
                        )
                    }
                }
            },
            onSharePdf = {
                scope.launch {
                    val pdf = withContext(Dispatchers.IO) {
                        PdfAndBackupHelper.generateDailySheetPdfFile(
                            context = context, dateStr = selectedDate, dayName = dayName,
                            products = products, dayInvoices = dayInvoices, stockStats = stockStats, userName = userName
                        )
                    }
                    if (pdf != null) {
                        PdfAndBackupHelper.sharePdfFile(context, pdf, "كشف حساب $dayName $displaySelectedDate")
                    }
                }
            },
            onPrintPdf = {
                scope.launch {
                    val pdf = withContext(Dispatchers.IO) {
                        PdfAndBackupHelper.generateDailySheetPdfFile(
                            context = context, dateStr = selectedDate, dayName = dayName,
                            products = products, dayInvoices = dayInvoices, stockStats = stockStats, userName = userName
                        )
                    }
                    if (pdf != null) {
                        PdfAndBackupHelper.printPdfFile(context, pdf, "Daily_Ledger_${selectedDate.replace("#", "_")}")
                    }
                }
            }
        )

        DailyLedgerTableGrid(
            products = products,
            dayInvoices = dayInvoices,
            stockStats = stockStats,
            financialTotals = financialTotals,
            currency = currency,
            userName = userName,
            currentPageIndex = currentPageIndex,
            rowsPerPage = rowsPerPage,
            horizontalScroll = horizontalScroll,
            verticalScroll = verticalScroll,
            onClickInvoiceRow = { inv ->
                val msg = inv.managerWhatsappText.ifBlank { inv.whatsappText }
                openWhatsAppDirectOrShare(context, msg, managerPhone, userName)
            }
        )
    }

    if (showCloseDayConfirmDialog) {
        ConfirmActionDialog(
            title = "🔒 تأكيد إغلاق اليوم وفتح سجل جديد",
            message = "هل تريد إغلاق السجل الحالي ($dayName $displaySelectedDate — عدد الفواتير: ${dayInvoices.size}، الإجمالي بيد المستخدم: ${financialTotals.totalCashInUserHand.cleanNum()} $currency)؟\n\n• سيتم فتح سجل جديد صافي فوراً مع ترحيل المتبقي.\n• سيتم حفظ نسخة احتياطية إجبارية محلياً وتجهيزها للرفع التلقائي فور توفر الإنترنت.",
            confirmText = "إغلاق اليوم",
            cancelText = "تراجع",
            isDanger = false,
            onConfirm = {
                viewModel.closeCurrentDaySheet(context, selectedDate)
            },
            onDismiss = { showCloseDayConfirmDialog = false }
        )
    }

    if (showPeriodBookDialog) {
        PeriodLedgerBookDialog(
            viewModel = viewModel,
            activeDates = activeDates,
            initialSelectedDate = selectedDate,
            managerPhone = managerPhone,
            userName = userName,
            onDismiss = { showPeriodBookDialog = false }
        )
    }
}

@Composable
private fun DailyLedgerCompactToolbar(
    dayName: String,
    selectedDate: String,
    displaySelectedDate: String,
    activeOpenSheetDate: String,
    totalPages: Int,
    currentPageIndex: Int,
    isLandscape: Boolean,
    toolbarScroll: ScrollState,
    onCloseDayClick: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToday: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onToggleLandscape: () -> Unit,
    onOpenPeriodBookDialog: () -> Unit,
    onSharePrintedLedgerToManager: () -> Unit,
    onSharePdf: () -> Unit,
    onPrintPdf: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(toolbarScroll)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أول زر من جهة اليمين: إغلاق اليوم وفتح سجل جديد صافي
            ToolbarChip(
                text = "🔒 إغلاق اليوم",
                bgColor = Color(0xFFB91C1C),
                textColor = Color.White,
                testTag = "close_day_sheet_button",
                onClick = onCloseDayClick
            )

            ToolbarChip("▶ الأمس", Color(0xFFF1F5F9), Color(0xFF1E293B), onClick = onPrevDay)

            Surface(color = Color(0xFF141E2E), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = "كشف حساب: $dayName $displaySelectedDate م",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (selectedDate != activeOpenSheetDate) {
                ToolbarChip("اليوم", Color(0xFF10A36A), Color.White, onClick = { onJumpToday(activeOpenSheetDate) })
            }

            ToolbarChip("التالي ◀", Color(0xFFF1F5F9), Color(0xFF1E293B), onClick = onNextDay)

            for (pIdx in 0 until totalPages) {
                val selectedPage = pIdx == currentPageIndex
                ToolbarChip(
                    text = "ص${pIdx + 1}",
                    bgColor = if (selectedPage) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                    textColor = if (selectedPage) Color.White else Color(0xFF334155),
                    onClick = { onSelectPage(pIdx) }
                )
            }

            ToolbarChip("+ صفحة", Color(0xFFECFDF5), Color(0xFF047857), onClick = onAddPage)
            ToolbarChip(
                text = "📚 كتاب السجلات (فترة / شهر) PDF",
                bgColor = Color(0xFF312E81),
                textColor = Color.White,
                onClick = onOpenPeriodBookDialog
            )
            ToolbarChip(
                text = if (isLandscape) "🔄 عمودي" else "🔄 أفقي",
                bgColor = if (isLandscape) Color(0xFF065F46) else Color(0xFF1E293B),
                textColor = Color.White,
                onClick = onToggleLandscape
            )
            ToolbarChip(
                text = "📄 مشاركة السجل المطبوع للمدير",
                bgColor = Color(0xFF10A36A),
                textColor = Color.White,
                onClick = onSharePrintedLedgerToManager
            )
            ToolbarChip("مشاركة PDF", Color(0xFF2563EB), Color.White, onClick = onSharePdf)
            ToolbarChip("🖨️", Color(0xFFF1F5F9), Color(0xFF1E293B), onClick = onPrintPdf)
        }
    }
}

@Composable
private fun ToolbarChip(
    text: String,
    bgColor: Color,
    textColor: Color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable { onClick() }
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
