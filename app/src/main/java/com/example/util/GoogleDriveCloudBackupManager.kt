package com.example.util

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * بيانات الحساب المتصل رسمياً عبر Google Sign-In
 */
data class ConnectedGoogleAccountProfile(
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isDriveScopeGranted: Boolean = false
) {
    val isConnected: Boolean get() = email.isNotBlank()
}

/**
 * بيانات ملف النسخة الاحتياطية المخزنة فعلياً على Google Drive
 */
data class DriveBackupFileMetadata(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedTimeFormatted: String
) {
    val formattedSize: String
        get() = when {
            sizeBytes <= 0L -> "—"
            sizeBytes < 1024L -> "$sizeBytes B"
            sizeBytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

/**
 * معلومات التوقيع الرقمي للتطبيق لتسجيله في Google Cloud Console بسهولة
 */
data class ApkSigningConsoleInfo(
    val packageName: String,
    val sha1: String,
    val sha256: String
)

/**
 * نتيجة عمليات Google Drive السحابية الحقيقية
 */
sealed class DriveCloudResult<out T> {
    data class Success<T>(val data: T) : DriveCloudResult<T>()
    data class NeedsUserConsent(val consentIntent: Intent, val message: String) : DriveCloudResult<Nothing>()
    data class Error(val message: String, val isConsoleConfigError: Boolean = false) : DriveCloudResult<Nothing>()
}

/**
 * مدير المصادقة الحقيقية بحسابات Google والربط المباشر مع Google Drive REST API v3
 */
object GoogleDriveCloudBackupManager {

    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val OAUTH_SCOPE_STRING = "oauth2:$DRIVE_FILE_SCOPE $DRIVE_APPDATA_SCOPE"

    const val CLOUD_FOLDER_NAME = "السجل اليومي - النسخ الاحتياطي السحابي"
    const val CLOUD_BACKUP_FILE_NAME = "SmartTradeBook_CloudBackup.json"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * تحديد Web Client ID المعتمد (من المدخلات المخصصة، أو تلقائياً من سر النظام الآمن BuildConfig)
     */
    fun resolveWebClientId(webClientId: String = ""): String {
        val cleanClientId = webClientId.trim()
        if (cleanClientId.isNotBlank() && cleanClientId.endsWith(".apps.googleusercontent.com")) {
            return cleanClientId
        }
        val configClientId = try {
            com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        } catch (_: Throwable) {
            ""
        }
        if (configClientId.isNotBlank() && configClientId.endsWith(".apps.googleusercontent.com")) {
            return configClientId
        }
        return ""
    }

    /**
     * بناء عميل GoogleSignIn الرسمي الذي يفتح نافذة "اختيار حساب" الأصلية من خدمات Google Play
     * مع عرض صور الحسابات والأسماء والبريد الإلكتروني وخيار "إضافة حساب آخر"
     */
    fun getGoogleSignInClient(context: Context, webClientId: String = ""): GoogleSignInClient {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_FILE_SCOPE), Scope(DRIVE_APPDATA_SCOPE))

        val cleanClientId = resolveWebClientId(webClientId)
        if (cleanClientId.isNotBlank()) {
            gsoBuilder.requestIdToken(cleanClientId)
        }

        return GoogleSignIn.getClient(context.applicationContext, gsoBuilder.build())
    }

    /**
     * تجهيز Intent تسجيل الدخول مع ضمان إظهار نافذة اختيار الحسابات كاملة عند تبديل الحساب
     */
    suspend fun prepareSignInIntent(
        context: Context,
        webClientId: String = "",
        forceAccountChooser: Boolean = true
    ): Intent = withContext(Dispatchers.Main) {
        val client = getGoogleSignInClient(context, webClientId)
        if (forceAccountChooser) {
            suspendCancellableCoroutine { cont ->
                client.signOut().addOnCompleteListener {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
        client.signInIntent
    }

    /**
     * تسجيل الخروج الكامل من حساب Google وإلغاء ربط الجلسة الحالية
     */
    suspend fun signOutFromGoogle(context: Context, webClientId: String = "") = withContext(Dispatchers.Main) {
        val client = getGoogleSignInClient(context, webClientId)
        suspendCancellableCoroutine { cont ->
            client.signOut().addOnCompleteListener {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    /**
     * فحص آخر حساب Google تم تسجيل الدخول به فعلياً على الجهاز
     */
    fun getCurrentSignedInProfile(context: Context): ConnectedGoogleAccountProfile? {
        val account: GoogleSignInAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val email = account.email.orEmpty()
        if (email.isBlank()) return null
        val hasDrive = GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))
        return ConnectedGoogleAccountProfile(
            email = email,
            displayName = account.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
            photoUrl = account.photoUrl?.toString().orEmpty(),
            isDriveScopeGranted = hasDrive
        )
    }

    /**
     * قراءة نتيجة نافذة GoogleSignIn الأصلية وتحويل رموز الخطأ إلى رسائل تشخيصية واضحة
     */
    fun handleSignInActivityResult(data: Intent?): Result<ConnectedGoogleAccountProfile> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val email = account?.email.orEmpty()
            if (email.isBlank()) {
                Result.failure(IllegalStateException("لم يتم استلام البريد الإلكتروني من حساب Google المختار."))
            } else {
                val hasDrive = GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))
                Result.success(
                    ConnectedGoogleAccountProfile(
                        email = email,
                        displayName = account.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
                        photoUrl = account.photoUrl?.toString().orEmpty(),
                        isDriveScopeGranted = hasDrive
                    )
                )
            }
        } catch (e: ApiException) {
            val diagnostic = formatGoogleSignInApiException(e.statusCode)
            Result.failure(ApiExceptionWithDiagnostic(e.statusCode, diagnostic, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    class ApiExceptionWithDiagnostic(
        val statusCode: Int,
        override val message: String,
        cause: Throwable? = null
    ) : Exception(message, cause) {
        val isConsoleSetupRequired: Boolean
            get() = statusCode == 10 || statusCode == 12500
    }

    private fun formatGoogleSignInApiException(statusCode: Int): String {
        return when (statusCode) {
            12501, CommonStatusCodes.CANCELED ->
                "تم إغلاق نافذة اختيار الحساب قبل إتمام المصادقة."
            7, CommonStatusCodes.NETWORK_ERROR ->
                "تعذر الاتصال بخوادم Google. تحقق من اتصال الإنترنت وحاول مجدداً."
            10, CommonStatusCodes.DEVELOPER_ERROR ->
                "كود (10 - DEVELOPER_ERROR): نافذة حسابات Google تعمل بنجاح، ويلزم فقط إضافة بصمة الشهادة (SHA-1) واسم الحزمة المعروضين أدناه في Google Cloud Console وتفعيل Google Drive API."
            12500 ->
                "كود (12500 - SIGN_IN_FAILED): يلزم استكمال إعداد شاشة موافقة OAuth (OAuth Consent Screen) وتسجيل بصمة SHA-1 في Google Cloud Console."
            else ->
                "تعذر إكمال مصادقة Google Play Services (كود الحالة: $statusCode)."
        }
    }

    /**
     * الحصول على رمز وصول OAuth2 حقيقي لـ Google Drive API v3
     */
    private fun fetchAccessTokenBlocking(context: Context, email: String): String {
        val googleAccount = Account(email.trim(), "com.google")
        return GoogleAuthUtil.getToken(context.applicationContext, googleAccount, OAUTH_SCOPE_STRING)
    }

    /**
     * رفع ومزامنة النسخة الاحتياطية مباشرة إلى Google Drive REST API v3
     */
    suspend fun uploadBackupToDrive(
        context: Context,
        email: String,
        backupJson: String
    ): DriveCloudResult<DriveBackupFileMetadata> = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext DriveCloudResult.Error("يرجى تسجيل الدخول بحساب Google أولاً.")
        }
        try {
            var token = fetchAccessTokenBlocking(context, email)
            var uploadResult = executeMultipartUpload(token, backupJson)
            if (uploadResult.code == 401) {
                GoogleAuthUtil.clearToken(context.applicationContext, token)
                token = fetchAccessTokenBlocking(context, email)
                uploadResult = executeMultipartUpload(token, backupJson)
            }
            if (uploadResult.metadata != null) {
                DriveCloudResult.Success(uploadResult.metadata)
            } else {
                DriveCloudResult.Error(
                    message = uploadResult.errorMessage ?: "فشل رفع النسخة إلى Google Drive (كود ${uploadResult.code})",
                    isConsoleConfigError = uploadResult.code == 403
                )
            }
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                DriveCloudResult.NeedsUserConsent(
                    consentIntent = intent,
                    message = "يرجى منح صلاحية الوصول إلى Google Drive لحفظ النسخة الاحتياطية السحابية."
                )
            } else {
                DriveCloudResult.Error("يلزم منح صلاحية الوصول إلى Google Drive.")
            }
        } catch (e: SecurityException) {
            DriveCloudResult.Error(
                message = "صلاحية OAuth غير مفعلة لهذا التوقيع بعد. يرجى التأكد من تسجيل بصمة SHA-1 وتفعيل Google Drive API في Google Cloud Console.",
                isConsoleConfigError = true
            )
        } catch (e: Exception) {
            DriveCloudResult.Error(
                message = "تعذر الاتصال بـ Google Drive: ${e.localizedMessage ?: "خطأ في الشبكة"}"
            )
        }
    }

    /**
     * جلب معلومات أحدث نسخة احتياطية محفوظة فعلياً على Google Drive
     */
    suspend fun fetchLatestBackupMetadata(
        context: Context,
        email: String
    ): DriveCloudResult<DriveBackupFileMetadata?> = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext DriveCloudResult.Error("غير متصل بحساب Google.")
        }
        try {
            var token = fetchAccessTokenBlocking(context, email)
            var searchRes = findExistingBackupFile(token)
            if (searchRes.code == 401) {
                GoogleAuthUtil.clearToken(context.applicationContext, token)
                token = fetchAccessTokenBlocking(context, email)
                searchRes = findExistingBackupFile(token)
            }
            if (searchRes.code in 200..299) {
                DriveCloudResult.Success(searchRes.metadata)
            } else {
                DriveCloudResult.Error(
                    message = searchRes.errorMessage ?: "تعذر فحص ملفات Google Drive (كود ${searchRes.code})",
                    isConsoleConfigError = searchRes.code == 403
                )
            }
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                DriveCloudResult.NeedsUserConsent(
                    consentIntent = intent,
                    message = "يرجى السماح للتطبيق بالوصول إلى ملف النسخة الاحتياطية على Google Drive."
                )
            } else {
                DriveCloudResult.Error("يلزم منح صلاحية Google Drive.")
            }
        } catch (e: Exception) {
            DriveCloudResult.Error("تعذر قراءة حالة النسخة من Google Drive: ${e.localizedMessage ?: ""}")
        }
    }

    /**
     * تنزيل واستعادة ملف النسخة الاحتياطية من Google Drive REST API v3
     */
    suspend fun downloadBackupJsonFromDrive(
        context: Context,
        email: String,
        knownFileId: String? = null
    ): DriveCloudResult<Pair<String, DriveBackupFileMetadata>> = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext DriveCloudResult.Error("يرجى تسجيل الدخول بحساب Google أولاً لاستعادة النسخة السحابية.")
        }
        try {
            var token = fetchAccessTokenBlocking(context, email)
            var searchRes = findExistingBackupFile(token)
            if (searchRes.code == 401) {
                GoogleAuthUtil.clearToken(context.applicationContext, token)
                token = fetchAccessTokenBlocking(context, email)
                searchRes = findExistingBackupFile(token)
            }
            val meta = searchRes.metadata
            val targetId = meta?.fileId ?: knownFileId.orEmpty()
            if (targetId.isBlank()) {
                return@withContext DriveCloudResult.Error("لا توجد نسخة احتياطية مرفوعة سابقاً على هذا الحساب في Google Drive.")
            }

            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$targetId?alt=media")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext DriveCloudResult.Error(
                        message = parseGoogleApiError(bodyStr, resp.code),
                        isConsoleConfigError = resp.code == 403
                    )
                }
                if (bodyStr.isBlank() || !bodyStr.trim().startsWith("{")) {
                    return@withContext DriveCloudResult.Error("ملف النسخة المستلم من Google Drive فارغ أو غير صالح.")
                }
                val resolvedMeta = meta ?: DriveBackupFileMetadata(
                    fileId = targetId,
                    fileName = CLOUD_BACKUP_FILE_NAME,
                    sizeBytes = bodyStr.toByteArray(Charsets.UTF_8).size.toLong(),
                    modifiedTimeFormatted = getCurrentDateTimeArabic().let { "${it.dayName} ${it.date} (${it.time12h})" }
                )
                DriveCloudResult.Success(bodyStr to resolvedMeta)
            }
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                DriveCloudResult.NeedsUserConsent(
                    consentIntent = intent,
                    message = "يرجى منح صلاحية قراءة النسخة الاحتياطية من Google Drive."
                )
            } else {
                DriveCloudResult.Error("يلزم منح صلاحية الوصول إلى Google Drive.")
            }
        } catch (e: Exception) {
            DriveCloudResult.Error("تعذر تنزيل النسخة السحابية: ${e.localizedMessage ?: "خطأ في الاتصال"}")
        }
    }

    private data class InternalDriveResponse(
        val code: Int,
        val metadata: DriveBackupFileMetadata? = null,
        val errorMessage: String? = null
    )

    private fun findOrCreateBackupFolderId(token: String): String? {
        val query = "mimeType='application/vnd.google-apps.folder' and name='$CLOUD_FOLDER_NAME' and trashed=false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&spaces=drive&fields=files(id,name)&pageSize=1"

        val listReq = Request.Builder()
            .url(listUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(listReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val existingId = files.getJSONObject(0).optString("id")
                    if (existingId.isNotBlank()) return existingId
                }
            }
        }

        val createFolderJson = JSONObject().apply {
            put("name", CLOUD_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .addHeader("Authorization", "Bearer $token")
            .post(createFolderJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(createReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                return json.optString("id").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun findExistingBackupFile(token: String): InternalDriveResponse {
        val query = "name='$CLOUD_BACKUP_FILE_NAME' and trashed=false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&spaces=drive&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name,size,modifiedTime)"

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return InternalDriveResponse(
                    code = resp.code,
                    errorMessage = parseGoogleApiError(raw, resp.code)
                )
            }
            val root = JSONObject(raw.ifBlank { "{}" })
            val files = root.optJSONArray("files")
            if (files == null || files.length() == 0) {
                return InternalDriveResponse(code = 200, metadata = null)
            }
            val fileObj = files.getJSONObject(0)
            return InternalDriveResponse(
                code = 200,
                metadata = parseDriveFileMetadata(fileObj, 0L)
            )
        }
    }

    private fun executeMultipartUpload(token: String, backupJson: String): InternalDriveResponse {
        val existingSearch = findExistingBackupFile(token)
        if (existingSearch.code == 401 || existingSearch.code == 403) {
            return existingSearch
        }

        val existingFileId = existingSearch.metadata?.fileId
        val folderId = if (existingFileId == null) findOrCreateBackupFolderId(token) else null

        val metadataJson = JSONObject().apply {
            put("name", CLOUD_BACKUP_FILE_NAME)
            put("mimeType", "application/json")
            put("description", "نسخة احتياطية لتطبيق السجل اليومي")
            if (existingFileId == null && !folderId.isNullOrBlank()) {
                put("parents", JSONArray().put(folderId))
            }
        }

        val boundary = "SmartTradeDriveBoundary_${System.currentTimeMillis()}"
        val multipartBody = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(backupJson)
            append("\r\n--$boundary--\r\n")
        }

        val requestBody = multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val uploadUrl = if (!existingFileId.isNullOrBlank()) {
            "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart&fields=id,name,size,modifiedTime"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime"
        }

        val reqBuilder = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", "Bearer $token")

        val req = if (!existingFileId.isNullOrBlank()) {
            reqBuilder.patch(requestBody).build()
        } else {
            reqBuilder.post(requestBody).build()
        }

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return InternalDriveResponse(
                    code = resp.code,
                    errorMessage = parseGoogleApiError(raw, resp.code)
                )
            }
            val fileObj = JSONObject(raw.ifBlank { "{}" })
            val fallbackSize = backupJson.toByteArray(Charsets.UTF_8).size.toLong()
            return InternalDriveResponse(
                code = resp.code,
                metadata = parseDriveFileMetadata(fileObj, fallbackSize)
            )
        }
    }

    private fun parseDriveFileMetadata(fileObj: JSONObject, fallbackSize: Long): DriveBackupFileMetadata {
        val id = fileObj.optString("id", "")
        val name = fileObj.optString("name", CLOUD_BACKUP_FILE_NAME)
        val size = fileObj.optString("size", "").toLongOrNull() ?: fallbackSize
        val modifiedIso = fileObj.optString("modifiedTime", "")
        return DriveBackupFileMetadata(
            fileId = id,
            fileName = name,
            sizeBytes = size,
            modifiedTimeFormatted = formatIsoTimeArabic(modifiedIso)
        )
    }

    private fun formatIsoTimeArabic(iso: String): String {
        if (iso.isBlank()) {
            val dt = getCurrentDateTimeArabic()
            return "${dt.dayName} ${dt.date} (${dt.time12h})"
        }
        return try {
            val cleanIso = iso.replace(Regex("\\.\\d+Z$"), "Z")
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date: Date = parser.parse(cleanIso) ?: return iso
            val outFmt = SimpleDateFormat("yyyy-MM-dd | hh:mm a", Locale("ar")).apply {
                timeZone = TimeZone.getDefault()
            }
            outFmt.format(date)
        } catch (_: Exception) {
            val dt = getCurrentDateTimeArabic()
            "${dt.dayName} ${dt.date} (${dt.time12h})"
        }
    }

    private fun parseGoogleApiError(rawBody: String, httpCode: Int): String {
        return try {
            val errObj = JSONObject(rawBody).optJSONObject("error")
            val msg = errObj?.optString("message").orEmpty()
            when {
                httpCode == 403 && (msg.contains("Drive API", ignoreCase = true) || msg.contains("has not been used", ignoreCase = true) || msg.contains("disabled", ignoreCase = true)) ->
                    "يرجى تفعيل خدمة (Google Drive API) داخل مشروعك في Google Cloud Console ثم إعادة المحاولة."
                httpCode == 403 ->
                    "رفض الخادم الوصول (403): تأكد من تفعيل Google Drive API وإضافة نطاق drive.file في شاشة موافقة OAuth في Google Cloud Console."
                msg.isNotBlank() ->
                    "خطأ من خادم Google Drive ($httpCode): $msg"
                else ->
                    "تعذر إتمام العملية على Google Drive (رمز HTTP $httpCode)"
            }
        } catch (_: Exception) {
            "تعذر إتمام العملية على Google Drive (رمز HTTP $httpCode)"
        }
    }

    /**
     * استخراج بصمة شهادة التوقيع الفعلية (SHA-1 و SHA-256) واسم الحزمة للتطبيق المثبت حالياً
     * لمساعدة المستخدم في تسجيل التطبيق في Google Cloud Console بضغطة زر
     */
    @Suppress("DEPRECATION")
    fun extractApkSigningInfo(context: Context): ApkSigningConsoleInfo {
        val pkgName = context.packageName
        return try {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = pkgInfo.signingInfo
                val sigs = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                    else signingInfo.signingCertificateHistory
                } else null
                sigs?.firstOrNull()?.toByteArray()
            } else {
                val pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }

            if (certBytes != null) {
                val sha1 = MessageDigest.getInstance("SHA-1").digest(certBytes)
                    .joinToString(":") { "%02X".format(it) }
                val sha256 = MessageDigest.getInstance("SHA-256").digest(certBytes)
                    .joinToString(":") { "%02X".format(it) }
                ApkSigningConsoleInfo(packageName = pkgName, sha1 = sha1, sha256 = sha256)
            } else {
                ApkSigningConsoleInfo(packageName = pkgName, sha1 = "غير متاح", sha256 = "غير متاح")
            }
        } catch (_: Exception) {
            ApkSigningConsoleInfo(packageName = pkgName, sha1 = "غير متاح", sha256 = "غير متاح")
        }
    }
}
