package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.LedgerCalculator
import com.example.ui.CartItemUi
import com.example.ui.SmartTradeViewModel
import com.example.util.cleanNum

/**
 * بطاقة عرض الأصناف المدرجة في الفاتورة الحالية (بمساحة مضبوطة لا تدفع الشاشة للأسفل + نافذة معاينة كاملة)
 * مع بطاقة الحسابات المالية الموحدة والمنظمة
 */
@Composable
fun PosCurrentInvoiceItemsCard(
    effectiveItems: List<CartItemUi>,
    netInvoiceSubtotal: Double,
    currency: String,
    selectedProductKey: String,
    majorQtyInput: String,
    minorQtyInput: String,
    viewModel: SmartTradeViewModel
) {
    var pendingDeleteItem by remember { mutableStateOf<CartItemUi?>(null) }
    var isItemsExpanded by remember { mutableStateOf(true) }
    var showFullItemsDialog by remember { mutableStateOf(false) }

    Surface(
        color = PosUiTokens.CardSurface,
        shape = PosUiTokens.CardShape,
        border = BorderStroke(1.dp, PosUiTokens.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isItemsExpanded = !isItemsExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "الأصناف في الفاتورة (${effectiveItems.size})",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = PosUiTokens.TextPrimary
                    )
                    Surface(
                        color = PosUiTokens.SubtleSurface,
                        shape = PosUiTokens.BadgeShape,
                        border = BorderStroke(1.dp, PosUiTokens.CardBorder)
                    ) {
                        Text(
                            text = if (isItemsExpanded) "طي ▴" else "عرض ▾",
                            color = PosUiTokens.TextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                    if (effectiveItems.size > 2) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = PosUiTokens.BadgeShape,
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.clickable { showFullItemsDialog = true }
                        ) {
                            Text(
                                text = "معاينة الكل ⤢",
                                color = PosUiTokens.PrimaryBrand,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = PosUiTokens.PositiveSoftBg,
                    shape = PosUiTokens.BadgeShape,
                    border = BorderStroke(1.dp, PosUiTokens.PositiveSoftBorder)
                ) {
                    Text(
                        text = "إجمالي الفاتورة: ${netInvoiceSubtotal.cleanNum()} $currency",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF065F46),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            if (isItemsExpanded) {
                // تحديد أقصى ارتفاع لقائمة الأصناف المدمجة حتى لا تدفع البطاقة المالية خارج الشاشة عند كثرة الأصناف
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 58.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    effectiveItems.forEach { item ->
                        InvoiceItemCompactRow(
                            item = item,
                            currency = currency,
                            onRequestDelete = { pendingDeleteItem = item }
                        )
                    }
                }
            }
        }
    }

    if (showFullItemsDialog) {
        Dialog(onDismissRequest = { showFullItemsDialog = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(
                    color = PosUiTokens.CardSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PosUiTokens.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تفاصيل أصناف الفاتورة (${effectiveItems.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = PosUiTokens.TextPrimary
                            )
                            Surface(
                                color = PosUiTokens.PositiveSoftBg,
                                shape = PosUiTokens.BadgeShape,
                                border = BorderStroke(1.dp, PosUiTokens.PositiveSoftBorder)
                            ) {
                                Text(
                                    text = "${netInvoiceSubtotal.cleanNum()} $currency",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PosUiTokens.PositiveGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            effectiveItems.forEach { item ->
                                InvoiceItemCompactRow(
                                    item = item,
                                    currency = currency,
                                    onRequestDelete = { pendingDeleteItem = item }
                                )
                            }
                        }

                        Button(
                            onClick = { showFullItemsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = PosUiTokens.PrimaryAction),
                            shape = PosUiTokens.FieldShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إغلاق المعاينة", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    pendingDeleteItem?.let { item ->
        ConfirmActionDialog(
            title = "تأكيد حذف الصنف من الفاتورة",
            message = "هل تريد بالتأكيد حذف الصنف (${item.productName} - ${item.unitDisplay}) من الفاتورة الحالية؟",
            confirmText = "نعم، حذف",
            isDanger = true,
            onConfirm = {
                if (item.productKey == selectedProductKey && (majorQtyInput.isNotBlank() || minorQtyInput.isNotBlank())) {
                    viewModel.setMajorQtyInput("")
                    viewModel.setMinorQtyInput("")
                }
                viewModel.removeCartItem(item.productKey)
            },
            onDismiss = { pendingDeleteItem = null }
        )
    }
}

@Composable
private fun InvoiceItemCompactRow(
    item: CartItemUi,
    currency: String,
    onRequestDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PosUiTokens.SubtleSurface, RoundedCornerShape(7.dp))
            .border(1.dp, PosUiTokens.CardBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.productName,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                color = PosUiTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                color = PosUiTokens.PositiveSoftBg,
                shape = PosUiTokens.BadgeShape,
                border = BorderStroke(0.5.dp, PosUiTokens.PositiveSoftBorder)
            ) {
                Text(
                    text = item.unitDisplay,
                    color = PosUiTokens.PositiveGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${item.total.cleanNum()} $currency",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                color = PosUiTokens.TextPrimary,
                maxLines = 1
            )
            Surface(
                color = PosUiTokens.DangerSoftBg,
                shape = PosUiTokens.BadgeShape,
                border = BorderStroke(0.5.dp, PosUiTokens.DangerSoftBorder),
                modifier = Modifier.clickable { onRequestDelete() }
            ) {
                Text(
                    text = "✕",
                    color = Color(0xFFE11D48),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun PosFinancialEntryAndPreviewCard(
    viewModel: SmartTradeViewModel,
    netInvoiceSubtotal: Double,
    paidInput: String,
    discountInput: String,
    previousDebtInput: String,
    selectedPaymentOption: Int,
    paidVal: Double,
    prevDebtVal: Double,
    remaining: Double,
    paidFocus: FocusRequester,
    prevDebtFocus: FocusRequester,
    focusManager: FocusManager,
    onOpenCalculator: (CalculatorTarget) -> Unit,
    onFinancialFocusChanged: (Boolean) -> Unit = {}
) {
    var showExtraAdjustments by remember { mutableStateOf(true) }
    var isPaidFocused by remember { mutableStateOf(false) }
    var isDiscountFocused by remember { mutableStateOf(false) }
    var isPrevDebtFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isPaidFocused, isDiscountFocused, isPrevDebtFocused) {
        onFinancialFocusChanged(isPaidFocused || isDiscountFocused || isPrevDebtFocused)
    }

    LaunchedEffect(netInvoiceSubtotal, selectedPaymentOption) {
        if (selectedPaymentOption == 0) {
            viewModel.syncCashFullPaymentIfSelected(netInvoiceSubtotal)
        }
    }

    Surface(
        color = PosUiTokens.CardSurface,
        shape = PosUiTokens.CardShape,
        border = BorderStroke(1.dp, PosUiTokens.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. شريط ملخص الفاتورة الموحد والهادئ (إجمالي الفاتورة | مسلم في الكشف | المتبقي)
            val paidDisplayPreview = remember(paidVal, prevDebtVal) {
                LedgerCalculator.formatPaidCellValues(paidVal, prevDebtVal)
            }
            val hasRem = remaining > 0.0

            Surface(
                color = PosUiTokens.SubtleSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PosUiTokens.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 4.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "إجمالي الفاتورة",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosUiTokens.TextMuted,
                            maxLines = 1
                        )
                        Text(
                            text = netInvoiceSubtotal.cleanNum(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = PosUiTokens.TextPrimary,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(PosUiTokens.DividerColor)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1.25f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "مسلم (في الكشف)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosUiTokens.PositiveGreen,
                            maxLines = 1
                        )
                        Text(
                            text = paidDisplayPreview,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = PosUiTokens.PositiveGreen,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(PosUiTokens.DividerColor)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "المتبقي (الفاتورة)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRem) PosUiTokens.WarningAmber else PosUiTokens.TextMuted,
                            maxLines = 1
                        )
                        Text(
                            text = remaining.cleanNum(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (hasRem) PosUiTokens.DangerRed else PosUiTokens.PrimaryBrand,
                            maxLines = 1
                        )
                    }
                }
            }

            // 2. خيارات طريقة السداد السريعة: انتقال حر وسلس في أي وقت حتى أثناء التركيز داخل أي حقل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.5.dp)
            ) {
                PaymentModelChip(
                    title = "نقد كامل",
                    selected = selectedPaymentOption == 0,
                    activeBg = PosUiTokens.PositiveGreen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_chip_cash_full"),
                    onClick = {
                        viewModel.selectPaymentMethod(0, netInvoiceSubtotal)
                    }
                )
                PaymentModelChip(
                    title = "مسلم جزء / آجل",
                    selected = selectedPaymentOption == 1,
                    activeBg = Color(0xFF2563EB),
                    modifier = Modifier
                        .weight(1.15f)
                        .testTag("payment_chip_partial"),
                    onClick = {
                        viewModel.selectPaymentMethod(1, netInvoiceSubtotal)
                        paidFocus.requestFocus()
                    }
                )
                PaymentModelChip(
                    title = "سداد دين سابق",
                    selected = selectedPaymentOption == 2,
                    activeBg = Color(0xFFD97706),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("payment_chip_prev_debt"),
                    onClick = {
                        showExtraAdjustments = true
                        viewModel.selectPaymentMethod(2, netInvoiceSubtotal)
                        prevDebtFocus.requestFocus()
                    }
                )
            }

            // 3. الحقل الأساسي: المسلّم نقداً للفاتورة مع مفتاح طي/عرض الحقول الإضافية في نفس السطر لتوفير الارتفاع
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مسلّم نقداً (للفاتورة)",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46),
                        maxLines = 1
                    )
                    Surface(
                        color = PosUiTokens.SubtleSurface,
                        shape = PosUiTokens.BadgeShape,
                        border = BorderStroke(1.dp, PosUiTokens.CardBorder),
                        modifier = Modifier.clickable { showExtraAdjustments = !showExtraAdjustments }
                    ) {
                        Text(
                            text = if (showExtraAdjustments) "خيارات إضافية (الخصم وسداد دين سابق) ▴" else "خيارات إضافية (الخصم وسداد دين سابق) ▾",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosUiTokens.TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                CompactInlineFieldWithCalc(
                    value = paidInput,
                    onValueChange = {
                        if (selectedPaymentOption == 0 && it != netInvoiceSubtotal.cleanNum()) {
                            viewModel.selectPaymentMethod(1, netInvoiceSubtotal)
                        }
                        viewModel.setPaidInput(it)
                    },
                    placeholder = "أدخل المبلغ المسلّم نقداً...",
                    onOpenCalc = {
                        if (selectedPaymentOption == 0) {
                            viewModel.selectPaymentMethod(1, netInvoiceSubtotal)
                        }
                        onOpenCalculator(CalculatorTarget.PAID)
                    },
                    onImeDone = { focusManager.clearFocus() },
                    onFocusStateChanged = { isPaidFocused = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(paidFocus)
                        .testTag("paid_input")
                )
            }

            // 4. صف الحقول الإضافية (الخصم + سداد دين سابق) مباشرة بدون إطار داخلي مزدوج لمنع التكدس الرأسي
            if (showExtraAdjustments) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "خصم",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosUiTokens.TextSecondary,
                            maxLines = 1
                        )
                        CompactInlineFieldWithCalc(
                            value = discountInput,
                            onValueChange = { viewModel.setDiscountInput(it) },
                            placeholder = "مبلغ الخصم",
                            onOpenCalc = { onOpenCalculator(CalculatorTarget.DISCOUNT) },
                            onImeDone = { paidFocus.requestFocus() },
                            onFocusStateChanged = { isDiscountFocused = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("discount_input")
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "سداد دين سابق",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PosUiTokens.TextSecondary,
                            maxLines = 1
                        )
                        CompactInlineFieldWithCalc(
                            value = previousDebtInput,
                            onValueChange = {
                                if (it.isNotBlank()) {
                                    viewModel.selectPaymentMethod(2, netInvoiceSubtotal)
                                }
                                viewModel.setPreviousDebtInput(it)
                            },
                            placeholder = "مبلغ سداد دين سابق",
                            onOpenCalc = {
                                viewModel.selectPaymentMethod(2, netInvoiceSubtotal)
                                onOpenCalculator(CalculatorTarget.PREV_DEBT)
                            },
                            onImeDone = { focusManager.clearFocus() },
                            onFocusStateChanged = { isPrevDebtFocused = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(prevDebtFocus)
                                .testTag("previous_debt_input")
                        )
                    }
                }
            }
        }
    }
}
