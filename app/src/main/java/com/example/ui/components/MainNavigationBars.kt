package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * شريط التنقل العلوي النحيف بسطر واحد فقط مع أيقونة الترس الصغيرة (⚙️)
 * وشريط الحفظ والمشاركة السفلي (مع وضع زر المدير في المقدمة لسهولة الوصول)
 */
@Composable
fun SlimNavigationStrip(
    activeTab: Int,
    totalRecordsCount: Int,
    hasManagerPhone: Boolean,
    onSelectTab: (Int) -> Unit,
    onOpenSettingsGear: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderTabButton(
                title = "🔴 فاتورة بيع",
                selected = activeTab == 0,
                activeColor = Color(0xFF0D9488),
                modifier = Modifier.weight(1.05f),
                onClick = { onSelectTab(0) }
            )
            HeaderTabButton(
                title = "📋 كشف اليومية",
                selected = activeTab == 1,
                activeColor = Color(0xFF2563EB),
                modifier = Modifier.weight(1.05f),
                onClick = { onSelectTab(1) }
            )
            HeaderTabButton(
                title = "🟢 التوريدات",
                selected = activeTab == 2,
                activeColor = Color(0xFF059669),
                modifier = Modifier.weight(0.95f),
                onClick = { onSelectTab(2) }
            )
            HeaderTabButton(
                title = "السجل ($totalRecordsCount)",
                selected = activeTab == 3,
                activeColor = Color(0xFF3B82F6),
                modifier = Modifier
                    .weight(0.95f)
                    .testTag("records_history_button"),
                onClick = { onSelectTab(3) }
            )

            Surface(
                color = if (hasManagerPhone) Color(0xFF065F46) else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (hasManagerPhone) Color(0xFF10B981) else Color(0xFF334155)),
                modifier = Modifier
                    .size(32.dp)
                    .testTag("settings_gear_button")
                    .clickable { onOpenSettingsGear() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "الإعدادات والنسخ الاحتياطي ورقم المدير",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderTabButton(
    title: String,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) activeColor else Color(0xFF1E293B))
            .clickable { onClick() }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFFCBD5E1),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PinnedSaveAndShareBottomBar(
    nextInvoiceNumber: String,
    lastSavedInvoiceNumber: String?,
    onSaveAndShareToCustomer: () -> Unit,
    onSaveAndShareToManager: () -> Unit,
    onSaveAndSharePdf: () -> Unit,
    onPrintLastInvoicePdf: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر المدير في الصدارة (أول زر يميناً) لسهولة وسرعة الوصول للمدير
            Button(
                onClick = onSaveAndShareToManager,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(40.dp)
                    .testTag("share_manager_whatsapp_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SupervisorAccount,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "إرسال للمدير ($nextInvoiceNumber)",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // زر العميل في الموضع الثاني
            Button(
                onClick = onSaveAndShareToCustomer,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1.05f)
                    .height(40.dp)
                    .testTag("share_customer_whatsapp_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "واتس العميل",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onSaveAndSharePdf,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("save_and_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "PDF",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PDF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (lastSavedInvoiceNumber != null) {
                IconButton(
                    onClick = onPrintLastInvoicePdf,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "طباعة آخر فاتورة",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

