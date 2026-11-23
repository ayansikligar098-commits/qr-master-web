package qrmaster.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import qrmaster.web.data.FirebaseApi
import qrmaster.web.data.SessionStore
import qrmaster.web.data.UserProfile
import qrmaster.web.data.UserSession

enum class Screen { Dashboard, AllTools, QrGenerator, Wallet, Premium, Login, Placeholder }

data class ToolDef(
    val name: String,
    val desc: String,
    val category: String,
    val rating: String,
    val icon: IconKind,
    val tint: Color,
    val target: Screen,
    val placeholderTitle: String = ""
)

val ToolCategories = listOf("All", "QR Tools", "PDF Tools", "Image Tools", "Earn", "Utilities")

val AllToolsList = listOf(
    ToolDef("QR Code Generator", "Text, URL, UPI ke liye QR banao", "QR Tools", "4.9", IconKind.Qr, Color(0xFF7C3AED), Screen.QrGenerator),
    ToolDef("QR Code Scanner", "Camera ya photo se QR scan karo", "QR Tools", "4.8", IconKind.Scan, Color(0xFF2563EB), Screen.Placeholder, "QR Scanner"),
    ToolDef("Barcode Generator", "Code 128, EAN, UPC barcodes", "QR Tools", "4.8", IconKind.Barcode, Color(0xFF0891B2), Screen.Placeholder, "Barcode Generator"),
    ToolDef("PDF Converter", "PDF ko image/Word me badlo", "PDF Tools", "4.9", IconKind.Pdf, Color(0xFFDC2626), Screen.Placeholder, "PDF Converter"),
    ToolDef("PDF Editor", "PDF pages edit, sign aur annotate", "PDF Tools", "4.7", IconKind.Edit, Color(0xFFEA580C), Screen.Placeholder, "PDF Editor"),
    ToolDef("Merge & Split PDF", "PDF jodo ya alag karo", "PDF Tools", "4.8", IconKind.Merge, Color(0xFFDB2777), Screen.Placeholder, "PDF Merger & Splitter"),
    ToolDef("Compress PDF", "PDF ka size chhota karo", "PDF Tools", "4.7", IconKind.Compress, Color(0xFF65A30D), Screen.Placeholder, "PDF Compressor"),
    ToolDef("PDF Security", "PDF par password lagao/hatayo", "PDF Tools", "4.6", IconKind.Lock, Color(0xFF475569), Screen.Placeholder, "PDF Security"),
    ToolDef("Document Scanner", "Document scan karke PDF banao", "Image Tools", "4.9", IconKind.FileText, Color(0xFF0D9488), Screen.Placeholder, "Document Scanner"),
    ToolDef("Image Compressor", "Photo ka size kam karo", "Image Tools", "4.8", IconKind.Image, Color(0xFF9333EA), Screen.Placeholder, "Image Tools"),
    ToolDef("Photo Editor", "Crop, filter aur background tools", "Image Tools", "4.6", IconKind.Edit, Color(0xFFF59E0B), Screen.Placeholder, "Photo Editor"),
    ToolDef("UPI QR Generator", "UPI payment QR turant banao", "Utilities", "4.9", IconKind.Zap, Color(0xFF16A34A), Screen.QrGenerator),
    ToolDef("Spin & Earn", "Roz spin karo, paise jeeto", "Earn", "4.8", IconKind.Spin, Color(0xFF7C3AED), Screen.Placeholder, "Spin & Earn"),
    ToolDef("Tasks & Earn", "Tasks pure karo, reward pao", "Earn", "4.7", IconKind.Task, Color(0xFF2563EB), Screen.Placeholder, "Tasks & Earn"),
    ToolDef("Watch & Earn", "Ads dekho aur kamao", "Earn", "4.6", IconKind.Play, Color(0xFFDC2626), Screen.Placeholder, "Watch & Earn"),
    ToolDef("Daily Check-in", "Roz check-in, roz spin", "Earn", "4.7", IconKind.Calendar, Color(0xFF0891B2), Screen.Placeholder, "Daily Check-in")
)

class AppState {
    var screen by mutableStateOf(Screen.Dashboard)
    var placeholderTitle by mutableStateOf("")
    var dark by mutableStateOf(false)
    var session by mutableStateOf<UserSession?>(null)
    var profile by mutableStateOf<UserProfile?>(null)
    var query by mutableStateOf("")
    var snackbar by mutableStateOf<String?>(null)
    val activity = mutableStateListOf<String>()

