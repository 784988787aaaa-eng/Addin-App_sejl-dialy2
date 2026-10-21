package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.data.SaleInvoiceEntity
import com.example.domain.DailyFinancialTotals
import com.example.domain.LedgerCalculator
import com.example.domain.ProductDailyStockStats
import com.example.util.cleanNum
import com.example.util.decodeInvoiceItemsMap

/**
 * شبكة جدول كشف الحساب اليومي ملء الصفحة مع تفعيل التفاف النص (Wrap Text)
 * ومرتبطة بالكامل بالحساب الموحد (UnifiedInvoiceAccounting) وبحث O(1) للأصناف
 */
@Composable
fun ColumnScope.DailyLedgerTableGrid(
    products: List<ProductEntity>,
    dayInvoices: List<SaleInvoiceEntity>,
    stockStats: Map<String, ProductDailyStockStats>,
    financialTotals: DailyFinancialTotals,
    currency: String,
    userName: String = "",
    currentPageIndex: Int,
    rowsPerPage: Int,
    horizontalScroll: ScrollState,
    verticalScroll: ScrollState,
    onClickInvoiceRow: (SaleInvoiceEntity) -> Unit
) {
    val cleanUser = userName.trim()
    val userHandTitle = if (cleanUser.isNotEmpty()) "إجمالي بيد المستخدم ($cleanUser)" else "إجمالي بيد المستخدم"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        val prodCols = products
        val colM = 28.dp
        val colName = 112.dp
        val colInvoiceTotal = 68.dp
        val colPaid = 88.dp
        val colRem = 68.dp
        val fixedColsWidth = colM + colName + colInvoiceTotal + colPaid + colRem
        val remainingScreenW = maxWidth - fixedColsWidth
        val calculatedProdW = if (prodCols.isNotEmpty()) {
            (remainingScreenW / prodCols.size).coerceAtLeast(62.dp)
        } else 64.dp

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScroll)
            ) {
                // الصف الأول: وارد بضاعة جديد
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    LedgerCell("", colM, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                    LedgerCell("وارد بضاعة جديد", colName, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                    prodCols.forEach { p ->
                        val suppliedDisp = stockStats[p.key]?.suppliedDisplay ?: ""
                        LedgerCell(suppliedDisp, calculatedProdW, Color(0xFFE2E8F0), Color(0xFF065F46), isBold = true)
                    }
                    LedgerCell(
                        text = "نقد الفواتير: ${financialTotals.totalCashReceived.cleanNum()}",
                        width = colInvoiceTotal + colPaid + colRem,
                        bgColor = Color(0xFFECFDF5),
                        textColor = Color(0xFF065F46),
                        isBold = true
                    )
                }

                // الصف الثاني: إجمالي المتبقي (من اليوم السابق)
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    LedgerCell("م", colM, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                    LedgerCell("إجمالي المتبقي", colName, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                    prodCols.forEach { p ->
                        val prevRemDisp = stockStats[p.key]?.prevRemainingDisplay ?: ""
                        LedgerCell(prevRemDisp, calculatedProdW, Color(p.colorBgHex), Color(p.colorTextHex), isBold = true)
                    }
                    LedgerCell(
                        text = "سداد دين سابق: ${financialTotals.totalPrevDebtNotified.cleanNum()}",
                        width = colInvoiceTotal + colPaid + colRem,
                        bgColor = Color(0xFFFFFBEB),
                        textColor = Color(0xFF92400E),
                        isBold = true
                    )
                }

                // الصف الثالث: ترويسة الأعمدة
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    LedgerCell("", colM, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                    LedgerCell("الإســــــــــــــــــــــــم", colName, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                    prodCols.forEach { p ->
                        val headerTitle = if (p.minorUnit.isNotBlank()) "${p.name}\n(${p.majorUnit}/${p.minorUnit})" else p.name
                        LedgerCell(headerTitle, calculatedProdW, Color(p.colorBgHex), Color(p.colorTextHex), isBold = true)
                    }
                    LedgerCell("إجمالي الفاتورة", colInvoiceTotal, Color(0xFFFEF3C7), Color(0xFF92400E), isBold = true)
                    LedgerCell("مسلم", colPaid, Color(0xFFF1F5F9), Color(0xFF0F172A), isBold = true)
                    LedgerCell("المتبقي", colRem, Color(0xFFDBEAFE), Color(0xFF1E3A8A), isBold = true)
                }

                // صفوف العمليات والعملاء للصفحة الحالية (20 سطراً لكل صفحة)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(verticalScroll)
                ) {
                    val startIdx = currentPageIndex * rowsPerPage
                    for (r in 0 until rowsPerPage) {
                        val globalIdx = startIdx + r
                        val inv = dayInvoices.getOrNull(globalIdx)
                        val acc = remember(inv) {
                            inv?.let { LedgerCalculator.computeUnifiedInvoiceAccounting(it) }
                        }
                        val decodedItemsMap = remember(inv?.itemsJson) {
                            if (inv != null) decodeInvoiceItemsMap(inv.itemsJson) else emptyMap()
                        }

                        val customerCellText = when {
                            inv == null -> ""
                            inv.suppliedGoodsNote.isNotBlank() -> "${inv.customerName}\n(${inv.suppliedGoodsNote.trim()})"
                            else -> inv.customerName
                        }
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .then(
                                    if (inv != null) Modifier.clickable { onClickInvoiceRow(inv) }
                                    else Modifier
                                )
                        ) {
                            LedgerCell("${globalIdx + 1}", colM, Color(0xFFCBD5E1), Color(0xFF0F172A), isBold = true)
                            LedgerCell(customerCellText, colName, Color(0xFFE2E8F0), Color(0xFF0F172A), isBold = true)
                            prodCols.forEach { p ->
                                val item = decodedItemsMap[p.key]
                                LedgerCell(
                                    text = item?.unitDisplay ?: "",
                                    width = calculatedProdW,
                                    bgColor = Color(p.colorBgHex),
                                    textColor = Color(p.colorTextHex),
                                    isBold = item != null
                                )
                            }
                            val invTotalStr = when {
                                acc == null || acc.netInvoiceSubtotal <= 0.0 -> ""
                                acc.discount > 0.0 -> "${acc.netInvoiceSubtotal.cleanNum()}\n(خصم: ${acc.discount.cleanNum()})"
                                else -> acc.netInvoiceSubtotal.cleanNum()
                            }
                            LedgerCell(
                                text = invTotalStr,
                                width = colInvoiceTotal,
                                bgColor = Color(0xFFFFFBEB),
                                textColor = Color(0xFF92400E),
                                isBold = true
                            )
                            val paidStr = acc?.paidCellText ?: ""
                            LedgerCell(paidStr, colPaid, Color.White, Color(0xFF047857), isBold = true)

                            val hasRem = acc != null && acc.remainingOnInvoice > 0.0
                            LedgerCell(
                                text = acc?.remainingOnInvoice?.cleanNum() ?: "",
                                width = colRem,
                                bgColor = if (hasRem) Color(0xFFFEF3C7) else Color(0xFFDBEAFE),
                                textColor = if (hasRem) Color(0xFFDC2626) else Color(0xFF1E3A8A),
                                isBold = true
                            )
                        }
                    }
                }

                // الصف السفلي 1: إجمالي البيع
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    LedgerCell("", colM, Color.White, Color.Black, isBold = true)
                    LedgerCell("إجمالي البيع", colName, Color(0xFFDCFCE7), Color(0xFF065F46), isBold = true)
                    prodCols.forEach { p ->
                        val soldDisp = stockStats[p.key]?.soldDisplay ?: ""
                        LedgerCell(soldDisp, calculatedProdW, Color(0xFFECFDF5), Color(0xFF047857), isBold = true)
                    }
                    val totalSubCell = when {
                        financialTotals.totalInvoicesSubtotal <= 0.0 -> "0"
                        financialTotals.totalDiscountGiven > 0.0 -> "${financialTotals.totalInvoicesSubtotal.cleanNum()}\n(خصم: ${financialTotals.totalDiscountGiven.cleanNum()})"
                        else -> financialTotals.totalInvoicesSubtotal.cleanNum()
                    }
                    LedgerCell(
                        text = totalSubCell,
                        width = colInvoiceTotal,
                        bgColor = Color(0xFFFEF3C7),
                        textColor = Color(0xFF92400E),
                        isBold = true
                    )
                    val paidSummaryCell = if (financialTotals.totalPrevDebtNotified > 0.0) {
                        "نقد: ${financialTotals.totalCashReceived.cleanNum()}\nسابق: ${financialTotals.totalPrevDebtNotified.cleanNum()}"
                    } else {
                        financialTotals.totalCashReceived.cleanNum()
                    }
                    LedgerCell(paidSummaryCell, colPaid, Color(0xFFECFDF5), Color(0xFF047857), isBold = true)
                    LedgerCell(
                        text = financialTotals.totalInvoicesRemaining.cleanNum(),
                        width = colRem,
                        bgColor = Color(0xFFFEF3C7),
                        textColor = Color(0xFFDC2626),
                        isBold = true
                    )
                }

                // الصف السفلي 2: متبقي اليوم = (الوارد + المتبقي السابق) - المباع
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    LedgerCell("", colM, Color.White, Color.Black, isBold = true)
                    LedgerCell("متبقي اليوم", colName, Color(0xFFDCFCE7), Color(0xFF065F46), isBold = true)
                    prodCols.forEach { p ->
                        val endRemDisp = stockStats[p.key]?.endRemainingDisplay ?: "0 ${p.majorUnit}"
                        LedgerCell(endRemDisp, calculatedProdW, Color(0xFFECFDF5), Color(0xFF065F46), isBold = true)
                    }
                    val bottomSummaryText = buildString {
                        append("$userHandTitle: ${financialTotals.totalCashInUserHand.cleanNum()} $currency\n")
                        append("ديون اليوم: ${financialTotals.totalInvoicesRemaining.cleanNum()} $currency")
                    }
                    LedgerCell(
                        text = bottomSummaryText,
                        width = colInvoiceTotal + colPaid + colRem,
                        bgColor = Color(0xFFDCFCE7),
                        textColor = Color(0xFF0F172A),
                        isBold = true
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerCell(
    text: String,
    width: Dp,
    bgColor: Color,
    textColor: Color,
    isBold: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .defaultMinSize(minHeight = 28.dp)
            .background(bgColor)
            .border(0.5.dp, Color(0xFF475569))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Medium,
            softWrap = true,
            textAlign = TextAlign.Center
        )
    }
}
