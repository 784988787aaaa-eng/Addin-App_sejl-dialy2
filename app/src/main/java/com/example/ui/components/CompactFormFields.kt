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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * نظام التصميم البصري الموحد لشاشة نقاط البيع (POS Design System Tokens)
 * يضمن هوية تجارية هادئة، متسقة، عالية التباين، وخالية من التشتت البصري
 */
object PosUiTokens {
    // الخلفيات والأسطح
    val ScreenBackground = Color(0xFFF1F5F9)
    val CardSurface = Color(0xFFFFFFFF)
    val SubtleSurface = Color(0xFFF8FAFC)
    val MutedSurface = Color(0xFFF1F5F9)
    val ElevatedDarkSurface = Color(0xFF0F172A)

    // الحدود والفواصل
    val CardBorder = Color(0xFFE2E8F0)
    val InputBorderIdle = Color(0xFFCBD5E1)
    val InputBorderFilled = Color(0xFF94A3B8)
    val InputBorderFocused = Color(0xFF0D9488)
    val DividerColor = Color(0xFFE2E8F0)

    // الألوان الوظيفية الموحدة
    val PrimaryAction = Color(0xFF0D9488)       // الإجراء الرئيسي (+ صنف / الاعتماد)
    val PrimaryBrand = Color(0xFF1E3A8A)        // اللون الرسمي الثانوي والتنظيمي
    val PositiveGreen = Color(0xFF047857)       // الحالات المالية الإيجابية والمسلّم نقداً
    val PositiveSoftBg = Color(0xFFECFDF5)
    val PositiveSoftBorder = Color(0xFFA7F3D0)

    val WarningAmber = Color(0xFFB45309)        // سداد دين سابق والتنبيهات المالية
    val WarningSoftBg = Color(0xFFFFFBEB)
    val WarningSoftBorder = Color(0xFFFDE68A)

    val DangerRed = Color(0xFFDC2626)           // الحذف والمتبقي (الديون)
    val DangerSoftBg = Color(0xFFFFF1F2)
    val DangerSoftBorder = Color(0xFFFECDD3)

    // ألوان النصوص
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF334155)
    val TextMuted = Color(0xFF64748B)
    val TextPlaceholder = Color(0xFF94A3B8)

    // الزوايا والأبعاد القياسية المضبوطة لمنع التمرير الرأسي
    val CardShape = RoundedCornerShape(10.dp)
    val FieldShape = RoundedCornerShape(8.dp)
    val ChipShape = RoundedCornerShape(8.dp)
    val BadgeShape = RoundedCornerShape(6.dp)

    val FieldHeight: Dp = 33.dp
    val PaymentChipHeight: Dp = 29.dp
    val QuickPillHeight: Dp = 21.dp
}

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
        color = if (selected) activeBg.copy(alpha = 0.08f) else PosUiTokens.SubtleSurface,
        shape = PosUiTokens.ChipShape,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) activeBg else PosUiTokens.CardBorder
        ),
        modifier = modifier
            .height(PosUiTokens.PaymentChipHeight)
            .clip(PosUiTokens.ChipShape)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // دائرة راديو احترافية صغيرة وواضحة طبقاً لنموذج (له / عليه)
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .border(
                        width = if (selected) 1.6.dp else 1.2.dp,
                        color = if (selected) activeBg else Color(0xFF94A3B8),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(activeBg)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.5.dp))

            Text(
                text = title,
                color = if (selected) PosUiTokens.TextPrimary else PosUiTokens.TextSecondary,
                fontSize = 9.5.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
            .height(PosUiTokens.QuickPillHeight)
            .clip(PosUiTokens.BadgeShape)
            .background(PosUiTokens.SubtleSurface)
            .border(1.dp, PosUiTokens.CardBorder, PosUiTokens.BadgeShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PosUiTokens.TextSecondary,
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
    onFocusStateChanged: (Boolean) -> Unit = {},
    fieldHeight: Dp = PosUiTokens.FieldHeight,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isFocused -> PosUiTokens.InputBorderFocused
        value.isNotEmpty() -> PosUiTokens.InputBorderFilled
        else -> PosUiTokens.InputBorderIdle
    }
    val borderWidth = if (isFocused) 1.5.dp else 1.dp
    val containerBg = if (isFocused) PosUiTokens.CardSurface else PosUiTokens.SubtleSurface

    Surface(
        color = containerBg,
        shape = PosUiTokens.FieldShape,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.height(fieldHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingIcon != null) {
                if (onLeadingIconClick != null) {
                    Surface(
                        color = if (hasAttachedPhone) PosUiTokens.PositiveSoftBg else Color(0xFFEFF6FF),
                        shape = PosUiTokens.BadgeShape,
                        border = BorderStroke(
                            1.dp,
                            if (hasAttachedPhone) PosUiTokens.PositiveSoftBorder else Color(0xFFBFDBFE)
                        ),
                        modifier = Modifier
                            .size(25.dp)
                            .clip(PosUiTokens.BadgeShape)
                            .clickable { onLeadingIconClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = leadingIcon, fontSize = 11.sp)
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
                        color = PosUiTokens.TextPlaceholder,
                        fontSize = 10.sp,
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
                        color = PosUiTokens.TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = if (keyboardType == KeyboardType.Decimal || keyboardType == KeyboardType.Phone) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        }
                    ),
                    cursorBrush = SolidColor(PosUiTokens.PrimaryAction),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (isFocused != state.isFocused) {
                                isFocused = state.isFocused
                                onFocusStateChanged(state.isFocused)
                            }
                        }
                )
            }
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = PosUiTokens.TextMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .clickable { onValueChange("") }
                        .padding(3.dp)
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
    imeAction: ImeAction = ImeAction.Done,
    onFocusStateChanged: (Boolean) -> Unit = {},
    fieldHeight: Dp = PosUiTokens.FieldHeight,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isFocused -> PosUiTokens.InputBorderFocused
        value.isNotEmpty() -> PosUiTokens.InputBorderFilled
        else -> PosUiTokens.InputBorderIdle
    }
    val borderWidth = if (isFocused) 1.5.dp else 1.dp
    val containerBg = if (isFocused) PosUiTokens.CardSurface else PosUiTokens.SubtleSurface

    Surface(
        color = containerBg,
        shape = PosUiTokens.FieldShape,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.height(fieldHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = PosUiTokens.MutedSurface,
                shape = PosUiTokens.BadgeShape,
                border = BorderStroke(1.dp, PosUiTokens.InputBorderIdle),
                modifier = Modifier
                    .size(23.dp)
                    .clip(PosUiTokens.BadgeShape)
                    .clickable { onOpenCalc() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🧮", fontSize = 10.sp)
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = PosUiTokens.TextPlaceholder,
                        fontSize = 9.5.sp,
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
                        color = PosUiTokens.TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(PosUiTokens.PrimaryAction),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeDone() },
                        onDone = { onImeDone() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (isFocused != state.isFocused) {
                                isFocused = state.isFocused
                                onFocusStateChanged(state.isFocused)
                            }
                        }
                )
            }

            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = PosUiTokens.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clickable { onValueChange("") }
                        .padding(2.dp)
                )
            }
        }
    }
}
