package qrmaster.web.calc

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

// Pure calculator/converter logic — a faithful port of the Android app's
// src/calc/CalcLogic.kt (same formulas, same defaults, same reference
// behaviour). The Android version uses java.math.BigDecimal /
// java.text.NumberFormat, which do not exist on Kotlin/Wasm, so the
// formatting below is hand-rolled to produce identical strings for the
// reference values (verified by JVM tests against the app's expectations).

object CalcFormat {
    /** Up to 6 decimal places, trailing zeros trimmed (reference style: 3.28084, 0.000621). */
    fun number(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        if (v == 0.0) return "0"
        val r6 = roundToPlaces(v, 6)
        if (r6 == 0.0) return sigFigString(v, 4)
        return plainDecimal(r6)
    }

    /** Currency result: 2 decimals + thousands grouping (US style). */
    fun money(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        val neg = v < 0
        val cents = round(abs(v) * 100.0).toLong()
        val intPart = cents / 100
        val frac = (cents % 100).toString().padStart(2, '0')
        return (if (neg) "-" else "") + groupLong(intPart) + "." + frac
    }

    /** Exchange-rate line: more precision for small rates. */
    fun rate(v: Double): String {
        if (v.isNaN() || v.isInfinite() || v == 0.0) return "—"
        return if (abs(v) >= 0.01) number(v) else sigFigString(v, 5)
    }

    /** Whole number with thousands grouping (US style: 7,884 / 11,352,960). */
    fun grouped(v: Long): String = groupLong(v)

    fun groupLong(v: Long): String {
        val neg = v < 0
        var s = (if (neg) -v else v).toString()
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count == 3 && i > 0) {
                sb.append(',')
                count = 0
            }
        }
        return (if (neg) "-" else "") + sb.reverse().toString()
    }

    /** Round half-up (ties toward +infinity, like the app) to [places] decimals. */
    fun roundToPlaces(v: Double, places: Int): Double {
        val f = 10.0.pow(places)
        return round(v * f) / f
    }

    /**
     * Plain (non-exponent) decimal string for a Double that has already been
     * rounded to a short decimal expansion. Handles the exponent form that
     * Double.toString produces for very large/small magnitudes.
     */
    fun plainDecimal(v: Double): String {
        if (v == 0.0) return "0"
        var s = v.toString()
        var neg = false
        if (s.startsWith("-")) { neg = true; s = s.substring(1) }
        val out: String
        val ePos = s.indexOf('E').let { if (it >= 0) it else s.indexOf('e') }
        if (ePos < 0) {
            out = s
        } else {
            val mant = s.substring(0, ePos)
            val exp = s.substring(ePos + 1).toInt()
            val dot = mant.indexOf('.')
            val intDigits = if (dot >= 0) mant.substring(0, dot) else mant
            val fracDigits = if (dot >= 0) mant.substring(dot + 1) else ""
            val digits = intDigits + fracDigits
            val pointPos = intDigits.length + exp
            out = when {
                pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
                pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length)
                else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
            }
        }
        var res = out
        if (res.contains('.')) {
            res = res.trimEnd('0').trimEnd('.')
        }
        if (res.isEmpty() || res == "-") res = "0"
        return (if (neg) "-" else "") + res
    }

    /** [n] significant digits, plain string (used when 6dp rounding would give 0). */
    fun sigFigString(v: Double, n: Int): String {
        if (v == 0.0) return "0"
        val neg = v < 0
        val a = abs(v)
        var exp = floor(kotlin.math.log10(a)).toInt()
        // correct possible log10 boundary errors
        var p = 10.0.pow(exp)
        while (a / p >= 10.0) { exp++; p *= 10.0 }
        while (a / p < 1.0) { exp--; p /= 10.0 }
        val shift = exp - n + 1
        val scaled = a / 10.0.pow(shift)
        var r = round(scaled)
        var e = exp
        if (r >= 10.0.pow(n)) { r /= 10.0; e++ }
        val digits = r.toLong().toString().padStart(n, '0')
        val pointPos = e + 1
        val body = when {
            pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
            pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length)
            else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
        }
        var res = body
        if (res.contains('.')) res = res.trimEnd('0').trimEnd('.')
        return (if (neg) "-" else "") + res
    }
}

// ── Age Calculator (port of the app's AgeLogic) ─────────────────────────────

data class Ymd(val y: Int, val m: Int, val d: Int) : Comparable<Ymd> {
    override fun compareTo(other: Ymd): Int =
        compareValuesBy(this, other, { it.y }, { it.m }, { it.d })

