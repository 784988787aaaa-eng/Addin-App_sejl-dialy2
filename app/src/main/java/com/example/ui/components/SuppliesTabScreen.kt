package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.data.SupplyBatchEntity
import com.example.domain.LedgerCalculator
import com.example.ui.SmartTradeViewModel
import com.example.ui.openWhatsAppDirectOrShare
import com.example.util.cleanNum

/**
 * شاشة التوريدات المستقلة (🟢 B1001 — حصراً لواتساب المدير مع رسائل تأكيد الحذف والتعديل)
 * ومرتبطة بالكامل بالحساب الموحد للمخزون (UnifiedSupplyAccounting)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuppliesTabScreen(
    viewModel: SmartTradeViewModel,
    products: List<ProductEntity>,
    supplies: List<SupplyBatchEntity>,
    nextSupplyCounter: Int,
    managerPhone: String = "",
    userName: String = ""
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var selectedProdKey by remember(products) {
        mutableStateOf(products.firstOrNull()?.key ?: "khadra")
    }
    val selectedProd = products.find { it.key == selectedProdKey } ?: products.firstOrNull()
    val hasMinorUnit = selectedProd?.minorUnit?.isNotBlank() == true

    var majorQtyStr by remember { mutableStateOf("") }
    var minorQtyStr by remember { mutableStateOf("") }
    var notesStr by remember { mutableStateOf("") }
    val notesFocus = remember { FocusRequester() }
    val minorFocus = remember { FocusRequester() }

    var pendingDeleteSupply by remember { mutableStateOf<SupplyBatchEntity?>(null) }
    var pendingEditSupply by remember { mutableStateOf<SupplyBatchEntity?>(null) }
    var deleteBadgeProductKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val enteredMaj = majorQtyStr.toDoubleOrNull() ?: 0.0
    val enteredMin = if (hasMinorUnit) (minorQtyStr.toDoubleOrNull() ?: 0.0) else 0.0
    val unifiedSupplyPreview = remember(selectedProd, enteredMaj, enteredMin) {
        selectedProd?.let {
            LedgerCalculator.computeUnifiedSupplyAccounting(
                product = it,
                enteredMajor = enteredMaj,
                enteredMinor = enteredMin
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // شريط العنوان والمخزون الحالي المدمج الأنيق
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🟢 توريد بضاعة (B$nextSupplyCounter)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF065F46)
                        )
                        if (unifiedSupplyPreview != null) {
                            Surface(
                                color = Color(0xFFECFDF5),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF6EE7B7))
                            ) {
                                Text(
                                    text = "المتوفر: ${unifiedSupplyPreview.prevStockDisplay} ➔ الإجمالي: ${unifiedSupplyPreview.cumulativeStockDisplay}",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF064E3B),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // شريط اختيار الأصناف الرشيق
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(products, key = { it.key }) { prod ->
                            val isSel = prod.key == selectedProdKey
                            val showDeleteBadge = deleteBadgeProductKey == prod.key
                            Surface(
                                color = if (isSel) Color(0xFF059669) else Color(prod.colorBgHex),
                                shape = RoundedCornerShape(7.dp),
                                border = BorderStroke(
                                    width = if (showDeleteBadge) 1.8.dp else 0.8.dp,
                                    color = when {
                                        showDeleteBadge -> Color(0xFFDC2626)
                                        isSel -> Color(0xFF047857)
                                        else -> Color(prod.borderHex)
                                    }
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (deleteBadgeProductKey != null) deleteBadgeProductKey = null
                                            selectedProdKey = prod.key
                                            minorQtyStr = ""
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            deleteBadgeProductKey = if (deleteBadgeProductKey == prod.key) null else prod.key
                                        }
                                    )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = prod.name,
                                        color = if (isSel) Color.White else Color(prod.colorTextHex),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                    if (showDeleteBadge) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(1.5.dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFDC2626))
                                                .border(1.dp, Color.White, CircleShape)
                                                .clickable {
                                                    deleteBadgeProductKey = null
                                                    pendingDeleteProduct = prod
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف الصنف ${prod.name}",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // حقول إدخال التوريد الرشيقة التي توفر المساحة بالكامل
                    if (hasMinorUnit) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = majorQtyStr,
                                onValueChange = { majorQtyStr = it.replace("-", "") },
                                label = { Text("المورد (${selectedProd?.majorUnit ?: "كبرى"})", fontSize = 9.5.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { minorFocus.requestFocus() }),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = minorQtyStr,
                                onValueChange = { minorQtyStr = it.replace("-", "") },
                                label = { Text("صغرى (${selectedProd?.minorUnit})", fontSize = 9.5.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { notesFocus.requestFocus() }),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(minorFocus)
                            )
                        }
                        OutlinedTextField(
                            value = notesStr,
                            onValueChange = { notesStr = it },
                            placeholder = { Text("بيان أو ملاحظات التوريد للمدير (اختياري)...", fontSize = 9.5.sp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(notesFocus)
                        )
                    } else {
                        // في حال الصنف بوحدة كبرى فقط: نضع الكمية والملاحظات جنباً إلى جنب في سطر واحد فائق الرشاقة!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = majorQtyStr,
                                onValueChange = { majorQtyStr = it.replace("-", "") },
                                label = { Text("العدد المورد (${selectedProd?.majorUnit ?: "كيلو"})", fontSize = 9.5.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { notesFocus.requestFocus() }),
                                singleLine = true,
                                modifier = Modifier.weight(1.1f)
                            )
                            OutlinedTextField(
                                value = notesStr,
                                onValueChange = { notesStr = it },
                                placeholder = { Text("ملاحظات التوريد...", fontSize = 9.5.sp) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .focusRequester(notesFocus)
                            )
                        }
                    }

                    // أزرار الحفظ والإرسال الرشيقة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedProd != null && (enteredMaj > 0.0 || enteredMin > 0.0)) {
                                    viewModel.saveSupplyBatchToManagerWhatsApp(
                                        context = context,
                                        productKey = selectedProd.key,
                                        majorQty = enteredMaj,
                                        minorQty = enteredMin,
                                        notes = notesStr,
                                        shareToWhatsApp = true
                                    )
                                    majorQtyStr = ""
                                    minorQtyStr = ""
                                    notesStr = ""
                                } else {
                                    viewModel.saveSupplyBatchToManagerWhatsApp(
                                        context = context,
                                        productKey = selectedProd?.key ?: "",
                                        majorQty = enteredMaj,
                                        minorQty = enteredMin,
                                        notes = notesStr,
                                        shareToWhatsApp = true
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(35.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "إرسال للمدير (B$nextSupplyCounter)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = {
                                if (selectedProd != null && (enteredMaj > 0.0 || enteredMin > 0.0)) {
                                    viewModel.saveSupplyBatchToManagerWhatsApp(
                                        context = context,
                                        productKey = selectedProd.key,
                                        majorQty = enteredMaj,
                                        minorQty = enteredMin,
                                        notes = notesStr,
                                        shareToWhatsApp = false
                                    )
                                    majorQtyStr = ""
                                    minorQtyStr = ""
                                    notesStr = ""
                                } else {
                                    viewModel.saveSupplyBatchToManagerWhatsApp(
                                        context = context,
                                        productKey = selectedProd?.key ?: "",
                                        majorQty = enteredMaj,
                                        minorQty = enteredMin,
                                        notes = notesStr,
                                        shareToWhatsApp = false
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(0.85f)
                                .height(35.dp)
                        ) {
                            Text(
                                text = "حفظ فقط",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        items(supplies, key = { it.id }) { sup ->
            val prod = products.find { it.key == sup.productKey }
            val prevFormatted = if (prod != null) LedgerCalculator.formatStockMajorMinorArabic(sup.prevStock, prod) else "${sup.prevStock.cleanNum()} ${sup.majorUnit}"
            val cumFormatted = if (prod != null) LedgerCalculator.formatStockMajorMinorArabic(sup.cumulativeStock, prod) else "${sup.cumulativeStock.cleanNum()} ${sup.majorUnit}"

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🟢 [${sup.batchNumber}] ${sup.productName} — المورد: ${sup.enteredDisplay}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF065F46)
                        )
                        Text(
                            text = "اليوم: ${sup.dayName} | التاريخ: ${sup.date} | الساعة: ${sup.time}",
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "السابق: $prevFormatted  +  المورد: ${sup.enteredDisplay}  =  الإجمالي: $cumFormatted",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                        if (sup.notes.isNotBlank()) {
                            Text(
                                text = "📝 ملاحظات: ${sup.notes.trim()}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = {
                            val directSupplyMsg = if (prod != null) {
                                LedgerCalculator.buildManagerSupplyWhatsAppMessage(
                                    batchNum = sup.batchNumber,
                                    dayName = sup.dayName,
                                    date = sup.date,
                                    time = sup.time,
                                    product = prod,
                                    prevStock = sup.prevStock,
                                    enteredDisplay = sup.enteredDisplay,
                                    cumulativeStock = sup.cumulativeStock,
                                    notes = sup.notes,
                                    userName = userName
                                )
                            } else {
                                sup.whatsappText
                            }
                            openWhatsAppDirectOrShare(context, directSupplyMsg, managerPhone, userName)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "إرسال للمدير", tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { pendingEditSupply = sup }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { pendingDeleteSupply = sup }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (supplies.size >= 250) {
            item {
                OutlinedButton(
                    onClick = { viewModel.loadMoreSupplies() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("عرض المزيد من التوريدات السابقة...", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    pendingDeleteSupply?.let { sup ->
        ConfirmActionDialog(
            title = "تأكيد حذف سند التوريد (${sup.batchNumber})",
            message = "هل أنت متأكد من حذف سند التوريد رقم ${sup.batchNumber} للصنف (${sup.productName} - ${sup.enteredDisplay}) وعكس الكمية من المخزون؟",
            confirmText = "نعم، حذف السند",
            isDanger = true,
            onConfirm = { viewModel.deleteSupplyBatch(sup) },
            onDismiss = { pendingDeleteSupply = null }
        )
    }

    pendingEditSupply?.let { sup ->
        EditSavedSupplyDialog(
            batch = sup,
            product = products.find { it.key == sup.productKey },
            onConfirmSaveEdit = { newMaj, newMin, newNotes ->
                viewModel.updateSupplyRecord(sup, newMaj, newMin, newNotes)
            },
            onDismiss = { pendingEditSupply = null }
        )
    }

    pendingDeleteProduct?.let { prodToDelete ->
        ConfirmActionDialog(
            title = "تأكيد حذف الصنف (${prodToDelete.name})",
            message = "هل أنت متأكد من حذف الصنف (${prodToDelete.name}) نهائياً من قائمة الأصناف؟",
            confirmText = "نعم، حذف الصنف",
            isDanger = true,
            onConfirm = {
                viewModel.deleteProduct(context, prodToDelete)
                pendingDeleteProduct = null
            },
            onDismiss = { pendingDeleteProduct = null }
        )
    }
}
