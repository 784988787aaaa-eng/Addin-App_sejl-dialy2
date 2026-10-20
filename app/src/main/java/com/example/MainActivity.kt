package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.InvoiceShareDestination
import com.example.ui.SmartTradeViewModel
import com.example.ui.components.*
import com.example.ui.theme.SmartTradeBookTheme
import com.example.util.PdfAndBackupHelper
import com.example.util.getCurrentDateTimeArabic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartTradeBookTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SmartTradeMainApp()
                }
            }
        }
    }
}

@Composable
fun SmartTradeMainApp(viewModel: SmartTradeViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val totalRecordsCount by viewModel.totalRecordsCount.collectAsStateWithLifecycle()
    val sheetDayInvoices by viewModel.sheetDayInvoices.collectAsStateWithLifecycle()
    val sheetStockStats by viewModel.sheetStockStats.collectAsStateWithLifecycle()
    val sheetFinancialTotals by viewModel.sheetFinancialTotals.collectAsStateWithLifecycle()
    val recentSupplies by viewModel.recentSupplies.collectAsStateWithLifecycle()
    val activeDates by viewModel.activeDates.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val selectedSheetDate by viewModel.selectedSheetDate.collectAsStateWithLifecycle()

    val salesCounter by viewModel.salesCounter.collectAsStateWithLifecycle()
    val supplyCounter by viewModel.supplyCounter.collectAsStateWithLifecycle()
    val connectedAccount by viewModel.connectedAccount.collectAsStateWithLifecycle()
    val connectedAccountName by viewModel.connectedAccountName.collectAsStateWithLifecycle()
    val connectedAccountPhoto by viewModel.connectedAccountPhoto.collectAsStateWithLifecycle()
    val lastAutoBackupTime by viewModel.lastAutoBackupTime.collectAsStateWithLifecycle()
    val lastCloudBackupTime by viewModel.lastCloudBackupTime.collectAsStateWithLifecycle()
    val lastCloudBackupSize by viewModel.lastCloudBackupSize.collectAsStateWithLifecycle()
    val autoCloudSyncEnabled by viewModel.autoCloudSyncEnabled.collectAsStateWithLifecycle()
    val customWebClientId by viewModel.customWebClientId.collectAsStateWithLifecycle()
    val managerPhone by viewModel.managerPhone.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    val customerNameInput by viewModel.customerNameInput.collectAsStateWithLifecycle()
    val selectedProductKey by viewModel.selectedProductKey.collectAsStateWithLifecycle()
    val majorQtyInput by viewModel.majorQtyInput.collectAsStateWithLifecycle()
    val minorQtyInput by viewModel.minorQtyInput.collectAsStateWithLifecycle()
    val unitPriceInput by viewModel.unitPriceInput.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val previousDebtInput by viewModel.previousDebtInput.collectAsStateWithLifecycle()
    val discountInput by viewModel.discountInput.collectAsStateWithLifecycle()
    val paidInput by viewModel.paidInput.collectAsStateWithLifecycle()
    val lastSavedInvoice by viewModel.lastSavedInvoice.collectAsStateWithLifecycle()

    var currentDateTime by remember { mutableStateOf(getCurrentDateTimeArabic()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentDateTime = getCurrentDateTimeArabic()
            delay(15_000L)
        }
    }

    var showEditProductsDialog by remember { mutableStateOf(false) }
    var showSettingsAndBackupDialog by remember { mutableStateOf(false) }
    var showManagerPhoneDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var activeCalculatorTarget by remember { mutableStateOf<CalculatorTarget?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.buildFullBackupString()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                }
                if (json.isNotBlank()) {
                    pendingRestoreJson = json
                }
            }
        }
    }

    // معالجة زر الرجوع: التنقل للشاشة الرئيسية أو إظهار رسالة تأكيد الخروج
    BackHandler {
        if (activeTab != 0) {
            viewModel.setActiveTab(0)
        } else {
            showExitConfirmDialog = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF3F6FB),
        topBar = {
            SlimNavigationStrip(
                activeTab = activeTab,
                totalRecordsCount = totalRecordsCount,
                hasManagerPhone = managerPhone.isNotBlank(),
                onSelectTab = { viewModel.setActiveTab(it) },
                onOpenSettingsGear = { showSettingsAndBackupDialog = true }
            )
        },
        bottomBar = {
            if (activeTab == 0) {
                PinnedSaveAndShareBottomBar(
                    nextInvoiceNumber = "A$salesCounter",
                    lastSavedInvoiceNumber = lastSavedInvoice?.invoiceNumber,
                    onSaveAndShareToCustomer = {
                        viewModel.saveAndShareInvoiceImmediately(context, InvoiceShareDestination.CUSTOMER_WHATSAPP)
                    },
                    onSaveAndShareToManager = {
                        viewModel.saveAndShareInvoiceImmediately(context, InvoiceShareDestination.MANAGER_WHATSAPP)
                    },
                    onSaveAndSharePdf = {
                        viewModel.saveAndShareInvoiceImmediately(context, InvoiceShareDestination.PDF_SHARE)
                    },
                    onPrintLastInvoicePdf = {
                        val last = lastSavedInvoice
                        if (last != null) {
                            scope.launch {
                                val pdf = withContext(Dispatchers.IO) {
                                    PdfAndBackupHelper.generateInvoicePdfFile(context, last, currency, userName)
                                }
                                if (pdf != null) {
                                    PdfAndBackupHelper.printPdfFile(context, pdf, "Invoice_${last.invoiceNumber}")
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> PosFastEntryScreen(
                    viewModel = viewModel,
                    products = products,
                    customers = customers,
                    selectedProductKey = selectedProductKey,
                    customerNameInput = customerNameInput,
                    majorQtyInput = majorQtyInput,
                    minorQtyInput = minorQtyInput,
                    unitPriceInput = unitPriceInput,
                    cartItems = cartItems,
                    previousDebtInput = previousDebtInput,
                    discountInput = discountInput,
                    paidInput = paidInput,
                    dayName = currentDateTime.dayName,
                    dateStr = currentDateTime.date,
                    time12h = currentDateTime.time12h,
                    currency = currency,
                    onOpenEditProducts = { showEditProductsDialog = true },
                    onOpenCalculator = { target -> activeCalculatorTarget = target }
                )

                1 -> DailyLedgerSheetScreen(
                    viewModel = viewModel,
                    selectedDate = selectedSheetDate,
                    activeDates = activeDates,
                    products = products,
                    dayInvoices = sheetDayInvoices,
                    stockStats = sheetStockStats,
                    financialTotals = sheetFinancialTotals,
                    currency = currency,
                    managerPhone = managerPhone,
                    userName = userName
                )

                2 -> SuppliesTabScreen(
                    viewModel = viewModel,
                    products = products,
                    supplies = recentSupplies,
                    nextSupplyCounter = supplyCounter,
                    managerPhone = managerPhone,
                    userName = userName
                )

                3 -> UnifiedRecordsScreen(
                    viewModel = viewModel,
                    activeDates = activeDates,
                    totalRecordsCount = totalRecordsCount,
                    currency = currency,
                    managerPhone = managerPhone,
                    userName = userName
                )
            }
        }
    }

    activeCalculatorTarget?.let { target ->
        val initialVal = when (target) {
            CalculatorTarget.PREV_DEBT -> previousDebtInput
            CalculatorTarget.DISCOUNT -> discountInput
            CalculatorTarget.PAID -> paidInput
            CalculatorTarget.UNIT_PRICE -> unitPriceInput
        }
        ArabicCalculatorDialog(
            targetFieldName = target.title,
            initialValue = initialVal,
            onDismiss = { activeCalculatorTarget = null },
            onApplyValue = { resultStr ->
                when (target) {
                    CalculatorTarget.PREV_DEBT -> viewModel.setPreviousDebtInput(resultStr)
                    CalculatorTarget.DISCOUNT -> viewModel.setDiscountInput(resultStr)
                    CalculatorTarget.PAID -> viewModel.setPaidInput(resultStr)
                    CalculatorTarget.UNIT_PRICE -> viewModel.setUnitPriceInput(resultStr)
                }
                activeCalculatorTarget = null
            }
        )
    }

    if (showManagerPhoneDialog) {
        ManagerPhoneQuickDialog(
            currentManagerPhone = managerPhone,
            onSavePhone = { viewModel.saveManagerPhoneQuickly(it) },
            onDismiss = { showManagerPhoneDialog = false }
        )
    }

    if (showEditProductsDialog) {
        EditProductsAndUnitsDialog(
            products = products,
            initialProductKey = selectedProductKey,
            viewModel = viewModel,
            onDismiss = { showEditProductsDialog = false }
        )
    }

    if (showSettingsAndBackupDialog) {
        CloudAndLocalBackupDialog(
            viewModel = viewModel,
            connectedAccount = connectedAccount,
            connectedAccountName = connectedAccountName,
            connectedAccountPhoto = connectedAccountPhoto,
            lastAutoBackupTime = lastAutoBackupTime,
            lastCloudBackupTime = lastCloudBackupTime,
            lastCloudBackupSize = lastCloudBackupSize,
            autoCloudSyncEnabled = autoCloudSyncEnabled,
            customWebClientId = customWebClientId,
            totalRecordsCount = totalRecordsCount,
            managerPhone = managerPhone,
            currency = currency,
            userName = userName,
            salesCounter = salesCounter,
            supplyCounter = supplyCounter,
            initialFocusCloud = true,
            onExportJsonToDevice = {
                exportBackupLauncher.launch("SmartTradeBook_Backup_${currentDateTime.date}.json")
            },
            onImportJsonFromDevice = {
                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onOpenEditProducts = { showEditProductsDialog = true },
            onDismiss = { showSettingsAndBackupDialog = false }
        )
    }

    pendingRestoreJson?.let { jsonToRestore ->
        ConfirmActionDialog(
            title = "تأكيد استعادة النسخة الاحتياطية",
            message = "هل أنت متأكد من استعادة بيانات النسخة الاحتياطية المختارة وتحديث السجلات الحالية؟",
            confirmText = "نعم، استعادة",
            onConfirm = { viewModel.restoreFromBackupJson(context, jsonToRestore) },
            onDismiss = { pendingRestoreJson = null }
        )
    }

    if (showExitConfirmDialog) {
        ConfirmActionDialog(
            title = "تأكيد الخروج من التطبيق",
            message = "هل تريد بالتأكيد الخروج من تطبيق السجل اليومي؟ جميع بياناتك محفوظة تلقائياً.",
            confirmText = "خروج",
            cancelText = "بقاء في التطبيق",
            isDanger = true,
            onConfirm = { (context as? Activity)?.finish() },
            onDismiss = { showExitConfirmDialog = false }
        )
    }
}
