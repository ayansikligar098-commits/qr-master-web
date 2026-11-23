package qrmaster.web.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.floor
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import qrmaster.web.data.FirebaseApi
import qrmaster.web.data.HistoryItem
import qrmaster.web.data.Profile
import qrmaster.web.data.TaskItem
import qrmaster.web.data.nowMillis
import qrmaster.web.qr.QrCode
import qrmaster.web.qr.QrEcc

/* ---------------- shared helpers ---------------- */

fun floorDivLong(a: Long, b: Long): Long {
    val q = a / b
    return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
}

fun todayIst(): String {
    val ist = nowMillis() + (5 * 60 + 30) * 60000L
    val days = floorDivLong(ist, 86400000L)
    val (y, m, d) = civilFromDays(days)
    return y.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')
}

fun civilFromDays(zIn: Long): Triple<Long, Long, Long> {
    val z = zIn + 719468L
    val era = floorDivLong(z, 146097L)
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    if (m <= 2) y += 1
    return Triple(y, m, d)
}

fun fmtDateTime(ms: Long): String {
    if (ms <= 0) return ""
    val ist = ms + (5 * 60 + 30) * 60000L
    val days = floorDivLong(ist, 86400000L)
    val rem = ist - days * 86400000L
    val (y, m, d) = civilFromDays(days)
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val hh = rem / 3600000L
    val mm = (rem % 3600000L) / 60000L
    val ampm = if (hh < 12) "AM" else "PM"
    val h12 = if (hh % 12 == 0L) 12 else hh % 12
    return "$d ${months[(m - 1).toInt()]} $y, $h12:${mm.toString().padStart(2, '0')} $ampm"
}

fun fmtRupees(paise: Long): String = "₹" + FirebaseApi.fmtPaise(paise)

@Composable
fun PageHeader(icon: IconKind, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(PurplePrimary),
            contentAlignment = Alignment.Center
        ) {
            QrIcon(icon, Color.White, size = 23.dp)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = NavyText)
            Text(subtitle, fontSize = 12.5.sp, color = MutedText)
        }
    }
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun ScreenContent(state: AppState) {
    when (state.screen) {
        Screen.Dashboard -> DashboardScreen(state)
        Screen.AllTools -> AllToolsScreen(state)
        Screen.QrGenerator -> QrGeneratorScreen(state)
        Screen.PdfConverter -> PdfConverterScreen(state)
        Screen.Spin -> SpinScreen(state)
        Screen.Wallet -> WalletScreen(state)
        Screen.Tasks -> TasksScreen(state)
        Screen.Settings -> SettingsScreen(state)
        Screen.Premium -> PremiumScreen(state)
        Screen.Login -> LoginScreen(state)
        Screen.Placeholder -> PlaceholderScreen(state, state.placeholderTitle)
        Screen.Barcode -> BarcodeScreen(state)
        Screen.QrScan -> QrScanScreen(state)
        Screen.ImageTools -> ImageToolsScreen(state)
        Screen.Scanner -> ScannerScreen(state)
        Screen.RemoveBg -> RemoveBgScreen(state)
        Screen.ResizeImage -> ResizeImageScreen(state)
        Screen.AgeCalc -> AgeCalcScreen(state)
        Screen.BmiCalc -> BmiScreen(state)
        Screen.UnitConvert -> UnitScreen(state)
        Screen.CurrencyConvert -> CurrencyScreen(state)
        Screen.PercentCalc -> PercentScreen(state)
        Screen.CompressPdf -> CompressPdfScreen(state)
        Screen.CompressImage -> CompressImageScreen(state)
        Screen.Bonus -> BonusScreen(state)
        Screen.Invite -> InviteScreen(state)
        Screen.History -> HistoryScreen(state)
        Screen.Notifications -> NotificationsScreen(state)
        Screen.Support -> SupportScreen(state)
        Screen.About -> AboutScreen(state)
    }
}

/* ---------------- QR canvas (styled) ---------------- */

