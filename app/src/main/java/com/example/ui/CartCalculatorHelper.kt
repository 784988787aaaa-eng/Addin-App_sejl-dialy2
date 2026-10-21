package com.example.ui

import android.content.Context
import com.example.data.ProductEntity
import com.example.domain.LedgerCalculator
import com.example.domain.WhatsAppMessageFormatter
import com.example.util.formatCombinedUnitsArabic
import java.math.BigDecimal
import java.math.RoundingMode

data class CartItemUi(
    val productKey: String,
    val productName: String,
    val majorUnit: String,
    val minorUnit: String,
    val factor: Double,
    val majorQty: Double,
    val minorQty: Double,
    val unitDisplay: String,
    val majorUnitPrice: Double,
    val total: Double
)

enum class InvoiceShareDestination {
    SAVE_ONLY,
    CUSTOMER_WHATSAPP,
    MANAGER_WHATSAPP,
    PDF_SHARE
}

data class PosEntryValidationState(
    val isOutOfStock: Boolean = false,
    val isInsufficientStock: Boolean = false,
    val isPriceMissing: Boolean = false,
    val availableMajorEq: Double = 0.0,
    val availableFormatted: String = "",
    val requestedMajorEq: Double = 0.0,
    val stockErrorMessage: String? = null,
    val priceErrorMessage: String? = null
)

fun openWhatsAppDirectOrShare(context: Context, text: String, rawPhone: String = "", userName: String = "") {
    WhatsAppMessageFormatter.openWhatsAppDirectOrShare(context, text, rawPhone, userName)
}

/**
 * مساعد حسابات سلة المبيعات اللحظية والدمج التلقائي للكميات المدخلة (مرتبط بالمحرك المحاسبي الموحد LedgerCalculator)
 */
object CartCalculatorHelper {

