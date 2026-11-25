package qrmaster.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import qrmaster.web.data.AppReleaseInfo
import qrmaster.web.data.EarnApi
import qrmaster.web.data.FirebaseApi
import qrmaster.web.data.Profile
import qrmaster.web.data.RELEASES_PAGE_URL
import qrmaster.web.data.WebUser
import qrmaster.web.data.ensureReferralProfile
import qrmaster.web.data.fetchAppRelease

enum class Screen {
    Dashboard, AllTools, QrGenerator, PdfConverter, Spin, Wallet, Tasks,
    Settings, Premium, Login, Placeholder,
    Barcode, QrScan, ImageTools, Scanner, RemoveBg, ResizeImage, CompressPdf, CompressImage, PdfEditor, PassportPhoto,
    CompressKb, SignatureMaker, CropRotate, JoinPhotos, PhotoEditor, PhotoWatermark,
    AgeCalc, BmiCalc, UnitConvert, CurrencyConvert, PercentCalc,
    Bonus, Invite, History, Notifications, Support, About
}

data class QrPreset(val typeLabel: String, val fieldLabel: String, val initialContent: String)

val NavyText = Color(0xFF191938)
val MutedText = Color(0xFF6E6A85)

class AppState {
    private val _screen = mutableStateOf(Screen.Dashboard)

    /** The current screen. Assigning a DIFFERENT screen bumps
     *  [navEpoch]; the 760 dp shell swap never touches this property,
     *  which is how a screen can tell "the user navigated here" apart
     *  from "the shell recreated me after a layout flip". */
    var screen: Screen
        get() = _screen.value
        set(value) {
            if (_screen.value != value) navEpoch++
            _screen.value = value
        }

    /** Monotonic count of real navigations (see [screen]). */
    var navEpoch: Int = 0
        private set
    var placeholderTitle by mutableStateOf("")
    var user by mutableStateOf<WebUser?>(FirebaseApi.session)
    var profile by mutableStateOf<Profile?>(null)
    var profileLoading by mutableStateOf(false)
    var darkMode by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var toolsChip by mutableStateOf("All Tools")
    var qrPreset by mutableStateOf<QrPreset?>(null)
    var pdfTool by mutableStateOf("PDF Converter")
    var notifDot by mutableStateOf(false)
    var toastMessage by mutableStateOf<String?>(null)
    val activity = mutableStateListOf<String>()

    // Latest Android app release (version feed) — shared by the Home
    // download card and the sidebar Download App button. Null + not
    // failed = still loading.
    var appRelease by mutableStateOf<AppReleaseInfo?>(null)
    var appReleaseFailed by mutableStateOf(false)

    suspend fun loadAppRelease() {
        if (appRelease != null || appReleaseFailed) return
        val r = fetchAppRelease()
        if (r != null) appRelease = r else appReleaseFailed = true
    }

    fun navigate(s: Screen) { screen = s }

    fun openPlaceholder(title: String) {
        placeholderTitle = title
        screen = Screen.Placeholder
    }

    fun openQr(preset: QrPreset?) {
        qrPreset = preset
        screen = Screen.QrGenerator
    }

    fun openPdf(tool: String) {
        pdfTool = tool
        screen = Screen.PdfConverter
    }

    fun applySearch(q: String) {
        searchQuery = q
        screen = Screen.AllTools
    }

    fun toast(msg: String) { toastMessage = msg }

    fun logActivity(text: String) {
        activity.add(0, text)
        while (activity.size > 8) activity.removeAt(activity.size - 1)
    }

    suspend fun refreshProfile() {
        if (user == null) return
        profileLoading = true
        profile = FirebaseApi.fetchProfile()
        profileLoading = false
    }

    fun onLoggedIn(u: WebUser) { user = u }

    fun signOut() {
        FirebaseApi.signOut()
        user = null
        profile = null
        screen = Screen.Dashboard
        toastMessage = "Logged out"
    }
}

/**
 * The one download action behind the Home card and the sidebar button:
 * latest APK when the version feed loaded, the public releases page
 * when it failed, and an honest "still checking" toast while loading.
 */
fun downloadAppAction(state: AppState) {
    val rel = state.appRelease
    when {
        rel != null -> downloadUrl(rel.apkUrl, rel.fileName)
        state.appReleaseFailed -> openExternal(RELEASES_PAGE_URL)
        else -> state.toast("Checking for the latest app version…")
    }
}

