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
    CUSTOMER_WHATSAPP,
    MANAGER_WHATSAPP,
    PDF_SHARE
}

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

    fun buildPendingTypedCartItem(
        allProducts: List<ProductEntity>,
        selectedKey: String,
        majorStr: String,
        minorStr: String,
        priceStr: String
    ): CartItemUi? {
        val prod = allProducts.find { it.key == selectedKey } ?: allProducts.firstOrNull() ?: return null
        val hasMinorUnit = prod.minorUnit.isNotBlank()
        val factor = if (prod.factor > 0.0) prod.factor else 2.0
        val rawMaj = majorStr.toDoubleOrNull() ?: 0.0
        val rawMin = if (hasMinorUnit) (minorStr.toDoubleOrNull() ?: 0.0) else 0.0
        val (maj, min) = LedgerCalculator.normalizeMajorMinorPair(rawMaj, rawMin, factor, hasMinorUnit)
        if (maj <= 0.0 && min <= 0.0) return null

        val price = LedgerCalculator.roundMoney(priceStr.toDoubleOrNull() ?: prod.price)
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
        val pending = buildPendingTypedCartItem(allProducts, selectedKey, majorStr, minorStr, priceStr)
            ?: return cart
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

        val cleanMaj = LedgerCalculator.roundStock(maj)
        val cleanMin = if (hasMinorUnit) LedgerCalculator.roundStock(min) else 0.0
        val cleanPrice = LedgerCalculator.roundMoney(price)

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
