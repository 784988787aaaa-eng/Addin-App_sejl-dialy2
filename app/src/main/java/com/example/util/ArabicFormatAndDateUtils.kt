package com.example.util

import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class InvoiceItemRecord(
    val key: String,
    val name: String,
    val unitDisplay: String,
    val majorQty: Double,
    val minorQty: Double,
    val unitPrice: Double,
    val total: Double
)

data class ArabicDateTimeInfo(
    val dayName: String,
    val date: String,
    val time12h: String,
    val timestamp: Long
)

private val dayNameCache = ConcurrentHashMap<String, String>(512)

private val decodedItemsCache = object : LruCache<String, List<InvoiceItemRecord>>(600) {
    override fun sizeOf(key: String, value: List<InvoiceItemRecord>): Int = 1
}

private val decodedItemsMapCache = object : LruCache<String, Map<String, InvoiceItemRecord>>(600) {
    override fun sizeOf(key: String, value: Map<String, InvoiceItemRecord>): Int = 1
}

/**
 * تنسيق رقمي موحد فائق الدقة يعتمد على BigDecimal لمنع أخطاء الفاصلة العائمة والترميز العلمي (1E7)
 * حتى مع المبالغ والكميات المليونية
 */
fun Double.cleanNum(): String {
    if (this.isNaN() || this.isInfinite()) return "0"
    return try {
        val bd = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        if (bd.compareTo(BigDecimal.ZERO) == 0) {
            "0"
        } else {
            bd.toPlainString()
        }
    } catch (_: Exception) {
        "0"
    }
}

fun getArabicDayName(dayOfWeek: Int): String = when (dayOfWeek) {
    Calendar.SUNDAY -> "الأحد"
    Calendar.MONDAY -> "الإثنين"
    Calendar.TUESDAY -> "الثلاثاء"
    Calendar.WEDNESDAY -> "الأربعاء"
    Calendar.THURSDAY -> "الخميس"
    Calendar.FRIDAY -> "الجمعة"
    Calendar.SATURDAY -> "السبت"
    else -> "اليوم"
}

fun getCurrentDateTimeArabic(timeMillis: Long = System.currentTimeMillis()): ArabicDateTimeInfo {
    val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    val dayName = getArabicDayName(cal.get(Calendar.DAY_OF_WEEK))
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dateStr = dateFormat.format(Date(timeMillis))
    dayNameCache[dateStr] = dayName

    var hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
    val amPm = if (hour >= 12) "م" else "ص"
    hour %= 12
    if (hour == 0) hour = 12
    val time12h = "$hour:$minute $amPm"

    return ArabicDateTimeInfo(
        dayName = dayName,
        date = dateStr,
        time12h = time12h,
        timestamp = timeMillis
    )
}

fun extractBaseDateFromSheetKey(sheetKey: String): String =
    sheetKey.substringBefore("#").trim()

fun extractSheetSequenceNumber(sheetKey: String): Int =
    if (sheetKey.contains("#")) (sheetKey.substringAfter("#").trim().toIntOrNull() ?: 1) else 1

fun formatSheetDateDisplay(sheetKey: String): String {
    val clean = sheetKey.trim()
    if (!clean.contains("#")) return clean
    val base = extractBaseDateFromSheetKey(clean)
    val seq = extractSheetSequenceNumber(clean)
    return if (seq > 1) "$base (سجل $seq)" else base
}

/**
 * استخراج التاريخ الميلادي الفعلي الدقيق للمعاملة (سواء قبل 12 ليلاً أو بعد 12 ليلاً)
 * اعتماداً على الطابع الزمني الحقيقي للمعاملة مع الحفاظ على التوافق التام مع بيانات الاختبارات
 */
fun resolveActualTransactionDate(storedSheetDate: String, timestamp: Long): String {
    if (timestamp > 1_000_000_000_000L) {
        return getCurrentDateTimeArabic(timestamp).date
    }
    return extractBaseDateFromSheetKey(storedSheetDate)
}

