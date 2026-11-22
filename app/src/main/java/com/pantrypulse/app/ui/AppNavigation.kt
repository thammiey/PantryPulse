package com.pantrypulse.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pantrypulse.app.notifications.ExpiryNotifier
import kotlinx.coroutines.launch
import com.pantrypulse.app.ui.community.CommunityProfileCard
import com.pantrypulse.app.ui.community.CommunityViewModel
import com.pantrypulse.app.data.community.ListingStatus
import com.pantrypulse.app.ui.community.GiveAwayFormScreen
import com.pantrypulse.app.ui.community.GiveawaysScreen
import com.pantrypulse.app.ui.community.ShopPostScreen
import com.pantrypulse.app.ui.components.ActionRow
import com.pantrypulse.app.ui.components.PulseMark
import com.pantrypulse.app.ui.screens.AuthScreen
import com.pantrypulse.app.ui.screens.EditorScreen
import com.pantrypulse.app.ui.screens.EditorViewModel
import com.pantrypulse.app.ui.screens.HomeScreen
import com.pantrypulse.app.ui.screens.ProfileScreen
import com.pantrypulse.app.ui.screens.RecipeDetailScreen
import com.pantrypulse.app.ui.screens.RecipesScreen
import com.pantrypulse.app.ui.screens.RecipesViewModel
import com.pantrypulse.app.ui.screens.RescueScreen
import com.pantrypulse.app.ui.screens.RescueViewModel
import com.pantrypulse.app.ui.screens.ShoppingScreen
import com.pantrypulse.app.ui.scan.BarcodeScanScreen
import com.pantrypulse.app.ui.scan.ExpiryScanScreen
import com.pantrypulse.app.ui.scan.ReceiptScanScreen
import com.pantrypulse.app.ui.theme.PulseTheme

object Routes {
    const val HOME = "home"
    const val RECIPES = "recipes?focus={focus}"
    const val SHOPPING = "shopping"
    const val PROFILE = "profile"
    const val EDITOR = "editor?id={id}&barcode={barcode}"
    const val SCAN_BARCODE = "scan/barcode?mode={mode}"
    const val SCAN_EXPIRY = "scan/expiry"
    const val SCAN_RECEIPT = "scan/receipt"
    const val RECIPE = "recipe/{id}"
    const val RESCUE = "rescue?productId={productId}"
    const val GIVEAWAYS = "giveaways"
    const val GIVE_AWAY = "giveaway/new?productId={productId}"
    const val SHOP_POST = "giveaway/shop"

    fun giveAway(productId: Long?) = "giveaway/new?productId=${productId ?: -1}"

    fun editor(id: Long? = null, barcode: String? = null) = "editor?id=${id ?: -1}" + (barcode?.let { "&barcode=$it" } ?: "")
    fun recipes(focus: String? = null) = "recipes" + (focus?.let { "?focus=${android.net.Uri.encode(it)}" } ?: "")
    fun rescue(productId: Long?) = "rescue?productId=${productId ?: -1}"
    fun recipe(id: String) = "recipe/${android.net.Uri.encode(id)}"
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val session by vm.session.collectAsState()
    when {
        session == null -> Splash()
        session?.isSignedIn != true -> AuthScreen(vm)
        else -> MainShell(vm)
    }
}

@Composable
private fun Splash() {
    val c = PulseTheme.colors
    Box(Modifier.fillMaxSize().background(Color(0xFF17140F)), contentAlignment = Alignment.Center) {
        PulseMark(Modifier.size(width = 120.dp, height = 70.dp), c.accent, stroke = 7.dp)
    }
}

private data class Tab(val route: String, val base: String, val icon: ImageVector, val label: String)

