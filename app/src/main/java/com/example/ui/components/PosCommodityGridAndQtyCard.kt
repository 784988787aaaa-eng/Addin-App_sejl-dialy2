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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ProductEntity
import com.example.domain.LedgerCalculator
import com.example.ui.CartItemUi
import com.example.ui.SmartTradeViewModel
import com.example.util.cleanNum
import com.example.util.formatCombinedUnitsArabic

/**
 * شريط استدعاء الأصناف الذكي: إزاحة زر عرض الأصناف مكان نص الأصناف بدون إسدال رأسي،
 * وعرض شبكة الأصناف في نافذة مستقلة فورية فوق البيانات تُغلق تلقائياً بمجرد اختيار الصنف لمواصلة الإدخال.
 */
@Composable
fun PosCommoditySelectionCard(
    products: List<ProductEntity>,
    selectedProduct: ProductEntity?,
    effectiveItems: List<CartItemUi>,
    autoCollapseForKeyboard: Boolean = false,
    onSelectProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onOpenEditProducts: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val rows = remember(products) { products.chunked(4) }
    var showCommoditiesDialog by remember { mutableStateOf(false) }
    var deleteBadgeProductKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteProduct by remember { mutableStateOf<ProductEntity?>(null) }

    Surface(
        color = PosUiTokens.CardSurface,
        shape = PosUiTokens.CardShape,
        border = BorderStroke(1.dp, PosUiTokens.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // تم حذف نص "الأصناف" وإزاحة زر "عرض الأصناف" مكانه مباشرة كما طلب المستخدم
                Surface(
                    color = PosUiTokens.SubtleSurface,
                    shape = PosUiTokens.BadgeShape,
                    border = BorderStroke(1.dp, PosUiTokens.InputBorderIdle),
                    modifier = Modifier.clickable { showCommoditiesDialog = true }
                ) {
                    Text(
                        text = "عرض الأصناف (${products.size}) ▾",
                        color = PosUiTokens.PrimaryBrand,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (selectedProduct != null) {
                    Surface(
                        color = Color(selectedProduct.colorBgHex),
                        shape = PosUiTokens.BadgeShape,
                        border = BorderStroke(1.dp, Color(selectedProduct.borderHex).copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { showCommoditiesDialog = true }
                    ) {
                        Text(
                            text = "المحدد: ${selectedProduct.name}",
                            color = Color(selectedProduct.colorTextHex),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Surface(
                color = PosUiTokens.SubtleSurface,
                shape = PosUiTokens.BadgeShape,
                border = BorderStroke(1.dp, PosUiTokens.InputBorderIdle),
                modifier = Modifier
                    .testTag("edit_default_products_button")
                    .clickable { onOpenEditProducts() }
            ) {
                Text(
                    text = "⚙️ الوحدات والأسعار",
                    color = PosUiTokens.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }

    // نافذة اختيار الأصناف المنبثقة المستقلة (تظهر فوق البيانات فوراً وبدون إسدال للأسفل)
    if (showCommoditiesDialog) {
        Dialog(onDismissRequest = { showCommoditiesDialog = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(
                    color = PosUiTokens.CardSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PosUiTokens.CardBorder),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
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
                                    text = "اختر الصنف للبيع (${products.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PosUiTokens.TextPrimary
                                )
                                if (selectedProduct != null) {
                                    Surface(
                                        color = Color(selectedProduct.colorBgHex),
                                        shape = PosUiTokens.BadgeShape,
                                        border = BorderStroke(0.8.dp, Color(selectedProduct.borderHex).copy(alpha = 0.7f))
                                    ) {
                                        Text(
                                            text = "الحالي: ${selectedProduct.name}",
                                            color = Color(selectedProduct.colorTextHex),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = PosUiTokens.SubtleSurface,
                                shape = PosUiTokens.BadgeShape,
                                border = BorderStroke(1.dp, PosUiTokens.CardBorder),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { showCommoditiesDialog = false }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "✕",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = PosUiTokens.TextSecondary
                                    )
                                }
                            }
                        }

                        // شبكة الأصناف الكاملة في نافذة منبثقة فوق البيانات
                        Column(verticalArrangement = Arrangement.spacedBy(4.5.dp)) {
                            rows.forEach { rowProducts ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(4.5.dp)
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
                                                // بالضغط على الصنف يتم إضافته في الحقول فوراً وإغلاق النافذة للمواصلة
                                                onSelectProduct(prod)
                                                showCommoditiesDialog = false
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
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (showDeleteBadge || isSelected) 1.7.dp else 0.9.dp,
            color = when {
                showDeleteBadge -> PosUiTokens.DangerRed
                isSelected -> PosUiTokens.ElevatedDarkSurface
                else -> Color(product.borderHex).copy(alpha = 0.65f)
            }
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 35.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.45f)
                        .height(2.dp)
                        .background(
                            color = PosUiTokens.ElevatedDarkSurface,
                            shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = product.name,
                    color = Color(product.colorTextHex),
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!cartBadgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = cartBadgeText,
                            color = PosUiTokens.PositiveGreen,
                            fontSize = 8.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }

            if (showDeleteBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(1.5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(PosUiTokens.DangerRed)
                        .border(1.dp, Color.White, CircleShape)
                        .testTag("delete_product_badge_${product.key}")
                        .clickable { onDeleteBadgeClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الصنف ${product.name}",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
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
    cartItems: List<CartItemUi>,
    majorQtyInput: String,
    minorQtyInput: String,
    unitPriceInput: String,
    showPriceField: Boolean,
    entryValidationAlert: String?,
    highlightMissingPrice: Boolean,
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
    val previewMaj = (majorQtyInput.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val previewMin = if (hasMinorUnit) (minorQtyInput.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0
    val combinedPreview = remember(selectedProduct, previewMaj, previewMin) {
        if (previewMaj > 0 || previewMin > 0) formatCombinedUnitsArabic(selectedProduct.majorUnit, selectedProduct.minorUnit, previewMaj, previewMin) else ""
    }

    val validation = remember(selectedProduct, cartItems, majorQtyInput, minorQtyInput, unitPriceInput) {
        com.example.ui.CartCalculatorHelper.evaluateProductEntryValidation(
            product = selectedProduct,
            cart = cartItems,
            majorStr = majorQtyInput,
            minorStr = minorQtyInput,
            priceStr = unitPriceInput
        )
    }

    val currentStockFormatted = validation.availableFormatted
    val isProductZeroStock = selectedProduct.stock <= 0.0
    val activeStockAlert = validation.stockErrorMessage
        ?: entryValidationAlert?.takeIf { !it.contains("سعر") }
    val isMissingPriceNow = validation.isPriceMissing

    var showConfirmEditPrice by remember { mutableStateOf(false) }

    Surface(
        color = PosUiTokens.CardSurface,
        shape = PosUiTokens.CardShape,
        border = BorderStroke(1.dp, PosUiTokens.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.5.dp)
        ) {
            // شريط معلومات الصنف المحدد والسعر والكمية المتوفرة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color(selectedProduct.colorBgHex),
                        shape = PosUiTokens.BadgeShape,
                        border = BorderStroke(1.dp, Color(selectedProduct.borderHex).copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = selectedProduct.name,
                            color = Color(selectedProduct.colorTextHex),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    when {
                        isProductZeroStock -> {
                            Surface(
                                color = PosUiTokens.DangerSoftBg,
                                shape = PosUiTokens.BadgeShape,
                                border = BorderStroke(0.8.dp, PosUiTokens.DangerSoftBorder)
                            ) {
                                Text(
                                    text = "غير متوفر (0 ${selectedProduct.majorUnit})",
                                    color = PosUiTokens.DangerRed,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        validation.isInsufficientStock -> {
                            Surface(
                                color = PosUiTokens.DangerSoftBg,
                                shape = PosUiTokens.BadgeShape,
                                border = BorderStroke(0.8.dp, PosUiTokens.DangerSoftBorder)
                            ) {
                                Text(
                                    text = "المتوفر فقط: $currentStockFormatted",
                                    color = PosUiTokens.DangerRed,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        combinedPreview.isNotBlank() -> {
                            Surface(
                                color = PosUiTokens.PositiveSoftBg,
                                shape = PosUiTokens.BadgeShape,
                                border = BorderStroke(1.dp, PosUiTokens.PositiveSoftBorder)
                            ) {
                                Text(
                                    text = combinedPreview,
                                    color = PosUiTokens.PositiveGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "المتوفر: $currentStockFormatted",
                                color = PosUiTokens.TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!hasMinorUnit) {
                        Surface(
                            color = PosUiTokens.SubtleSurface,
                            shape = PosUiTokens.BadgeShape,
                            border = BorderStroke(1.dp, PosUiTokens.CardBorder),
                            modifier = Modifier.clickable { onOpenEditProducts() }
                        ) {
                            Text(
                                text = "+ وحدة صغرى",
                                color = PosUiTokens.TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!showPriceField && selectedProduct.price > 0) {
                        Surface(
                            color = PosUiTokens.SubtleSurface,
                            shape = PosUiTokens.BadgeShape,
                            border = BorderStroke(1.dp, PosUiTokens.InputBorderIdle),
                            modifier = Modifier.clickable { showConfirmEditPrice = true }
                        ) {
                            Text(
                                text = "السعر: ${selectedProduct.price.cleanNum()} ✎",
                                color = PosUiTokens.TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // صف إدخال الكميات (الكبرى والصغرى) مع أزرار الزيادة السريعة وزر الإضافة المباشر
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "الكمية (${selectedProduct.majorUnit})",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosUiTokens.TextSecondary,
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
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        QuickMiniPill("+1", Modifier.weight(1f)) { viewModel.addQuickMajorQty(1.0) }
                        QuickMiniPill("+5", Modifier.weight(1f)) { viewModel.addQuickMajorQty(5.0) }
                        QuickMiniPill("نص", Modifier.weight(1f)) { viewModel.addQuickMajorQty(0.5) }
                    }
                }

                if (hasMinorUnit) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "الوحدة الصغرى (${selectedProduct.minorUnit})",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosUiTokens.TextSecondary,
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
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
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
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "إضافة",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Transparent
                        )
                        Button(
                            onClick = {
                                val added = viewModel.addOrUpdateCurrentItemInCart(autoDefaultToOne = true)
                                if (added) {
                                    onFinishEditSavedPrice()
                                } else if (isMissingPriceNow) {
                                    unitPriceFocus.requestFocus()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PosUiTokens.PrimaryAction),
                            shape = PosUiTokens.FieldShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(PosUiTokens.FieldHeight)
                                .testTag("add_to_invoice_button")
                        ) {
                            Text(
                                text = "+ صنف",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // إشارة تنبيه احترافية بخط صغير عند عدم توفر المنتج أو عدم كفاية الكمية بدون تكدس رأسي
            if (!activeStockAlert.isNullOrBlank()) {
                Surface(
                    color = PosUiTokens.DangerSoftBg,
                    shape = PosUiTokens.BadgeShape,
                    border = BorderStroke(0.8.dp, PosUiTokens.DangerSoftBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_validation_alert")
                ) {
                    Text(
                        text = activeStockAlert,
                        color = PosUiTokens.DangerRed,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // صف مستقل لسعر الوحدة مع زر الإضافة وخط توضيحي أحمر هادئ عند عدم وجود تسعيرة محفوظة
            if (showPriceField) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.5.dp)
                        ) {
                            Text(
                                text = "سعر ${selectedProduct.majorUnit} (يُحفظ تلقائياً بعد إدخاله للمرة الأولى)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMissingPriceNow || highlightMissingPrice) PosUiTokens.DangerRed else PosUiTokens.TextSecondary,
                                maxLines = 1
                            )
                            CompactInlineFieldWithCalc(
                                value = unitPriceInput,
                                onValueChange = { viewModel.setUnitPriceInput(it) },
                                placeholder = "أدخل سعر ${selectedProduct.majorUnit} أولاً...",
                                onOpenCalc = { onOpenCalculator(CalculatorTarget.UNIT_PRICE) },
                                onImeDone = {
                                    if (!isMissingPriceNow) {
                                        viewModel.commitCurrentUnitPrice()
                                        onFinishEditSavedPrice()
                                        paidFocus.requestFocus()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(unitPriceFocus)
                                    .testTag("unit_price_input")
                            )
                        }

                        Button(
                            onClick = {
                                val added = viewModel.addOrUpdateCurrentItemInCart(autoDefaultToOne = true)
                                if (added) {
                                    onFinishEditSavedPrice()
                                } else if (isMissingPriceNow) {
                                    unitPriceFocus.requestFocus()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PosUiTokens.PrimaryAction),
                            shape = PosUiTokens.FieldShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(PosUiTokens.FieldHeight)
                                .testTag("add_to_invoice_button")
                        ) {
                            Text(
                                text = "+ صنف",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // خط توضيحي بلون أحمر تحت حقل السعر بشكل هادئ لا يشكل زحمة
                    if (isMissingPriceNow) {
                        Text(
                            text = "أدخل سعر الصنف أولاً (لا يُحتسب سعر افتراضي بدون تسعيرة)",
                            color = PosUiTokens.DangerRed,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag("missing_price_alert")
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
