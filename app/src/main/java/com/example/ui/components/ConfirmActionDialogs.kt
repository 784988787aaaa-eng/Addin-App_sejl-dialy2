package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.data.SupplyBatchEntity
import com.example.domain.LedgerCalculator
import com.example.util.cleanNum
import com.example.util.decodeInvoiceItemsJson

/**
 * نوافذ التأكيد الاحترافية الموحدة (تأكيد الحذف، تأكيد التعديل، تأكيد تهيئة البيانات، وتأكيد الخروج)
 * مع نوافذ تعديل الفواتير والتوريدات المحفوظة ومرتبطة بالحساب الموحد اللحظي (UnifiedAccounting)
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String = "تأكيد",
    cancelText: String = "إلغاء",
    isDanger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // توزيع عرض الأزرار بتناسب بصري ذكي حسب طول النص لمنع التفاف النص دون داعٍ
    val cancelLength = cancelText.trim().length
    val confirmLength = confirmText.trim().length
    val (cancelWeight, confirmWeight) = when {
        cancelLength > confirmLength + 3 -> 1.45f to 0.95f
        confirmLength > cancelLength + 3 -> 0.95f to 1.45f
        else -> 1f to 1f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isDanger) Color(0xFFFECDD3) else Color(0xFFCBD5E1)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDanger) Color(0xFFBE123C) else Color(0xFF0F172A)
                    )
                    Text(
                        text = message,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF8FAFC)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(cancelWeight)
                                .height(44.dp)
                                .testTag("confirm_dialog_cancel_button")
                        ) {
                            Text(
                                text = cancelText,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155),
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = {
                                onConfirm()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDanger) Color(0xFFE11D48) else Color(0xFF0D9488)
                            ),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(confirmWeight)
                                .height(44.dp)
                                .testTag("confirm_dialog_confirm_button")
                        ) {
                            Text(
                                text = confirmText,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditSavedInvoiceDialog(
    invoice: SaleInvoiceEntity,
    onConfirmSaveEdit: (String, Double, Double, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var customerName by remember(invoice) { mutableStateOf(invoice.customerName) }
    var paidStr by remember(invoice) { mutableStateOf(if (invoice.paid > 0) invoice.paid.cleanNum() else "") }
    var prevDebtStr by remember(invoice) { mutableStateOf(if (invoice.previousDebt > 0) invoice.previousDebt.cleanNum() else "") }
    var discountStr by remember(invoice) { mutableStateOf(if (invoice.discount > 0) invoice.discount.cleanNum() else "") }
    var notesStr by remember(invoice) { mutableStateOf(invoice.suppliedGoodsNote) }
    var showConfirmSave by remember { mutableStateOf(false) }

    val decodedItems = remember(invoice.itemsJson) { decodeInvoiceItemsJson(invoice.itemsJson) }
    val liveAcc = remember(decodedItems, invoice.subtotal, invoice.discount, paidStr, prevDebtStr, discountStr) {
        LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = LedgerCalculator.sumRecordTotals(decodedItems),
            discount = discountStr.toDoubleOrNull() ?: 0.0,
            paidCash = paidStr.toDoubleOrNull() ?: 0.0,
            previousDebtPaid = prevDebtStr.toDoubleOrNull() ?: 0.0,
            fallbackGrossSubtotalIfNoItems = LedgerCalculator.addMoney(invoice.subtotal, invoice.discount)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✎ تعديل بيانات الفاتورة (${invoice.invoiceNumber})",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                    // شريط معاينة الحساب الموحد اللحظي أثناء التعديل
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "إجمالي الأصناف: ${liveAcc.rawItemsTotal.cleanNum()} | الصافي: ${liveAcc.netInvoiceSubtotal.cleanNum()}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "المسلّم: ${liveAcc.paidCellText} | المتبقي: ${liveAcc.remainingOnInvoice.cleanNum()}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = if (liveAcc.remainingOnInvoice > 0.0) Color(0xFFDC2626) else Color(0xFF047857)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("اسم العميل", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = paidStr,
                            onValueChange = { paidStr = it },
                            label = { Text("المسلّم نقداً", fontSize = 10.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = discountStr,
                            onValueChange = { discountStr = it },
                            label = { Text("الخصم", fontSize = 10.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                    OutlinedTextField(
                        value = prevDebtStr,
                        onValueChange = { prevDebtStr = it },
                        label = { Text("سداد دين سابق", fontSize = 10.5.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("بيان الفاتورة / ملاحظات (اختياري)", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showConfirmSave = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("✓ حفظ التعديل", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmSave) {
        ConfirmActionDialog(
            title = "تأكيد تعديل الفاتورة (${invoice.invoiceNumber})",
            message = "هل أنت متأكد من حفظ التعديلات على الفاتورة رقم ${invoice.invoiceNumber} الخاصة بالعميل (${customerName.ifBlank { invoice.customerName }})؟",
            confirmText = "نعم، حفظ التعديل",
            isDanger = false,
            onConfirm = {
                onConfirmSaveEdit(
                    customerName,
                    liveAcc.paidCash,
                    liveAcc.previousDebtPaid,
                    liveAcc.discount,
                    notesStr
                )
                onDismiss()
            },
            onDismiss = { showConfirmSave = false }
        )
    }
}

@Composable
fun EditSavedSupplyDialog(
    batch: SupplyBatchEntity,
    product: ProductEntity?,
    onConfirmSaveEdit: (Double, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    val hasMinor = product?.minorUnit?.isNotBlank() == true
    var majorStr by remember(batch) { mutableStateOf(if (batch.enteredMajor > 0) batch.enteredMajor.cleanNum() else "") }
    var minorStr by remember(batch) { mutableStateOf(if (batch.enteredMinor > 0) batch.enteredMinor.cleanNum() else "") }
    var notesStr by remember(batch) { mutableStateOf(batch.notes) }
    var showConfirmSave by remember { mutableStateOf(false) }

    val liveSupplyAcc = remember(product, batch, majorStr, minorStr) {
        product?.let {
            LedgerCalculator.computeUnifiedSupplyAccounting(
                product = it,
                enteredMajor = majorStr.toDoubleOrNull() ?: 0.0,
                enteredMinor = if (hasMinor) (minorStr.toDoubleOrNull() ?: 0.0) else 0.0,
                basePrevStock = batch.prevStock
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✎ تعديل سند التوريد (${batch.batchNumber}) — ${batch.productName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF065F46)
                    )
                    if (liveSupplyAcc != null) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "المورد بعد التعديل: ${liveSupplyAcc.enteredDisplay}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                                Text(
                                    text = "الإجمالي التراكمي للسند: ${liveSupplyAcc.cumulativeStockDisplay}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF064E3B)
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = majorStr,
                            onValueChange = { majorStr = it },
                            label = { Text("العدد المورد (${batch.majorUnit})", fontSize = 10.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasMinor && product != null) {
                            OutlinedTextField(
                                value = minorStr,
                                onValueChange = { minorStr = it },
                                label = { Text("الوحدة الصغرى (${product.minorUnit})", fontSize = 10.5.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("بيان التوريد / ملاحظات", fontSize = 10.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showConfirmSave = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("✓ حفظ التعديل", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmSave) {
        ConfirmActionDialog(
            title = "تأكيد تعديل التوريد (${batch.batchNumber})",
            message = "هل أنت متأكد من حفظ التعديلات على سند التوريد رقم ${batch.batchNumber} للصنف (${batch.productName}) وتحديث المخزون؟",
            confirmText = "نعم، حفظ التعديل",
            isDanger = false,
            onConfirm = {
                onConfirmSaveEdit(
                    majorStr.toDoubleOrNull() ?: 0.0,
                    minorStr.toDoubleOrNull() ?: 0.0,
                    notesStr
                )
                onDismiss()
            },
            onDismiss = { showConfirmSave = false }
        )
    }
}