@Composable
fun App() {
    val state = remember { AppState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            state.darkMode = window.localStorage.getItem("qm_dark") == "1"
            // Referral link landing (?ref=CODE): stash the code; the Invite
            // screen offers it for one-tap apply after sign-up/login.
            val search = window.location.search
            if (search.contains("ref=")) {
                val code = search.substringAfter("ref=")
                    .takeWhile { it.isLetterOrDigit() }
                if (code.isNotBlank()) window.localStorage.setItem("qm_pending_ref", code.uppercase())
            }
        } catch (e: Exception) { }
        if (state.user != null) state.refreshProfile()
        state.loadAppRelease()
    }
    LaunchedEffect(state.user) {
        if (state.user != null) {
            // App-parity presence + referral-index maintenance (best effort).
            try { EarnApi.ensureReferralProfile() } catch (e: Exception) { }
        }
    }
    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2800)
            state.toastMessage = null
        }
    }

    QrMasterTheme(darkTheme = state.darkMode) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 760.dp) {
                        DesktopShell(state)
                    } else {
                        MobileShell(state)
                    }
                }
                // Toast
                val toast = state.toastMessage
                if (toast != null) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF232038))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(toast, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    // keep scope referenced for future shell actions
    LaunchedEffect(scope) { }
}

@Composable
fun DesktopShell(state: AppState) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(state, Modifier.width(252.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(state)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScreenContent(state)
            }
        }
    }
}

@Composable
fun MobileShell(state: AppState) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Sidebar(state, Modifier.width(272.dp).fillMaxHeight()) {
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { scope.launch { drawerState.open() } },
                    contentAlignment = Alignment.Center
                ) {
                    QrIcon(IconKind.Menu, MaterialTheme.colorScheme.onSurface, size = 22.dp)
                }
                Spacer(Modifier.width(6.dp))
                LogoMark(30.dp)
                Spacer(Modifier.width(8.dp))
                Text("Kitnook", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = NavyText)
                Spacer(Modifier.weight(1f))
                AvatarButton(state)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScreenContent(state)
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf(
                    Triple(Screen.Dashboard, IconKind.Home, "Home"),
                    Triple(Screen.AllTools, IconKind.Grid, "Tools"),
                    Triple(Screen.QrGenerator, IconKind.Qr, "QR"),
                    Triple(Screen.Wallet, IconKind.Wallet, "Wallet"),
                    Triple(Screen.Premium, IconKind.Crown, "Premium")
                )
                items.forEach { (s, icon, label) ->
                    NavigationBarItem(
                        selected = state.screen == s,
                        onClick = { if (s == Screen.QrGenerator) state.openQr(null) else state.navigate(s) },
                        icon = { QrIcon(icon, if (state.screen == s) PurplePrimary else MutedText, size = 21.dp) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

/** Final brand logo (loaded once from the site root; vector mark until it arrives). */
object BrandLogo {
    var bitmap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var started = false
}

@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!BrandLogo.started) {
            BrandLogo.started = true
            scope.launch {
                try {
                    val dataUrl = fetchResourceDataUrl("logo-mark.png")
                    if (dataUrl != null) BrandLogo.bitmap = decodeBitmapFromDataUrl(dataUrl)
                } catch (e: Exception) { }
            }
        }
    }
    val bmp = BrandLogo.bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = "Kitnook",
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
                .background(PurplePrimary),
            contentAlignment = Alignment.Center
        ) {
            QrIcon(IconKind.Logo, Color.White, size = size * 0.68f)
        }
    }
}