    fun navigate(s: Screen, title: String = "") {
        screen = s
        if (s == Screen.Placeholder) placeholderTitle = title
    }

    fun openTool(tool: ToolDef) {
        navigate(tool.target, tool.placeholderTitle.ifEmpty { tool.name })
    }

    fun toast(msg: String) { snackbar = msg }

    fun logActivity(text: String) {
        activity.add(0, text)
        while (activity.size > 8) activity.removeAt(activity.size - 1)
    }
}

@Composable
fun App() {
    val state = remember { AppState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val saved = SessionStore.load()
        if (saved != null) {
            val now = qrmaster.web.data.nowMillis()
            val usable = if (saved.expiresAtMillis <= now + 60_000) {
                FirebaseApi.refreshSession(saved) ?: saved
            } else saved
            state.session = usable
            state.profile = FirebaseApi.fetchProfile(usable)
        }
    }

    LaunchedEffect(state.snackbar) {
        if (state.snackbar != null) {
            delay(3200)
            state.snackbar = null
        }
    }

    QrMasterTheme(darkTheme = state.dark) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val isDesktop = maxWidth >= 820.dp
            val isWide = maxWidth >= 1180.dp
            if (isDesktop) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(state, Modifier.width(264.dp).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(state, showMenu = false) { }
                        ScreenContent(state, wide = isWide, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                            Sidebar(state, Modifier.width(280.dp).fillMaxHeight()) {
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(state, showMenu = true) { scope.launch { drawerState.open() } }
                        ScreenContent(state, wide = false, modifier = Modifier.weight(1f))
                        BottomNav(state)
                    }
                }
            }
            // Snackbar
            state.snackbar?.let { msg ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1B2E),
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(msg, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenContent(state: AppState, wide: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (state.screen) {
            Screen.Dashboard -> DashboardScreen(state, wide)
            Screen.AllTools -> AllToolsScreen(state)
            Screen.QrGenerator -> QrGeneratorScreen(state)
            Screen.Wallet -> WalletScreen(state)
            Screen.Premium -> PremiumScreen(state)
            Screen.Login -> LoginScreen(state)
            Screen.Placeholder -> PlaceholderScreen(state)
        }
    }
}

@Composable
fun Sidebar(state: AppState, modifier: Modifier = Modifier, onNavigate: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, color = colors.surface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 18.dp)) {
            // Logo
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(PurpleDeep, BlueAccent))),
                    contentAlignment = Alignment.Center
                ) {
                    QrIcon(IconKind.Logo, Color.White, size = 26.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("QR Master", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("All-in-One Tools", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(22.dp))

            SideSection("MAIN")
            SideItem(state, IconKind.Home, "Home", Screen.Dashboard, onNavigate)
            SideItem(state, IconKind.Grid, "All Tools", Screen.AllTools, onNavigate)

            SideSection("TOOLS")
            SideItem(state, IconKind.Qr, "QR Generator", Screen.QrGenerator, onNavigate)
            SideItem(state, IconKind.Scan, "QR Scanner", Screen.Placeholder, onNavigate, "QR Scanner")
            SideItem(state, IconKind.Barcode, "Barcode Generator", Screen.Placeholder, onNavigate, "Barcode Generator")
            SideItem(state, IconKind.Pdf, "PDF Tools", Screen.Placeholder, onNavigate, "PDF Tools")
            SideItem(state, IconKind.Image, "Image Tools", Screen.Placeholder, onNavigate, "Image Tools")
            SideItem(state, IconKind.FileText, "Document Scanner", Screen.Placeholder, onNavigate, "Document Scanner")

            SideSection("WALLET & EARN")
            SideItem(state, IconKind.Wallet, "Wallet", Screen.Wallet, onNavigate)
            SideItem(state, IconKind.Spin, "Spin & Earn", Screen.Placeholder, onNavigate, "Spin & Earn")
            SideItem(state, IconKind.Calendar, "Daily Check-in", Screen.Placeholder, onNavigate, "Daily Check-in")
            SideItem(state, IconKind.Gift, "Bonus Codes", Screen.Placeholder, onNavigate, "Bonus Codes")
            SideItem(state, IconKind.Task, "Tasks & Earn", Screen.Placeholder, onNavigate, "Tasks & Earn")
            SideItem(state, IconKind.Users, "Referral", Screen.Placeholder, onNavigate, "Referral System")
            SideItem(state, IconKind.Play, "Watch & Earn", Screen.Placeholder, onNavigate, "Watch & Earn")
            SideItem(state, IconKind.Crown, "Premium", Screen.Premium, onNavigate)

            SideSection("GENERAL")
            SideItem(state, IconKind.Clock, "History", Screen.Placeholder, onNavigate, "History")
            SideItem(state, IconKind.Bell, "Notifications", Screen.Placeholder, onNavigate, "Notifications")
            SideItem(state, IconKind.Gear, "Settings", Screen.Placeholder, onNavigate, "Settings")
            SideItem(state, IconKind.Help, "Help & Support", Screen.Placeholder, onNavigate, "Help & Support")
            SideItem(state, IconKind.Info, "About Us", Screen.Placeholder, onNavigate, "About Us")

            Spacer(Modifier.height(18.dp))
            // User card at bottom
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    state.navigate(Screen.Login); onNavigate()
                }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(state.session?.email ?: "G", 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.profile?.name ?: state.session?.email?.substringBefore("@") ?: "Guest User",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            if (state.session != null) "Logged in" else "Login / Register",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                        )
                    }
                    QrIcon(IconKind.Right, colors.onSurfaceVariant, size = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun SideSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SideItem(state: AppState, icon: IconKind, label: String, target: Screen, onNavigate: () -> Unit, placeholderTitle: String = "") {
    val colors = MaterialTheme.colorScheme
    val selected = state.screen == target && (target != Screen.Placeholder || state.placeholderTitle == placeholderTitle)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable { state.navigate(target, placeholderTitle.ifEmpty { label }); onNavigate() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QrIcon(icon, if (selected) colors.primary else colors.onSurfaceVariant, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
    }
}

@Composable
fun TopBar(state: AppState, showMenu: Boolean, onMenu: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showMenu) {
                IconCircleButton(IconKind.Menu, colors.onSurface) { onMenu() }
            }
            // Search
            OutlinedTextField(
                value = state.query,
                onValueChange = {
                    state.query = it
                    if (state.screen != Screen.Dashboard && state.screen != Screen.AllTools) state.navigate(Screen.Dashboard)
                },
                placeholder = { Text("Search tools... (QR, PDF, Image)") },
                leadingIcon = { QrIcon(IconKind.Search, colors.onSurfaceVariant, size = 20.dp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            Box {
                IconCircleButton(IconKind.Bell, colors.onSurface) { state.navigate(Screen.Placeholder, "Notifications") }
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444))
                        .align(Alignment.TopEnd)
                )
            }
            IconCircleButton(if (state.dark) IconKind.Sun else IconKind.Moon, colors.onSurface) { state.dark = !state.dark }
            if (state.session == null) {
                Button(onClick = { state.navigate(Screen.Login) }, shape = RoundedCornerShape(20.dp)) {
                    Text("Login")
                }
            } else {
                Box(Modifier.clickable { state.navigate(Screen.Login) }) {
                    AvatarCircle(state.session?.email ?: "U", 38.dp)
                }
            }
        }
    }
}

@Composable
fun IconCircleButton(icon: IconKind, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        QrIcon(icon, tint, size = 20.dp)
    }
}

@Composable
fun AvatarCircle(text: String, size: androidx.compose.ui.unit.Dp) {
    val initial = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(PurplePrimary, BlueAccent))),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun BottomNav(state: AppState) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            Triple(IconKind.Home, "Home", Screen.Dashboard),
            Triple(IconKind.Grid, "Tools", Screen.AllTools),
            Triple(IconKind.Qr, "Create", Screen.QrGenerator),
            Triple(IconKind.Wallet, "Wallet", Screen.Wallet),
            Triple(IconKind.Users, "Account", Screen.Login)
        )
        for ((icon, label, target) in items) {
            NavigationBarItem(
                selected = state.screen == target,
                onClick = { state.navigate(target) },
                icon = { QrIcon(icon, if (state.screen == target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) },
                label = { Text(label) }
            )
        }
    }
}
