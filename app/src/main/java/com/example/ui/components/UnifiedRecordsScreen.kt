package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FinancialSummaryTuple
import com.example.data.SaleInvoiceEntity
import com.example.domain.LedgerCalculator
import com.example.domain.WhatsAppMessageFormatter
import com.example.ui.SmartTradeViewModel
import com.example.ui.openWhatsAppDirectOrShare
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة السجل الشامل والمنظم (مُهيأة للعمل مع ملايين السجلات عبر استعلامات SQL المفهرسة المغطاة،
 * ومربوطة بالكامل بالحساب الموحد UnifiedInvoiceAccounting)
 */
@Composable
fun UnifiedRecordsScreen(
    viewModel: SmartTradeViewModel,
    activeDates: List<String>,
    totalRecordsCount: Int,
    currency: String,
    managerPhone: String,
    userName: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var filterDate by remember { mutableStateOf("") }
    var pageLimit by remember { mutableIntStateOf(150) }

    val filteredInvoices by remember(searchQuery, filterDate, pageLimit) {
        viewModel.observeFilteredInvoicesPaged(searchQuery, filterDate, pageLimit)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val summaryTuple by remember(searchQuery, filterDate) {
        viewModel.observeFilteredFinancialSummary(searchQuery, filterDate)
    }.collectAsStateWithLifecycle(
        initialValue = FinancialSummaryTuple()
    )

    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customersPhoneMap = remember(customers) {
        customers.associate { it.name to it.phone }
    }

    val totalInUserHand = remember(summaryTuple) {
        LedgerCalculator.addMoney(summaryTuple.totalCashReceived, summaryTuple.totalPrevDebtNotified)
    }
    val cleanUser = userName.trim()
    val userHandHeader = if (cleanUser.isNotEmpty()) "بيد ($cleanUser)" else "الكلي بيد المستخدم"

    var pendingDeleteInvoice by remember { mutableStateOf<SaleInvoiceEntity?>(null) }
    var pendingEditInvoice by remember { mutableStateOf<SaleInvoiceEntity?>(null) }
    var pendingEditCustomerContact by remember { mutableStateOf<Pair<SaleInvoiceEntity, String>?>(null) }
    var showPeriodBookDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = Color(0xFF141E2E),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryStatColumn("إجمالي النقد", "${summaryTuple.totalCashReceived.cleanNum()} $currency", Color(0xFF6EE7B7))
                SummaryStatColumn("سداد دين سابق", "${summaryTuple.totalPrevDebtNotified.cleanNum()} $currency", Color(0xFFFDE047))
                SummaryStatColumn(userHandHeader, "${totalInUserHand.cleanNum()} $currency", Color.White)
                SummaryStatColumn("متبقي الفواتير", "${summaryTuple.totalInvoicesRemaining.cleanNum()} $currency", Color(0xFFFCA5A5))
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(1f)
                            .height(35.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "بحث بالعميل أو الفاتورة أو الملاحظة...",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        pageLimit = 150
                                    },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح البحث",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable {
                                            searchQuery = ""
                                            pageLimit = 150
                                        }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showPeriodBookDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF312E81)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(35.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📚 كتاب السجلات PDF",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                        FilterChip(
                            selected = filterDate.isBlank(),
                            onClick = {
                                filterDate = ""
                                pageLimit = 150
                            },
                            label = { Text("كل الأيام ($totalRecordsCount)", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    items(activeDates, key = { it }) { dStr ->
                        val dName = getDayNameFromDateStr(dStr)
                        val dDisp = formatSheetDateDisplay(dStr)
                        FilterChip(
                            selected = filterDate == dStr,
                            onClick = {
                                filterDate = dStr
                                pageLimit = 150
                            },
                            label = { Text("$dName $dDisp", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        if (filteredInvoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد سجلات مطابقة حالياً",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredInvoices, key = { it.id }) { inv ->
                    val resolvedPhone = inv.customerPhone.ifBlank {
                        customersPhoneMap[inv.customerName].orEmpty()
                    }
                    InvoiceRecordCard(
                        inv = inv,
                        customerPhone = resolvedPhone,
                        currency = currency,
                        onEditCustomerContact = {
                            pendingEditCustomerContact = inv to resolvedPhone
                        },
                        onShareManager = {
                            val msg = inv.managerWhatsappText.ifBlank { inv.whatsappText }
                            openWhatsAppDirectOrShare(context, msg, managerPhone, userName)
                        },
                        onShareCustomer = {
                            openWhatsAppDirectOrShare(context, inv.whatsappText, resolvedPhone, userName)
                        },
                        onSharePdf = {
                            scope.launch {
                                val pdf = withContext(Dispatchers.IO) {
                                    PdfAndBackupHelper.generateInvoicePdfFile(context, inv, currency, userName)
                                }
                                if (pdf != null) {
                                    PdfAndBackupHelper.sharePdfFile(context, pdf, "فاتورة ${inv.invoiceNumber}")
                                }
                            }
                        },
                        onEdit = { pendingEditInvoice = inv },
                        onDelete = { pendingDeleteInvoice = inv }
                    )
                }

                if (filteredInvoices.size >= pageLimit && filteredInvoices.size < totalRecordsCount) {
                    item {
                        OutlinedButton(
                            onClick = { pageLimit += 200 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("عرض المزيد من السجلات...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showPeriodBookDialog) {
        PeriodLedgerBookDialog(
            viewModel = viewModel,
            activeDates = activeDates,
            initialSelectedDate = filterDate.ifBlank { activeDates.firstOrNull().orEmpty() },
            managerPhone = managerPhone,
            userName = userName,
            onDismiss = { showPeriodBookDialog = false }
        )
    }

    pendingDeleteInvoice?.let { inv ->
        ConfirmActionDialog(
            title = "تأكيد حذف الفاتورة (${inv.invoiceNumber})",
            message = "هل أنت متأكد من حذف الفاتورة رقم ${inv.invoiceNumber} الخاصة بالعميل (${inv.customerName}) وإرجاع كمياتها للمخزون؟",
            confirmText = "نعم، حذف الفاتورة",
            isDanger = true,
            onConfirm = { viewModel.deleteInvoice(inv) },
            onDismiss = { pendingDeleteInvoice = null }
        )
    }

    pendingEditInvoice?.let { inv ->
        EditSavedInvoiceDialog(
            invoice = inv,
            onConfirmSaveEdit = { newCust, newPaid, newPrevDebt, newDiscount, newNote ->
                viewModel.updateInvoiceRecord(inv, newCust, newPaid, newPrevDebt, newDiscount, newNote)
            },
            onDismiss = { pendingEditInvoice = null }
        )
    }

    pendingEditCustomerContact?.let { (inv, currentPhone) ->
        EditCustomerContactDialog(
            invoice = inv,
            initialPhone = currentPhone,
            onConfirmSave = { newCustName, newCustPhone ->
                viewModel.updateCustomerContactRecord(inv, newCustName, newCustPhone)
            },
            onDismiss = { pendingEditCustomerContact = null }
        )
    }
}

@Composable
private fun InvoiceRecordCard(
    inv: SaleInvoiceEntity,
    customerPhone: String,
    currency: String,
    onEditCustomerContact: () -> Unit,
    onShareManager: () -> Unit,
    onShareCustomer: () -> Unit,
    onSharePdf: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val items = remember(inv.itemsJson) { decodeInvoiceItemsJson(inv.itemsJson) }
    val acc = remember(inv) { LedgerCalculator.computeUnifiedInvoiceAccounting(inv) }
    val paidFormatted = acc.paidCellText
    val hasPhone = customerPhone.isNotBlank()

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(color = Color(0xFFFFE4E6), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "🔴 ${inv.invoiceNumber}",
                            color = Color(0xFFBE123C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = inv.customerName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A),
                        maxLines = 1
                    )
                    // عرض رقم الهاتف جوار اسم العميل بشكل أنيق مع أيقونة تعديل صغيرة بجواره
                    Surface(
                        color = if (hasPhone) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            0.7.dp,
                            if (hasPhone) Color(0xFF6EE7B7) else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.clickable { onEditCustomerContact() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (hasPhone) "📞 $customerPhone" else "إضافة هاتف ✎",
                                color = if (hasPhone) Color(0xFF065F46) else Color(0xFF2563EB),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (hasPhone) FontFamily.Monospace else FontFamily.Default,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (hasPhone) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل اسم ورقم العميل",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp)) {
                    val actualInvDate = resolveActualTransactionDate(inv.date, inv.timestamp)
                    Text(
                        text = "${inv.dayName} | $actualInvDate | ${inv.time}",
                        color = Color(0xFF334155),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            if (items.isNotEmpty()) {
                Text(
                    text = items.joinToString(" • ") { "${it.name}: ${it.unitDisplay} (${it.total.cleanNum()})" },
                    color = Color(0xFF475569),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (inv.suppliedGoodsNote.isNotBlank()) {
                Text(
                    text = "📝 ملاحظات: ${inv.suppliedGoodsNote.trim()}",
                    color = Color(0xFF1E3A8A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val subtotalSummary = buildString {
                        append("إجمالي الفاتورة: ${acc.netInvoiceSubtotal.cleanNum()}")
                        if (acc.discount > 0.0) {
                            append(" (خصم: ${acc.discount.cleanNum()})")
                        }
                        append(" | مسلم: $paidFormatted")
                    }
                    Text(
                        text = subtotalSummary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Text(
                        text = "المتبقي: ${acc.remainingOnInvoice.cleanNum()} $currency",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (acc.remainingOnInvoice > 0.0) Color(0xFFDC2626) else Color(0xFF047857)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    // زر المدير أولاً لسهولة الوصول للمدير
                    Surface(
                        color = Color(0xFFDBEAFE),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.clickable { onShareManager() }
                    ) {
                        Text(
                            text = "واتس المدير", color = Color(0xFF1E3A8A), fontSize = 9.sp, fontWeight = FontWeight.Black,
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.clickable { onShareCustomer() }
                    ) {
                        Text(
                            text = "واتس العميل", color = Color(0xFF065F46), fontSize = 9.sp, fontWeight = FontWeight.Black,
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onSharePdf, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color(0xFF0D9488), modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFE11D48), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFFCBD5E1), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(value, color = valueColor, fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}