private val tabs = listOf(
    Tab(Routes.HOME, "home", Icons.Outlined.GridView, "Pantry"),
    Tab(Routes.RECIPES, "recipes", Icons.Outlined.Restaurant, "Recipes"),
    Tab(Routes.GIVEAWAYS, "giveaways", Icons.Outlined.VolunteerActivism, "Giveaways"),
    Tab(Routes.SHOPPING, "shopping", Icons.Outlined.Checklist, "Shopping list"),
    Tab(Routes.PROFILE, "profile", Icons.Outlined.Person, "Profile"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(vm: MainViewModel) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val activityOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }
    var showAddSheet by remember { mutableStateOf(false) }
    // Shared with the Giveaways screen so the floating button and the feed show the same data.
    val giveawayVm = appViewModel(owner = activityOwner) { CommunityViewModel(it) }
    val community by giveawayVm.state.collectAsState()

    // Keep "N free nearby" fresh whenever the person moves between main screens.
    LaunchedEffect(currentRoute, community.connected) {
        if (showBar) giveawayVm.refreshNearbyBadge(context)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            // A newer message replaces the one on screen instead of queueing behind it,
            // and messages with an action time out rather than staying forever.
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    msg.text, msg.actionLabel, withDismissAction = msg.actionLabel == null,
                    duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
            }
        }
    }

    // Messages belong to the screen that raised them; clear them when the screen changes.
    LaunchedEffect(currentRoute) { snackbar.currentSnackbarData?.dismiss() }

    // Ask once for notification permission so expiry alerts can be shown (Android 13+).
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!askedNotifications && Build.VERSION.SDK_INT >= 33 && !ExpiryNotifier.canNotify(context)) {
            askedNotifications = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = c.paper,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (showBar) 96.dp else 0.dp)) { data ->
                Snackbar(data, containerColor = Color(0xFF17140F), contentColor = Color(0xFFF2EFE9), actionColor = c.accent, dismissActionContentColor = Color(0xFFF2EFE9), shape = RoundedCornerShape(20.dp))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            AppNavHost(nav, vm, padding, activityOwner) { showAddSheet = true }
            if (showBar) {
                FloatingNavBar(
                    current = currentRoute,
                    shoppingCount = vm.shopping.collectAsState().value.count { !it.checked },
                    onTab = { tab -> nav.navigate(tab.base) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    onAdd = { showAddSheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                // One floating button with a tidy menu of shortcuts; free food nearby comes first.
                val freeNearby = giveawayVm.nearby.filter { it.status == ListingStatus.AVAILABLE }
                val closest = freeNearby.minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
                val quickActions = buildList {
                    if (currentRoute != Routes.GIVEAWAYS) add(QuickAction(
                        Icons.Outlined.VolunteerActivism,
                        if (closest == null) "Giveaways" else "${freeNearby.size} free nearby" + (closest.distanceLabel?.let { " · $it" } ?: ""),
                        highlight = closest != null,
                    ) {
                        giveawayVm.setUrgency(false) // closest first
                        nav.navigate(Routes.GIVEAWAYS) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
                    })
                    add(QuickAction(Icons.Outlined.QrCodeScanner, "Scan barcode or QR") { nav.navigate("scan/barcode?mode=new") })
                    add(QuickAction(Icons.Outlined.DocumentScanner, "Scan a receipt") { nav.navigate(Routes.SCAN_RECEIPT) })
                    add(QuickAction(Icons.Outlined.Edit, "Type it in") { nav.navigate(Routes.editor()) })
                }
                QuickActionsFab(quickActions, badgeCount = freeNearby.size)
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = c.paper) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Add to your pantry", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                ActionRow(Icons.Outlined.QrCodeScanner, "Scan a barcode", { showAddSheet = false; nav.navigate("scan/barcode?mode=new") }, "Fills in the name, brand and category")
                ActionRow(Icons.Outlined.DocumentScanner, "Scan a receipt", { showAddSheet = false; nav.navigate(Routes.SCAN_RECEIPT) }, "Paper photo, screenshot or pasted text")
                ActionRow(Icons.Outlined.Edit, "Type it in", { showAddSheet = false; nav.navigate(Routes.editor()) }, "Then scan the expiry date from the pack", tint = c.accent)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    vm: MainViewModel,
    padding: androidx.compose.foundation.layout.PaddingValues,
    activityOwner: ViewModelStoreOwner,
    onAdd: () -> Unit,
) {
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm, padding,
                onEdit = { nav.navigate(Routes.editor(it)) },
                onProfile = { nav.navigate("profile") { launchSingleTop = true } },
                onRescue = { nav.navigate(Routes.rescue(it)) },
                onRecipesFor = { nav.navigate(Routes.recipes(it)) { launchSingleTop = true } },
                onAdd = onAdd,
                onGiveAway = { nav.navigate(Routes.giveAway(it)) },
            )
        }
        composable(Routes.GIVEAWAYS) {
            val cvm = appViewModel(owner = activityOwner) { CommunityViewModel(it) }
            GiveawaysScreen(vm, cvm, padding, onNewGiveaway = { nav.navigate(Routes.giveAway(null)) }, onShopPost = { nav.navigate(Routes.SHOP_POST) })
        }
        composable(Routes.GIVE_AWAY, arguments = listOf(navArgument("productId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val cvm = appViewModel(owner = activityOwner) { CommunityViewModel(it) }
            val pid = entry.arguments?.getLong("productId")?.takeIf { it > 0 }
            val community by cvm.state.collectAsState()
            if (community.connected) {
                GiveAwayFormScreen(vm, cvm, pid,
                    onDone = { nav.navigate(Routes.GIVEAWAYS) { popUpTo(Routes.HOME) } },
                    onBack = { nav.popBackStack() })
            } else {
                // Not linked to the community yet: show the connect card first.
                GiveawaysScreen(vm, cvm, padding, onNewGiveaway = {}, onShopPost = {})
            }
        }
        composable(Routes.SHOP_POST) {
            val cvm = appViewModel(owner = activityOwner) { CommunityViewModel(it) }
            ShopPostScreen(vm, cvm, onDone = { nav.navigate(Routes.GIVEAWAYS) { popUpTo(Routes.HOME) } }, onBack = { nav.popBackStack() })
        }
        composable(Routes.RECIPES, arguments = listOf(navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val rvm = appViewModel(owner = activityOwner) { RecipesViewModel(it) }
            RecipesScreen(vm, rvm, entry.arguments?.getString("focus"), padding) { nav.navigate(Routes.recipe(it)) }
        }
        composable(Routes.RECIPE) { entry ->
            val rvm = appViewModel(owner = activityOwner) { RecipesViewModel(it) }
            RecipeDetailScreen(entry.arguments?.getString("id").orEmpty(), vm, rvm) { nav.popBackStack() }
        }
        composable(Routes.SHOPPING) { ShoppingScreen(vm, padding) }
        composable(Routes.PROFILE) {
            val cvm = appViewModel(owner = activityOwner) { CommunityViewModel(it) }
            ProfileScreen(vm, padding, onRescue = { nav.navigate(Routes.rescue(null)) }, community = {
                CommunityProfileCard(vm, cvm) { nav.navigate("giveaways") { launchSingleTop = true } }
            })
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val evm = appViewModel { EditorViewModel(it, id) }
            val handle = entry.savedStateHandle
            LaunchedEffect(Unit) {
                val arg = entry.arguments?.getString("barcode")
                if (arg != null && handle.get<Boolean>("arg_used") != true) {
                    handle["arg_used"] = true
                    handle["barcode"] = arg
                }
            }
            val scannedBarcode by handle.getStateFlow<String?>("barcode", null).collectAsState()
            val scannedExpiry by handle.getStateFlow<Long?>("expiry", null).collectAsState()
            EditorScreen(
                vm, evm, scannedBarcode, scannedExpiry,
                onConsumeScan = { handle["barcode"] = null; handle["expiry"] = null },
                onScanBarcode = { nav.navigate("scan/barcode?mode=result") },
                onScanExpiry = { nav.navigate(Routes.SCAN_EXPIRY) },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.SCAN_BARCODE, arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "new" })) { entry ->
            val mode = entry.arguments?.getString("mode")
            BarcodeScanScreen(
                onResult = { code ->
                    if (mode == "result") {
                        nav.previousBackStackEntry?.savedStateHandle?.set("barcode", code)
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.editor(barcode = code)) { popUpTo(Routes.SCAN_BARCODE) { inclusive = true } }
                    }
                },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.SCAN_EXPIRY) {
            ExpiryScanScreen(
                onResult = { date ->
                    nav.previousBackStackEntry?.savedStateHandle?.set("expiry", date.toEpochDay())
                    nav.popBackStack()
                },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.SCAN_RECEIPT) { ReceiptScanScreen(vm, onDone = { nav.popBackStack() }, onClose = { nav.popBackStack() }) }
        composable(Routes.RESCUE, arguments = listOf(navArgument("productId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val pid = entry.arguments?.getLong("productId")?.takeIf { it > 0 }
            val rvm = appViewModel { RescueViewModel(it, pid) }
            RescueScreen(vm, rvm) { nav.popBackStack() }
        }
    }
}

/** The floating ink pill navigation from the design, with the orange add button in the middle. */
@Composable
private fun FloatingNavBar(current: String?, shoppingCount: Int, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = PulseTheme.colors
    val ink = Color(0xFF17140F)
    val paper = Color(0xFFF2EFE9)
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(50)).background(ink).padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("nav_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items: List<Tab?> = listOf(tabs[0], tabs[1], null, tabs[2], tabs[3], tabs[4])
        items.forEach { tab ->
            if (tab == null) {
                Box(
                    Modifier.size(58.dp).clip(CircleShape).background(c.accent)
                        .clickable(role = Role.Button, onClick = onAdd)
                        .semantics { contentDescription = "Add item" }
                        .testTag("nav_add"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, tint = ink, modifier = Modifier.size(32.dp)) }
            } else {
                val active = current == tab.route
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(if (active) c.accent else Color.Transparent)
                        .clickable(role = Role.Tab) { onTab(tab) }
                        .semantics { contentDescription = tab.label; selected = active }
                        .testTag("nav_${tab.base}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, null, tint = if (active) ink else paper, modifier = Modifier.size(26.dp))
                    if (tab.base == "shopping" && shoppingCount > 0 && !active) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(10.dp).clip(CircleShape).background(c.accent),
                        )
                    }
                }
            }
        }
    }
}
