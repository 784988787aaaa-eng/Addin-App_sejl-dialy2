package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.SmartTradeViewModel
import com.example.util.ContactPickerHelper
import com.example.util.rememberContactPickerLauncher

/**
 * نافذة اعتماد رقم واتساب المدير السريعة
 */
@Composable
fun ManagerPhoneQuickDialog(
    currentManagerPhone: String,
    onSavePhone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var phoneText by remember(currentManagerPhone) { mutableStateOf(currentManagerPhone) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val launchManagerContactPicker = rememberContactPickerLauncher { picked ->
        if (picked.phone.isNotBlank()) {
            phoneText = picked.phone
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📞 اعتماد رقم واتساب المدير", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                    Text(
                        text = "يُعتمد هذا الرقم لإرسال تقارير الفواتير والتوريدات وكشف الحساب اليومي إلى محادثة المدير مباشرة:",
                        fontSize = 11.sp, color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = phoneText,
                        onValueChange = { phoneText = ContactPickerHelper.normalizePhoneDigits(it) },
                        label = { Text("رقم واتساب المدير (مثال: 777123456)", fontSize = 11.sp) },
                        trailingIcon = {
                            IconButton(
                                onClick = launchManagerContactPicker,
                                modifier = Modifier.testTag("quick_pick_manager_contact_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContactPhone,
                                    contentDescription = "اختيار رقم المدير من جهات الاتصال",
                                    tint = Color(0xFF2563EB)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A36A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text("✓ اعتماد الرقم", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmActionDialog(
            title = "تأكيد تعديل رقم المدير",
            message = "هل تريد حفظ واعتماد رقم واتساب المدير (${phoneText.ifBlank { "فارغ" }})؟",
            confirmText = "نعم، اعتماد",
            onConfirm = {
                onSavePhone(phoneText)
                onDismiss()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

/**
 * نافذة الإعدادات والنسخ الاحتياطي السحابي المطورة بمعيار عالمي
 */
@Composable
fun CloudAndLocalBackupDialog(
    viewModel: SmartTradeViewModel,
    connectedAccount: String,
    connectedAccountName: String = "",
    connectedAccountPhoto: String = "",
    lastAutoBackupTime: String,
    lastCloudBackupTime: String = "لم يرفع بعد",
    lastCloudBackupSize: String = "—",
    autoCloudSyncEnabled: Boolean = true,
    customWebClientId: String = "",
    totalRecordsCount: Int = 0,
    managerPhone: String,
    currency: String,
    userName: String,
    salesCounter: Int = 1001,
    supplyCounter: Int = 1001,
    initialFocusCloud: Boolean = false,
    onExportJsonToDevice: () -> Unit,
    onImportJsonFromDevice: () -> Unit,
    onOpenEditProducts: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var phoneInput by remember(managerPhone) { mutableStateOf(managerPhone) }
    var currencyInput by remember(currency) { mutableStateOf(currency) }
    var userNameInput by remember(userName) { mutableStateOf(userName) }
    var salesStartInput by remember(salesCounter) { mutableStateOf(salesCounter.toString()) }
    var supplyStartInput by remember(supplyCounter) { mutableStateOf(supplyCounter.toString()) }
    var showConfirmSaveSettings by remember { mutableStateOf(false) }
    var showConfirmResetAllData by remember { mutableStateOf(false) }
    var selectedSectionTab by remember(initialFocusCloud) { mutableIntStateOf(if (initialFocusCloud) 0 else 0) }

    val parsedSalesStart = salesStartInput.replace(Regex("[^0-9]"), "").toIntOrNull()?.coerceAtLeast(1) ?: 1001
    val parsedSupplyStart = supplyStartInput.replace(Regex("[^0-9]"), "").toIntOrNull()?.coerceAtLeast(1) ?: 1001

    val launchManagerContactPicker = rememberContactPickerLauncher { picked ->
        if (picked.phone.isNotBlank()) {
            phoneInput = picked.phone
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 740.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // شريط العنوان العلوي
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (selectedSectionTab == 0) Color(0xFF1E3A8A) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (selectedSectionTab == 0) Icons.Default.CloudSync else Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (selectedSectionTab == 0)
                                        "مركز النسخ الاحتياطي السحابي (Google)"
                                    else
                                        "إعدادات النظام والمدير والأصناف",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "ربط رسمي بحسابات Google ومزامنة Google Drive وإعدادات السجل",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // شريط التبويب المزدوج للتنقل السلس بين السحابة والإعدادات
                    Surface(
                        color = Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SettingsSegmentButton(
                                title = "☁️ النسخ السحابي (Google)",
                                selected = selectedSectionTab == 0,
                                badgeDotColor = if (connectedAccount.isNotBlank()) Color(0xFF10B981) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_cloud_backup_section"),
                                onClick = { selectedSectionTab = 0 }
                            )
                            SettingsSegmentButton(
                                title = "⚙️ الإعدادات والمدير",
                                selected = selectedSectionTab == 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_general_settings_section"),
                                onClick = { selectedSectionTab = 1 }
                            )
                        }
                    }

                    if (selectedSectionTab == 0) {
                        // تبويب النسخ الاحتياطي السحابي بمعيار عالمي (مفصول ومنظم بالكامل)
                        GoogleDriveCloudBackupSection(
                            viewModel = viewModel,
                            connectedAccount = connectedAccount,
                            connectedAccountName = connectedAccountName,
                            connectedAccountPhoto = connectedAccountPhoto,
                            lastAutoBackupTime = lastAutoBackupTime,
                            lastCloudBackupTime = lastCloudBackupTime,
                            lastCloudBackupSize = lastCloudBackupSize,
                            autoCloudSyncEnabled = autoCloudSyncEnabled,
                            customWebClientId = customWebClientId,
                            totalRecordsCount = totalRecordsCount,
                            onExportJsonToDevice = onExportJsonToDevice,
                            onImportJsonFromDevice = onImportJsonFromDevice
                        )
                    } else {
                        // شريط مختصر للوصول السريع لحالة الحساب السحابي من داخل الإعدادات
                        Surface(
                            color = if (connectedAccount.isNotBlank()) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (connectedAccount.isNotBlank()) Color(0xFF6EE7B7) else Color(0xFFBFDBFE)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSectionTab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    GoogleLogoCanvasIcon(modifier = Modifier.size(18.dp))
                                    Column {
                                        Text(
                                            text = if (connectedAccount.isNotBlank())
                                                "متصل بـ Google: ${connectedAccountName.ifBlank { connectedAccount }}"
                                            else
                                                "ربط حساب Google والنسخ الاحتياطي السحابي",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = if (connectedAccount.isNotBlank()) connectedAccount else "اضغط لفتح مركز السحابة واختيار حسابك الرسمي",
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                                Text(
                                    text = "فتح السحابة ←",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }
                    }

                    // قسم إعدادات المستخدم ورقم المدير والعملة والأصناف والتهيئة
                    // يظهر في تبويب الإعدادات أو أسفل السحابة لسهولة الوصول الشامل
                    if (selectedSectionTab == 1) {
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                    } else {
                        HorizontalDivider(color = Color(0xFFCBD5E1))
                    }

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "👤 بيانات المستخدم ورقم واتساب المدير والعملة",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            OutlinedTextField(
                                value = userNameInput,
                                onValueChange = { userNameInput = it },
                                label = { Text("اسم المستخدم (يظهر في تذييل الفواتير والتقارير وبيد المستخدم)", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_name_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = ContactPickerHelper.normalizePhoneDigits(it) },
                                    label = { Text("رقم واتساب المدير", fontSize = 10.5.sp) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = launchManagerContactPicker,
                                            modifier = Modifier.testTag("pick_manager_contact_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContactPhone,
                                                contentDescription = "اختيار رقم المدير من جهات الاتصال",
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .testTag("manager_phone_input")
                                )
                                OutlinedTextField(
                                    value = currencyInput,
                                    onValueChange = { currencyInput = it },
                                    label = { Text("العملة", fontSize = 10.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.75f)
                                )
                            }
                            Button(
                                onClick = { showConfirmSaveSettings = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("save_settings_button")
                            ) {
                                Text(
                                    text = "✓ حفظ اسم المستخدم ورقم المدير والإعدادات",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenEditProducts()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تعديل الأصناف والوحدات الكبرى والصغرى والأسعار",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        color = Color(0xFFFFF1F2),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔢 بدء ترقيم الفواتير (بيع / توريد) لمواصلة الرقم قبل أو بعد تهيئة النظام:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF881337)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = salesStartInput,
                                    onValueChange = { salesStartInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("رقم بداية البيع (A)", fontSize = 10.sp) },
                                    prefix = { Text("A", fontWeight = FontWeight.Black, color = Color(0xFFBE123C), fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sales_start_counter_input")
                                )
                                OutlinedTextField(
                                    value = supplyStartInput,
                                    onValueChange = { supplyStartInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("رقم بداية التوريد (B)", fontSize = 10.sp) },
                                    prefix = { Text("B", fontWeight = FontWeight.Black, color = Color(0xFF047857), fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("supply_start_counter_input")
                                )
                            }

                            Button(
                                onClick = { showConfirmResetAllData = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("reset_all_data_button")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تهيئة البيانات (محو كل شيء وبدء الترقيم من A$parsedSalesStart / B$parsedSupplyStart)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmSaveSettings) {
        ConfirmActionDialog(
            title = "تأكيد حفظ تعديلات الإعدادات",
            message = "هل أنت متأكد من حفظ واعتماد التعديلات على اسم المستخدم ورقم المدير والعملة وأرقام بداية الفواتير (بيع: A$parsedSalesStart | توريد: B$parsedSupplyStart)؟",
            confirmText = "نعم، حفظ الإعدادات",
            onConfirm = {
                viewModel.saveSettings(phoneInput, currencyInput, userNameInput, parsedSalesStart, parsedSupplyStart)
                onDismiss()
            },
            onDismiss = { showConfirmSaveSettings = false }
        )
    }

    if (showConfirmResetAllData) {
        ConfirmActionDialog(
            title = "⚠️ تأكيد تهيئة ومحو جميع البيانات",
            message = "هل أنت متأكد من تهيئة النظام؟ سيتم محو جميع الفواتير والتوريدات والعملاء وتصفير الأرصدة، وسيبدأ ترقيم الفواتير الجديدة من الرقم المحدد (بيع: A$parsedSalesStart | توريد: B$parsedSupplyStart).",
            confirmText = "نعم، تهيئة النظام",
            isDanger = true,
            onConfirm = {
                viewModel.resetAllApplicationData(context, parsedSalesStart, parsedSupplyStart)
                onDismiss()
            },
            onDismiss = { showConfirmResetAllData = false }
        )
    }
}

@Composable
private fun SettingsSegmentButton(
    title: String,
    selected: Boolean,
    badgeDotColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            if (badgeDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeDotColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                color = if (selected) Color(0xFF0F172A) else Color(0xFF475569)
            )
        }
    }
}
