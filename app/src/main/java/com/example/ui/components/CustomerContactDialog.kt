package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SaleInvoiceEntity
import com.example.util.ContactPickerHelper
import com.example.util.rememberContactPickerLauncher

/**
 * نافذة أنيقة واحترافية لتعديل اسم العميل وإضافة أو تعديل رقم هاتفه المعتمد في كل التطبيق
 */
@Composable
fun EditCustomerContactDialog(
    invoice: SaleInvoiceEntity,
    initialPhone: String,
    onConfirmSave: (newCustomerName: String, newCustomerPhone: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(invoice.customerName) { mutableStateOf(invoice.customerName) }
    var phoneInput by remember(initialPhone) { mutableStateOf(initialPhone) }
    var showConfirmSave by remember { mutableStateOf(false) }

    val launchContactPicker = rememberContactPickerLauncher { picked ->
        if (picked.name.isNotBlank()) {
            nameInput = picked.name
        }
        if (picked.phone.isNotBlank()) {
            phoneInput = picked.phone
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFF0D9488),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "تعديل اسم ورقم هاتف العميل",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                        }

                        OutlinedButton(
                            onClick = launchContactPicker,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("dialog_pick_customer_contact_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContactPhone,
                                contentDescription = "جهات الاتصال",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "من جهات الاتصال",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E40AF)
                            )
                        }
                    }

                    Text(
                        text = "الرقم المعتمد هنا يُحفظ لهذا العميل تلقائياً ويُستخدم في زر (واتس العميل) في كل التطبيق:",
                        fontSize = 10.5.sp,
                        color = Color(0xFF475569),
                        lineHeight = 15.sp
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("اسم العميل", fontSize = 10.5.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_customer_name_field")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = ContactPickerHelper.normalizePhoneDigits(it) },
                        label = { Text("رقم هاتف العميل (إضافة أو تعديل)", fontSize = 10.5.sp) },
                        placeholder = { Text("مثال: 777123456", fontSize = 10.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = launchContactPicker,
                                modifier = Modifier.testTag("field_pick_customer_contact_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContactPhone,
                                    contentDescription = "اختيار من جهات الاتصال",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_customer_phone_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                        }
                        Button(
                            onClick = { showConfirmSave = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("save_customer_contact_button")
                        ) {
                            Text("✓ حفظ واعتماد الرقم", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmSave) {
        val finalName = nameInput.trim().ifBlank { invoice.customerName }
        val finalPhone = ContactPickerHelper.normalizePhoneDigits(phoneInput)
        ConfirmActionDialog(
            title = "تأكيد حفظ بيانات ورقم العميل",
            message = "هل أنت متأكد من اعتماد الاسم ($finalName) ورقم الهاتف (${finalPhone.ifBlank { "غير مسجل" }}) كرقم معتمد لهذا العميل في التطبيق؟",
            confirmText = "نعم، حفظ واعتماد",
            isDanger = false,
            onConfirm = {
                onConfirmSave(finalName, finalPhone)
                onDismiss()
            },
            onDismiss = { showConfirmSave = false }
        )
    }
}