/**
 * توليد مفتاح السجل المحاسبي الجديد الصافي عند ضغط المستخدم على زر «إغلاق اليوم»
 */
fun computeNextOpenSheetKey(
    closingSheetKey: String,
    currentClockDate: String,
    existingOrClosedSheetKeys: Collection<String>
): String {
    val closingBaseDate = extractBaseDateFromSheetKey(closingSheetKey)
    val targetBaseDate = if (currentClockDate > closingBaseDate) currentClockDate else closingBaseDate
    val usedSet = existingOrClosedSheetKeys.toHashSet()
    usedSet.add(closingSheetKey.trim())

    if (!usedSet.contains(targetBaseDate) && targetBaseDate != closingSheetKey.trim()) {
        return targetBaseDate
    }
    var seq = if (targetBaseDate == closingBaseDate) {
        extractSheetSequenceNumber(closingSheetKey) + 1
    } else {
        2
    }
    while (seq < 1000) {
        val candidate = "$targetBaseDate#$seq"
        if (!usedSet.contains(candidate)) {
            return candidate
        }
        seq++
    }
    return "$targetBaseDate#${System.currentTimeMillis() % 10000}"
}

fun getDayNameFromDateStr(dateStr: String): String {
    val baseDate = extractBaseDateFromSheetKey(dateStr)
    if (baseDate.isBlank()) return "اليوم"
    dayNameCache[baseDate]?.let { return it }
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(baseDate) ?: return "اليوم"
        val cal = Calendar.getInstance().apply { time = parsed }
        val resolved = getArabicDayName(cal.get(Calendar.DAY_OF_WEEK))
        if (dayNameCache.size > 2000) dayNameCache.clear()
        dayNameCache[baseDate] = resolved
        resolved
    } catch (e: Exception) {
        "اليوم"
    }
}

fun shiftDateString(dateStr: String, daysDelta: Int): String {
    val baseDate = extractBaseDateFromSheetKey(dateStr)
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(baseDate) ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, daysDelta)
        }
        sdf.format(cal.time)
    } catch (e: Exception) {
        baseDate
    }
}

fun getArabicMonthName(monthOneBased: Int): String = when (monthOneBased) {
    1 -> "يناير"
    2 -> "فبراير"
    3 -> "مارس"
    4 -> "أبريل"
    5 -> "مايو"
    6 -> "يونيو"
    7 -> "يوليو"
    8 -> "أغسطس"
    9 -> "سبتمبر"
    10 -> "أكتوبر"
    11 -> "نوفمبر"
    12 -> "ديسمبر"
    else -> "شهر $monthOneBased"
}

/**
 * إرجاع (تاريخ البداية YYYY-MM-01 ، تاريخ النهاية YYYY-MM-lastDay) لأي سنة وشهر (1..12)
 */
fun getMonthDateRange(year: Int, monthOneBased: Int): Pair<String, String> {
    val cleanMonth = monthOneBased.coerceIn(1, 12)
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, cleanMonth - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val startStr = String.format(Locale.US, "%04d-%02d-01", year, cleanMonth)
    val endStr = String.format(Locale.US, "%04d-%02d-%02d", year, cleanMonth, maxDay)
    return startStr to endStr
}

fun formatQtyWithUnitArabic(qty: Double, unitName: String): String {
    val cleanU = unitName.trim()
    val qStr = qty.cleanNum()
    if (cleanU.isEmpty()) return qStr
    if (cleanU == "حبة" || cleanU == "حبه") {
        return when {
            qty == 1.0 -> "1 حبة"
            qty == 2.0 -> "2 حبتين"
            qty in 3.0..10.0 && qty % 1.0 == 0.0 -> "$qStr حبات"
            else -> "$qStr حبة"
        }
    }
    if (cleanU == "فراسلة" || cleanU == "فراسله") {
        return when {
            qty in 3.0..10.0 && qty % 1.0 == 0.0 -> "$qStr فراسل"
            else -> "$qStr فراسلة"
        }
    }
    if (cleanU == "ثمن") {
        return when {
            qty in 3.0..10.0 && qty % 1.0 == 0.0 -> "$qStr أثمان"
            else -> "$qStr ثمن"
        }
    }
    if (cleanU == "كرتون" && qty in 3.0..10.0 && qty % 1.0 == 0.0) {
        return "$qStr كراتين"
    }
    if (cleanU == "شدة" && qty in 3.0..10.0 && qty % 1.0 == 0.0) {
        return "$qStr شدات"
    }
    return "$qStr $cleanU"
}

