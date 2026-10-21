package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerEntity
import com.example.data.ProductEntity
import com.example.domain.LedgerCalculator
import com.example.ui.CartCalculatorHelper
import com.example.ui.CartItemUi
import com.example.ui.SmartTradeViewModel
import com.example.util.rememberContactPickerLauncher

/**
 * شاشة نقطة البيع السريعة والمهيأة هندسياً للعرض المتكامل بدون تمرير رأسي في الوضع القياسي،
 * مع استجابة ذكية لحجم الشاشة ولوحة المفاتيح وتسلسل انتقال التركيز.
 */
@Composable
fun PosFastEntryScreen(
    viewModel: SmartTradeViewModel,
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    selectedProductKey: String,
    customerNameInput: String,
    majorQtyInput: String,
    minorQtyInput: String,
    unitPriceInput: String,
    cartItems: List<CartItemUi>,
    previousDebtInput: String,
    discountInput: String,
    paidInput: String,
    dayName: String,
    dateStr: String,
    time12h: String,
    currency: String,
    onOpenEditProducts: () -> Unit,
    onOpenCalculator: (CalculatorTarget) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val majorQtyFocus = remember { FocusRequester() }
    val minorQtyFocus = remember { FocusRequester() }
    val unitPriceFocus = remember { FocusRequester() }
    val prevDebtFocus = remember { FocusRequester() }
    val paidFocus = remember { FocusRequester() }

    var isFinancialFieldFocused by remember { mutableStateOf(false) }

    val customerPhoneInput by viewModel.customerPhoneInput.collectAsStateWithLifecycle()
    val selectedPaymentOption by viewModel.selectedPaymentOption.collectAsStateWithLifecycle()
    val entryValidationAlert by viewModel.entryValidationAlert.collectAsStateWithLifecycle()
    val highlightMissingPrice by viewModel.highlightMissingPrice.collectAsStateWithLifecycle()

    val launchCustomerContactPicker = rememberContactPickerLauncher { picked ->
        viewModel.onCustomerContactPicked(context, picked.name, picked.phone)
        majorQtyFocus.requestFocus()
    }

    val selectedProduct = products.find { it.key == selectedProductKey } ?: products.firstOrNull()

    var isEditingSavedPrice by remember(selectedProductKey) { mutableStateOf(false) }
    val showPriceField = (selectedProduct?.price ?: 0.0) <= 0.0 || isEditingSavedPrice

    val effectiveItems = remember(cartItems, products, selectedProductKey, majorQtyInput, minorQtyInput, unitPriceInput) {
        viewModel.getEffectiveCartItems(
            cart = cartItems,
            allProducts = products,
            selectedKey = selectedProductKey,
            majorStr = majorQtyInput,
            minorStr = minorQtyInput,
            priceStr = unitPriceInput
        )
    }

    val invoiceAccounting = remember(effectiveItems, discountInput, paidInput, previousDebtInput) {
        val rawItemsSum = CartCalculatorHelper.sumCartTotals(effectiveItems)
        LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = rawItemsSum,
            discount = discountInput.toDoubleOrNull() ?: 0.0,
            paidCash = paidInput.toDoubleOrNull() ?: 0.0,
            previousDebtPaid = previousDebtInput.toDoubleOrNull() ?: 0.0
        )
    }
    val netInvoiceSubtotal = invoiceAccounting.netInvoiceSubtotal
    val paidVal = invoiceAccounting.paidCash
    val prevDebtVal = invoiceAccounting.previousDebtPaid
    val remaining = invoiceAccounting.remainingOnInvoice

    val customerSuggestions = remember(customers, customerNameInput) {
        val q = customerNameInput.trim()
        if (q.isEmpty()) customers.take(6)
        else customers.filter { it.name.contains(q, ignoreCase = true) && !it.name.equals(q, ignoreCase = true) }.take(6)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isWideLayout = maxWidth >= 640.dp
        val isCompactKeyboardViewport = maxHeight < 470.dp

        if (isWideLayout) {
            // تخطيط اللوحي والشاشات العريضة: قسم الإدخال والأصناف يميناً + قسم مراجعة الفاتورة والحسابات يساراً
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PosCustomerInlineCard(
                        customerNameInput = customerNameInput,
                        hasAttachedPhone = customerPhoneInput.isNotBlank(),
                        dayName = dayName,
                        dateStr = dateStr,
                        time12h = time12h,
                        customerSuggestions = customerSuggestions,
                        onCustomerNameChange = { viewModel.setCustomerNameInput(it) },
                        onPickFromContacts = launchCustomerContactPicker,
                        onSelectCustomerSuggestion = { cust ->
                            viewModel.selectCustomerSuggestion(cust)
                            majorQtyFocus.requestFocus()
                        },
                        onNextFocus = { majorQtyFocus.requestFocus() }
                    )

                    PosCommoditySelectionCard(
                        products = products,
                        selectedProduct = selectedProduct,
                        effectiveItems = effectiveItems,
                        autoCollapseForKeyboard = false,
                        onSelectProduct = { prod ->
                            viewModel.selectProduct(prod)
                            majorQtyFocus.requestFocus()
                        },
                        onDeleteProduct = { prod ->
                            viewModel.deleteProduct(context, prod)
                        },
                        onOpenEditProducts = onOpenEditProducts
                    )

                    if (selectedProduct != null) {
                        PosDirectQuantityEntryCard(
                            viewModel = viewModel,
                            selectedProduct = selectedProduct,
                            cartItems = cartItems,
                            majorQtyInput = majorQtyInput,
                            minorQtyInput = minorQtyInput,
                            unitPriceInput = unitPriceInput,
                            showPriceField = showPriceField,
                            entryValidationAlert = entryValidationAlert,
                            highlightMissingPrice = highlightMissingPrice,
                            onRequestEditSavedPrice = { isEditingSavedPrice = true },
                            onFinishEditSavedPrice = { isEditingSavedPrice = false },
                            majorQtyFocus = majorQtyFocus,
                            minorQtyFocus = minorQtyFocus,
                            unitPriceFocus = unitPriceFocus,
                            paidFocus = paidFocus,
                            onOpenEditProducts = onOpenEditProducts,
                            onOpenCalculator = onOpenCalculator
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (effectiveItems.isNotEmpty()) {
                        PosCurrentInvoiceItemsCard(
                            effectiveItems = effectiveItems,
                            netInvoiceSubtotal = netInvoiceSubtotal,
                            currency = currency,
                            selectedProductKey = selectedProductKey,
                            majorQtyInput = majorQtyInput,
                            minorQtyInput = minorQtyInput,
                            viewModel = viewModel
                        )
                    }

                    PosFinancialEntryAndPreviewCard(
                        viewModel = viewModel,
                        netInvoiceSubtotal = netInvoiceSubtotal,
                        paidInput = paidInput,
                        discountInput = discountInput,
                        previousDebtInput = previousDebtInput,
                        selectedPaymentOption = selectedPaymentOption,
                        paidVal = paidVal,
                        prevDebtVal = prevDebtVal,
                        remaining = remaining,
                        paidFocus = paidFocus,
                        prevDebtFocus = prevDebtFocus,
                        focusManager = focusManager,
                        onOpenCalculator = onOpenCalculator,
                        onFinancialFocusChanged = { isFinancialFieldFocused = it }
                    )
                }
            }
        } else {
            // التخطيط القياسي للهواتف: مضغوط ومدروس ليحتوي جميع المناطق الوظيفية بدون تمرير رأسي في الوضع الطبيعي
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.5.dp)
            ) {
                // 1. منطقة بيانات العميل والتاريخ والوقت (مدمجة مع طي تلقائي للاقتراحات)
                PosCustomerInlineCard(
                    customerNameInput = customerNameInput,
                    hasAttachedPhone = customerPhoneInput.isNotBlank(),
                    dayName = dayName,
                    dateStr = dateStr,
                    time12h = time12h,
                    customerSuggestions = customerSuggestions,
                    onCustomerNameChange = { viewModel.setCustomerNameInput(it) },
                    onPickFromContacts = launchCustomerContactPicker,
                    onSelectCustomerSuggestion = { cust ->
                        viewModel.selectCustomerSuggestion(cust)
                        majorQtyFocus.requestFocus()
                    },
                    onNextFocus = { majorQtyFocus.requestFocus() }
                )

                // 2. منطقة شبكة الأصناف الرشيقة (تطوى تلقائياً عند فتح لوحة المفاتيح على الحقول المالية في الشاشات القصيرة)
                PosCommoditySelectionCard(
                    products = products,
                    selectedProduct = selectedProduct,
                    effectiveItems = effectiveItems,
                    autoCollapseForKeyboard = isCompactKeyboardViewport && isFinancialFieldFocused,
                    onSelectProduct = { prod ->
                        viewModel.selectProduct(prod)
                        majorQtyFocus.requestFocus()
                    },
                    onDeleteProduct = { prod ->
                        viewModel.deleteProduct(context, prod)
                    },
                    onOpenEditProducts = onOpenEditProducts
                )

                // 3. منطقة إدخال الكمية المباشر بالوحدة الكبرى والصغرى والسعر
                if (selectedProduct != null) {
                    PosDirectQuantityEntryCard(
                        viewModel = viewModel,
                        selectedProduct = selectedProduct,
                        cartItems = cartItems,
                        majorQtyInput = majorQtyInput,
                        minorQtyInput = minorQtyInput,
                        unitPriceInput = unitPriceInput,
                        showPriceField = showPriceField,
                        entryValidationAlert = entryValidationAlert,
                        highlightMissingPrice = highlightMissingPrice,
                        onRequestEditSavedPrice = { isEditingSavedPrice = true },
                        onFinishEditSavedPrice = { isEditingSavedPrice = false },
                        majorQtyFocus = majorQtyFocus,
                        minorQtyFocus = minorQtyFocus,
                        unitPriceFocus = unitPriceFocus,
                        paidFocus = paidFocus,
                        onOpenEditProducts = onOpenEditProducts,
                        onOpenCalculator = onOpenCalculator
                    )
                }

                // 4. منطقة مراجعة أصناف الفاتورة الحالية (محددة الارتفاع مع نافذة معاينة كاملة لمنع تمدد الشاشة)
                if (effectiveItems.isNotEmpty()) {
                    PosCurrentInvoiceItemsCard(
                        effectiveItems = effectiveItems,
                        netInvoiceSubtotal = netInvoiceSubtotal,
                        currency = currency,
                        selectedProductKey = selectedProductKey,
                        majorQtyInput = majorQtyInput,
                        minorQtyInput = minorQtyInput,
                        viewModel = viewModel
                    )
                }

                // 5. منطقة الملخص المالي وإدخال الدفعات والخصم
                PosFinancialEntryAndPreviewCard(
                    viewModel = viewModel,
                    netInvoiceSubtotal = netInvoiceSubtotal,
                    paidInput = paidInput,
                    discountInput = discountInput,
                    previousDebtInput = previousDebtInput,
                    selectedPaymentOption = selectedPaymentOption,
                    paidVal = paidVal,
                    prevDebtVal = prevDebtVal,
                    remaining = remaining,
                    paidFocus = paidFocus,
                    prevDebtFocus = prevDebtFocus,
                    focusManager = focusManager,
                    onOpenCalculator = onOpenCalculator,
                    onFinancialFocusChanged = { isFinancialFieldFocused = it }
                )
            }
        }
    }
}