@Composable
fun QrCanvas(
    qr: QrCode,
    fg: Color,
    bg: Color?,
    style: PngEncoder.QrStyle,
    logo: ImageBitmap?,
    modifier: Modifier = Modifier,
    multiColor: Boolean = false
) {
    Canvas(modifier) {
        val total = qr.size + 8
        val cell = size.minDimension / total
        val ox = (size.width - cell * total) / 2f
        val oy = (size.height - cell * total) / 2f
        if (bg != null) {
            drawRoundRect(bg, topLeft = Offset(ox, oy), size = Size(cell * total, cell * total), cornerRadius = CornerRadius(10f, 10f))
        } else {
            // transparent: subtle checkerboard
            val chk = cell * 2
            var yy = 0
            while (yy < total / 2 + 1) {
                var xx = 0
                while (xx < total / 2 + 1) {
                    if ((xx + yy) % 2 == 0) {
                        drawRect(
                            Color(0xFFE9E9F2),
                            topLeft = Offset(ox + xx * chk, oy + yy * chk),
                            size = Size(chk, chk)
                        )
                    }
                    xx++
                }
                yy++
            }
        }
        fun isFinder(mx: Int, my: Int) =
            (mx < 9 && my < 9) || (mx >= qr.size - 8 && my < 9) || (mx < 9 && my >= qr.size - 8)
        for (my in 0 until qr.size) {
            for (mx in 0 until qr.size) {
                if (!qr.getModule(mx, my)) continue
                val px = ox + (mx + 4) * cell
                val py = oy + (my + 4) * cell
                val modColor = if (multiColor && !isFinder(mx, my)) {
                    HeroQrPalette[(mx * 31 + my * 17) % HeroQrPalette.size]
                } else fg
                when {
                    style == PngEncoder.QrStyle.SQUARE || isFinder(mx, my) ->
                        drawRect(modColor, topLeft = Offset(px, py), size = Size(cell + 0.6f, cell + 0.6f))
                    style == PngEncoder.QrStyle.DOTS ->
                        drawCircle(modColor, radius = cell * 0.46f, center = Offset(px + cell / 2, py + cell / 2))
                    else ->
                        drawRoundRect(
                            modColor, topLeft = Offset(px, py), size = Size(cell + 0.4f, cell + 0.4f),
                            cornerRadius = CornerRadius(cell * 0.34f, cell * 0.34f)
                        )
                }
            }
        }
        if (logo != null) {
            val ls = cell * total * 0.22f
            val lx = (size.width - ls) / 2f
            val ly = (size.height - ls) / 2f
            drawRoundRect(
                Color.White,
                topLeft = Offset(lx - 6f, ly - 6f),
                size = Size(ls + 12f, ls + 12f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawImage(
                logo,
                dstOffset = IntOffset(lx.toInt(), ly.toInt()),
                dstSize = IntSize(ls.toInt(), ls.toInt())
            )
        }
    }
}

/* ---------------- tool catalogue ---------------- */

data class ToolCardDef(
    val name: String,
    val categoryLabel: String,
    val chip: String,
    val icon: IconKind,
    val color: Color,
    val action: (AppState) -> Unit
)

fun homeTools(): List<ToolCardDef> = listOf(
    ToolCardDef("QR Generator", "QR Tools", "Productivity", IconKind.Qr, Color(0xFF4338CA)) { it.openQr(QrPreset("Website URL", "Website URL", "https://")) },
    ToolCardDef("PDF Converter", "PDF Tools", "PDF", IconKind.Pdf, Color(0xFFE11D48)) { it.navigate(Screen.PdfConverter) },
    ToolCardDef("Image Tools", "Edit & Convert", "Productivity", IconKind.Image, Color(0xFF16A34A)) { it.navigate(Screen.ImageTools) },
    ToolCardDef("Document Scanner", "Scan & PDF", "Productivity", IconKind.Scan, Color(0xFF2563EB)) { it.navigate(Screen.Scanner) },
    ToolCardDef("Image to PDF", "PDF Tools", "PDF", IconKind.FileText, Color(0xFF9333EA)) { it.openPdf("Images to PDF") },
    ToolCardDef("Merge PDF", "PDF Tools", "PDF", IconKind.Merge, Color(0xFFDC2626)) { it.openPdf("Merge PDF") },
    ToolCardDef("Compress PDF", "PDF Tools", "PDF", IconKind.Compress, Color(0xFFEA580C)) { it.navigate(Screen.CompressPdf) },
    ToolCardDef("Remove BG", "Photo Tools", "Productivity", IconKind.Star, Color(0xFF7C3AED)) { it.navigate(Screen.RemoveBg) },
    ToolCardDef("Resize Image", "Photo Tools", "Productivity", IconKind.Edit, Color(0xFF0D9488)) { it.navigate(Screen.ResizeImage) },
    ToolCardDef("Age Calculator", "Calculators", "Calculators", IconKind.Calendar, Color(0xFFDB2777)) { it.navigate(Screen.AgeCalc) },
    ToolCardDef("BMI Calculator", "Calculators & Converters", "Calculators", IconKind.Calc, Color(0xFF0284C7)) { it.navigate(Screen.BmiCalc) },
    ToolCardDef("Unit Converter", "Converters", "Calculators", IconKind.Swap, Color(0xFF65A30D)) { it.navigate(Screen.UnitConvert) },
    ToolCardDef("Currency Converter", "Converters", "Calculators", IconKind.Wallet, Color(0xFFCA8A04)) { it.navigate(Screen.CurrencyConvert) },
    ToolCardDef("Percentage Calculator", "Calculator", "Calculators", IconKind.Percent, Color(0xFFC026D3)) { it.navigate(Screen.PercentCalc) }
)

val HeroQrPalette = listOf(
    Color(0xFF6D28D9), Color(0xFF2563EB), Color(0xFF0D9488), Color(0xFF4F46E5), Color(0xFF7C3AED)
)

/* ---------------- 1) Home Dashboard (reference) ---------------- */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(state: AppState) {
    var chip by remember { mutableStateOf("All Tools") }
    val chips = listOf("All Tools", "PDF", "Calculators", "Productivity")
    val tools = homeTools().filter { chip == "All Tools" || it.chip == chip }.take(9)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { HeroBanner(state) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    FilterChip(
                        selected = chip == c,
                        onClick = { chip = c },
                        label = { Text(c, fontSize = 12.5.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                tools.forEach { t -> HomeToolCard(t) { t.action(state) } }
                // More Tools card (reference)
                Column(
                    Modifier.width(216.dp).height(138.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { state.navigate(Screen.AllTools) }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F0FA)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 24.sp, color = MutedText, fontWeight = FontWeight.Light)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("More Tools", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = NavyText)
                    Text("View All", fontSize = 11.sp, color = MutedText)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun HomeToolCard(t: ToolCardDef, onClick: () -> Unit) {
    Column(
        Modifier.width(216.dp).height(138.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(t.color),
            contentAlignment = Alignment.Center
        ) {
            QrIcon(t.icon, Color.White, size = 25.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = NavyText, maxLines = 1)
        Text(t.categoryLabel, fontSize = 12.sp, color = MutedText, maxLines = 1)
    }
}

@Composable
fun HeroBanner(state: AppState) {
    var heroQuery by remember { mutableStateOf("") }
    val heroQr = remember {
        try { QrCode.encodeText("https://qrmaster-4ai.pages.dev/", QrEcc.MEDIUM) }
        catch (e: Exception) { null }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFD7E7FF), Color(0xFFE6E1FF), Color(0xFFDACFFF))
                )
            )
    ) {
        val wide = maxWidth >= 700.dp
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1.5f)) {
                Text("Everything You Need,", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF141433), lineHeight = 36.sp)
                Text("All in One Place.", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary, lineHeight = 36.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create QR Codes, Edit Photos, Manage PDFs and Access Powerful Tools in One Platform.",
                    fontSize = 13.sp, color = Color(0xFF55516E), lineHeight = 18.sp
                )
                Spacer(Modifier.height(16.dp))
                // In-hero search (reference)
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 470.dp).height(46.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable { state.applySearch(heroQuery) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QrIcon(IconKind.Search, MutedText, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = heroQuery,
                        onValueChange = { heroQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = NavyText, fontSize = 13.sp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { state.applySearch(heroQuery) }
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (heroQuery.isEmpty()) {
                                    Text("Search for tools... (e.g., QR, PDF converter, calculator)", color = MutedText, fontSize = 12.5.sp, maxLines = 1)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (wide) {
                Box(Modifier.weight(1f).height(208.dp), contentAlignment = Alignment.Center) {
                    // saturated blue-purple glow behind the illustration (reference)
                    Box(
                        Modifier.size(330.dp).clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0x8C7C9CFF), Color(0x4D38BDF8), Color(0x006D28D9))
                                )
                            )
                    )
                    // glowing platform
                    Box(
                        Modifier.width(300.dp).height(58.dp).offset(y = 66.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0x805B8CFF), Color(0x006D28D9))
                                )
                            )
                    )
                    // floating chips
                    FloatingChip(IconKind.Image, Color(0xFF16A34A), Modifier.align(Alignment.TopStart).offset(x = 30.dp, y = 10.dp))
                    FloatingChip(IconKind.Play, Color(0xFF2563EB), Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 2.dp))
                    FloatingChip(IconKind.Pdf, Color(0xFFE11D48), Modifier.align(Alignment.BottomEnd).offset(x = (-26).dp, y = (-10).dp))
                    FloatingChip(IconKind.Music, Color(0xFF9333EA), Modifier.align(Alignment.BottomStart).offset(x = 48.dp, y = (-4).dp))
                    // QR card — large, tilted, multicolour modules (reference illustration)
                    Box(
                        Modifier.rotate(-6f).width(178.dp).height(178.dp)
                            .shadow(14.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(11.dp)
                    ) {
                        if (heroQr != null) {
                            QrCanvas(
                                heroQr, Color(0xFF312E81), Color.White, PngEncoder.QrStyle.SQUARE, null,
                                Modifier.fillMaxSize(), multiColor = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingChip(icon: IconKind, color: Color, modifier: Modifier) {
    Box(
        modifier.size(46.dp)
            .shadow(7.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        QrIcon(icon, color, size = 24.dp)
    }
}

/* ---------------- 2) All Tools (reference) ---------------- */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AllToolsScreen(state: AppState) {
    var chip by remember { mutableStateOf("All Tools") }
    var query by remember { mutableStateOf(state.searchQuery) }
    val chips = listOf("All Tools", "QR Tools", "PDF Tools", "Image Tools", "Converters", "More")

    data class SectionDef(val title: String, val chipKey: String, val tools: List<ToolCardDef>)

    val qrTools = listOf(
        ToolCardDef("QR Generator", "", "QR Tools", IconKind.Qr, Color(0xFF4338CA)) { it.openQr(QrPreset("Website URL", "Website URL", "https://")) },
        ToolCardDef("QR Scanner", "", "QR Tools", IconKind.Scan, Color(0xFF0891B2)) { it.navigate(Screen.QrScan) },
        ToolCardDef("Wi-Fi QR", "", "QR Tools", IconKind.Zap, Color(0xFF2563EB)) { it.openQr(QrPreset("Wi-Fi", "Wi-Fi Name (SSID)", "WIFI:T:WPA;S:;P:;;")) },
        ToolCardDef("Website QR", "", "QR Tools", IconKind.Grid, Color(0xFF059669)) { it.openQr(QrPreset("Website URL", "Website URL", "https://")) },
        ToolCardDef("WhatsApp QR", "", "QR Tools", IconKind.Play, Color(0xFF16A34A)) { it.openQr(QrPreset("WhatsApp", "WhatsApp Number", "https://wa.me/")) },
        ToolCardDef("Instagram QR", "", "QR Tools", IconKind.Image, Color(0xFFDB2777)) { it.openQr(QrPreset("Instagram", "Instagram Username", "https://instagram.com/")) },
        ToolCardDef("Telegram QR", "", "QR Tools", IconKind.Share, Color(0xFF0284C7)) { it.openQr(QrPreset("Telegram", "Telegram Username", "https://t.me/")) },
        ToolCardDef("UPI QR", "", "QR Tools", IconKind.Wallet, Color(0xFF7C3AED)) { it.openQr(QrPreset("UPI Payment", "UPI ID", "upi://pay?pa=&pn=")) },
        ToolCardDef("Contact QR", "", "QR Tools", IconKind.Users, Color(0xFFEA580C)) { it.openQr(QrPreset("Contact Card", "Contact Details", "BEGIN:VCARD\nVERSION:3.0\nFN:\nTEL:\nEND:VCARD")) },
        ToolCardDef("Barcode Generator", "", "QR Tools", IconKind.Barcode, Color(0xFF334155)) { it.navigate(Screen.Barcode) }
    )
    val pdfTools = listOf(
        ToolCardDef("PDF Converter", "", "PDF Tools", IconKind.Pdf, Color(0xFFE11D48)) { it.navigate(Screen.PdfConverter) },
        ToolCardDef("Merge PDF", "", "PDF Tools", IconKind.Merge, Color(0xFFDC2626)) { it.openPdf("Merge PDF") },
        ToolCardDef("Split PDF", "", "PDF Tools", IconKind.FileText, Color(0xFF7C3AED)) { it.openPdf("Split PDF") },
        ToolCardDef("Compress PDF", "", "PDF Tools", IconKind.Compress, Color(0xFFEA580C)) { it.navigate(Screen.CompressPdf) },
        ToolCardDef("Rotate PDF", "", "PDF Tools", IconKind.Swap, Color(0xFF0891B2)) { it.openPdf("Rotate PDF") }
    )
    val imageTools = listOf(
        ToolCardDef("Image Tools", "", "Image Tools", IconKind.Image, Color(0xFF16A34A)) { it.navigate(Screen.ImageTools) },
        ToolCardDef("Document Scanner", "", "Image Tools", IconKind.Scan, Color(0xFF2563EB)) { it.navigate(Screen.Scanner) },
        ToolCardDef("Compress Image", "", "Image Tools", IconKind.Compress, Color(0xFFEA580C)) { it.navigate(Screen.CompressImage) },
        ToolCardDef("Remove BG", "", "Image Tools", IconKind.Star, Color(0xFF9333EA)) { it.navigate(Screen.RemoveBg) },
        ToolCardDef("Resize Image", "", "Image Tools", IconKind.Edit, Color(0xFF0D9488)) { it.navigate(Screen.ResizeImage) },
        ToolCardDef("Image to PDF", "", "Image Tools", IconKind.FileText, Color(0xFFDB2777)) { it.openPdf("Images to PDF") }
    )
    val converters = listOf(
        ToolCardDef("Age Calculator", "", "Converters", IconKind.Calendar, Color(0xFFDB2777)) { it.navigate(Screen.AgeCalc) },
        ToolCardDef("BMI Calculator", "", "Converters", IconKind.Calc, Color(0xFF0284C7)) { it.navigate(Screen.BmiCalc) },
        ToolCardDef("Unit Converter", "", "Converters", IconKind.Swap, Color(0xFF65A30D)) { it.navigate(Screen.UnitConvert) },
        ToolCardDef("Currency Converter", "", "Converters", IconKind.Wallet, Color(0xFFCA8A04)) { it.navigate(Screen.CurrencyConvert) },
        ToolCardDef("Percentage Calculator", "", "Converters", IconKind.Percent, Color(0xFFC026D3)) { it.navigate(Screen.PercentCalc) }
    )
    val earnTools = listOf(
        ToolCardDef("Tasks & Earn", "", "More", IconKind.Task, Color(0xFFD97706)) { it.navigate(Screen.Tasks) },
        ToolCardDef("Spin & Earn", "", "More", IconKind.Spin, Color(0xFF7C3AED)) { it.navigate(Screen.Spin) },
        ToolCardDef("Bonus Code", "", "More", IconKind.Gift, Color(0xFFDB2777)) { it.navigate(Screen.Bonus) }
    )
    val sections = listOf(
        SectionDef("QR & Barcode Tools", "QR Tools", qrTools),
        SectionDef("PDF Tools", "PDF Tools", pdfTools),
        SectionDef("Image Tools", "Image Tools", imageTools),
        SectionDef("Converters & Calculators", "Converters", converters),
        SectionDef("Earn & Rewards", "More", earnTools)
    )
    val anyVisible = sections
        .filter { chip == "All Tools" || chip == "More" || it.chipKey == chip }
        .any { sec -> sec.tools.any { query.isBlank() || it.name.contains(query, ignoreCase = true) } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { PageHeader(IconKind.Grid, "All Tools", "Explore all powerful tools in one place.") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search for tools... (e.g., QR, PDF converter, calculator)", fontSize = 13.sp) },
                leadingIcon = { QrIcon(IconKind.Search, MutedText, size = 19.dp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    FilterChip(
                        selected = chip == c,
                        onClick = { chip = c },
                        label = { Text(c, fontSize = 12.5.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        if (!anyVisible) {
            item {
                Text(
                    "No tools found for \"$query\" — try another search.",
                    fontSize = 13.5.sp, color = MutedText
                )
            }
        }
        sections.filter { chip == "All Tools" || chip == "More" || it.chipKey == chip }.forEach { sec ->
            val visible = sec.tools.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            if (visible.isNotEmpty()) {
                item {
                    Text(sec.title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        visible.forEach { t -> AllToolCard(t) { t.action(state) } }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun AllToolCard(t: ToolCardDef, onClick: () -> Unit) {
    Column(
        Modifier.width(200.dp).height(120.dp)
            .shadow(3.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(t.color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            QrIcon(t.icon, t.color, size = 22.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(t.name, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = NavyText, maxLines = 2, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

/* ---------------- 3) QR Generator (reference, fully functional) ---------------- */

data class QrTypeDef(val label: String, val fieldLabel: String, val template: String)

val QrTypes = listOf(
    QrTypeDef("Website URL", "Website URL", "https://"),
    QrTypeDef("Plain Text", "Your Text", ""),
    QrTypeDef("UPI Payment", "UPI ID", "upi://pay?pa=&pn="),
    QrTypeDef("Wi-Fi", "Wi-Fi Name (SSID)", "WIFI:T:WPA;S:;P:;;"),
    QrTypeDef("WhatsApp", "WhatsApp Number", "https://wa.me/"),
    QrTypeDef("Phone Number", "Phone Number", "tel:+91"),
    QrTypeDef("Email", "Email Address", "mailto:")
)

val QrColorOptions = listOf(
    Color(0xFF6D28D9), Color(0xFF2563EB), Color(0xFF16A34A),
    Color(0xFFEA580C), Color(0xFFDC2626), Color(0xFF111111)
)

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun QrGeneratorScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var typeIndex by remember { mutableStateOf(0) }
    var content by remember { mutableStateOf("https://qrmaster.app") }
    var colorIndex by remember { mutableStateOf(0) }
    var transparentBg by remember { mutableStateOf(false) }
    var styleIndex by remember { mutableStateOf(0) }
    var logoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var logoName by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.qrPreset) {
        val p = state.qrPreset ?: return@LaunchedEffect
        val idx = QrTypes.indexOfFirst { it.label == p.typeLabel }
        if (idx >= 0) typeIndex = idx
        content = p.initialContent
    }

    val styles = listOf(PngEncoder.QrStyle.SQUARE, PngEncoder.QrStyle.ROUNDED, PngEncoder.QrStyle.DOTS)
    val styleNames = listOf("Square", "Rounded", "Dots")
    val ecl = if (logoBitmap != null) QrEcc.HIGH else QrEcc.QUARTILE
    val qrResult = remember(content, ecl) {
        if (content.isBlank()) null
        else try { Result.success(QrCode.encodeText(content, ecl)) } catch (e: Exception) { Result.failure(e) }
    }
    val qr = qrResult?.getOrNull()
    val fgColor = QrColorOptions[colorIndex]

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { PageHeader(IconKind.Qr, "QR Generator", "Create a custom QR code for any purpose.") }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val settingsCard: @Composable () -> Unit = {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Text("QR Type", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                            Box {
                                Row(
                                    Modifier.fillMaxWidth().height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                        .clickable { typeMenuOpen = true }
                                        .padding(horizontal = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(QrTypes[typeIndex].label, fontSize = 13.5.sp, color = NavyText, modifier = Modifier.weight(1f))
                                    Text("▾", color = MutedText, fontSize = 15.sp)
                                }
                                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                                    QrTypes.forEachIndexed { i, t ->
                                        DropdownMenuItem(
                                            text = { Text(t.label) },
                                            onClick = {
                                                typeIndex = i
                                                content = t.template
                                                typeMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            Text(QrTypes[typeIndex].fieldLabel, fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = content,
                                onValueChange = { content = it },
                                singleLine = false,
                                minLines = 1,
                                maxLines = 3,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Customization", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                            Text("QR Color", fontSize = 12.5.sp, color = MutedText)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QrColorOptions.forEachIndexed { i, c ->
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape).background(c)
                                            .border(
                                                if (colorIndex == i) 2.5.dp else 0.dp,
                                                if (colorIndex == i) NavyText else Color.Transparent,
                                                CircleShape
                                            )
                                            .clickable { colorIndex = i }
                                    )
                                }
                            }
                            Text("Background", fontSize = 12.5.sp, color = MutedText)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !transparentBg, onClick = { transparentBg = false })
                                Text("White", fontSize = 13.sp)
                                Spacer(Modifier.width(14.dp))
                                RadioButton(selected = transparentBg, onClick = { transparentBg = true })
                                Text("Transparent", fontSize = 13.sp)
                            }
                            Text("Logo (Optional)", fontSize = 12.5.sp, color = MutedText)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val id = pickFile("image/*", true)
                                            var tries = 0
                                            var result = ""
                                            while (tries < 150) {
                                                delay(200)
                                                result = pollPickedFile(id)
                                                if (result.isNotEmpty()) break
                                                tries++
                                            }
                                            if (result.isNotEmpty() && result != "CANCELLED") {
                                                val parts = result.split("|", limit = 3)
                                                logoName = parts.getOrNull(0) ?: ""
                                                val dataUrl = parts.getOrNull(2) ?: ""
                                                val b64 = dataUrl.substringAfter(",", "")
                                                if (b64.isNotEmpty()) {
                                                    try {
                                                        val bytes = Base64.decode(b64)
                                                        logoBitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                                                        state.toast("Logo added")
                                                    } catch (e: Exception) {
                                                        state.toast("Could not read that image")
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(9.dp)
                                ) {
                                    QrIcon(IconKind.Image, PurplePrimary, size = 16.dp)
                                    Spacer(Modifier.width(7.dp))
                                    Text("Upload Logo", fontSize = 12.5.sp)
                                }
                                if (logoBitmap != null) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(logoName.take(18), fontSize = 12.sp, color = MutedText, maxLines = 1)
                                    TextButton(onClick = { logoBitmap = null; logoName = "" }) {
                                        Text("Remove", fontSize = 12.sp)
                                    }
                                }
                            }
                            Text("QR Style", fontSize = 12.5.sp, color = MutedText)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                styleNames.forEachIndexed { i, n ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(46.dp).clip(RoundedCornerShape(9.dp))
                                                .background(Color.White)
                                                .border(
                                                    if (styleIndex == i) 2.dp else 1.dp,
                                                    if (styleIndex == i) PurplePrimary else MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(9.dp)
                                                )
                                                .clickable { styleIndex = i }
                                                .padding(7.dp)
                                        ) {
                                            Canvas(Modifier.fillMaxSize()) {
                                                val cell = size.minDimension / 5
                                                for (yy in 0 until 5) {
                                                    for (xx in 0 until 5) {
                                                        if ((xx * 7 + yy * 13) % 3 != 0) {
                                                            when (styles[i]) {
                                                                PngEncoder.QrStyle.SQUARE ->
                                                                    drawRect(Color(0xFF333355), Offset(xx * cell, yy * cell), Size(cell, cell))
                                                                PngEncoder.QrStyle.DOTS ->
                                                                    drawCircle(Color(0xFF333355), cell * 0.42f, Offset(xx * cell + cell / 2, yy * cell + cell / 2))
                                                                else ->
                                                                    drawRoundRect(Color(0xFF333355), Offset(xx * cell, yy * cell), Size(cell, cell), CornerRadius(cell * 0.35f, cell * 0.35f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Text(n, fontSize = 10.5.sp, color = MutedText)
                                    }
                                }
                            }
                        }
                    }
                }
                val previewCard: @Composable () -> Unit = {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("QR Preview", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText, modifier = Modifier.fillMaxWidth())
                            when {
                                content.isBlank() -> {
                                    Box(
                                        Modifier.size(230.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F2FA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Type something to\ngenerate your QR", color = MutedText, textAlign = TextAlign.Center, fontSize = 13.sp)
                                    }
                                }
                                qr != null -> {
                                    Box(Modifier.size(230.dp)) {
                                        QrCanvas(
                                            qr, fgColor,
                                            if (transparentBg) null else Color.White,
                                            styles[styleIndex], logoBitmap,
                                            Modifier.fillMaxSize()
                                        )
                                    }
                                    Text("QR Master", fontSize = 12.sp, color = MutedText)
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val fgArgb = (0xFF shl 24) or
                                                        ((fgColor.red * 255).toInt() shl 16) or
                                                        ((fgColor.green * 255).toInt() shl 8) or
                                                        (fgColor.blue * 255).toInt()
                                                    val png = PngEncoder.encodeQrStyled(
                                                        qr, 1024, fgArgb,
                                                        if (transparentBg) null else 0xFFFFFFFF.toInt(),
                                                        styles[styleIndex]
                                                    )
                                                    downloadPngBase64("qr-master-qr.png", Base64.encode(png))
                                                    state.logActivity("QR downloaded — ${content.take(24)}")
                                                    recordHistory("QR Generator", "QR code downloaded", content.take(48))
                                                    state.toast("QR downloaded")
                                                } catch (e: Exception) {
                                                    state.toast("Download failed — please try again")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Text("Download QR", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val r = shareText(content)
                                                state.toast(if (r == "copy") "Content copied to clipboard" else "Share opened")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            QrIcon(IconKind.Share, PurplePrimary, size = 15.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Share", fontSize = 12.5.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { printPage() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            QrIcon(IconKind.Printer, PurplePrimary, size = 15.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Print", fontSize = 12.5.sp)
                                        }
                                    }
                                }
                                else -> {
                                    Text(
                                        "This content is too long for a QR code. Please shorten it.",
                                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
                if (maxWidth >= 900.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1.3f)) { settingsCard() }
                        Box(Modifier.weight(1f)) { previewCard() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        settingsCard()
                        previewCard()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Converts a Skia image to a Compose ImageBitmap. */
fun SkiaImage.toComposeImageBitmap(): ImageBitmap =
    org.jetbrains.skia.Bitmap.makeFromImage(this).asComposeImageBitmap()

/* ---------------- 4) PDF Converter (workspace — real pdf-lib tools) ---------------- */

private suspend fun ensurePdfLibsReady(): String? {
    loadPdfLibs()
    var tries = 0
    var status = pdfLibStatus()
    while (status != "ready" && tries < 120) {
        if (status.startsWith("error")) return status.removePrefix("error:")
        delay(300)
        tries++
        status = pdfLibStatus()
    }
    return if (status == "ready") null else "The PDF engine is still loading. Try again in a moment."
}

private fun parsePageRange(input: String, total: Int): List<Int> {
    val out = sortedSetOf<Int>()
    for (part in input.split(",")) {
        val p = part.trim()
        if (p.isEmpty()) continue
        if (p.contains("-")) {
            val a = p.substringBefore("-").trim().toIntOrNull() ?: continue
            val b = p.substringAfter("-").trim().toIntOrNull() ?: continue
            for (n in minOf(a, b)..maxOf(a, b)) if (n in 1..total) out.add(n)
        } else {
            val n = p.toIntOrNull() ?: continue
            if (n in 1..total) out.add(n)
        }
    }
    return out.toList()
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PdfConverterScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var selectedTool by remember { mutableStateOf(state.pdfTool) }
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var pageRange by remember { mutableStateOf("") }
    var angle by remember { mutableStateOf(90) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var resultB64 by remember { mutableStateOf<String?>(null) }
    var resultName by remember { mutableStateOf("output.pdf") }
    var pageImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pageBitmaps by remember { mutableStateOf<Map<Int, ImageBitmap>>(emptyMap()) }
    var pageCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.pdfTool) {
        if (state.pdfTool == "Compress PDF") {
            state.navigate(Screen.CompressPdf)
        } else if (state.pdfTool in listOf("Merge PDF", "Split PDF", "Rotate PDF", "PDF to Images", "Images to PDF")) {
            selectedTool = state.pdfTool
        } else {
            selectedTool = "Merge PDF"
        }
    }
    LaunchedEffect(selectedTool) {
        files = emptyList()
        resultB64 = null; pageImages = emptyList(); pageCount = null
        statusText = null; errorText = null
    }

    val toolSpec = if (selectedTool == "Images to PDF") IMAGE_SPEC else PDF_SPEC
    val multi = selectedTool == "Merge PDF" || selectedTool == "Images to PDF"

    fun runOp(op: String, args: String, onOk: (JsonObject) -> Unit) {
        busy = true; errorText = null; statusText = null
        scope.launch {
            val libErr = ensurePdfLibsReady()
            if (libErr != null) { busy = false; errorText = libErr; return@launch }
            pdfOp(op, args)
            val res = awaitAsync("pdf", 300_000)
            busy = false
            when {
                res == null -> errorText = "Processing timed out. Try a smaller file."
                res.asyncOk() -> onOk(res)
                else -> errorText = res.asyncError()
            }
        }
    }

    fun loadFirstPdfCount() {
        val f = files.firstOrNull() ?: return
        scope.launch {
            if (ensurePdfLibsReady() != null) return@launch
            val b64 = loadFileBase64(f.id) ?: return@launch
            pdfOp("count", "{\"pdf\":\"$b64\"}")
            val res = awaitAsync("pdf", 60_000)
            if (res != null && res.asyncOk()) pageCount = res.asyncStr("pages").toIntOrNull()
        }
    }
    LaunchedEffect(files.firstOrNull()?.id, selectedTool) {
        pageCount = null
        if (selectedTool != "Images to PDF" && files.isNotEmpty()) loadFirstPdfCount()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { PageHeader(IconKind.Pdf, "PDF Converter", "Convert, edit and manage your PDF files — everything runs in your browser.") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple("Merge PDF", IconKind.Merge, Color(0xFFDC2626)),
                    Triple("Split PDF", IconKind.FileText, Color(0xFF7C3AED)),
                    Triple("Compress PDF", IconKind.Compress, Color(0xFFEA580C)),
                    Triple("Rotate PDF", IconKind.Swap, Color(0xFF0891B2)),
                    Triple("PDF to Images", IconKind.Image, Color(0xFF2563EB)),
                    Triple("Images to PDF", IconKind.Pdf, Color(0xFFDB2777))
                ).forEach { (name, icon, color) ->
                    Column(
                        Modifier.width(118.dp).height(96.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                if (selectedTool == name) 2.dp else 0.dp,
                                if (selectedTool == name) PurplePrimary else Color.Transparent,
                                RoundedCornerShape(13.dp)
                            )
                            .clickable {
                                if (name == "Compress PDF") state.navigate(Screen.CompressPdf)
                                else selectedTool = name
                            }
                            .padding(9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        QrIcon(icon, color, size = 24.dp)
                        Spacer(Modifier.height(7.dp))
                        Text(name, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = NavyText, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(
                        state, files, toolSpec,
                        if (selectedTool == "Images to PDF") "PNG or JPG images (up to 10)"
                        else "PDF files (max 25 MB each)"
                    ) { newFiles -> files = if (multi) newFiles else newFiles.take(1) }
                    if (pageCount != null && selectedTool != "Images to PDF") {
                        Text("This PDF has $pageCount page(s).", fontSize = 12.5.sp, color = MutedText)
                    }
                    when (selectedTool) {
                        "Split PDF" -> {
                            OutlinedTextField(
                                value = pageRange,
                                onValueChange = { pageRange = it },
                                label = { Text("Pages to extract (e.g. 1-3, 5, 8)") },
                                singleLine = true, shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "Rotate PDF" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Rotate by:", fontSize = 13.sp, color = NavyText)
                                listOf(90, 180, 270).forEach { a ->
                                    FilterChip(
                                        selected = angle == a,
                                        onClick = { angle = a },
                                        label = { Text("$a°", fontSize = 12.5.sp) },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                    PrimaryToolButton(
                        when (selectedTool) {
                            "Merge PDF" -> "Merge PDFs"
                            "Split PDF" -> "Extract Pages"
                            "Rotate PDF" -> "Rotate PDF"
                            "PDF to Images" -> "Convert to Images"
                            "Images to PDF" -> "Create PDF"
                            else -> "Process"
                        },
                        enabled = files.isNotEmpty() && (selectedTool != "Merge PDF" || files.size >= 2),
                        busy = busy
                    ) {
                        resultB64 = null; pageImages = emptyList()
                        when (selectedTool) {
                            "Merge PDF" -> scope.launch {
                                busy = true
                                val b64s = ArrayList<String>()
                                for (f in files) {
                                    val b = loadFileBase64(f.id)
                                    if (b == null) { busy = false; errorText = "Could not read ${f.name}."; return@launch }
                                    b64s.add(b)
                                }
                                busy = false
                                val args = "{\"pdfs\":[${b64s.joinToString(",") { "\"$it\"" }}]}"
                                runOp("merge", args) { res ->
                                    resultB64 = res.asyncStr("b64")
                                    resultName = "merged.pdf"
                                    statusText = "Merged ${files.size} PDFs into ${res.asyncStr("pages")} page(s)."
                                    recordHistory("Merge PDF", "PDFs merged", "${files.size} files")
                                }
                            }
                            "Split PDF" -> scope.launch {
                                val f = files.first()
                                val total = pageCount ?: run {
                                    busy = true
                                    if (ensurePdfLibsReady() != null) { busy = false; return@launch }
                                    val b = loadFileBase64(f.id) ?: run { busy = false; return@launch }
                                    pdfOp("count", "{\"pdf\":\"$b\"}")
                                    val r = awaitAsync("pdf", 60_000)
                                    busy = false
                                    r?.asyncStr("pages")?.toIntOrNull() ?: 0
                                }
                                val pages = parsePageRange(pageRange, total)
                                if (pages.isEmpty()) { errorText = "Enter valid page numbers (this PDF has $total pages)."; return@launch }
                                val b64 = loadFileBase64(f.id) ?: run { errorText = "Could not read the file."; return@launch }
                                runOp("extract", "{\"pdf\":\"$b64\",\"pages\":[${pages.joinToString(",")}]}") { res ->
                                    resultB64 = res.asyncStr("b64")
                                    resultName = "extracted.pdf"
                                    statusText = "Extracted ${res.asyncStr("pages")} page(s)."
                                    recordHistory("Split PDF", "Pages extracted", pages.joinToString(","))
                                }
                            }
                            "Rotate PDF" -> scope.launch {
                                val f = files.first()
                                val b64 = loadFileBase64(f.id) ?: run { errorText = "Could not read the file."; return@launch }
                                runOp("rotate", "{\"pdf\":\"$b64\",\"angle\":$angle}") { res ->
                                    resultB64 = res.asyncStr("b64")
                                    resultName = "rotated.pdf"
                                    statusText = "Rotated ${res.asyncStr("pages")} page(s) by $angle°."
                                    recordHistory("Rotate PDF", "PDF rotated", "$angle°")
                                }
                            }
                            "PDF to Images" -> scope.launch {
                                val f = files.first()
                                val b64 = loadFileBase64(f.id) ?: run { errorText = "Could not read the file."; return@launch }
                                runOp("render", "{\"pdf\":\"$b64\",\"scale\":1.6,\"maxPages\":20}") { res ->
                                    val arr = res["images"] as? kotlinx.serialization.json.JsonArray
                                    pageImages = arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
                                    pageBitmaps = pageImages.mapIndexedNotNull { idx, u ->
                                        decodeBitmapFromDataUrl(u)?.let { idx to it }
                                    }.toMap()
                                    val totalPages = res.asyncStr("pages")
                                    statusText = "Rendered ${pageImages.size} of $totalPages page(s) as images."
                                    recordHistory("PDF to Images", "PDF rendered", "${pageImages.size} pages")
                                }
                            }
                            "Images to PDF" -> scope.launch {
                                busy = true
                                val imgs = ArrayList<String>()
                                for (f in files) {
                                    val b = loadFileBase64(f.id)
                                    if (b == null) { busy = false; errorText = "Could not read ${f.name}."; return@launch }
                                    val mime = if (f.dataUrlMime().contains("png")) "image/png" else "image/jpeg"
                                    imgs.add("{\"b64\":\"$b\",\"mime\":\"$mime\"}")
                                }
                                busy = false
                                runOp("imagesToPdf", "{\"images\":[${imgs.joinToString(",")}]}") { res ->
                                    resultB64 = res.asyncStr("b64")
                                    resultName = "images.pdf"
                                    statusText = "Created a ${res.asyncStr("pages")}-page PDF from ${files.size} image(s)."
                                    recordHistory("Images to PDF", "PDF created", "${files.size} images")
                                }
                            }
                        }
                    }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Working…", fontSize = 13.sp, color = MutedText)
                        }
                    }
                    if (errorText != null) Text(errorText!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    if (statusText != null) Text(statusText!!, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    if (resultB64 != null) {
                        Button(
                            onClick = {
                                downloadFileBase64(resultName, resultB64!!, "application/pdf")
                                state.toast("Downloaded $resultName")
                            },
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) { Text("Download $resultName", fontWeight = FontWeight.SemiBold) }
                    }
                    pageImages.forEachIndexed { i, url ->
                        val bmp = pageBitmaps[i]
                        WhiteCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(
                                        bmp, contentDescription = "Page ${i + 1}",
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFFF3F2FA))
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        downloadFileBase64("page-${i + 1}.png", url.substringAfter(",", ""), "image/png")
                                        state.toast("Downloaded page-${i + 1}.png")
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("Download Page ${i + 1} (PNG)", fontSize = 12.5.sp) }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------------- 5) Spin & Earn (Android app wheel) ---------------- */

val SpinSegmentLabels = listOf("₹1", "₹2", "₹5", "₹10", "₹15", "₹20", "BLNT", "JACKPOT")
val SpinSegmentColors = listOf(
    Color(0xFF8B5CF6), Color(0xFF2563EB), Color(0xFF059669), Color(0xFF4F46E5),
    Color(0xFFDB2777), Color(0xFF0D9488), Color(0xFF64748B), Color(0xFFF59E0B)
)

@Composable
fun SpinScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val profile = state.profile
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var checkingIn by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    val today = todayIst()
    val checkedIn = profile != null && profile.checkinDate == today
    val baseLimit = if (profile?.isPremium == true) 10L else 3L
    val usedToday = if (profile != null && profile.spinDate == today) profile.spinUsedToday else 0L
    val totalSpins = baseLimit + (profile?.giftSpins ?: 0L) + (profile?.notifSpins ?: 0L)
    val usedTotal = usedToday + (profile?.giftSpinsUsed ?: 0L) + (profile?.notifSpinsUsed ?: 0L)
    val spinsLeft = maxOf(0L, totalSpins - usedTotal - (profile?.spinDebt ?: 0L))

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrIcon(IconKind.Gift, PurplePrimary, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Spin & Earn", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    Text("Spin the wheel and win exciting rewards!", fontSize = 12.5.sp, color = MutedText)
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 860.dp
                val wheelBox: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(330.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = size.minDimension / 2f - 6f
                                // gold rim
                                drawCircle(Color(0xFFF59E0B), radius = radius + 3f, center = Offset(cx, cy))
                                drawCircle(Color(0xFFFFF7E0), radius = radius, center = Offset(cx, cy))
                                for (i in 0 until 8) {
                                    drawArc(
                                        color = SpinSegmentColors[i],
                                        startAngle = -90f + i * 45f + rotation.value,
                                        sweepAngle = 45f,
                                        useCenter = true,
                                        topLeft = Offset(cx - radius + 4f, cy - radius + 4f),
                                        size = Size((radius - 4f) * 2, (radius - 4f) * 2)
                                    )
                                }
                                // labels — app convention: rotate from 12 o'clock to segment centre
                                for (i in 0 until 8) {
                                    val label = SpinSegmentLabels[i]
                                    val layout = textMeasurer.measure(
                                        label,
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = if (label.length > 3) 12.5.sp else 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    withTransform({
                                        rotate(i * 45f + 22.5f + rotation.value, Offset(cx, cy))
                                    }) {
                                        drawText(
                                            layout,
                                            topLeft = Offset(cx - layout.size.width / 2f, cy - radius * 0.80f)
                                        )
                                    }
                                }
                                // hub
                                drawCircle(PurplePrimary, radius = 34f, center = Offset(cx, cy))
                                drawCircle(Color.White, radius = 34f, center = Offset(cx, cy), style = Stroke(width = 3f))
                            }
                            Text("SPIN", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            // pointer at top
                            Canvas(Modifier.size(26.dp).align(Alignment.TopCenter).offset(y = (-2).dp)) {
                                val path = androidx.compose.ui.graphics.Path()
                                path.moveTo(size.width / 2f, size.height)
                                path.lineTo(0f, 0f)
                                path.lineTo(size.width, 0f)
                                path.close()
                                drawPath(path, Color(0xFFDC2626))
                            }
                        }
                        if (resultText != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(resultText!!, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                        }
                    }
                }
                val sidePanel: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                        WhiteCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QrIcon(IconKind.Check, PurplePrimary, size = 18.dp)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    if (state.user == null) "Login to see your spins"
                                    else "Spins Left Today:  $spinsLeft / $totalSpins",
                                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText
                                )
                            }
                            if (state.user != null && checkedIn) {
                                Text(
                                    "Checked in today — spins unlocked. Good luck!",
                                    fontSize = 12.sp, color = Color(0xFF16A34A),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 11.dp)
                                )
                            }
                        }
                        when {
                            state.user == null -> {
                                Button(
                                    onClick = { state.navigate(Screen.Login) },
                                    shape = RoundedCornerShape(11.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) { Text("Login to Spin", fontWeight = FontWeight.SemiBold) }
                            }
                            !checkedIn -> {
                                Button(
                                    onClick = {
                                        checkingIn = true
                                        scope.launch {
                                            val r = FirebaseApi.dailyCheckIn(today, 1L)
                                            checkingIn = false
                                            if (r.isSuccess) {
                                                state.refreshProfile()
                                                state.toast("Checked in for today")
                                            } else {
                                                state.toast(r.exceptionOrNull()?.message ?: "Check-in failed")
                                            }
                                        }
                                    },
                                    enabled = !checkingIn,
                                    shape = RoundedCornerShape(11.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    if (checkingIn) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(19.dp))
                                    else Text("Daily Check-In", fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    "Check in first — spins unlock after today's check-in, just like the app.",
                                    fontSize = 12.sp, color = MutedText
                                )
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        if (spinning) return@Button
                                        if (spinsLeft <= 0L) {
                                            state.toast("No spins left today")
                                            return@Button
                                        }
                                        spinning = true
                                        resultText = null
                                        scope.launch {
                                            // Outcome decided FIRST (app band logic), then the wheel lands on it.
                                            val balance = profile?.walletPaise ?: 0L
                                            val segIndex = pickSpinSegment(balance)
                                            val current = rotation.value
                                            val segCenter = segIndex * 45f + 22.5f
                                            val targetMod = (360f - segCenter) % 360f
                                            val currentMod = ((current % 360f) + 360f) % 360f
                                            var delta = targetMod - currentMod
                                            if (delta < 0) delta += 360f
                                            val target = current + 360f * 5 + delta
                                            rotation.animateTo(
                                                target,
                                                animationSpec = tween(
                                                    durationMillis = 7000,
                                                    easing = CubicBezierEasing(0.12f, 0.8f, 0.08f, 1f)
                                                )
                                            )
                                            spinning = false
                                            resultText = when (segIndex) {
                                                6 -> "Better Luck Next Time!"
                                                7 -> "JACKPOT! You won ₹1"
                                                else -> "You won ${SpinSegmentLabels[segIndex]}"
                                            }
                                        }
                                    },
                                    enabled = !spinning,
                                    shape = RoundedCornerShape(11.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    if (spinning) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(19.dp))
                                    else Text("Spin Now", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                        }
                        WhiteCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QrIcon(IconKind.Gift, Color(0xFFDB2777), size = 26.dp)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Your Rewards", fontSize = 12.5.sp, color = MutedText)
                                    Text(
                                        if (state.user == null) "—" else fmtRupees(profile?.walletPaise ?: 0L),
                                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyText
                                    )
                                }
                                Text("Wallet", fontSize = 11.5.sp, color = MutedText)
                            }
                        }
                        Text(
                            "Demo spin on the website — rewards are credited by the secure server engine and go live with the full website build. The Android app credits instantly.",
                            fontSize = 11.5.sp, color = MutedText
                        )
                    }
                }
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top
                    ) {
                        wheelBox()
                        sidePanel()
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        wheelBox()
                        sidePanel()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Same bands as the app's EarnLogic: outcome segment by wallet balance. */
fun pickSpinSegment(balancePaise: Long): Int {
    val r = (nowMillis() % 10000L) / 100.0 // 0..99.99 pseudo-random from clock
    return when {
        balancePaise < 500 -> when {
            r < 20 -> 1   // ₹2 (20%)
            r < 70 -> 0   // ₹1 (50%)
            else -> 6     // BLNT (30%)
        }
        balancePaise < 800 -> if (r < 15) 0 else 6   // ₹1 15% / BLNT 85%
        balancePaise < 900 -> if (r < 3) 0 else 6    // ₹1 3% / BLNT 97%
        else -> if (r < 1) 7 else 6                  // JACKPOT 1% / BLNT
    }
}

/* ---------------- 6) Wallet (reference, real data) ---------------- */

@Composable
fun WalletScreen(state: AppState) {
    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var upiId by remember { mutableStateOf("") }

    LaunchedEffect(state.user, state.profile) {
        if (state.user != null) {
            loading = true
            history = FirebaseApi.fetchHistory()
            loading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(PurplePrimary),
                            contentAlignment = Alignment.Center
                        ) { QrIcon(IconKind.Wallet, Color.White, size = 23.dp) }
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text("Wallet", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = NavyText)
                            Text("Manage your earnings, rewards and withdrawals.", fontSize = 12.5.sp, color = MutedText)
                        }
                    }
                }
                Button(
                    onClick = {
                        if (state.user == null) state.navigate(Screen.Login) else showWithdraw = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Withdraw", fontSize = 13.sp) }
            }
        }
        if (state.user == null) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(26.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QrIcon(IconKind.Lock, PurplePrimary, size = 34.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Login to see your wallet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyText)
                        Text("Your real balance and transactions appear here after login.", color = MutedText, fontSize = 12.5.sp)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    }
                }
            }
        } else {
            item {
                // Balance card (reference: purple gradient)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Available Balance", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                if (state.profileLoading && state.profile == null) "…"
                                else fmtRupees(state.profile?.walletPaise ?: 0L),
                                color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Credits: ${state.profile?.credits ?: 0}" +
                                    if (state.profile?.isPremium == true) "  •  Premium member" else "",
                                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                            )
                        }
                        QrIcon(IconKind.Wallet, Color.White.copy(alpha = 0.5f), size = 52.dp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxWidth()) {
                    WalletAction("Add Money", IconKind.Download, Modifier.weight(1f)) { state.navigate(Screen.Premium) }
                    WalletAction("Withdraw", IconKind.Wallet, Modifier.weight(1f)) { showWithdraw = true }
                    WalletAction("Transactions", IconKind.FileText, Modifier.weight(1f)) {
                        scopeToastHistory(state)
                    }
                    WalletAction("Payment Methods", IconKind.Gear, Modifier.weight(1f)) {
                        state.toast("Payments are received on your UPI ID")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Transactions", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = NavyText, modifier = Modifier.weight(1f))
                    Text("View All", fontSize = 12.5.sp, color = PurplePrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PurplePrimary)
                    }
                }
            } else if (history.isEmpty()) {
                item {
                    Text("No transactions yet. Your spin, task and bonus history will appear here.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                items(history) { h ->
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape)
                                    .background(if (h.positive == false) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                QrIcon(
                                    if (h.positive == false) IconKind.Wallet else IconKind.Check,
                                    if (h.positive == false) Color(0xFFDC2626) else Color(0xFF16A34A),
                                    size = 18.dp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
                                Text(
                                    listOf(fmtDateTime(h.createdAtMs), h.status).filter { it.isNotBlank() }.joinToString("  •  "),
                                    fontSize = 11.5.sp, color = MutedText, maxLines = 1
                                )
                            }
                            Text(
                                h.amountText,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = when (h.positive) {
                                    true -> Color(0xFF16A34A)
                                    false -> Color(0xFFDC2626)
                                    else -> NavyText
                                }
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showWithdraw) {
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            title = { Text("Withdraw") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Available balance: ${fmtRupees(state.profile?.walletPaise ?: 0L)} (minimum ₹10, full balance withdraws — same rule as the app).", fontSize = 13.sp)
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("Your UPI ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Website withdrawals start with the full website build. For now, please withdraw from the QR Master Android app — your balance is the same.",
                        fontSize = 12.sp, color = MutedText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWithdraw = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showWithdraw = false }) { Text("Cancel") }
            }
        )
    }
}

fun scopeToastHistory(state: AppState) {
    state.toast("Your latest transactions are listed below")
}

@Composable
fun WalletAction(label: String, icon: IconKind, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(74.dp).clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        QrIcon(icon, PurplePrimary, size = 21.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.5.sp, color = NavyText, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/* ---------------- 7) Tasks & Earn (reference, real tasks) ---------------- */

@Composable
fun TasksScreen(state: AppState) {
    var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf("All Tasks") }
    var selected by remember { mutableStateOf<TaskItem?>(null) }
    val chips = listOf("All Tasks", "App Install", "Register", "Referral", "Survey")

    LaunchedEffect(state.user) {
        if (state.user != null) {
            loading = true
            tasks = FirebaseApi.fetchTasks()
            loading = false
        }
    }

    fun chipMatch(t: TaskItem): Boolean {
        val hay = (t.title + " " + t.description).lowercase()
        return when (chip) {
            "All Tasks" -> true
            "App Install" -> listOf("install", "demat", "app", "open").any { hay.contains(it) }
            "Register" -> listOf("register", "sign up", "signup", "create account", "kyc").any { hay.contains(it) }
            "Referral" -> hay.contains("refer")
            "Survey" -> hay.contains("survey")
            else -> true
        }
    }
    val visible = tasks.filter { chipMatch(it) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { PageHeader(IconKind.Task, "Tasks & Earn", "Complete simple tasks and earn rewards.") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    FilterChip(
                        selected = chip == c,
                        onClick = { chip = c },
                        label = { Text(c, fontSize = 12.5.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        if (state.user == null) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(26.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QrIcon(IconKind.Lock, PurplePrimary, size = 34.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Login to see tasks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyText)
                        Text("Tasks are personal — they appear after you log in.", color = MutedText, fontSize = 12.5.sp)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    }
                }
            }
        } else if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PurplePrimary)
                }
            }
        } else if (visible.isEmpty()) {
            item {
                Text("No tasks available right now. New tasks appear here as soon as they are published.", color = MutedText, fontSize = 13.sp)
            }
        } else {
            items(visible) { t ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFFF0EAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            QrIcon(IconKind.Task, PurplePrimary, size = 22.dp)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
                            Text(t.description, fontSize = 12.sp, color = MutedText, maxLines = 1)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF4D6))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                            Spacer(Modifier.width(5.dp))
                            Text(fmtRupees(t.rewardPaise), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { selected = t },
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.height(36.dp)
                        ) { Text("Start Task", fontSize = 12.5.sp) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    val sel = selected
    if (sel != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(sel.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (sel.description.isNotBlank()) Text(sel.description, fontSize = 13.sp)
                    Text("Reward: ${fmtRupees(sel.rewardPaise)}" +
                        (if (sel.payDays > 0) "  •  Payment within ${sel.payDays} days of approval" else "") +
                        (if (sel.mode.isNotBlank()) "  •  ${sel.mode}" else ""), fontSize = 12.5.sp, color = MutedText)
                    if (sel.conditions.isNotBlank()) {
                        Text("Conditions:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(sel.conditions, fontSize = 12.5.sp)
                    }
                    Text(
                        "Apply and proof submission currently run in the QR Master Android app; the website flow arrives with the full build.",
                        fontSize = 12.sp, color = MutedText
                    )
                }
            },
            confirmButton = {
                if (sel.link.isNotBlank()) {
                    TextButton(onClick = { openExternal(sel.link) }) { Text("Open Task Link") }
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Close") }
            }
        )
    }
}

/* ---------------- 8) Settings (reference) ---------------- */

@Composable
fun SettingsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Account") }
    val tabs = listOf("Account", "Appearance", "Notifications", "Security")
    val tabIcons = listOf(IconKind.Users, IconKind.Edit, IconKind.Bell, IconKind.Shield)
    val profile = state.profile
    var editing by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { PageHeader(IconKind.Gear, "Settings", "Manage your account and preferences.") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t, fontSize = 12.5.sp) },
                        leadingIcon = { QrIcon(tabIcons[i], if (tab == t) Color.White else MutedText, size = 15.dp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        if (state.user == null && tab == "Account") {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(26.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QrIcon(IconKind.Lock, PurplePrimary, size = 34.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Login to manage your account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyText)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    }
                }
            }
        } else {
            when (tab) {
                "Account" -> {
                    item {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val twoCol = maxWidth >= 860.dp
                            val profileCard: @Composable () -> Unit = {
                                WhiteCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                        Text("Profile Information", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.size(46.dp).clip(CircleShape).background(PurplePrimary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    (profile?.name?.firstOrNull() ?: state.user?.email?.firstOrNull() ?: 'G')
                                                        .uppercaseChar().toString(),
                                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(profile?.name?.ifBlank { "QR Master User" } ?: "QR Master User", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                                                Text(state.user?.email ?: "", fontSize = 12.sp, color = MutedText)
                                            }
                                        }
                                        if (editing) {
                                            OutlinedTextField(
                                                value = nameInput,
                                                onValueChange = { nameInput = it },
                                                label = { Text("Full name") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                Button(
                                                    onClick = {
                                                        saving = true
                                                        scope.launch {
                                                            val r = FirebaseApi.updateProfileName(nameInput.trim())
                                                            saving = false
                                                            if (r.isSuccess) {
                                                                editing = false
                                                                state.refreshProfile()
                                                                state.toast("Profile updated")
                                                            } else {
                                                                state.toast(r.exceptionOrNull()?.message ?: "Update failed")
                                                            }
                                                        }
                                                    },
                                                    enabled = !saving && nameInput.isNotBlank(),
                                                    shape = RoundedCornerShape(9.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                                                ) { Text("Save", fontSize = 12.5.sp) }
                                                OutlinedButton(onClick = { editing = false }, shape = RoundedCornerShape(9.dp)) {
                                                    Text("Cancel", fontSize = 12.5.sp)
                                                }
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { nameInput = profile?.name ?: ""; editing = true },
                                                shape = RoundedCornerShape(9.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Edit Profile", fontSize = 12.5.sp) }
                                        }
                                    }
                                }
                            }
                            val prefsCard: @Composable () -> Unit = {
                                WhiteCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                        Text("App Preferences", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Dark Mode", fontSize = 13.5.sp, color = NavyText, modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = state.darkMode,
                                                onCheckedChange = {
                                                    state.darkMode = it
                                                    try { window.localStorage.setItem("qm_dark", if (it) "1" else "0") } catch (e: Exception) { }
                                                }
                                            )
                                        }
                                        Text(
                                            "Language: English (the app and website are English-only).",
                                            fontSize = 12.sp, color = MutedText
                                        )
                                        }
                                    }
                                }
                            }
                            if (twoCol) {
                                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                                    Box(Modifier.weight(1f)) { profileCard() }
                                    Box(Modifier.weight(1f)) { prefsCard() }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                    profileCard()
                                    prefsCard()
                                }
                            }
                        }
                    }
                    item {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val twoCol = maxWidth >= 860.dp
                            val privacyCard: @Composable () -> Unit = {
                                WhiteCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            QrIcon(IconKind.Shield, Color(0xFF16A34A), size = 19.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Privacy & Security", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            QrIcon(IconKind.Check, MutedText, size = 16.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Two-Factor Authentication", fontSize = 13.sp, color = NavyText, modifier = Modifier.weight(1f))
                                            Text("Disabled", fontSize = 12.5.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            QrIcon(IconKind.Lock, MutedText, size = 16.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Change Password", fontSize = 13.sp, color = NavyText, modifier = Modifier.weight(1f))
                                            Text(
                                                "Update", fontSize = 12.5.sp, color = PurplePrimary, fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable {
                                                    val email = state.user?.email ?: ""
                                                    if (email.isNotBlank()) {
                                                        scope.launch {
                                                            val r = FirebaseApi.sendPasswordReset(email)
                                                            state.toast(
                                                                if (r.isSuccess) "Password reset email sent to $email"
                                                                else r.exceptionOrNull()?.message ?: "Could not send reset email"
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            val supportCard: @Composable () -> Unit = {
                                WhiteCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            QrIcon(IconKind.Help, BlueAccent, size = 19.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Support", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                        }
                                        Text("Need help? Contact our support team.", fontSize = 12.5.sp, color = MutedText)
                                        Button(
                                            onClick = { state.navigate(Screen.Support) },
                                            shape = RoundedCornerShape(9.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                                        ) { Text("Get Support", fontSize = 12.5.sp) }
                                    }
                                }
                            }
                            if (twoCol) {
                                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                                    Box(Modifier.weight(1f)) { privacyCard() }
                                    Box(Modifier.weight(1f)) { supportCard() }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                    privacyCard()
                                    supportCard()
                                }
                            }
                        }
                    }
                }
                "Appearance" -> {
                    item {
                        WhiteCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Theme", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(false to "Light", true to "Dark").forEach { (dark, label) ->
                                        Row(
                                            Modifier.clip(RoundedCornerShape(10.dp))
                                                .border(
                                                    if (state.darkMode == dark) 2.dp else 1.dp,
                                                    if (state.darkMode == dark) PurplePrimary else MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    state.darkMode = dark
                                                    try { window.localStorage.setItem("qm_dark", if (dark) "1" else "0") } catch (e: Exception) { }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            QrIcon(if (dark) IconKind.Moon else IconKind.Sun, PurplePrimary, size = 17.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(label, fontSize = 13.sp, color = NavyText)
                                        }
                                    }
                                }
                                Text("The theme applies instantly across the whole website.", fontSize = 12.sp, color = MutedText)
                            }
                        }
                    }
                }
                "Notifications" -> {
                    item {
                        WhiteCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Notifications", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                Text(
                                    "Announcements and your request updates live on the Notifications page.",
                                    fontSize = 12.5.sp, color = MutedText
                                )
                                Button(
                                    onClick = { state.navigate(Screen.Notifications) },
                                    shape = RoundedCornerShape(9.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                                ) { Text("Open Notifications", fontSize = 12.5.sp) }
                                Text(
                                    "Free-spin reward: turning notifications ON in the QR Master Android app gives +3 free spins (once per account). " +
                                        "Used so far: ${profile?.notifSpinsUsed ?: 0}. That reward follows your phone's notification setting, so it is managed in the app — spins work on both.",
                                    fontSize = 12.sp, color = MutedText
                                )
                            }
                        }
                    }
                }
                "Security" -> {
                    item {
                        WhiteCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Security", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                                Text("Signed in as ${state.user?.email ?: "guest"}", fontSize = 13.sp, color = MutedText)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            val email = state.user?.email ?: ""
                                            if (email.isNotBlank()) {
                                                scope.launch {
                                                    val r = FirebaseApi.sendPasswordReset(email)
                                                    state.toast(
                                                        if (r.isSuccess) "Password reset email sent to $email"
                                                        else r.exceptionOrNull()?.message ?: "Could not send reset email"
                                                    )
                                                }
                                            } else {
                                                state.navigate(Screen.Login)
                                            }
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                                    ) { Text("Change Password", fontSize = 12.5.sp) }
                                    if (state.user != null) {
                                        OutlinedButton(
                                            onClick = { state.signOut() },
                                            shape = RoundedCornerShape(9.dp)
                                        ) { Text("Logout", fontSize = 12.5.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------------- 9) Premium (reference, real plans) ---------------- */

data class PlanDef(val name: String, val desc: String, val price: Int, val per: String, val tag: String?)

val PremiumPlans = listOf(
    PlanDef("Free", "Basic Access", 0, "Forever", null),
    PlanDef("1 Month", "Best for Short Term", 99, "/ 1 Month", null),
    PlanDef("3 Months", "Great Value", 249, "/ 3 Months", null),
    PlanDef("6 Months", "Most Popular", 449, "/ 6 Months", "Popular"),
    PlanDef("1 Year", "Most Savings", 849, "/ 1 Year", null)
)

@Composable
fun PremiumScreen(state: AppState) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            // Hero banner (reference)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF312E81), Color(0xFF6D28D9), Color(0xFF4338CA))))
                    .padding(22.dp)
            ) {
              BoxWithConstraints(Modifier.fillMaxWidth()) {
                val heroWide = maxWidth >= 640.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.6f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QrIcon(IconKind.Crown, Color(0xFFFBBF24), size = 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Upgrade to Premium", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("Unlock more features and get the best experience!", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Spacer(Modifier.height(13.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Ad-Free Experience", "Advanced Tools", "Priority Support").forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(15.dp).clip(CircleShape).background(Color(0xFF22C55E)),
                                        contentAlignment = Alignment.Center
                                    ) { QrIcon(IconKind.Check, Color.White, size = 10.dp) }
                                    Spacer(Modifier.width(5.dp))
                                    Text(f, color = Color.White, fontSize = 11.5.sp)
                                }
                            }
                        }
                        if (state.profile?.isPremium == true) {
                            Spacer(Modifier.height(11.dp))
                            Text(
                                "You are a Premium member" +
                                    (if (state.profile!!.premiumExpiryMs > 0) " — valid till ${fmtDateTime(state.profile!!.premiumExpiryMs)}" else ""),
                                color = Color(0xFFFDE68A), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    // Gift illustration (simple shapes, reference-style)
                    if (heroWide) Box(Modifier.weight(0.7f).height(110.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(74.dp).rotate(4f).clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFEC4899), Color(0xFF9333EA))))
                        )
                        Box(Modifier.width(14.dp).height(74.dp).rotate(4f).background(Color(0xFFFDE68A)))
                        Box(Modifier.width(74.dp).height(14.dp).rotate(4f).background(Color(0xFFFDE68A)))
                        Box(
                            Modifier.offset(y = (-44).dp).size(17.dp).clip(CircleShape)
                                .border(4.dp, Color(0xFFFDE68A), CircleShape)
                        )
                        Box(
                            Modifier.offset(x = 16.dp, y = (-42).dp).size(15.dp).clip(CircleShape)
                                .border(4.dp, Color(0xFFFDE68A), CircleShape)
                        )
                    }
                }
              }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                PremiumPlans.forEach { plan ->
                    Box {
                        Column(
                            Modifier.width(196.dp).height(218.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(plan.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                            Text(plan.desc, fontSize = 11.5.sp, color = MutedText)
                            Spacer(Modifier.height(13.dp))
                            Text("₹${plan.price}", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = NavyText)
                            Text(plan.per, fontSize = 11.5.sp, color = MutedText)
                            Spacer(Modifier.weight(1f))
                            if (plan.price == 0) {
                                OutlinedButton(
                                    onClick = { },
                                    enabled = false,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Current Plan", fontSize = 12.5.sp) }
                            } else {
                                Button(
                                    onClick = {
                                        if (state.user == null) {
                                            state.navigate(Screen.Login)
                                            state.toast("Login first to upgrade")
                                        } else {
                                            state.toast("Complete the payment in the QR Master app — premium activates after approval.")
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Upgrade Now", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        if (plan.tag != null) {
                            Box(
                                Modifier.align(Alignment.TopCenter).offset(y = (-9).dp)
                                    .clip(RoundedCornerShape(10.dp)).background(PurplePrimary)
                                    .padding(horizontal = 11.dp, vertical = 3.dp)
                            ) {
                                Text(plan.tag!!, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                listOf(
                    Triple("Instant Access", IconKind.Zap, Color(0xFFF59E0B)),
                    Triple("Secure & Private", IconKind.Shield, Color(0xFF2563EB)),
                    Triple("24/7 Support", IconKind.Help, Color(0xFF16A34A)),
                    Triple("Cancel Anytime", IconKind.Check, Color(0xFF9333EA))
                ).forEach { (label, icon, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QrIcon(icon, color, size = 17.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 12.sp, color = NavyText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------------- Login / Register ---------------- */

@Composable
fun LoginScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun googleSignIn() {
        if (googleBusy || busy) return
        error = null
        googleBusy = true
        scope.launch {
            val r = FirebaseApi.signInWithGoogle()
            googleBusy = false
            r.onSuccess { u ->
                state.onLoggedIn(u)
                state.refreshProfile()
                state.toast("Signed in with Google")
                state.navigate(Screen.Dashboard)
            }.onFailure {
                error = it.message ?: "Google sign-in failed. Please try again."
            }
        }
    }

    fun submit() {
        if (busy) return
        error = null
        if (email.isBlank() || password.isBlank() || (isRegister && name.isBlank())) {
            error = "Please fill all fields."
            return
        }
        busy = true
        scope.launch {
            val r = if (isRegister) FirebaseApi.signUp(name.trim(), email.trim(), password)
            else FirebaseApi.signIn(email.trim(), password)
            busy = false
            r.onSuccess { u ->
                state.onLoggedIn(u)
                state.refreshProfile()
                state.toast(if (isRegister) "Account created — welcome!" else "Welcome back!")
                state.navigate(Screen.Dashboard)
            }.onFailure {
                error = it.message ?: "Login failed. Please try again."
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
        WhiteCard(Modifier.widthIn(max = 430.dp).fillMaxWidth()) {
            Column(
                Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                LogoMark(52.dp)
                Text("Welcome to QR Master", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyText)
                Text(
                    "Use the same account as the QR Master Android app.",
                    fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFF1F0FA)).padding(4.dp)
                ) {
                    listOf(false to "Login", true to "Create Account").forEach { (reg, label) ->
                        Box(
                            Modifier.weight(1f).height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRegister == reg) PurplePrimary else Color.Transparent)
                                .clickable { isRegister = reg; error = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (isRegister == reg) Color.White else MutedText
                            )
                        }
                    }
                }
                // Google sign-in (both tabs — label follows the tab)
                OutlinedButton(
                    onClick = { googleSignIn() },
                    enabled = !googleBusy && !busy,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (googleBusy) {
                        CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(19.dp))
                    } else {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .border(1.dp, Color(0xFFE2E2EF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("G", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isRegister) "Sign up with Google" else "Continue with Google",
                            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE6E4F2)))
                    Text("  or  ", fontSize = 12.sp, color = MutedText)
                    Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE6E4F2)))
                }
                if (isRegister) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        Box(
                            Modifier.size(36.dp).clickable { showPassword = !showPassword },
                            contentAlignment = Alignment.Center
                        ) {
                            QrIcon(if (showPassword) IconKind.EyeOff else IconKind.Eye, MutedText, size = 19.dp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Button(
                    onClick = { submit() },
                    enabled = !busy,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(19.dp))
                    else Text(if (isRegister) "Create Account" else "Login", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                }
                if (!isRegister) {
                    Text(
                        "Forgot password?",
                        fontSize = 12.5.sp, color = PurplePrimary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            if (email.isBlank()) {
                                error = "Enter your email above first."
                            } else {
                                scope.launch {
                                    val r = FirebaseApi.sendPasswordReset(email.trim())
                                    if (r.isSuccess) state.toast("Password reset email sent")
                                    else error = r.exceptionOrNull()?.message
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/* ---------------- Placeholder (pages outside the 9 core screens) ---------------- */

@Composable
fun PlaceholderScreen(state: AppState, title: String) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.TopCenter) {
        WhiteCard(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Column(
                Modifier.padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFF0EAFE)),
                    contentAlignment = Alignment.Center
                ) {
                    QrIcon(IconKind.Gear, PurplePrimary, size = 27.dp)
                }
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = NavyText)
                Text(
                    "This page is part of the full website build and arrives after the design approval of the 9 core screens.",
                    fontSize = 13.sp, color = MutedText, textAlign = TextAlign.Center
                )
                Button(
                    onClick = { state.navigate(Screen.Dashboard) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Back to Home") }
            }
        }
    }
}