    fun display(): String {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "$d ${months.getOrElse(m - 1) { "" }} $y"
    }

    fun displayFull(): String = "${AgeLogic.MONTH_NAMES_FULL.getOrElse(m - 1) { "" }} $d, $y"
}

object AgeLogic {
    val MONTH_NAMES_FULL = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun isLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysInMonth(y: Int, m: Int): Int = when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeap(y)) 29 else 28
        else -> 0
    }

    fun isValid(y: Int, m: Int, d: Int): Boolean =
        m in 1..12 && d in 1..daysInMonth(y, m)

    fun ymdFromEpochDays(z0: Long): Ymd {
        val z = z0 + 719468
        val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Ymd((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    fun epochDaysFromYmd(ymd: Ymd): Long {
        val y = if (ymd.m <= 2) ymd.y - 1 else ymd.y
        val era = if (y >= 0) y / 400 else (y - 399) / 400
        val yoe = y - era * 400
        val mp = if (ymd.m > 2) ymd.m - 3 else ymd.m + 9
        val doy = (153 * mp + 2) / 5 + ymd.d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    fun daysBetween(a: Ymd, b: Ymd): Long = epochDaysFromYmd(b) - epochDaysFromYmd(a)

    fun addYearsClamped(ymd: Ymd, years: Int): Ymd {
        val ny = ymd.y + years
        return Ymd(ny, ymd.m, minOf(ymd.d, daysInMonth(ny, ymd.m)))
    }

    fun addMonthsClamped(ymd: Ymd, months: Int): Ymd {
        val total = ymd.y * 12 + (ymd.m - 1) + months
        val ny = floorDiv(total, 12)
        val nm = total - ny * 12 + 1
        return Ymd(ny, nm, minOf(ymd.d, daysInMonth(ny, nm)))
    }

    private fun floorDiv(a: Int, b: Int): Int {
        val q = a / b
        return if (a % b != 0 && (a < 0) != (b < 0)) q - 1 else q
    }

    data class AgeResult(
        val years: Int, val months: Int, val days: Int,
        val nextBirthday: Ymd, val countdownMonths: Int, val countdownDays: Int,
        val totalDaysToNext: Long, val isBirthdayToday: Boolean
    )

    fun computeAge(dob: Ymd, today: Ymd): AgeResult? {
        if (!isValid(dob.y, dob.m, dob.d) || dob > today) return null
        var years = today.y - dob.y
        if (addYearsClamped(dob, years) > today) years--
        val afterYears = addYearsClamped(dob, years)
        var months = 0
        while (months < 11 && addMonthsClamped(afterYears, months + 1) <= today) months++
        val afterMonths = addMonthsClamped(afterYears, months)
        val days = daysBetween(afterMonths, today).toInt()

        val birthdayToday = afterYears == today && months == 0 && days == 0
        val next = if (birthdayToday) today else addYearsClamped(dob, years + 1)
        val (cm, cd) = countdown(today, next)
        return AgeResult(years, months, days, next, cm, cd, daysBetween(today, next), birthdayToday)
    }

    fun countdown(from: Ymd, target: Ymd): Pair<Int, Int> {
        if (target <= from) return 0 to 0
        var tm = (target.y - from.y) * 12 + (target.m - from.m)
        while (tm > 0 && addMonthsClamped(target, -tm) < from) tm--
        val anchor = addMonthsClamped(target, -tm)
        return tm to daysBetween(from, anchor).toInt()
    }

    fun decomposeYmd(from: Ymd, to: Ymd): Triple<Int, Int, Int>? {
        if (!isValid(from.y, from.m, from.d) || !isValid(to.y, to.m, to.d) || to < from) return null
        var years = to.y - from.y
        if (addYearsClamped(from, years) > to) years--
        val afterYears = addYearsClamped(from, years)
        var months = 0
        while (months < 11 && addMonthsClamped(afterYears, months + 1) <= to) months++
        val afterMonths = addMonthsClamped(afterYears, months)
        val days = daysBetween(afterMonths, to).toInt()
        return Triple(years, months, days)
    }

    data class DetailedAge(
        val years: Int, val months: Int, val days: Int,
        val totalMonths: Int, val monthRemainDays: Int,
        val totalWeeks: Long, val weekRemainDays: Long,
        val totalDays: Long, val totalHours: Long,
        val totalMinutes: Long, val totalSeconds: Long
    )

    fun computeDetailed(dob: Ymd, target: Ymd): DetailedAge? {
        val (y, m, d) = decomposeYmd(dob, target) ?: return null
        val totalDays = daysBetween(dob, target)
        return DetailedAge(
            years = y, months = m, days = d,
            totalMonths = y * 12 + m, monthRemainDays = d,
            totalWeeks = totalDays / 7, weekRemainDays = totalDays % 7,
            totalDays = totalDays,
            totalHours = totalDays * 24,
            totalMinutes = totalDays * 1440,
            totalSeconds = totalDays * 86400
        )
    }

    fun weekdaySunday0(ymd: Ymd): Int {
        val z = epochDaysFromYmd(ymd)
        return (((z % 7 + 7) % 7 + 4) % 7).toInt()
    }

    fun shiftMonth(y: Int, m: Int, delta: Int): Ymd {
        val total = y * 12 + (m - 1) + delta
        val ny = floorDiv(total, 12)
        return Ymd(ny, total - ny * 12 + 1, 1)
    }

    fun monthsSpanInclusive(a: Ymd, b: Ymd): Int =
        (b.y - a.y) * 12 + (b.m - a.m) + 1

    fun shareText(dob: Ymd, target: Ymd, d: DetailedAge): String = buildString {
        appendLine("Age Calculator Result")
        appendLine("Date of Birth: ${dob.display()}")
        appendLine("Age at the Date of: ${target.display()}")
        appendLine("Age: ${d.years} years ${d.months} months ${d.days} days")
        appendLine("or ${CalcFormat.grouped(d.totalMonths.toLong())} months ${d.monthRemainDays} days")
        appendLine("or ${CalcFormat.grouped(d.totalWeeks)} weeks ${d.weekRemainDays} days")
        appendLine("or ${CalcFormat.grouped(d.totalDays)} days")
        appendLine("or ${CalcFormat.grouped(d.totalHours)} hours")
        appendLine("or ${CalcFormat.grouped(d.totalMinutes)} minutes")
        append("or ${CalcFormat.grouped(d.totalSeconds)} seconds")
    }
}

// ── BMI Calculator (port) ───────────────────────────────────────────────────

object BmiLogic {
    enum class Category(val label: String, val rangeText: String) {
        UNDERWEIGHT("Underweight", "< 18.5"),
        NORMAL("Normal", "18.5 – 24.9"),
        OVERWEIGHT("Overweight", "25 – 29.9"),
        OBESE("Obese", "≥ 30")
    }

    fun bmiMetric(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0 || heightCm <= 0 || weightKg.isNaN() || heightCm.isNaN()) return null
        val h = heightCm / 100.0
        return weightKg / (h * h)
    }

    fun bmiImperial(weightLb: Double, heightFt: Int, heightIn: Double): Double? {
        if (weightLb <= 0 || heightFt < 0 || heightIn < 0 || (heightFt == 0 && heightIn <= 0.0)) return null
        val kg = weightLb * 0.45359237
        val cm = (heightFt * 12 + heightIn) * 2.54
        return bmiMetric(kg, cm)
    }

    fun category(bmi: Double): Category = when {
        bmi < 18.5 -> Category.UNDERWEIGHT
        bmi < 25.0 -> Category.NORMAL
        bmi < 30.0 -> Category.OVERWEIGHT
        else -> Category.OBESE
    }

    /** One decimal, half-up (same as the app's BigDecimal HALF_UP). */
    fun display(bmi: Double): String {
        val t = round(bmi * 10.0).toLong()
        return "${t / 10}.${abs(t % 10)}"
    }

    fun note(cat: Category): String = when (cat) {
        Category.UNDERWEIGHT -> "You are below the healthy weight range."
        Category.NORMAL -> "Your weight is in a healthy range."
        Category.OVERWEIGHT -> "You are above the healthy weight range."
        Category.OBESE -> "You are well above the healthy weight range."
    }
}

// ── Unit Converter (port) ───────────────────────────────────────────────────

data class UnitDef(val id: String, val name: String, val symbol: String, val toBase: Double)

data class UnitCategory(
    val id: String, val name: String,
    val units: List<UnitDef>,
    val commonPairs: List<Pair<String, String>>
) {
    fun unit(id: String): UnitDef = units.first { it.id == id }
}

object UnitLogic {
    const val TEMP_ID = "temperature"

    val CATEGORIES: List<UnitCategory> = listOf(
        UnitCategory("length", "Length", listOf(
            UnitDef("mm", "Millimeter", "mm", 0.001),
            UnitDef("cm", "Centimeter", "cm", 0.01),
            UnitDef("m", "Meter", "m", 1.0),
            UnitDef("km", "Kilometer", "km", 1000.0),
            UnitDef("in", "Inch", "in", 0.0254),
            UnitDef("ft", "Foot", "ft", 0.3048),
            UnitDef("yd", "Yard", "yd", 0.9144),
            UnitDef("mi", "Mile", "mi", 1609.344)
        ), listOf("m" to "ft", "m" to "cm", "m" to "mm", "m" to "yd", "m" to "mi", "km" to "mi")),
        UnitCategory("weight", "Weight / Mass", listOf(
            UnitDef("g", "Gram", "g", 0.001),
            UnitDef("kg", "Kilogram", "kg", 1.0),
            UnitDef("t", "Metric Ton", "t", 1000.0),
            UnitDef("oz", "Ounce", "oz", 0.028349523125),
            UnitDef("lb", "Pound", "lb", 0.45359237),
            UnitDef("st", "Stone", "st", 6.35029318)
        ), listOf("kg" to "lb", "kg" to "g", "lb" to "kg", "oz" to "g", "t" to "kg")),
        UnitCategory("temperature", "Temperature", listOf(
            UnitDef("c", "Celsius", "°C", 1.0),
            UnitDef("f", "Fahrenheit", "°F", 1.0),
            UnitDef("k", "Kelvin", "K", 1.0)
        ), listOf("c" to "f", "c" to "k", "f" to "c")),
        UnitCategory("area", "Area", listOf(
            UnitDef("cm2", "Square Centimeter", "cm²", 0.0001),
            UnitDef("m2", "Square Meter", "m²", 1.0),
            UnitDef("ha", "Hectare", "ha", 10000.0),
            UnitDef("km2", "Square Kilometer", "km²", 1000000.0),
            UnitDef("in2", "Square Inch", "in²", 0.00064516),
            UnitDef("ft2", "Square Foot", "ft²", 0.09290304),
            UnitDef("acre", "Acre", "acre", 4046.8564224),
            UnitDef("mi2", "Square Mile", "mi²", 2589988.110336)
        ), listOf("m2" to "ft2", "ha" to "acre", "km2" to "mi2", "acre" to "m2")),
        UnitCategory("volume", "Volume", listOf(
            UnitDef("ml", "Milliliter", "mL", 0.001),
            UnitDef("l", "Liter", "L", 1.0),
            UnitDef("m3", "Cubic Meter", "m³", 1000.0),
            UnitDef("tsp", "Teaspoon (US)", "tsp", 0.00492892159375),
            UnitDef("tbsp", "Tablespoon (US)", "tbsp", 0.01478676478125),
            UnitDef("floz", "Fluid Ounce (US)", "fl oz", 0.0295735295625),
            UnitDef("cup", "Cup (US)", "cup", 0.2365882365),
            UnitDef("gal", "Gallon (US)", "gal", 3.785411784)
        ), listOf("l" to "ml", "gal" to "l", "cup" to "ml", "l" to "floz")),
        UnitCategory("speed", "Speed", listOf(
            UnitDef("ms", "Meters / Second", "m/s", 1.0),
            UnitDef("kmh", "Kilometers / Hour", "km/h", 1.0 / 3.6),
            UnitDef("fts", "Feet / Second", "ft/s", 0.3048),
            UnitDef("mph", "Miles / Hour", "mph", 0.44704),
            UnitDef("kn", "Knot", "kn", 0.514444444444)
        ), listOf("kmh" to "ms", "mph" to "kmh", "ms" to "kmh", "kn" to "kmh")),
        UnitCategory("time", "Time", listOf(
            UnitDef("ms", "Millisecond", "ms", 0.001),
            UnitDef("s", "Second", "s", 1.0),
            UnitDef("min", "Minute", "min", 60.0),
            UnitDef("h", "Hour", "h", 3600.0),
            UnitDef("day", "Day", "day", 86400.0),
            UnitDef("week", "Week", "week", 604800.0),
            UnitDef("year", "Year (365 days)", "yr", 31536000.0)
        ), listOf("h" to "min", "day" to "h", "week" to "day", "year" to "day")),
        UnitCategory("data", "Data Storage", listOf(
            UnitDef("bit", "Bit", "bit", 0.125),
            UnitDef("byte", "Byte", "B", 1.0),
            UnitDef("kb", "Kilobyte", "KB", 1024.0),
            UnitDef("mb", "Megabyte", "MB", 1048576.0),
            UnitDef("gb", "Gigabyte", "GB", 1073741824.0),
            UnitDef("tb", "Terabyte", "TB", 1099511627776.0)
        ), listOf("mb" to "kb", "gb" to "mb", "gb" to "kb", "tb" to "gb")),
        UnitCategory("pressure", "Pressure", listOf(
            UnitDef("pa", "Pascal", "Pa", 1.0),
            UnitDef("kpa", "Kilopascal", "kPa", 1000.0),
            UnitDef("bar", "Bar", "bar", 100000.0),
            UnitDef("atm", "Atmosphere", "atm", 101325.0),
            UnitDef("psi", "PSI", "psi", 6894.757293168),
            UnitDef("mmhg", "mmHg", "mmHg", 133.322387415)
        ), listOf("atm" to "kpa", "bar" to "psi", "atm" to "psi", "kpa" to "pa")),
        UnitCategory("energy", "Energy", listOf(
            UnitDef("j", "Joule", "J", 1.0),
            UnitDef("kj", "Kilojoule", "kJ", 1000.0),
            UnitDef("cal", "Calorie", "cal", 4.184),
            UnitDef("kcal", "Kilocalorie", "kcal", 4184.0),
            UnitDef("wh", "Watt Hour", "Wh", 3600.0),
            UnitDef("kwh", "Kilowatt Hour", "kWh", 3600000.0),
            UnitDef("btu", "BTU", "BTU", 1055.05585262)
        ), listOf("kcal" to "kj", "kwh" to "j", "cal" to "j", "btu" to "j"))
    )

    fun category(id: String): UnitCategory = CATEGORIES.first { it.id == id }

    fun convert(cat: UnitCategory, fromId: String, toId: String, value: Double): Double {
        if (cat.id == TEMP_ID) return convertTemperature(fromId, toId, value)
        val from = cat.unit(fromId)
        val to = cat.unit(toId)
        return value * from.toBase / to.toBase
    }

    fun convertTemperature(fromId: String, toId: String, value: Double): Double {
        val celsius = when (fromId) {
            "c" -> value
            "f" -> (value - 32.0) * 5.0 / 9.0
            "k" -> value - 273.15
            else -> value
        }
        return when (toId) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32.0
            "k" -> celsius + 273.15
            else -> celsius
        }
    }

    fun commonLines(cat: UnitCategory): List<String> = cat.commonPairs.map { (f, t) ->
        val fu = cat.unit(f)
        val tu = cat.unit(t)
        if (cat.id == TEMP_ID) {
            val base = if (f == "f") 32.0 else 0.0
            "${CalcFormat.number(base)} ${fu.symbol} = ${CalcFormat.number(convert(cat, f, t, base))} ${tu.symbol}"
        } else {
            "1 ${fu.symbol} = ${CalcFormat.number(convert(cat, f, t, 1.0))} ${tu.symbol}"
        }
    }
}

// ── Currency Converter (port) ───────────────────────────────────────────────

data class CurrencyDef(val code: String, val name: String, val flag: String)

object CurrencyLogic {
    val CURRENCIES: List<CurrencyDef> = listOf(
        CurrencyDef("INR", "Indian Rupee", "🇮🇳"),
        CurrencyDef("USD", "US Dollar", "🇺🇸"),
        CurrencyDef("EUR", "Euro", "🇪🇺"),
        CurrencyDef("GBP", "British Pound", "🇬🇧"),
        CurrencyDef("AED", "UAE Dirham", "🇦🇪"),
        CurrencyDef("SAR", "Saudi Riyal", "🇸🇦"),
        CurrencyDef("QAR", "Qatari Riyal", "🇶🇦"),
        CurrencyDef("KWD", "Kuwaiti Dinar", "🇰🇼"),
        CurrencyDef("OMR", "Omani Rial", "🇴🇲"),
        CurrencyDef("JPY", "Japanese Yen", "🇯🇵"),
        CurrencyDef("AUD", "Australian Dollar", "🇦🇺"),
        CurrencyDef("CAD", "Canadian Dollar", "🇨🇦"),
        CurrencyDef("SGD", "Singapore Dollar", "🇸🇬"),
        CurrencyDef("CHF", "Swiss Franc", "🇨🇭"),
        CurrencyDef("CNY", "Chinese Yuan", "🇨🇳"),
        CurrencyDef("HKD", "Hong Kong Dollar", "🇭🇰"),
        CurrencyDef("NZD", "New Zealand Dollar", "🇳🇿"),
        CurrencyDef("THB", "Thai Baht", "🇹🇭"),
        CurrencyDef("MYR", "Malaysian Ringgit", "🇲🇾"),
        CurrencyDef("IDR", "Indonesian Rupiah", "🇮🇩"),
        CurrencyDef("ZAR", "South African Rand", "🇿🇦"),
        CurrencyDef("PKR", "Pakistani Rupee", "🇵🇰"),
        CurrencyDef("BDT", "Bangladeshi Taka", "🇧🇩"),
        CurrencyDef("LKR", "Sri Lankan Rupee", "🇱🇰"),
        CurrencyDef("NPR", "Nepalese Rupee", "🇳🇵"),
        CurrencyDef("PHP", "Philippine Peso", "🇵🇭"),
        CurrencyDef("KRW", "South Korean Won", "🇰🇷"),
        CurrencyDef("TRY", "Turkish Lira", "🇹🇷"),
        CurrencyDef("RUB", "Russian Ruble", "🇷🇺"),
        CurrencyDef("BRL", "Brazilian Real", "🇧🇷")
    )

    data class RatesSnapshot(
        val rates: Map<String, Double>,
        val fetchedAtMillis: Long,
        val source: String
    )

    fun availableCurrencies(rates: Map<String, Double>): List<CurrencyDef> =
        CURRENCIES.filter { rates.containsKey(it.code) || it.code == "USD" }

    fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double? {
        val rf = rateOf(from, rates) ?: return null
        val rt = rateOf(to, rates) ?: return null
        if (rf == 0.0) return null
        return amount / rf * rt
    }

    fun crossRate(from: String, to: String, rates: Map<String, Double>): Double? {
        val rf = rateOf(from, rates) ?: return null
        val rt = rateOf(to, rates) ?: return null
        if (rf == 0.0) return null
        return rt / rf
    }

    private fun rateOf(code: String, rates: Map<String, Double>): Double? =
        if (code == "USD") 1.0 else rates[code]

    fun resolveRates(fetched: RatesSnapshot?, cached: RatesSnapshot?): Pair<RatesSnapshot, Boolean>? = when {
        fetched != null -> fetched to false
        cached != null -> cached to true
        else -> null
    }
}

// ── Percentage Calculator ───────────────────────────────────────────────────

object PercentLogic {
    /** "What is p% of n?" */
    fun percentOf(p: Double, n: Double): Double = n * p / 100.0

    /** "a is what percent of b?" — null when b == 0. */
    fun whatPercent(a: Double, b: Double): Double? = if (b == 0.0) null else a / b * 100.0

    /** Percent change from old to new — null when old == 0. */
    fun change(oldV: Double, newV: Double): Double? =
        if (oldV == 0.0) null else (newV - oldV) / abs(oldV) * 100.0
}

// ── SHA-256 (pure Kotlin; needed for device hashes + referral codes) ───────

object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    fun hex(input: String): String {
        val msg = input.encodeToByteArray()
        val bitLen = msg.size.toLong() * 8
        val paddedLen = ((msg.size + 9 + 63) / 64) * 64
        val buf = ByteArray(paddedLen)
        msg.copyInto(buf)
        buf[msg.size] = 0x80.toByte()
        for (i in 0 until 8) {
            buf[paddedLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        }
        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19
        val w = IntArray(64)
        var offset = 0
        while (offset < paddedLen) {
            for (t in 0 until 16) {
                val i = offset + t * 4
                w[t] = ((buf[i].toInt() and 0xFF) shl 24) or ((buf[i + 1].toInt() and 0xFF) shl 16) or
                    ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = rotr(w[t - 15], 7) xor rotr(w[t - 15], 18) xor (w[t - 15] ushr 3)
                val s1 = rotr(w[t - 2], 17) xor rotr(w[t - 2], 19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (t in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[t] + w[t]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }
            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
            offset += 64
        }
        val sb = StringBuilder(64)
        for (v in intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)) {
            sb.append(v.toUInt().toString(16).padStart(8, '0'))
        }
        return sb.toString()
    }

    /** First 40 hex chars — the app's device-hash format. */
    fun hex40(input: String): String = hex(input).substring(0, 40)

    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))
}