@Composable
private fun PosCustomerInlineCard(
    customerNameInput: String,
    hasAttachedPhone: Boolean,
    dayName: String,
    dateStr: String,
    time12h: String,
    customerSuggestions: List<CustomerEntity>,
    onCustomerNameChange: (String) -> Unit,
    onPickFromContacts: () -> Unit,
    onSelectCustomerSuggestion: (CustomerEntity) -> Unit,
    onNextFocus: () -> Unit
) {
    // مطوية افتراضياً لتوفير المساحة الرأسية، وتفتح عند رغبة المستخدم أو أثناء البحث المباشر
    var showSuggestions by remember { mutableStateOf(false) }
    var isCustomerFocused by remember { mutableStateOf(false) }

    val shouldDisplaySuggestions = customerSuggestions.isNotEmpty() &&
        (showSuggestions || (isCustomerFocused && customerNameInput.isNotBlank()))

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "بيانات العميل",
                        color = PosUiTokens.TextPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (hasAttachedPhone) {
                        Surface(
                            color = PosUiTokens.PositiveSoftBg,
                            shape = PosUiTokens.BadgeShape,
                            border = BorderStroke(0.8.dp, PosUiTokens.PositiveSoftBorder)
                        ) {
                            Text(
                                text = "✓ مرتبط برقم",
                                color = PosUiTokens.PositiveGreen,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (customerSuggestions.isNotEmpty()) {
                        Surface(
                            color = if (shouldDisplaySuggestions) PosUiTokens.PositiveSoftBg else PosUiTokens.SubtleSurface,
                            shape = PosUiTokens.BadgeShape,
                            border = BorderStroke(
                                1.dp,
                                if (shouldDisplaySuggestions) PosUiTokens.PositiveSoftBorder else PosUiTokens.CardBorder
                            ),
                            modifier = Modifier.clickable { showSuggestions = !showSuggestions }
                        ) {
                            Text(
                                text = if (shouldDisplaySuggestions) "العملاء (${customerSuggestions.size}) ▴" else "العملاء (${customerSuggestions.size}) ▾",
                                color = if (shouldDisplaySuggestions) PosUiTokens.PositiveGreen else PosUiTokens.TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = PosUiTokens.SubtleSurface,
                    shape = PosUiTokens.BadgeShape,
                    border = BorderStroke(1.dp, PosUiTokens.CardBorder)
                ) {
                    Text(
                        text = "$dayName $dateStr • $time12h",
                        color = PosUiTokens.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            CompactInlineField(
                value = customerNameInput,
                onValueChange = onCustomerNameChange,
                placeholder = "اسم العميل (اكتب مباشرة أو اختر من جهات الاتصال)...",
                leadingIcon = "👤",
                onLeadingIconClick = onPickFromContacts,
                hasAttachedPhone = hasAttachedPhone,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                onImeAction = {
                    showSuggestions = false
                    onNextFocus()
                },
                onFocusStateChanged = { focused -> isCustomerFocused = focused },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_name_input")
            )

            if (shouldDisplaySuggestions) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(customerSuggestions, key = { it.name }) { cust ->
                        Surface(
                            color = PosUiTokens.SubtleSurface,
                            shape = PosUiTokens.ChipShape,
                            border = BorderStroke(1.dp, PosUiTokens.InputBorderIdle),
                            modifier = Modifier.clickable {
                                showSuggestions = false
                                onSelectCustomerSuggestion(cust)
                            }
                        ) {
                            Text(
                                text = cust.name,
                                color = PosUiTokens.PrimaryBrand,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
