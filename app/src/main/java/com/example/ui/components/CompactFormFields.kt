package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CalculatorTarget(val title: String) {
    PREV_DEBT("سداد دين سابق"),
    DISCOUNT("الخصم"),
    PAID("المسلّم نقداً"),
    UNIT_PRICE("سعر الوحدة")
}

@Composable
fun PaymentModelChip(
    title: String,
    selected: Boolean,
    activeBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) activeBg else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(
            width = if (selected) 1.6.dp else 1.dp,
            color = if (selected) activeBg else Color(0xFFE2E8F0)
        ),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (selected) {
                // خط إضاءة علوي دقيق جداً لا يأخذ أي مساحة من النص
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.55f)
                        .height(2.5.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                        )
                )
                // شارة زاوية ماسية/ركنية ذكية بعلامة صح دقيقة في الزاوية العلوية لا تأخذ أي حيز من النص
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(bottomEnd = 7.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(width = 14.dp, height = 13.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "✓",
                            color = activeBg,
                            fontSize = 8.5.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Text(
                text = title,
                color = if (selected) Color.White else Color(0xFF334155),
                fontSize = 9.5.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
fun QuickMiniPill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(25.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(7.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF334155),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun CompactInlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: String? = null,
    onLeadingIconClick: (() -> Unit)? = null,
    hasAttachedPhone: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (value.isNotEmpty()) Color(0xFF94A3B8) else Color(0xFFCBD5E1)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                if (onLeadingIconClick != null) {
                    Surface(
                        color = if (hasAttachedPhone) Color(0xFFDCFCE7) else Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hasAttachedPhone) Color(0xFF6EE7B7) else Color(0xFFBFDBFE)
                        ),
                        modifier = Modifier
                            .size(27.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onLeadingIconClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = leadingIcon, fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(text = leadingIcon, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = if (keyboardType == KeyboardType.Decimal || keyboardType == KeyboardType.Phone) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        }
                    ),
                    cursorBrush = SolidColor(Color(0xFF0D9488)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { onValueChange("") }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun CompactInlineFieldWithCalc(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onOpenCalc: () -> Unit,
    onImeDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (value.isNotEmpty()) Color(0xFF94A3B8) else Color(0xFFCBD5E1)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onOpenCalc() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🧮", fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF0D9488)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onImeDone() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { onValueChange("") }
                        .padding(3.dp)
                )
            }
        }
    }
}

