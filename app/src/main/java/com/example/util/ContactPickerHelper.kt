package com.example.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class PickedContactInfo(
    val name: String,
    val phone: String
)

/**
 * مساعد احترافي لطلب إذن جهات الاتصال (READ_CONTACTS) وفتح سجل الهاتف واستخراج الاسم والرقم تلقائياً
 */
object ContactPickerHelper {

    fun normalizePhoneDigits(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.trim()) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '+' -> if (sb.isEmpty()) sb.append('+')
            }
        }
        return sb.toString()
    }

    fun extractContactFromUri(context: Context, contactUri: Uri): PickedContactInfo? {
        return try {
            val resolver = context.contentResolver
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            resolver.query(contactUri, phoneProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawPhone = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val cleanPhone = normalizePhoneDigits(rawPhone)
                    if (name.isNotBlank() || cleanPhone.isNotBlank()) {
                        return PickedContactInfo(name = name, phone = cleanPhone)
                    }
                }
            }

            // Fallback if URI is a general Contact URI rather than a Phone URI
            val contactProjection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
            )
            resolver.query(contactUri, contactProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val contactId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    var phone = ""
                    if (contactId.isNotBlank() &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    ) {
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val pIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (pIdx >= 0) {
                                    phone = normalizePhoneDigits(phoneCursor.getString(pIdx).orEmpty())
                                }
                            }
                        }
                    }
                    if (name.isNotBlank() || phone.isNotBlank()) {
                        return PickedContactInfo(name = name, phone = phone)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun launchSystemContactPhonePicker(
        context: Context,
        onLaunchIntent: (Intent) -> Unit
    ) {
        try {
            val phonePickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            onLaunchIntent(phonePickIntent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                onLaunchIntent(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر فتح جهات الاتصال على هذا الجهاز", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun rememberContactPickerLauncher(
    onContactPicked: (PickedContactInfo) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val pickerActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val info = ContactPickerHelper.extractContactFromUri(context, uri)
                if (info != null) {
                    onContactPicked(info)
                } else {
                    Toast.makeText(context, "تعذر قراءة بيانات جهة الاتصال المختارة", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ContactPickerHelper.launchSystemContactPhonePicker(context) { intent ->
                pickerActivityLauncher.launch(intent)
            }
        } else {
            Toast.makeText(
                context,
                "يرجى السماح بإذن جهات الاتصال لاختيار الاسم والرقم تلقائياً",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            ContactPickerHelper.launchSystemContactPhonePicker(context) { intent ->
                pickerActivityLauncher.launch(intent)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}