@Composable
fun AvatarButton(state: AppState) {
    val initial = (state.profile?.name?.firstOrNull()
        ?: state.user?.email?.firstOrNull()
        ?: 'G').uppercaseChar().toString()
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(PurplePrimary)
            .clickable { state.navigate(if (state.user != null) Screen.Settings else Screen.Login) },
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun TopBar(state: AppState) {
    var query by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search (reference: wide rounded field)
        Row(
            Modifier.weight(1f).widthIn(max = 560.dp).height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrIcon(IconKind.Search, MutedText, size = 19.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp),
                cursorBrush = SolidColor(PurplePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.applySearch(query) }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Search for tools... (e.g., QR, PDF converter, calculator)",
                                color = MutedText, fontSize = 13.sp, maxLines = 1
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.weight(0.6f))
        // Theme toggle
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .clickable {
                    state.darkMode = !state.darkMode
                    try {
                        window.localStorage.setItem("qm_dark", if (state.darkMode) "1" else "0")
                    } catch (e: Exception) { }
                },
            contentAlignment = Alignment.Center
        ) {
            QrIcon(if (state.darkMode) IconKind.Moon else IconKind.Sun, MutedText, size = 20.dp)
        }
        Spacer(Modifier.width(4.dp))
        // Bell -> Notifications page; the dot lights only when the
        // Notifications screen found an announcement or pending items.
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .clickable { state.navigate(Screen.Notifications) },
            contentAlignment = Alignment.Center
        ) {
            QrIcon(IconKind.Bell, MutedText, size = 20.dp)
            if (state.notifDot) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 7.dp)
                        .size(8.dp).clip(CircleShape).background(Color(0xFFEF4444))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AvatarButton(state)
    }
}

@Composable
fun Sidebar(state: AppState, modifier: Modifier = Modifier, onNavigate: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.background(colors.surface)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoMark(36.dp)
            Spacer(Modifier.width(10.dp))
            Text("Kitnook", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = NavyText)
        }
        Spacer(Modifier.height(22.dp))

        NavItem(state, Screen.Dashboard, IconKind.Home, "Home", onNavigate)
        NavItem(state, Screen.AllTools, IconKind.Grid, "All Tools", onNavigate)
        NavItem(state, Screen.Wallet, IconKind.Wallet, "Wallet", onNavigate)
        NavItem(state, Screen.Spin, IconKind.Spin, "Spin & Earn", onNavigate, badge = "New")
        NavItem(state, Screen.Bonus, IconKind.Gift, "Bonus Code", onNavigate)
        NavItem(state, Screen.Invite, IconKind.Users, "Invite & Earn", onNavigate)
        NavItem(state, Screen.Premium, IconKind.Crown, "Premium", onNavigate, crownGold = true)

        Spacer(Modifier.height(18.dp))
        Text(
            "Tools", color = MutedText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(6.dp))
        NavItem(state, Screen.History, IconKind.Clock, "History", onNavigate)
        NavItem(state, Screen.Settings, IconKind.Gear, "Settings", onNavigate)
        NavItem(state, Screen.Support, IconKind.Help, "Help & Support", onNavigate)
        NavItem(state, Screen.About, IconKind.Info, "About Us", onNavigate)

        Spacer(Modifier.height(26.dp))
        // Go Premium card (reference: light lavender)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF0EAFE))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrIcon(IconKind.Crown, Color(0xFFF59E0B), size = 18.dp)
                Spacer(Modifier.width(7.dp))
                Text("Go Premium", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyText)
            }
            Spacer(Modifier.height(3.dp))
            Text("Unlock More Features", color = MutedText, fontSize = 11.5.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(PurplePrimary)
                    .clickable { state.navigate(Screen.Premium); onNavigate() },
                contentAlignment = Alignment.Center
            ) {
                Text("Upgrade Now", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Download the Android app (compact; version shows once the
        // version feed has loaded — see AppState.loadAppRelease).
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEFE9FF))
                .clickable { downloadAppAction(state); onNavigate() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrIcon(IconKind.Download, PurplePrimary, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text("Download App", color = PurpleDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            val rel = state.appRelease
            if (rel != null && rel.versionName.isNotBlank()) {
                Spacer(Modifier.weight(1f))
                Text("v${rel.versionName}", color = MutedText, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun NavItem(
    state: AppState,
    target: Screen,
    icon: IconKind,
    label: String,
    onNavigate: () -> Unit,
    badge: String? = null,
    placeholder: String? = null,
    crownGold: Boolean = false
) {
    val selected = if (placeholder != null) {
        state.screen == Screen.Placeholder && state.placeholderTitle == placeholder
    } else {
        state.screen == target
    }
    Row(
        Modifier.fillMaxWidth().height(41.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFFEFE9FF) else Color.Transparent)
            .clickable {
                if (placeholder != null) state.openPlaceholder(placeholder) else state.navigate(target)
                onNavigate()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QrIcon(
            icon,
            when {
                selected -> PurplePrimary
                crownGold -> Color(0xFFF59E0B)
                else -> Color(0xFF6E6A85)
            },
            size = 19.dp
        )
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            color = if (selected) PurplePrimary else Color(0xFF3B3852),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.5.sp
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(PurplePrimary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
