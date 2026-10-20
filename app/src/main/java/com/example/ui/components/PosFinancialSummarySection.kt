package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.LedgerCalculator
import com.example.ui.CartItemUi
import com.example.ui.SmartTradeViewModel
import com.example.util.cleanNum

/**
 * بطاقة عرض الأصناف المدرجة في الفاتورة الحالية (قابلة للطي مع تأكيد الحذف) + بطاقة الحسابات المالية المنظمة
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

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "الأصناف في الفاتورة (${effectiveItems.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = if (isItemsExpanded) "طي ▴" else "عرض ▾",
                            color = Color(0xFF475569),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Text(
                        text = "إجمالي الفاتورة: ${netInvoiceSubtotal.cleanNum()} $currency",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF065F46),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (isItemsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    effectiveItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.productName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A)
                                )
                                Surface(
                                    color = Color(0xFFECFDF5),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFA7F3D0))
                                ) {
                                    Text(
                                        text = item.unitDisplay,
                                        color = Color(0xFF047857),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${item.total.cleanNum()} $currency",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A)
                                )
                                Surface(
                                    color = Color(0xFFFFF1F2),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFFECDD3)),
                                    modifier = Modifier.clickable { pendingDeleteItem = item }
                                ) {
                                    Text(
                                        text = "✕",
                                        color = Color(0xFFE11D48),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
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
fun PosFinancialEntryAndPreviewCard(
    viewModel: SmartTradeViewModel,
    netInvoiceSubtotal: Double,
    paidInput: String,
    discountInput: String,
    previousDebtInput: String,
    paidVal: Double,
    prevDebtVal: Double,
    remaining: Double,
    paidFocus: FocusRequester,
    prevDebtFocus: FocusRequester,
    focusManager: FocusManager,
    onOpenCalculator: (CalculatorTarget) -> Unit
) {
    var showExtraAdjustments by remember { mutableStateOf(true) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. شريط ملخص الفاتورة الموحد والهادئ (إجمالي الفاتورة | مسلم في الكشف | المتبقي)
            val paidDisplayPreview = remember(paidVal, prevDebtVal) {
                LedgerCalculator.formatPaidCellValues(paidVal, prevDebtVal)
            }
            val hasRem = remaining > 0.0

            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "إجمالي الفاتورة",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = netInvoiceSubtotal.cleanNum(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A),
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFFE2E8F0))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1.25f)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "مسلم (في الكشف)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = paidDisplayPreview,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF047857),
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFFE2E8F0))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "المتبقي (الفاتورة)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRem) Color(0xFFB45309) else Color(0xFF64748B),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = remaining.cleanNum(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = if (hasRem) Color(0xFFDC2626) else Color(0xFF1E3A8A),
                            maxLines = 1
                        )
                    }
                }
            }

            // 2. خيارات طريقة السداد السريعة
            var selectedPaymentOption by remember { mutableIntStateOf(1) }
            val isCashFull = (netInvoiceSubtotal > 0 && paidVal == netInvoiceSubtotal && prevDebtVal == 0.0) ||
                (selectedPaymentOption == 0 && paidVal == netInvoiceSubtotal && prevDebtVal == 0.0)
            val hasPrevDebtNote = prevDebtVal > 0.0 || selectedPaymentOption == 2
            val isPartial = !isCashFull && !hasPrevDebtNote

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PaymentModelChip(
                    title = "💵 مسلم نقداً بالكامل",
                    selected = isCashFull,
                    activeBg = Color(0xFF0D9488),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("payment_chip_cash_full"),
                    onClick = {
                        selectedPaymentOption = 0
                        viewModel.applyCashFullPaymentModel(netInvoiceSubtotal)
                    }
                )
                PaymentModelChip(
                    title = "🌗 مسلم جزء / آجل",
                    selected = isPartial,
                    activeBg = Color(0xFF2563EB),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("payment_chip_partial"),
                    onClick = {
                        selectedPaymentOption = 1
                        paidFocus.requestFocus()
                    }
                )
                PaymentModelChip(
                    title = "📌 سداد دين سابق",
                    selected = hasPrevDebtNote,
                    activeBg = Color(0xFFD97706),
                    modifier = Modifier
                        .weight(1.05f)
                        .testTag("payment_chip_prev_debt"),
                    onClick = {
                        selectedPaymentOption = 2
                        showExtraAdjustments = true
                        prevDebtFocus.requestFocus()
                    }
                )
            }

            // 3. الحقل الأساسي: المسلّم نقداً للفاتورة (واضح وبمساحة كاملة مريحة)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "مسلّم نقداً (للفاتورة)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF065F46),
                    maxLines = 1
                )
                CompactInlineFieldWithCalc(
                    value = paidInput,
                    onValueChange = {
                        selectedPaymentOption = 1
                        viewModel.setPaidInput(it)
                    },
                    placeholder = "أدخل المبلغ المسلّم نقداً...",
                    onOpenCalc = {
                        selectedPaymentOption = 1
                        onOpenCalculator(CalculatorTarget.PAID)
                    },
                    onImeDone = { focusManager.clearFocus() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(paidFocus)
                        .testTag("paid_input")
                )
            }

            // 4. قسم قابل للطي لتجميع الحقول الإضافية (الخصم + سداد دين سابق) لمنع الازدحام
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExtraAdjustments = !showExtraAdjustments },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "خيارات إضافية (الخصم وسداد دين سابق)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = if (showExtraAdjustments) "طي ▴" else "عرض ▾",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }

                    if (showExtraAdjustments) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "خصم",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    maxLines = 1
                                )
                                CompactInlineFieldWithCalc(
                                    value = discountInput,
                                    onValueChange = { viewModel.setDiscountInput(it) },
                                    placeholder = "مبلغ الخصم",
                                    onOpenCalc = { onOpenCalculator(CalculatorTarget.DISCOUNT) },
                                    onImeDone = { paidFocus.requestFocus() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("discount_input")
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1.15f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "سداد دين سابق",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    maxLines = 1
                                )
                                CompactInlineFieldWithCalc(
                                    value = previousDebtInput,
                                    onValueChange = {
                                        selectedPaymentOption = 2
                                        viewModel.setPreviousDebtInput(it)
                                    },
                                    placeholder = "مبلغ سداد دين سابق",
                                    onOpenCalc = {
                                        selectedPaymentOption = 2
                                        onOpenCalculator(CalculatorTarget.PREV_DEBT)
                                    },
                                    onImeDone = { focusManager.clearFocus() },
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
    }
}