fun formatCombinedUnitsArabic(
    majorUnit: String,
    minorUnit: String,
    majorQty: Double,
    minorQty: Double
): String {
    val mUnit = majorUnit.trim().ifBlank { "كيلو" }
    val sUnit = minorUnit.trim()
    val hasMajor = majorQty > 0.0
    val hasMinor = minorQty > 0.0 && sUnit.isNotEmpty()

    return when {
        hasMajor && hasMinor -> {
            val majorPart = formatQtyWithUnitArabic(majorQty, mUnit)
            if ((sUnit == "نص كيلو" || sUnit == "نصف كيلو" || sUnit == "نص") && minorQty == 1.0) {
                "$majorPart ونص"
            } else {
                val minorPart = formatQtyWithUnitArabic(minorQty, sUnit)
                "$majorPart و $minorPart"
            }
        }
        hasMajor -> formatQtyWithUnitArabic(majorQty, mUnit)
        hasMinor -> {
            if ((sUnit == "نص كيلو" || sUnit == "نصف كيلو" || sUnit == "نص") && minorQty == 1.0) {
                "نص $mUnit"
            } else {
                formatQtyWithUnitArabic(minorQty, sUnit)
            }
        }
        else -> "0 $mUnit"
    }
}

fun encodeInvoiceItemsJson(items: List<InvoiceItemRecord>): String {
    val arr = JSONArray()
    items.forEach { item ->
        val obj = JSONObject().apply {
            put("key", item.key)
            put("name", item.name)
            put("unitDisplay", item.unitDisplay)
            put("majorQty", item.majorQty)
            put("minorQty", item.minorQty)
            put("unitPrice", item.unitPrice)
            put("total", item.total)
        }
        arr.put(obj)
    }
    return arr.toString()
}

fun decodeInvoiceItemsJson(json: String): List<InvoiceItemRecord> {
    if (json.isBlank() || json == "[]") return emptyList()
    synchronized(decodedItemsCache) {
        decodedItemsCache.get(json)?.let { return it }
    }
    return try {
        val arr = JSONArray(json)
        val len = arr.length()
        if (len == 0) return emptyList()
        val list = ArrayList<InvoiceItemRecord>(len)
        for (i in 0 until len) {
            val obj = arr.getJSONObject(i)
            list.add(
                InvoiceItemRecord(
                    key = obj.optString("key", ""),
                    name = obj.optString("name", ""),
                    unitDisplay = obj.optString("unitDisplay", ""),
                    majorQty = obj.optDouble("majorQty", 0.0),
                    minorQty = obj.optDouble("minorQty", 0.0),
                    unitPrice = obj.optDouble("unitPrice", 0.0),
                    total = obj.optDouble("total", 0.0)
                )
            )
        }
        synchronized(decodedItemsCache) {
            decodedItemsCache.put(json, list)
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * استرجاع أصناف الفاتورة مفهرسةً بمفتاح الصنف O(1) لسرعة فائقة في الجداول والتقارير
 */
fun decodeInvoiceItemsMap(json: String): Map<String, InvoiceItemRecord> {
    if (json.isBlank() || json == "[]") return emptyMap()
    synchronized(decodedItemsMapCache) {
        decodedItemsMapCache.get(json)?.let { return it }
    }
    val list = decodeInvoiceItemsJson(json)
    if (list.isEmpty()) return emptyMap()
    val map = list.associateBy { it.key }
    synchronized(decodedItemsMapCache) {
        decodedItemsMapCache.put(json, map)
    }
    return map
}