    fun sumCartTotals(cart: List<CartItemUi>): Double {
        if (cart.isEmpty()) return 0.0
        var sumBd = BigDecimal.ZERO
        for (item in cart) {
            val t = if (item.total.isNaN() || item.total.isInfinite()) 0.0 else item.total
            sumBd = sumBd.add(BigDecimal.valueOf(t))
        }
        return sumBd.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun evaluateProductEntryValidation(
        product: ProductEntity?,
        cart: List<CartItemUi>,
        majorStr: String,
        minorStr: String,
        priceStr: String
    ): PosEntryValidationState {
        if (product == null) return PosEntryValidationState()
        val hasMinor = product.minorUnit.isNotBlank()
        val factor = if (product.factor > 0.0) product.factor else 2.0

        val existingInCart = cart.find { it.productKey == product.key }
        val alreadyInCartEq = if (existingInCart != null) {
            LedgerCalculator.computeMajorEquivalent(existingInCart.majorQty, existingInCart.minorQty, factor, hasMinor)
        } else 0.0

        val remainingAvailableEq = LedgerCalculator.subtractStock(product.stock, alreadyInCartEq).coerceAtLeast(0.0)
        val availableFormatted = LedgerCalculator.formatStockMajorMinorArabic(remainingAvailableEq, product)

        val hasNegativeSign = majorStr.trim().startsWith("-") || minorStr.trim().startsWith("-")
        val rawMaj = majorStr.toDoubleOrNull() ?: 0.0
        val rawMin = if (hasMinor) (minorStr.toDoubleOrNull() ?: 0.0) else 0.0
        val hasNegativeValue = hasNegativeSign || rawMaj < 0.0 || rawMin < 0.0

        val cleanMaj = rawMaj.coerceAtLeast(0.0)
        val cleanMin = rawMin.coerceAtLeast(0.0)
        val typedEq = LedgerCalculator.computeMajorEquivalent(cleanMaj, cleanMin, factor, hasMinor)

        val effectivePrice = LedgerCalculator.roundMoney(priceStr.toDoubleOrNull() ?: product.price)
        val isPriceMissing = effectivePrice <= 0.0

        val isOutOfStock = product.stock <= 0.0 || remainingAvailableEq <= 0.0
        val isInsufficientStock = !isOutOfStock && typedEq > remainingAvailableEq + 1e-6

        val stockErrorMessage = when {
            hasNegativeValue -> "لا يمكن إدخال كمية سالبة"
            product.stock <= 0.0 -> "لا يمكن: هذا المنتج غير متوفر في المخزن (المتوفر: 0 ${product.majorUnit})"
            remainingAvailableEq <= 0.0 && typedEq > 0.0 -> "لا يمكن: استُنفدت الكمية المتوفرة للصنف في الفاتورة الحالية"
            isInsufficientStock -> "لا يمكن: الكمية غير كافية في المخزن (المتوفر: $availableFormatted)"
            else -> null
        }

        val priceErrorMessage = if (isPriceMissing) {
            "أدخل سعر الصنف أولاً"
        } else null

        return PosEntryValidationState(
            isOutOfStock = isOutOfStock,
            isInsufficientStock = isInsufficientStock,
            isPriceMissing = isPriceMissing,
            availableMajorEq = remainingAvailableEq,
            availableFormatted = availableFormatted,
            requestedMajorEq = typedEq,
            stockErrorMessage = stockErrorMessage,
            priceErrorMessage = priceErrorMessage
        )
    }

    fun buildPendingTypedCartItem(
        allProducts: List<ProductEntity>,
        selectedKey: String,
        majorStr: String,
        minorStr: String,
        priceStr: String,
        existingCart: List<CartItemUi> = emptyList()
    ): CartItemUi? {
        val prod = allProducts.find { it.key == selectedKey } ?: allProducts.firstOrNull() ?: return null
        if (prod.stock <= 0.0) return null

        val hasMinorUnit = prod.minorUnit.isNotBlank()
        val factor = if (prod.factor > 0.0) prod.factor else 2.0
        val rawMaj = majorStr.toDoubleOrNull() ?: 0.0
        val rawMin = if (hasMinorUnit) (minorStr.toDoubleOrNull() ?: 0.0) else 0.0
        if (rawMaj < 0.0 || rawMin < 0.0) return null

        val (maj, min) = LedgerCalculator.normalizeMajorMinorPair(rawMaj, rawMin, factor, hasMinorUnit)
        if (maj <= 0.0 && min <= 0.0) return null

        val price = LedgerCalculator.roundMoney(priceStr.toDoubleOrNull() ?: prod.price)
        // لا يمكن احتساب سعر افتراضي بـ 1 أو 0 إذا لم يتم إدخال تسعيرة واضحة للصنف
        if (price <= 0.0) return null

        val existingItem = existingCart.find { it.productKey == prod.key }
        val existingEq = if (existingItem != null) {
            LedgerCalculator.computeMajorEquivalent(existingItem.majorQty, existingItem.minorQty, factor, hasMinorUnit)
        } else 0.0
        val pendingEq = LedgerCalculator.computeMajorEquivalent(maj, min, factor, hasMinorUnit)
        if (LedgerCalculator.addStock(existingEq, pendingEq) > prod.stock + 1e-6) {
            return null
        }

        val unitDisplay = formatCombinedUnitsArabic(
            majorUnit = prod.majorUnit,
            minorUnit = prod.minorUnit,
            majorQty = maj,
            minorQty = min
        )
        val lineTotal = LedgerCalculator.computeLineTotal(
            majorQty = maj,
            minorQty = min,
            factor = factor,
            hasMinor = hasMinorUnit,
            majorUnitPrice = price
        )
        if (lineTotal <= 0.0) return null

        return CartItemUi(
            productKey = prod.key,
            productName = prod.name,
            majorUnit = prod.majorUnit,
            minorUnit = prod.minorUnit,
            factor = factor,
            majorQty = maj,
            minorQty = min,
            unitDisplay = unitDisplay,
            majorUnitPrice = price,
            total = lineTotal
        )
    }

    fun getEffectiveCartItems(
        cart: List<CartItemUi>,
        allProducts: List<ProductEntity>,
        selectedKey: String,
        majorStr: String,
        minorStr: String,
        priceStr: String
    ): List<CartItemUi> {
        val pending = buildPendingTypedCartItem(
            allProducts = allProducts,
            selectedKey = selectedKey,
            majorStr = majorStr,
            minorStr = minorStr,
            priceStr = priceStr,
            existingCart = cart
        ) ?: return cart
        val result = cart.toMutableList()
        val existingIdx = result.indexOfFirst { it.productKey == pending.productKey }
        if (existingIdx >= 0) {
            val ex = result[existingIdx]
            val hasMinor = pending.minorUnit.isNotBlank()
            val factor = if (pending.factor > 0.0) pending.factor else 2.0
            val sumMaj = LedgerCalculator.addStock(ex.majorQty, pending.majorQty)
            val sumMin = LedgerCalculator.addStock(ex.minorQty, pending.minorQty)
            val (finalMaj, finalMin) = LedgerCalculator.normalizeMajorMinorPair(sumMaj, sumMin, factor, hasMinor)
            val disp = formatCombinedUnitsArabic(pending.majorUnit, pending.minorUnit, finalMaj, finalMin)
            val mergedTotal = LedgerCalculator.computeLineTotal(
                majorQty = finalMaj,
                minorQty = finalMin,
                factor = factor,
                hasMinor = hasMinor,
                majorUnitPrice = pending.majorUnitPrice
            )
            result[existingIdx] = ex.copy(
                majorQty = finalMaj,
                minorQty = finalMin,
                unitDisplay = disp,
                majorUnitPrice = pending.majorUnitPrice,
                total = mergedTotal
            )
        } else {
            result.add(pending)
        }
        return result
    }

    fun mergeItemIntoCartList(
        currentList: List<CartItemUi>,
        prod: ProductEntity,
        maj: Double,
        min: Double,
        price: Double
    ): List<CartItemUi> {
        val mutable = currentList.toMutableList()
        val existingIdx = mutable.indexOfFirst { it.productKey == prod.key }
        val hasMinorUnit = prod.minorUnit.isNotBlank()
        val factor = if (prod.factor > 0.0) prod.factor else 2.0

        val cleanMaj = LedgerCalculator.roundStock(maj.coerceAtLeast(0.0))
        val cleanMin = if (hasMinorUnit) LedgerCalculator.roundStock(min.coerceAtLeast(0.0)) else 0.0
        val cleanPrice = LedgerCalculator.roundMoney(price.coerceAtLeast(0.0))

        val sumMaj = if (existingIdx >= 0) LedgerCalculator.addStock(mutable[existingIdx].majorQty, cleanMaj) else cleanMaj
        val sumMin = if (existingIdx >= 0) LedgerCalculator.addStock(mutable[existingIdx].minorQty, cleanMin) else cleanMin
        val (finalMaj, finalMin) = LedgerCalculator.normalizeMajorMinorPair(sumMaj, sumMin, factor, hasMinorUnit)

        val unitDisplay = formatCombinedUnitsArabic(
            majorUnit = prod.majorUnit,
            minorUnit = prod.minorUnit,
            majorQty = finalMaj,
            minorQty = finalMin
        )
        val lineTotal = LedgerCalculator.computeLineTotal(
            majorQty = finalMaj,
            minorQty = finalMin,
            factor = factor,
            hasMinor = hasMinorUnit,
            majorUnitPrice = cleanPrice
        )

        val newItem = CartItemUi(
            productKey = prod.key,
            productName = prod.name,
            majorUnit = prod.majorUnit,
            minorUnit = prod.minorUnit,
            factor = factor,
            majorQty = finalMaj,
            minorQty = finalMin,
            unitDisplay = unitDisplay,
            majorUnitPrice = cleanPrice,
            total = lineTotal
        )

        if (existingIdx >= 0) {
            mutable[existingIdx] = newItem
        } else {
            mutable.add(newItem)
        }
        return mutable
    }
}
