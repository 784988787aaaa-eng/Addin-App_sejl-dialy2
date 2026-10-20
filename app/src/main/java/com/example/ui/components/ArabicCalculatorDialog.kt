package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.util.cleanNum

/**
 * الآلة الحاسبة العربية السريعة (الأزرار الحسابية على اليمين في متناول الإبهام الأيمن)
 */
@Composable
fun ArabicCalculatorDialog(
    targetFieldName: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onApplyValue: (String) -> Unit
) {
    var expression by remember {
        val initClean = initialValue.trim().let { if (it == "0" || it.isBlank()) "" else it }
        mutableStateOf(initClean)
    }

    val liveResult = remember(expression) { evaluateSimpleMathExpression(expression) }

    fun appendToken(token: String) {
        val isOperator = token in listOf("+", "-", "×", "÷")
        if (isOperator) {
            if (expression.isEmpty()) {
                if (token == "-") expression = "-"
                return
            }
            val lastChar = expression.last().toString()
            expression = if (lastChar in listOf("+", "-", "×", "÷")) {
                expression.dropLast(1) + token
            } else {
                expression + token
            }
        } else {
            expression += token
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "الحاسبة العربية السريعة — $targetFieldName",
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "أزرار الجمع والطرح على اليمين في متناول إبهامك الأيمن",
                                color = Color(0xFF94A3B8), fontSize = 10.sp
                            )
                        }
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Text("✕", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = expression.ifEmpty { "0" },
                                color = Color(0xFFCBD5E1), fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الإجمالي المحسوب:", color = Color(0xFF6EE7B7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = liveResult.cleanNum(),
                                    color = Color(0xFF10B981), fontSize = 24.sp,
                                    fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CalcKeyButton("÷", Color(0xFF334155), Color.White, height = 42.dp, fontSize = 22.sp) { appendToken("÷") }
                            CalcKeyButton("×", Color(0xFF2563EB), Color.White, height = 42.dp, fontSize = 22.sp) { appendToken("×") }
                            CalcKeyButton("−", Color(0xFFE11D48), Color.White, height = 52.dp, fontSize = 26.sp) { appendToken("-") }
                            CalcKeyButton("+", Color(0xFF059669), Color.White, height = 54.dp, fontSize = 26.sp) { appendToken("+") }
                            CalcKeyButton("=", Color(0xFFD97706), Color.White, height = 48.dp, fontSize = 24.sp) { expression = liveResult.cleanNum() }
                        }

                        Column(modifier = Modifier.weight(2.45f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CalcKeyButton("⌫ حذف", Color(0xFF475569), Color.White, Modifier.weight(1f), 46.dp) {
                                    if (expression.isNotEmpty()) expression = expression.dropLast(1)
                                }
                                CalcKeyButton("C تصفير", Color(0xFF7F1D1D), Color(0xFFFECACA), Modifier.weight(1f), 46.dp) { expression = "" }
                            }

                            listOf(
                                listOf("9", "8", "7"),
                                listOf("6", "5", "4"),
                                listOf("3", "2", "1")
                            ).forEach { rowDigits ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowDigits.forEach { d ->
                                        CalcKeyButton(d, Color(0xFF1E293B), Color.White, Modifier.weight(1f), 48.dp, 18.sp) { appendToken(d) }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CalcKeyButton("0", Color(0xFF1E293B), Color.White, Modifier.weight(1f), 48.dp, 18.sp) { appendToken("0") }
                                CalcKeyButton("00", Color(0xFF1E293B), Color(0xFF93C5FD), Modifier.weight(1f), 48.dp, 16.sp) { appendToken("00") }
                                CalcKeyButton(".", Color(0xFF1E293B), Color.White, Modifier.weight(1f), 48.dp, 18.sp) { appendToken(".") }
                            }
                        }
                    }

                    Button(
                        onClick = { onApplyValue(liveResult.cleanNum()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "✓ تنزيل الإجمالي (${liveResult.cleanNum()}) في حقل $targetFieldName",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKeyButton(
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 46.dp,
    fontSize: TextUnit = 14.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.height(height).clip(RoundedCornerShape(12.dp)).background(bgColor).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

fun evaluateSimpleMathExpression(expr: String): Double {
    val clean = expr.replace(" ", "").trim()
    if (clean.isEmpty()) return 0.0
    return try {
        val tokens = mutableListOf<String>()
        var numberBuffer = StringBuilder()
        for (i in clean.indices) {
            val c = clean[i]
            if (c.isDigit() || c == '.') {
                numberBuffer.append(c)
            } else if (c in listOf('+', '-', '×', '÷', '*', '/')) {
                if (i == 0 && c == '-') {
                    numberBuffer.append(c)
                } else {
                    if (numberBuffer.isNotEmpty()) {
                        tokens.add(numberBuffer.toString())
                        numberBuffer = StringBuilder()
                    }
                    tokens.add(c.toString())
                }
            }
        }
        if (numberBuffer.isNotEmpty()) tokens.add(numberBuffer.toString())
        if (tokens.isEmpty()) return 0.0
        if (tokens.last() in listOf("+", "-", "×", "÷", "*", "/")) tokens.removeAt(tokens.lastIndex)
        if (tokens.isEmpty()) return 0.0

        val pass1 = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if ((t == "×" || t == "*" || t == "÷" || t == "/") && pass1.isNotEmpty() && i + 1 < tokens.size) {
                val left = pass1.removeAt(pass1.lastIndex).toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                val right = tokens[i + 1].toBigDecimalOrNull() ?: java.math.BigDecimal.ONE
                val res = if (t == "×" || t == "*") {
                    left.multiply(right)
                } else if (right.compareTo(java.math.BigDecimal.ZERO) != 0) {
                    left.divide(right, 8, java.math.RoundingMode.HALF_UP)
                } else {
                    java.math.BigDecimal.ZERO
                }
                pass1.add(res.stripTrailingZeros().toPlainString())
                i += 2
            } else {
                pass1.add(t)
                i++
            }
        }

        var result = pass1.firstOrNull()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        var j = 1
        while (j + 1 < pass1.size) {
            val op = pass1[j]
            val nextVal = pass1[j + 1].toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            if (op == "+") result = result.add(nextVal) else if (op == "-") result = result.subtract(nextVal)
            j += 2
        }
        result.setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    } catch (e: Exception) {
        0.0
    }
}

