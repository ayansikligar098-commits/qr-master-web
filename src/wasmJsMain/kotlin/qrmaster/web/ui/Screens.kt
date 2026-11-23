package qrmaster.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.launch
import qrmaster.web.data.FirebaseApi
import qrmaster.web.qr.QrCode
import qrmaster.web.qr.QrEcc

fun formatRupees(paise: Long): String {
    val rupees = paise / 100
    val rem = (paise % 100).toInt()
    return "₹$rupees.${rem.toString().padStart(2, '0')}"
}

/* ---------------- Shared bits ---------------- */

@Composable
fun QrCanvas(qr: QrCode, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White)) {
        val border = 4
        val total = qr.size + border * 2
        val cell = size.minDimension / total
        val ox = (size.width - cell * total) / 2
        val oy = (size.height - cell * total) / 2
        drawRect(Color.White, Offset(ox, oy), Size(cell * total, cell * total))
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    drawRect(
                        Color(0xFF14101F),
                        Offset(ox + (x + border) * cell, oy + (y + border) * cell),
                        Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolCard(tool: ToolDef, state: AppState) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { state.openTool(tool) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.width(252.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(tool.tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { QrIcon(tool.icon, tool.tint, size = 24.dp) }
                Spacer(Modifier.weight(1f))
                QrIcon(IconKind.Star, Color(0xFFF59E0B), size = 15.dp)
                Spacer(Modifier.width(3.dp))
                Text(tool.rating, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(tool.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(tool.desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, modifier = Modifier.height(34.dp))
            Spacer(Modifier.height(8.dp))
            Text("Use Tool →", style = MaterialTheme.typography.labelLarge, color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------------- Dashboard ---------------- */

@Composable
fun DashboardScreen(state: AppState, wide: Boolean) {
    val colors = MaterialTheme.colorScheme
    var category by remember { mutableStateOf("All") }
    val filtered = AllToolsList.filter {
        (category == "All" || it.category == category) &&
            (state.query.isBlank() || it.name.contains(state.query, ignoreCase = true) || it.desc.contains(state.query, ignoreCase = true))
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        HeroCard(state)
                        CategoryChips(category) { category = it }
                        ToolsGrid(filtered, state)
                    }
                    Column(Modifier.width(302.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        WalletMiniCard(state)
                        PremiumMiniCard(state)
                        RecentActivityCard(state)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    HeroCard(state)
                    CategoryChips(category) { category = it }
                    ToolsGrid(filtered, state)
                    WalletMiniCard(state)
                    PremiumMiniCard(state)
                    RecentActivityCard(state)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun HeroCard(state: AppState) {
    val heroQr = remember {
        try { QrCode.encodeText("https://ayansikligar098-commits.github.io/qr-master-web/", QrEcc.MEDIUM) } catch (e: Exception) { null }
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF5B21B6), Color(0xFF2563EB))))
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Create, Scan & Manage\nQR Codes Instantly",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "QR, Barcode, PDF aur Image tools — sab ek hi jagah. Apne app account se login karke wallet bhi yahi dekho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { state.navigate(Screen.QrGenerator) },
                            shape = RoundedCornerShape(24.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PurpleDeep)
                        ) { Text("Create QR Code", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { state.navigate(Screen.Placeholder, "QR Scanner") },
                            shape = RoundedCornerShape(24.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                        ) { Text("Scan QR Code") }
                    }
                }
                if (heroQr != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(118.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(6.dp)
                        ) {
                            QrCanvas(heroQr, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Scan karke site kholo", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (cat in ToolCategories) {
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = { Text(cat) },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
    // Section title below chips
    Text(
        "Popular Tools",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsGrid(tools: List<ToolDef>, state: AppState) {
    if (tools.isEmpty()) {
        Text("Koi tool nahi mila — search badal kar dekho.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (tool in tools) ToolCard(tool, state)
    }
}

@Composable
fun WalletMiniCard(state: AppState) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF16A34A).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { QrIcon(IconKind.Wallet, Color(0xFF16A34A), size = 22.dp) }
                Spacer(Modifier.width(10.dp))
                Text("Wallet Balance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            val paise = state.profile?.walletPaise
            when {
                state.session == null -> {
                    Text("Login karke apna live balance dekho", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { state.navigate(Screen.Login) }, shape = RoundedCornerShape(20.dp)) { Text("Login") }
                }
                paise != null -> {
                    Text(formatRupees(paise), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("App account ka live balance", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { state.navigate(Screen.Wallet) }, shape = RoundedCornerShape(20.dp)) { Text("Wallet Kholo") }
                    }
                }
                else -> {
                    Text("Balance load nahi hua", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { state.navigate(Screen.Wallet) }, shape = RoundedCornerShape(20.dp)) { Text("Wallet Kholo") }
                }
            }
        }
    }
}

@Composable
fun PremiumMiniCard(state: AppState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF7C2D12), Color(0xFFB45309))))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrIcon(IconKind.Crown, Color(0xFFFDE68A), size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Go Premium", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text("Saare tools unlimited, bina limits ke. Plans ₹99 se shuru.", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { state.navigate(Screen.Premium) },
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF92400E))
                ) { Text("Plans Dekho", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RecentActivityCard(state: AppState) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Recent Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (state.activity.isEmpty()) {
                Text("Abhi koi activity nahi. QR download karo ya login karo — yahan dikhega.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                for (item in state.activity) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrIcon(IconKind.Check, Color(0xFF16A34A), size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(item, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/* ---------------- All Tools ---------------- */

@Composable
fun AllToolsScreen(state: AppState) {
    val groups = ToolCategories.drop(1)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Column {
                Text("All Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Saare QR Master tools ek jagah", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (group in groups) {
            val tools = AllToolsList.filter {
                it.category == group && (state.query.isBlank() || it.name.contains(state.query, ignoreCase = true))
            }
            if (tools.isEmpty()) continue
            item {
                Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item { ToolsGrid(tools, state) }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

/* ---------------- QR Generator ---------------- */

@OptIn(ExperimentalEncodingApi::class, ExperimentalLayoutApi::class)
@Composable
fun QrGeneratorScreen(state: AppState) {
    val colors = MaterialTheme.colorScheme
    var content by remember { mutableStateOf("https://ayansikligar098-commits.github.io/qr-master-web/") }
    var eclIndex by remember { mutableStateOf(1) }
    val ecls = listOf(QrEcc.LOW, QrEcc.MEDIUM, QrEcc.QUARTILE, QrEcc.HIGH)
    val eclNames = listOf("Low", "Medium", "Quartile", "High")

    val qrResult = remember(content, eclIndex) {
        if (content.isBlank()) null
        else try { Result.success(QrCode.encodeText(content, ecls[eclIndex])) }
        catch (e: Exception) { Result.failure(e) }
    }
    val qr = qrResult?.getOrNull()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Column {
                Text("QR Code Generator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Text, link ya UPI — type karo, QR turant bane ga", color = colors.onSurfaceVariant)
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 900.dp) {
                    // Desktop workspace: settings panel + preview panel side by side
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1.15f)) {
                            QrSettingsCard(content, { content = it }, eclIndex, { eclIndex = it }, eclNames, qr)
                        }
                        Box(Modifier.weight(1f)) {
                            QrPreviewCard(state, content, qr)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        QrSettingsCard(content, { content = it }, eclIndex, { eclIndex = it }, eclNames, qr)
                        QrPreviewCard(state, content, qr)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrSettingsCard(
    content: String,
    onContentChange: (String) -> Unit,
    eclIndex: Int,
    onEclChange: (Int) -> Unit,
    eclNames: List<String>,
    qr: QrCode?
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("QR Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Quick Templates", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { onContentChange("https://") }, label = { Text("Website URL") })
                FilterChip(selected = false, onClick = { onContentChange("") }, label = { Text("Plain Text") })
                FilterChip(selected = false, onClick = { onContentChange("upi://pay?pa=yourname@upi&pn=Your%20Name&am=") }, label = { Text("UPI Payment") })
                FilterChip(selected = false, onClick = { onContentChange("WIFI:T:WPA;S:MyWiFi;P:password;;") }, label = { Text("Wi-Fi") })
                FilterChip(selected = false, onClick = { onContentChange("tel:+91") }, label = { Text("Phone Number") })
            }
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                label = { Text("QR me kya daalna hai — likho") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Error Correction Level", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                eclNames.forEachIndexed { i, name ->
                    FilterChip(selected = eclIndex == i, onClick = { onEclChange(i) }, label = { Text(name) })
                }
            }
            Text(
                "${content.length} characters" + (qr?.let { " • Version ${it.version} • ${it.size}×${it.size} modules" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun QrPreviewCard(state: AppState, content: String, qr: QrCode?) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Preview & Download", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            when {
                content.isBlank() -> {
                    Box(
                        Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Kuch likho —\nQR yahan bane ga", color = colors.onSurfaceVariant)
                    }
                }
                qr != null -> {
                    Box(Modifier.size(260.dp).clip(RoundedCornerShape(12.dp))) {
                        QrCanvas(qr, Modifier.fillMaxSize())
                    }
                    Text(content.take(60), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val png = PngEncoder.encodeQr(qr, 1024)
                                    downloadPngBase64("qr-master-qr.png", Base64.encode(png))
                                    state.logActivity("QR download hua — ${content.take(28)}")
                                    state.toast("QR download ho gaya")
                                } catch (e: Exception) {
                                    state.toast("Download fail — dobara try karo")
                                }
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.widthIn(min = 220.dp)
                    ) {
                        QrIcon(IconKind.Download, Color.White, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Download PNG", fontWeight = FontWeight.Bold)
                    }
                    Text("Phone se scan karke test karo — asli QR hai", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                else -> {
                    Text("Text bahut lamba hai — QR me itna data nahi aayega. Chhota karke try karo.", color = colors.error)
                }
            }
        }
    }
}

/* ---------------- Wallet ---------------- */

@Composable
fun WalletScreen(state: AppState) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Column {
                Text("Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Tumhare QR Master app account ka live wallet", color = colors.onSurfaceVariant)
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF5B21B6), Color(0xFF2563EB))))
                        .padding(22.dp)
                ) {
                    Column {
                        Text("Total Balance", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        val paise = state.profile?.walletPaise
                        when {
                            state.session == null -> Text("—", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            paise != null -> Text(formatRupees(paise), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            else -> Text("Load nahi hua", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.session?.email ?: "Login nahi hai",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item {
            if (state.session == null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Login karo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("App wala email/password use karo — balance, premium sab yahi dikhega.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { state.navigate(Screen.Login) }, shape = RoundedCornerShape(20.dp)) { Text("Login Page") }
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Account Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        DetailRow("Name", state.profile?.name ?: "—")
                        DetailRow("Email", state.session?.email ?: "—")
                        DetailRow("Premium", if (state.profile?.isPremium == true) "Active" else "Free Plan")
                        DetailRow("Balance", state.profile?.walletPaise?.let { formatRupees(it) } ?: "—")
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { state.toast("Add Money full website me aayega — POC me sirf live balance") }, shape = RoundedCornerShape(20.dp)) { Text("Add Money") }
                            OutlinedButton(onClick = { state.toast("Withdraw full website me aayega — POC me sirf live balance") }, shape = RoundedCornerShape(20.dp)) { Text("Withdraw") }
                        }
                        Text("Note: POC me wallet sirf READ hai — koi paisa move nahi hota.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------------- Premium ---------------- */

@Composable
fun PremiumScreen(state: AppState) {
    val colors = MaterialTheme.colorScheme
    data class Plan(val name: String, val price: String, val per: String, val credits: String, val best: Boolean)
    val plans = listOf(
        Plan("1 Month", "₹99", "per month", "100 HD Credits FREE", false),
        Plan("3 Months", "₹249", "3 months", "300 HD Credits FREE", false),
        Plan("6 Months", "₹449", "6 months", "600 HD Credits FREE", false),
        Plan("1 Year", "₹849", "per year", "1200 HD Credits FREE", true)
    )
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                QrIcon(IconKind.Crown, Color(0xFFB45309), size = 40.dp)
                Spacer(Modifier.height(6.dp))
                Text("QR Master Premium", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Saare tools bina limit ke — app aur website dono par", color = colors.onSurfaceVariant)
            }
        }
        items(plans) { plan ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = if (plan.best) 4.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (plan.best) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF59E0B)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("BEST VALUE", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(plan.price, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    Text("${plan.per} • ${plan.credits}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text("✓ Saare tools unlimited  ✓ Roz 10 Spin  ✓ Priority support", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Button(
                        onClick = { state.toast("Payment website par jald aayega — abhi premium Android app se lo (POC)") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose ${plan.name}") }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

/* ---------------- Login / Account ---------------- */

@Composable
fun LoginScreen(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(14.dp)) }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (state.session != null) {
                        // Account view
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(state.session?.email ?: "U", 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(state.profile?.name ?: "QR Master User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(state.session?.email ?: "", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        DetailRow("Wallet", state.profile?.walletPaise?.let { formatRupees(it) } ?: "—")
                        DetailRow("Premium", if (state.profile?.isPremium == true) "Active" else "Free Plan")
                        DetailRow("User ID", (state.session?.uid ?: "").take(18) + "…")
                        Button(
                            onClick = {
                                FirebaseApi.signOut()
                                state.session = null
                                state.profile = null
                                state.logActivity("Logout hua")
                                state.toast("Logout ho gaya")
                                state.navigate(Screen.Dashboard)
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QrIcon(IconKind.Logout, Color.White, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Logout")
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(PurpleDeep, BlueAccent))),
                                contentAlignment = Alignment.Center
                            ) { QrIcon(IconKind.Logo, Color.White, size = 34.dp) }
                            Spacer(Modifier.height(10.dp))
                            Text(if (isRegister) "Create Account" else "Welcome Back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "QR Master app wala hi account chalega",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Box(Modifier.clickable { showPassword = !showPassword }.padding(8.dp)) {
                                    QrIcon(if (showPassword) IconKind.EyeOff else IconKind.Eye, colors.onSurfaceVariant, size = 20.dp)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        error?.let {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.error.copy(alpha = 0.1f)).padding(10.dp)) {
                                Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = {
                                if (busy) return@Button
                                if (email.isBlank() || password.isBlank()) { error = "Email aur password dono bharo."; return@Button }
                                busy = true; error = null
                                scope.launch {
                                    val result = if (isRegister) FirebaseApi.signUp(email.trim(), password) else FirebaseApi.signIn(email.trim(), password)
                                    busy = false
                                    result.onSuccess { session ->
                                        state.session = session
                                        state.profile = FirebaseApi.fetchProfile(session)
                                        state.logActivity(if (isRegister) "Naya account bana — ${session.email}" else "Login hua — ${session.email}")
                                        state.toast(if (isRegister) "Account ban gaya — welcome!" else "Welcome back!")
                                        state.navigate(Screen.Dashboard)
                                    }.onFailure { e ->
                                        error = e.message ?: "Login fail ho gaya."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (isRegister) "Register" else "Login", fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isRegister) "Pehle se account hai?" else "Account nahi hai?", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            TextButton(onClick = { isRegister = !isRegister; error = null }) {
                                Text(if (isRegister) "Login karo" else "Register karo", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

/* ---------------- Placeholder ---------------- */

@Composable
fun PlaceholderScreen(state: AppState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) { QrIcon(IconKind.Info, colors.primary, size = 40.dp) }
        Spacer(Modifier.height(16.dp))
        Text(state.placeholderTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ye page full QR Master website me bane ga.\nIs POC me abhi ye live hai: Dashboard, QR Generator (asli QR + download), Wallet (live balance) aur Firebase Login.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 480.dp)
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = { state.navigate(Screen.Dashboard) }, shape = RoundedCornerShape(22.dp)) {
            QrIcon(IconKind.Back, Color.White, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Dashboard par jao")
        }
    }
}
