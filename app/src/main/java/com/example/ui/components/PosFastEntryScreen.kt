package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
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
 * شاشة فاتورة البيع السريعة والمنظمة (تجمع مكونات العميل والأصناف والكميات والحسابات في هيكل رشيق)
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

    val customerPhoneInput by viewModel.customerPhoneInput.collectAsStateWithLifecycle()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 1. بطاقة اسم العميل المباشر والتاريخ والوقت (مع الدخول لجهات الاتصال بالضغط على الأيقونة وقائمة العملاء القابلة للطي)
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

        // 2. شبكة الأصناف الرشيقة والمنظمة
        PosCommoditySelectionCard(
            products = products,
            selectedProduct = selectedProduct,
            effectiveItems = effectiveItems,
            onSelectProduct = { prod ->
                viewModel.selectProduct(prod)
                majorQtyFocus.requestFocus()
            },
            onDeleteProduct = { prod ->
                viewModel.deleteProduct(context, prod)
            },
            onOpenEditProducts = onOpenEditProducts
        )

        // 3. بطاقة إدخال الكمية المباشر بالوحدة الكبرى والصغرى
        if (selectedProduct != null) {
            PosDirectQuantityEntryCard(
                viewModel = viewModel,
                selectedProduct = selectedProduct,
                majorQtyInput = majorQtyInput,
                minorQtyInput = minorQtyInput,
                unitPriceInput = unitPriceInput,
                showPriceField = showPriceField,
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

        // 4. قائمة الأصناف الفعالة في الفاتورة الحالية (قابلة للطي)
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

        // 5. الحسابات المالية المبسطة والمنظمة
        PosFinancialEntryAndPreviewCard(
            viewModel = viewModel,
            netInvoiceSubtotal = netInvoiceSubtotal,
            paidInput = paidInput,
            discountInput = discountInput,
            previousDebtInput = previousDebtInput,
            paidVal = paidVal,
            prevDebtVal = prevDebtVal,
            remaining = remaining,
            paidFocus = paidFocus,
            prevDebtFocus = prevDebtFocus,
            focusManager = focusManager,
            onOpenCalculator = onOpenCalculator
        )
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
    var showSuggestions by remember { mutableStateOf(true) }

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "بيانات العميل",
                        color = Color(0xFF1E293B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (customerSuggestions.isNotEmpty()) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { showSuggestions = !showSuggestions }
                        ) {
                            Text(
                                text = if (showSuggestions) "العملاء (${customerSuggestions.size}) ▴" else "العملاء (${customerSuggestions.size}) ▾",
                                color = Color(0xFF475569),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Text(
                        text = "$dayName $dateStr • $time12h",
                        color = Color(0xFF475569),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                onImeAction = onNextFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_name_input")
            )

            if (customerSuggestions.isNotEmpty() && (showSuggestions || customerNameInput.isNotBlank())) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(customerSuggestions, key = { it.name }) { cust ->
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { onSelectCustomerSuggestion(cust) }
                        ) {
                            Text(
                                text = cust.name,
                                color = Color(0xFF1E3A8A),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

