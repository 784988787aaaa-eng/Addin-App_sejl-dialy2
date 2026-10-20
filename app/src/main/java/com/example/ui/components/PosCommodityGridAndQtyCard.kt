package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.domain.LedgerCalculator
import com.example.ui.CartItemUi
import com.example.ui.SmartTradeViewModel
import com.example.util.cleanNum
import com.example.util.formatCombinedUnitsArabic

/**
 * شبكة الأصناف الرشيقة والمنظمة (قابلة للطي + 4 أعمدة بمسافات مريحة + الحذف بضغطة مطولة ثم زر سلة محذوفات حمراء صغير جداً مع رسالة تأكيد)
 */
@Composable
fun PosCommoditySelectionCard(
    products: List<ProductEntity>,
    selectedProduct: ProductEntity?,
    effectiveItems: List<CartItemUi>,
    onSelectProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onOpenEditProducts: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val rows = remember(products) { products.chunked(4) }
    var isGridExpanded by remember { mutableStateOf(true) }
    var deleteBadgeProductKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProduct by remember { mutableStateOf<ProductEntity?>(null) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "الأصناف",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E293B),
                        maxLines = 1
                    )
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { isGridExpanded = !isGridExpanded }
                    ) {
                        Text(
                            text = if (isGridExpanded) "طي القائمة ▴" else "عرض الأصناف (${products.size}) ▾",
                            color = Color(0xFF475569),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .testTag("edit_default_products_button")
                        .clickable { onOpenEditProducts() }
                ) {
                    Text(
                        text = "⚙️ الوحدات والأسعار",
                        color = Color(0xFF1E3A8A),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (isGridExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowProducts ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowProducts.forEach { prod ->
                                val isSelected = prod.key == selectedProduct?.key
                                val inEffective = effectiveItems.find { it.productKey == prod.key }
                                AgileCommodityCard(
                                    product = prod,
                                    isSelected = isSelected,
                                    showDeleteBadge = deleteBadgeProductKey == prod.key,
                                    cartBadgeText = inEffective?.unitDisplay,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onClick = {
                                        if (deleteBadgeProductKey != null) {
                                            deleteBadgeProductKey = null
                                        }
                                        onSelectProduct(prod)
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        deleteBadgeProductKey = if (deleteBadgeProductKey == prod.key) null else prod.key
                                    },
                                    onDeleteBadgeClick = {
                                        deleteBadgeProductKey = null
                                        pendingDeleteProduct = prod
                                    }
                                )
                            }
                            repeat(4 - rowProducts.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteProduct?.let { prodToDelete ->
        ConfirmActionDialog(
            title = "تأكيد حذف الصنف (${prodToDelete.name})",
            message = "هل أنت متأكد من حذف الصنف (${prodToDelete.name}) نهائياً من قائمة الأصناف؟",
            confirmText = "نعم، حذف الصنف",
            isDanger = true,
            onConfirm = {
                onDeleteProduct(prodToDelete)
                pendingDeleteProduct = null
            },
            onDismiss = { pendingDeleteProduct = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgileCommodityCard(
    product: ProductEntity,
    isSelected: Boolean,
    showDeleteBadge: Boolean,
    cartBadgeText: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteBadgeClick: () -> Unit
) {
    Surface(
        color = Color(product.colorBgHex),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(
            width = if (showDeleteBadge || isSelected) 1.8.dp else 1.dp,
            color = when {
                showDeleteBadge -> Color(0xFFDC2626)
                isSelected -> Color(0xFF0F172A)
                else -> Color(product.borderHex).copy(alpha = 0.65f)
            }
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 42.dp)
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = product.name,
                    color = Color(product.colorTextHex),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    softWrap = true
                )
                if (!cartBadgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = cartBadgeText,
                            color = Color(0xFF047857),
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            softWrap = true,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            if (showDeleteBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                        .border(1.dp, Color.White, CircleShape)
                        .testTag("delete_product_badge_${product.key}")
                        .clickable { onDeleteBadgeClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الصنف ${product.name}",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PosDirectQuantityEntryCard(
    viewModel: SmartTradeViewModel,
    selectedProduct: ProductEntity,
    majorQtyInput: String,
    minorQtyInput: String,
    unitPriceInput: String,
    showPriceField: Boolean,
    onRequestEditSavedPrice: () -> Unit,
    onFinishEditSavedPrice: () -> Unit,
    majorQtyFocus: FocusRequester,
    minorQtyFocus: FocusRequester,
    unitPriceFocus: FocusRequester,
    paidFocus: FocusRequester,
    onOpenEditProducts: () -> Unit,
    onOpenCalculator: (CalculatorTarget) -> Unit
) {
    val hasMinorUnit = selectedProduct.minorUnit.isNotBlank()
    val previewMaj = majorQtyInput.toDoubleOrNull() ?: 0.0
    val previewMin = if (hasMinorUnit) (minorQtyInput.toDoubleOrNull() ?: 0.0) else 0.0
    val combinedPreview = remember(selectedProduct, previewMaj, previewMin) {
        if (previewMaj > 0 || previewMin > 0) formatCombinedUnitsArabic(selectedProduct.majorUnit, selectedProduct.minorUnit, previewMaj, previewMin) else ""
    }
    val currentStockFormatted = remember(selectedProduct) {
        LedgerCalculator.formatStockMajorMinorArabic(selectedProduct.stock, selectedProduct)
    }
    var showConfirmEditPrice by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // شريط معلومات الصنف المحدد والسعر والوحدة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color(selectedProduct.colorBgHex),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, Color(selectedProduct.borderHex).copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = selectedProduct.name,
                            color = Color(selectedProduct.colorTextHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (combinedPreview.isNotBlank()) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Text(
                                text = combinedPreview,
                                color = Color(0xFF047857),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "المتوفر: $currentStockFormatted",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (!hasMinorUnit) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { onOpenEditProducts() }
                        ) {
                            Text(
                                text = "+ وحدة صغرى",
                                color = Color(0xFF475569),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (!showPriceField && selectedProduct.price > 0) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { showConfirmEditPrice = true }
                        ) {
                            Text(
                                text = "السعر: ${selectedProduct.price.cleanNum()} ✎",
                                color = Color(0xFF334155),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // صف إدخال الكميات (الكبرى والصغرى) بشكل واسع ومريح
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "الكمية (${selectedProduct.majorUnit})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155),
                        maxLines = 1
                    )
                    CompactInlineField(
                        value = majorQtyInput,
                        onValueChange = { viewModel.setMajorQtyInput(it) },
                        placeholder = "أدخل ${selectedProduct.majorUnit}...",
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (hasMinorUnit || showPriceField) ImeAction.Next else ImeAction.Done,
                        onImeAction = {
                            when {
                                hasMinorUnit -> minorQtyFocus.requestFocus()
                                showPriceField -> unitPriceFocus.requestFocus()
                                else -> paidFocus.requestFocus()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(majorQtyFocus)
                            .testTag("major_qty_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickMiniPill("+1", Modifier.weight(1f)) { viewModel.addQuickMajorQty(1.0) }
                        QuickMiniPill("+5", Modifier.weight(1f)) { viewModel.addQuickMajorQty(5.0) }
                        QuickMiniPill("نص", Modifier.weight(1f)) { viewModel.addQuickMajorQty(0.5) }
                    }
                }

                if (hasMinorUnit) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "الوحدة الصغرى (${selectedProduct.minorUnit})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            maxLines = 1
                        )
                        CompactInlineField(
                            value = minorQtyInput,
                            onValueChange = { viewModel.setMinorQtyInput(it) },
                            placeholder = "أدخل ${selectedProduct.minorUnit}...",
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (showPriceField) ImeAction.Next else ImeAction.Done,
                            onImeAction = { if (showPriceField) unitPriceFocus.requestFocus() else paidFocus.requestFocus() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(minorQtyFocus)
                                .testTag("minor_qty_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            QuickMiniPill("+1", Modifier.weight(1f)) { viewModel.addQuickMinorQty(1.0) }
                            QuickMiniPill("+2", Modifier.weight(1f)) { viewModel.addQuickMinorQty(2.0) }
                            QuickMiniPill("+4", Modifier.weight(1f)) { viewModel.addQuickMinorQty(4.0) }
                        }
                    }
                }

                if (!showPriceField) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "إضافة",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Transparent
                        )
                        Button(
                            onClick = {
                                onFinishEditSavedPrice()
                                viewModel.addOrUpdateCurrentItemInCart(autoDefaultToOne = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("add_to_invoice_button")
                        ) {
                            Text(
                                text = "+ صنف",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // صف مستقل لسعر الوحدة مع زر الإضافة عند تفعيل تعديل السعر أو عدم وجود سعر محفوظ (لمنع تكدس الحقول أفقياً)
            if (showPriceField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "سعر ${selectedProduct.majorUnit} (يُحفظ تلقائياً)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            maxLines = 1
                        )
                        CompactInlineFieldWithCalc(
                            value = unitPriceInput,
                            onValueChange = { viewModel.setUnitPriceInput(it) },
                            placeholder = "أدخل سعر ${selectedProduct.majorUnit}...",
                            onOpenCalc = { onOpenCalculator(CalculatorTarget.UNIT_PRICE) },
                            onImeDone = {
                                onFinishEditSavedPrice()
                                paidFocus.requestFocus()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(unitPriceFocus)
                        )
                    }

                    Button(
                        onClick = {
                            onFinishEditSavedPrice()
                            viewModel.addOrUpdateCurrentItemInCart(autoDefaultToOne = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("add_to_invoice_button")
                    ) {
                        Text(
                            text = "+ صنف",
                            fontSize = 11.sp,
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

    if (showConfirmEditPrice) {
        ConfirmActionDialog(
            title = "تأكيد تعديل سعر الصنف",
            message = "هل تريد تعديل السعر المحفوظ للصنف (${selectedProduct.name})؟",
            confirmText = "نعم، تعديل السعر",
            onConfirm = { onRequestEditSavedPrice() },
            onDismiss = { showConfirmEditPrice = false }
        )
    }
}

