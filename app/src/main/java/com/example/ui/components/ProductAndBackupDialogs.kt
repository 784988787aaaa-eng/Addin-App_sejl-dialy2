package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ProductEntity
import com.example.ui.SmartTradeViewModel
import com.example.util.cleanNum

/**
 * نافذة تعديل وإضافة الأصناف والوحدات الكبرى والصغرى والأسعار (مع رسالة تأكيد عند التعديل)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditProductsAndUnitsDialog(
    products: List<ProductEntity>,
    initialProductKey: String,
    viewModel: SmartTradeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var isAddingNew by remember { mutableStateOf(false) }
    var selectedKey by remember(initialProductKey, products) {
        val exists = products.any { it.key == initialProductKey }
        mutableStateOf(if (exists) initialProductKey else (products.firstOrNull()?.key ?: ""))
    }
    val currentProduct = products.find { it.key == selectedKey } ?: products.firstOrNull()
    var deleteBadgeProductKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProduct by remember { mutableStateOf<ProductEntity?>(null) }

    var nameInput by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) "" else (currentProduct?.name ?: ""))
    }
    var majorUnitInput by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) "كيلو" else (currentProduct?.majorUnit ?: "كيلو"))
    }
    var enableMinorUnit by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) false else (currentProduct?.minorUnit?.isNotBlank() == true))
    }
    var minorUnitInput by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) "ثمن" else (currentProduct?.minorUnit?.ifBlank { "ثمن" } ?: "ثمن"))
    }
    var factorInput by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) "8" else (currentProduct?.factor?.cleanNum() ?: "2"))
    }
    var priceInput by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) "" else if ((currentProduct?.price ?: 0.0) > 0) currentProduct!!.price.cleanNum() else "")
    }
    var stockInput by remember(currentProduct, isAddingNew) {
        mutableStateOf(if (isAddingNew) "" else if ((currentProduct?.stock ?: 0.0) != 0.0) currentProduct!!.stock.cleanNum() else "")
    }
    var showConfirmEditProduct by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAddingNew) "إضافة صنف جديد للكشف" else "تعديل الأصناف والوحدات والأسعار",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        TextButton(onClick = { isAddingNew = !isAddingNew }) {
                            Text(
                                text = if (isAddingNew) "تعديل الأصناف الحالية" else "+ صنف جديد",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }

                    if (!isAddingNew) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(products, key = { it.key }) { prod ->
                                val isSel = prod.key == selectedKey
                                val showDeleteBadge = deleteBadgeProductKey == prod.key
                                Surface(
                                    color = if (isSel) Color(0xFF141E2E) else Color(prod.colorBgHex),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        width = if (showDeleteBadge) 2.dp else 1.dp,
                                        color = when {
                                            showDeleteBadge -> Color(0xFFDC2626)
                                            isSel -> Color(0xFF2563EB)
                                            else -> Color(prod.borderHex)
                                        }
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (deleteBadgeProductKey != null) deleteBadgeProductKey = null
                                                selectedKey = prod.key
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
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                        if (showDeleteBadge) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(2.dp)
                                                    .size(18.dp)
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
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("اسم الصنف", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = majorUnitInput,
                            onValueChange = { majorUnitInput = it },
                            label = { Text("الوحدة الكبرى (فراسلة / كيلو)", fontSize = 10.5.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تفعيل الوحدة الصغرى (مثل: ثمن / حبة / نص)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "يظهر المتبقي والمباع بالوحدة الكبرى ومتبقي الصغرى بوضوح تام",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Switch(checked = enableMinorUnit, onCheckedChange = { enableMinorUnit = it })
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    Triple("فراسلة", "ثمن", "8"),
                                    Triple("كيلو", "حبة", "2"),
                                    Triple("كيلو", "ثمن", "8"),
                                    Triple("كرتون", "حبة", "12")
                                ).forEach { (maj, min, fac) ->
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(7.dp),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                enableMinorUnit = true
                                                majorUnitInput = maj
                                                minorUnitInput = min
                                                factorInput = fac
                                            }
                                    ) {
                                        Text(
                                            text = "$maj + $min ($fac)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            if (enableMinorUnit) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = minorUnitInput,
                                        onValueChange = { minorUnitInput = it },
                                        label = { Text("اسم الوحدة الصغرى (ثمن / حبة)", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = factorInput,
                                        onValueChange = { factorInput = it },
                                        label = { Text("كم (${minorUnitInput.ifBlank { "صغرى" }}) في 1 (${majorUnitInput.ifBlank { "كبرى" }})", fontSize = 9.5.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = priceInput,
                            onValueChange = { priceInput = it },
                            label = { Text("سعر ${majorUnitInput.ifBlank { "الوحدة" }} (يُحفظ دائماً)", fontSize = 10.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stockInput,
                            onValueChange = { stockInput = it },
                            label = { Text("الرصيد المتاح (${majorUnitInput.ifBlank { "كيلو" }})", fontSize = 10.5.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Text("إغلاق", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                if (isAddingNew) {
                                    val fac = factorInput.toDoubleOrNull() ?: 2.0
                                    val pr = priceInput.toDoubleOrNull() ?: 0.0
                                    val st = stockInput.toDoubleOrNull() ?: 0.0
                                    val finalMinor = if (enableMinorUnit) minorUnitInput.trim().ifBlank { "حبة" } else ""
                                    viewModel.addNewCustomProduct(nameInput, majorUnitInput, finalMinor, fac, pr, st)
                                    onDismiss()
                                } else {
                                    showConfirmEditProduct = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A36A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("✓ حفظ واعتماد", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmEditProduct && currentProduct != null) {
        ConfirmActionDialog(
            title = "تأكيد تعديل بيانات الصنف",
            message = "هل أنت متأكد من حفظ التعديلات على الصنف (${currentProduct.name}) واعتماد الوحدات والسعر والرصيد الجديد؟",
            confirmText = "نعم، حفظ التعديل",
            onConfirm = {
                val fac = factorInput.toDoubleOrNull() ?: 2.0
                val pr = priceInput.toDoubleOrNull() ?: 0.0
                val st = stockInput.toDoubleOrNull() ?: 0.0
                val finalMinor = if (enableMinorUnit) minorUnitInput.trim().ifBlank { "حبة" } else ""
                viewModel.updateProductDetails(currentProduct, nameInput, majorUnitInput, finalMinor, fac, pr, st)
                onDismiss()
            },
            onDismiss = { showConfirmEditProduct = false }
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
