package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.SmartTradeViewModel
import com.example.util.DriveBackupFileMetadata
import com.example.util.DriveCloudResult
import com.example.util.GoogleDriveCloudBackupManager
import com.example.util.PdfAndBackupHelper
import kotlinx.coroutines.launch

private enum class PendingCloudAction {
    NONE, UPLOAD, RESTORE, CHECK_STATUS
}

/**
 * واجهة النسخ الاحتياطي السحابي الاحترافية بمعيار عالمي (Google Sign-In الرسمي + Google Drive REST API v3)
 */
@Composable
fun GoogleDriveCloudBackupSection(
    viewModel: SmartTradeViewModel,
    connectedAccount: String,
    connectedAccountName: String,
    connectedAccountPhoto: String,
    lastAutoBackupTime: String,
    lastCloudBackupTime: String,
    lastCloudBackupSize: String,
    autoCloudSyncEnabled: Boolean,
    customWebClientId: String,
    totalRecordsCount: Int,
    onExportJsonToDevice: () -> Unit,
    onImportJsonFromDevice: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signingInfo = remember(context) { GoogleDriveCloudBackupManager.extractApkSigningInfo(context) }

    var isCloudBusy by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf("") }
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var showConsoleHelper by remember { mutableStateOf(false) }
    var webClientIdInput by remember(customWebClientId) { mutableStateOf(customWebClientId) }
    var pendingCloudAction by remember { mutableStateOf(PendingCloudAction.NONE) }
    var pendingCloudRestoreData by remember { mutableStateOf<Pair<String, DriveBackupFileMetadata>?>(null) }

    var executeUploadToDrive by remember { mutableStateOf<() -> Unit>({}) }
    var executeRestoreFromDrive by remember { mutableStateOf<() -> Unit>({}) }
    var executeCheckDriveStatus by remember { mutableStateOf<() -> Unit>({}) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            when (pendingCloudAction) {
                PendingCloudAction.UPLOAD -> executeUploadToDrive()
                PendingCloudAction.RESTORE -> executeRestoreFromDrive()
                PendingCloudAction.CHECK_STATUS -> executeCheckDriveStatus()
                PendingCloudAction.NONE -> {}
            }
        } else {
            isCloudBusy = false
            isStatusError = true
            statusBannerText = "لم يتم منح صلاحية الوصول إلى Google Drive."
        }
        pendingCloudAction = PendingCloudAction.NONE
    }

    executeUploadToDrive = {
        val activeEmail = viewModel.connectedAccount.value
        if (activeEmail.isNotBlank()) {
            scope.launch {
                isCloudBusy = true
                busyMessage = "جاري الاتصال بـ Google Drive ورفع النسخة الاحتياطية..."
                statusBannerText = null
                val json = viewModel.buildFullBackupString()
                when (val result = GoogleDriveCloudBackupManager.uploadBackupToDrive(context, activeEmail, json)) {
                    is DriveCloudResult.Success -> {
                        isCloudBusy = false
                        viewModel.recordCloudBackupSuccess(result.data)
                        isStatusError = false
                        statusBannerText = "تم رفع النسخة الاحتياطية إلى Google Drive بنجاح ✓ (${result.data.formattedSize})"
                        Toast.makeText(context, "تم النسخ الاحتياطي السحابي إلى Google Drive ✓", Toast.LENGTH_LONG).show()
                    }
                    is DriveCloudResult.NeedsUserConsent -> {
                        busyMessage = "بانتظار موافقة صلاحية Google Drive..."
                        pendingCloudAction = PendingCloudAction.UPLOAD
                        driveConsentLauncher.launch(result.consentIntent)
                    }
                    is DriveCloudResult.Error -> {
                        isCloudBusy = false
                        isStatusError = true
                        statusBannerText = result.message
                        if (result.isConsoleConfigError) showConsoleHelper = true
                    }
                }
            }
        }
    }

    executeRestoreFromDrive = {
        val activeEmail = viewModel.connectedAccount.value
        if (activeEmail.isNotBlank()) {
            scope.launch {
                isCloudBusy = true
                busyMessage = "جاري البحث عن النسخة السحابية في Google Drive وتنزيلها..."
                statusBannerText = null
                when (val result = GoogleDriveCloudBackupManager.downloadBackupJsonFromDrive(context, activeEmail)) {
                    is DriveCloudResult.Success -> {
                        isCloudBusy = false
                        viewModel.recordCloudBackupSuccess(result.data.second)
                        pendingCloudRestoreData = result.data
                    }
                    is DriveCloudResult.NeedsUserConsent -> {
                        busyMessage = "بانتظار موافقة قراءة Google Drive..."
                        pendingCloudAction = PendingCloudAction.RESTORE
                        driveConsentLauncher.launch(result.consentIntent)
                    }
                    is DriveCloudResult.Error -> {
                        isCloudBusy = false
                        isStatusError = true
                        statusBannerText = result.message
                        if (result.isConsoleConfigError) showConsoleHelper = true
                    }
                }
            }
        }
    }

    executeCheckDriveStatus = {
        val activeEmail = viewModel.connectedAccount.value
        if (activeEmail.isNotBlank()) {
            scope.launch {
                isCloudBusy = true
                busyMessage = "جاري فحص حالة النسخة السحابية على Google Drive..."
                when (val result = GoogleDriveCloudBackupManager.fetchLatestBackupMetadata(context, activeEmail)) {
                    is DriveCloudResult.Success -> {
                        isCloudBusy = false
                        val meta = result.data
                        if (meta != null) {
                            viewModel.recordCloudBackupSuccess(meta)
                            isStatusError = false
                            statusBannerText = "تم العثور على نسخة سحابية في Google Drive بتاريخ ${meta.modifiedTimeFormatted} (${meta.formattedSize}) ✓"
                        } else {
                            isStatusError = false
                            statusBannerText = "الحساب متصل بـ Google Drive بنجاح ✓ (لا توجد نسخة مرفوعة بعد، اضغط 'نسخ احتياطي الآن' لرفع أول نسخة)."
                        }
                    }
                    is DriveCloudResult.NeedsUserConsent -> {
                        pendingCloudAction = PendingCloudAction.CHECK_STATUS
                        driveConsentLauncher.launch(result.consentIntent)
                    }
                    is DriveCloudResult.Error -> {
                        isCloudBusy = false
                        isStatusError = true
                        statusBannerText = result.message
                        if (result.isConsoleConfigError) showConsoleHelper = true
                    }
                }
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signInRes = GoogleDriveCloudBackupManager.handleSignInActivityResult(result.data)
        signInRes.onSuccess { profile ->
            viewModel.updateConnectedGoogleProfile(profile)
            isStatusError = false
            statusBannerText = "تم تسجيل الدخول بحساب Google (${profile.email}) بنجاح ✓"
            when (pendingCloudAction) {
                PendingCloudAction.UPLOAD -> executeUploadToDrive()
                PendingCloudAction.RESTORE -> executeRestoreFromDrive()
                else -> executeCheckDriveStatus()
            }
            pendingCloudAction = PendingCloudAction.NONE
        }.onFailure { err ->
            isCloudBusy = false
            pendingCloudAction = PendingCloudAction.NONE
            isStatusError = true
            statusBannerText = err.message ?: "تعذر إتمام تسجيل الدخول بحساب Google."
            if (err is GoogleDriveCloudBackupManager.ApiExceptionWithDiagnostic && err.isConsoleSetupRequired) {
                showConsoleHelper = true
            }
        }
    }

    fun launchOfficialGoogleAccountPicker(afterSignInAction: PendingCloudAction = PendingCloudAction.NONE) {
        scope.launch {
            isCloudBusy = true
            busyMessage = "جاري فتح نافذة اختيار حساب Google الرسمية..."
            statusBannerText = null
            pendingCloudAction = afterSignInAction
            try {
                val signInIntent = GoogleDriveCloudBackupManager.prepareSignInIntent(
                    context = context,
                    webClientId = webClientIdInput,
                    forceAccountChooser = true
                )
                isCloudBusy = false
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                isCloudBusy = false
                isStatusError = true
                statusBannerText = "تعذر تشغيل خدمات Google Play على هذا الجهاز: ${e.localizedMessage ?: ""}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. بطاقة الهوية البصرية وحالة السحابة (Hero Cloud Status Banner)
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = if (connectedAccount.isNotBlank()) {
                                listOf(Color(0xFF064E3B), Color(0xFF0F766E), Color(0xFF1E3A8A))
                            } else {
                                listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                            }
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (connectedAccount.isNotBlank()) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "النسخ الاحتياطي السحابي (Google Drive)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = if (connectedAccount.isNotBlank())
                                        "مرتبط رسمياً بحساب Google وجاهز للمزامنة الفورية"
                                    else
                                        "اربط حساب Google لحفظ واستعادة سجلّك سحابياً بأمان",
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            color = if (connectedAccount.isNotBlank()) Color(0xFF10B981) else Color(0xFFF59E0B),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = if (connectedAccount.isNotBlank()) "متصل ✓" else "غير مرتبط",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // شريط مؤشرات النسخة السحابية
                    Surface(
                        color = Color.Black.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CloudMetricItem(
                                label = "آخر رفع على Drive",
                                value = lastCloudBackupTime,
                                modifier = Modifier.weight(1.3f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                            CloudMetricItem(
                                label = "حجم النسخة",
                                value = lastCloudBackupSize,
                                modifier = Modifier.weight(0.75f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                            CloudMetricItem(
                                label = "السجلات المحمية",
                                value = "$totalRecordsCount سجل",
                                modifier = Modifier.weight(0.85f)
                            )
                        }
                    }
                }
            }
        }

        // 2. بطاقة حساب Google الرسمية (تفتح نافذة "اختيار حساب" الحقيقية)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (connectedAccount.isNotBlank()) Color(0xFF10B981) else Color(0xFFCBD5E1)
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (connectedAccount.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GoogleAccountAvatarBadge(
                            displayName = connectedAccountName.ifBlank { connectedAccount },
                            photoUrl = connectedAccountPhoto
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = connectedAccountName.ifBlank { connectedAccount.substringBefore("@") },
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Google Account",
                                        color = Color(0xFF166534),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = connectedAccount,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { executeCheckDriveStatus() },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFFF1F5F9), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "فحص وتحديث حالة السحابة",
                                tint = Color(0xFF1E40AF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchOfficialGoogleAccountPicker(PendingCloudAction.NONE) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(38.dp)
                                .testTag("switch_google_account_button")
                        ) {
                            GoogleLogoCanvasIcon(modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تبديل الحساب",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E3A8A)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    GoogleDriveCloudBackupManager.signOutFromGoogle(context, webClientIdInput)
                                    viewModel.clearConnectedGoogleProfile()
                                    statusBannerText = "تم تسجيل الخروج من حساب Google."
                                    isStatusError = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBE123C)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(0.85f)
                                .height(38.dp)
                                .testTag("sign_out_google_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء الربط", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                GoogleLogoCanvasIcon(modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الربط الرسمي بحساب Google",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "يفتح نافذة اختيار حسابات Google الرسمية مع صور الحسابات وخيار إضافة حساب آخر",
                                fontSize = 10.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    // زر تسجيل الدخول الرسمي بمعيار Google Identity العالمي
                    Surface(
                        onClick = { launchOfficialGoogleAccountPicker(PendingCloudAction.NONE) },
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF2563EB)),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("google_sign_in_official_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GoogleLogoCanvasIcon(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "تسجيل الدخول باستخدام Google (اختيار حساب)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        // شريط التحميل أو رسائل الحالة التشخيصية
        AnimatedVisibility(visible = isCloudBusy) {
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF2563EB)
                    )
                    Text(
                        text = busyMessage.ifBlank { "جاري الاتصال بخدمات Google السحابية..." },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E3A8A)
                    )
                }
            }
        }

        AnimatedVisibility(visible = statusBannerText != null && !isCloudBusy) {
            statusBannerText?.let { msg ->
                Surface(
                    color = if (isStatusError) Color(0xFFFFF1F2) else Color(0xFFECFDF5),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isStatusError) Color(0xFFFDA4AF) else Color(0xFF6EE7B7)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isStatusError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isStatusError) Color(0xFFBE123C) else Color(0xFF047857),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = msg,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStatusError) Color(0xFF881337) else Color(0xFF065F46),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { statusBannerText = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق التنبيه",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. بطاقة عمليات النسخ والاستعادة السحابية المباشرة (Google Drive API v3)
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "☁️ المزامنة السحابية التلقائية إلى Google Drive",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "رفع تلقائي للنسخة الاحتياطية عند حفظ الفواتير أو التوريدات",
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = autoCloudSyncEnabled,
                        onCheckedChange = { viewModel.setAutoCloudSyncEnabled(it) }
                    )
                }

                // زر الرفع المباشر إلى Google Drive
                Button(
                    onClick = {
                        if (connectedAccount.isBlank()) {
                            launchOfficialGoogleAccountPicker(PendingCloudAction.UPLOAD)
                        } else {
                            executeUploadToDrive()
                        }
                    },
                    enabled = !isCloudBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("cloud_backup_now_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "نسخ احتياطي الآن إلى Google Drive",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                // زر الاستعادة المباشرة من Google Drive
                OutlinedButton(
                    onClick = {
                        if (connectedAccount.isBlank()) {
                            launchOfficialGoogleAccountPicker(PendingCloudAction.RESTORE)
                        } else {
                            executeRestoreFromDrive()
                        }
                    },
                    enabled = !isCloudBusy,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, Color(0xFF2563EB)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E40AF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("cloud_restore_from_drive_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "استعادة النسخة السحابية من Google Drive",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 4. بطاقة النسخ الاحتياطي المحلي ومشاركة الملف
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📱 النسخ الاحتياطي المحلي على الهاتف",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "تلقائي محلي: $lastAutoBackupTime",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onExportJsonToDevice,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ بالهاتف", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = onImportJsonFromDevice,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استعادة ملف", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = viewModel.buildFullBackupString()
                                PdfAndBackupHelper.shareCloudBackupFile(context, json)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 5. بوابة بيانات تسجيل التطبيق في Google Cloud Console (SHA-1 & Package Name)
        Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(11.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showConsoleHelper = !showConsoleHelper },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.size(17.dp)
                        )
                        Column {
                            Text(
                                text = "بيانات ربط Google Cloud Console (SHA-1 & Package)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "انسخ اسم الحزمة وبصمة SHA-1 بضغطة زر لتسجيل التطبيق في قوقل كنسل",
                                fontSize = 9.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showConsoleHelper) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF475569)
                    )
                }

                AnimatedVisibility(visible = showConsoleHelper) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        CopyableCredentialRow(
                            title = "Package Name (اسم الحزمة):",
                            value = signingInfo.packageName,
                            onCopy = { copyToClipboard(context, "Package Name", signingInfo.packageName) }
                        )

                        CopyableCredentialRow(
                            title = "SHA-1 Certificate Fingerprint (بصمة التوقيع):",
                            value = signingInfo.sha1,
                            onCopy = { copyToClipboard(context, "SHA-1", signingInfo.sha1) }
                        )

                        CopyableCredentialRow(
                            title = "SHA-256 Certificate Fingerprint:",
                            value = signingInfo.sha256,
                            onCopy = { copyToClipboard(context, "SHA-256", signingInfo.sha256) }
                        )

                        val isConfiguredViaSecrets = remember {
                            try {
                                com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() &&
                                com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")
                            } catch (_: Throwable) { false }
                        }

                        if (isConfiguredViaSecrets) {
                            Surface(
                                color = Color(0xFFECFDF5),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "تم ربط معرّف Web Client ID تلقائياً عبر الأسرار الآمنة (GitHub / .env) ✓",
                                        fontSize = 10.sp,
                                        color = Color(0xFF065F46),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = webClientIdInput,
                            onValueChange = {
                                webClientIdInput = it
                                viewModel.setCustomWebClientId(it)
                            },
                            label = { Text("Web Client ID (اختياري - ينتهي بـ apps.googleusercontent.com)", fontSize = 9.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "خطوات التفعيل في Google Cloud Console:\n" +
                                    "1. من قسم (APIs & Services -> Library) فعّل خدمة (Google Drive API).\n" +
                                    "2. من (OAuth consent screen) أضف النطاق (.../auth/drive.file).\n" +
                                    "3. من (Credentials -> Create Credentials -> OAuth client ID) اختر (Android) والصق اسم الحزمة وبصمة SHA-1 أعلاه.",
                                fontSize = 9.5.sp,
                                color = Color(0xFF1E3A8A),
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingCloudRestoreData?.let { (backupJson, meta) ->
        ConfirmActionDialog(
            title = "☁️ تأكيد استعادة النسخة السحابية من Google Drive",
            message = "تم العثور على نسخة احتياطية سحابية في حسابك ($connectedAccount) بتاريخ (${meta.modifiedTimeFormatted}) وحجم (${meta.formattedSize}). هل تريد استعادتها الآن وتحديث البيانات؟",
            confirmText = "نعم، استعادة من السحابة",
            onConfirm = {
                viewModel.restoreFromBackupJson(context, backupJson)
                pendingCloudRestoreData = null
            },
            onDismiss = { pendingCloudRestoreData = null }
        )
    }
}

@Composable
private fun CloudMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GoogleAccountAvatarBadge(
    displayName: String,
    photoUrl: String
) {
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFFEFF6FF))
            .border(
                width = 2.dp,
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF4285F4),
                        Color(0xFFEA4335),
                        Color(0xFFFBBC05),
                        Color(0xFF34A853),
                        Color(0xFF4285F4)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color(0xFF1D4ED8),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "صورة حساب Google",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * شعار Google الرسمي بألوانه الأربعة المرسوم بدقة عبر Canvas
 */
@Composable
fun GoogleLogoCanvasIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.21f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        // الأحمر (العلوي)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // الأصفر (الأيسر)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // الأخضر (السفلي)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // الأزرق (الأيمن + الخط الأفقي)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 52f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width - inset * 0.4f, size.height * 0.50f),
            strokeWidth = stroke * 0.92f,
            cap = StrokeCap.Butt
        )
    }
}

@Composable
private fun CopyableCredentialRow(
    title: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = value,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("نسخ", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "تم نسخ $label إلى الحافظة ✓", Toast.LENGTH_SHORT).show()
}
