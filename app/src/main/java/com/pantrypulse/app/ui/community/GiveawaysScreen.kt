package com.pantrypulse.app.ui.community

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantrypulse.app.data.community.ClaimMode
import com.pantrypulse.app.data.community.Listing
import com.pantrypulse.app.data.community.ListingStatus
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.EmptyState
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.components.SegmentedPills
import com.pantrypulse.app.ui.theme.Bricolage
import com.pantrypulse.app.ui.theme.DmMono
import com.pantrypulse.app.ui.theme.PulseTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "today 8:00 pm", "tomorrow 10:00 am" or "Fri 14 Nov, 6:00 pm". */
fun deadlineLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    val time = t.format(DateTimeFormatter.ofPattern("h:mm a")).lowercase()
    return when (t.toLocalDate()) {
        today -> "today $time"
        today.plusDays(1) -> "tomorrow $time"
        else -> t.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ", $time"
    }
}

fun statusLabel(l: Listing): String = when {
    l.status == ListingStatus.RESERVED && l.claimedByMe -> "Reserved for you"
    l.status == ListingStatus.RESERVED -> "Reserved by ${l.claimedByName ?: "someone"}"
    else -> l.status.label
}

@Composable
fun GiveawaysScreen(
    vm: MainViewModel,
    cvm: CommunityViewModel,
    contentPadding: PaddingValues,
    onNewGiveaway: () -> Unit,
    onShopPost: () -> Unit,
) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val state by cvm.state.collectAsState()
    var selected by remember { mutableStateOf<Listing?>(null) }
    val ensureLocationOn = rememberEnsureLocationOn(cvm.container.location)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ensureLocationOn { cvm.locate(context) } else vm.message("Location is needed to find giveaways near you.")
    }
    fun locate() {
        if (cvm.container.location.hasPermission(context)) ensureLocationOn { cvm.locate(context) }
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) { cvm.message = { vm.message(it) } }
    LaunchedEffect(state.connected) {
        if (state.connected) {
            cvm.refreshAll()
            if (cvm.location == null && cvm.container.location.hasPermission(context)) ensureLocationOn { cvm.locate(context) }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.paper).testTag("giveaways_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 20.dp, bottom = contentPadding.calculateBottomPadding() + 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    EyebrowText(if (state.isShop) "Shop · ${state.user?.shopName.orEmpty()}" else "Community")
                    Text("Giveaways", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 6.dp))
                }
                if (state.connected) RoundButton(Icons.Outlined.Refresh, "Refresh giveaways", { cvm.refreshAll() })
            }
            Text(
                "Give away food you won't use and pick up free food nearby. Exact addresses are only shared once a claim is confirmed.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
        }
        if (!state.connected) {
            item { ConnectCard(vm, cvm) }
            return@LazyColumn
        }
        item {
            val labels = if (state.isShop) listOf("Nearby", "My shop", "Getting") else GiveawayTab.entries.map { it.label }
            SegmentedPills(labels, cvm.tab.ordinal, { cvm.tab = GiveawayTab.entries[it] })
        }
        when (cvm.tab) {
            GiveawayTab.NEARBY -> {
                item {
                    ChipRow {
                        listOf(2.0, 5.0, 10.0, 25.0).forEach { km -> PillChip("${km.toInt()} km", cvm.radiusKm == km, { cvm.setRadius(km) }) }
                    }
                    ChipRow {
                        PillChip("Nearest", !cvm.sortByUrgency, { cvm.setUrgency(false) })
                        PillChip("Expiring soonest", cvm.sortByUrgency, { cvm.setUrgency(true) })
                    }
                }
                if (cvm.location == null) {
                    item {
                        EmptyState(Icons.Outlined.MyLocation, "Where are you?", "We use your approximate location to show giveaways within your chosen distance.",
                            actionLabel = "Use my location", onAction = { locate() })
                    }
                } else if (cvm.loading && cvm.nearby.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
                } else if (cvm.nearby.isEmpty()) {
                    item {
                        EmptyState(Icons.Outlined.VolunteerActivism, "Nothing within ${cvm.radiusKm.toInt()} km yet",
                            if (cvm.error != null) cvm.error!! else "Try a wider distance, or be the first to give something away.",
                            actionLabel = "Give something away", onAction = onNewGiveaway)
                    }
                }
                if (cvm.location != null) items(cvm.nearby, key = { it.id }) { l -> GiveawayCard(l, onClick = { selected = l }) }
            }
            GiveawayTab.GIVING -> {
                if (state.isShop) item { ShopDashboard(cvm, onShopPost) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(if (state.isShop) "Add one item" else "Give something away", onNewGiveaway, Modifier.weight(1f), accent = !state.isShop, icon = Icons.Outlined.Add)
                    }
                }
                val active = cvm.mine.filter { it.status == ListingStatus.AVAILABLE || it.status == ListingStatus.RESERVED }
                val closed = cvm.mine - active.toSet()
                if (cvm.mine.isEmpty()) {
                    item { EmptyState(Icons.Outlined.VolunteerActivism, "Nothing listed yet", "Tap an item in your pantry and choose “Give away nearby”, or add one here.") }
                }
                items(active, key = { it.id }) { l -> DonorListingCard(l, cvm) }
                if (closed.isNotEmpty()) {
                    item { EyebrowText("History", Modifier.padding(top = 8.dp)) }
                    items(closed.take(20), key = { it.id }) { l -> DonorListingCard(l, cvm) }
                }
            }
            GiveawayTab.GETTING -> {
                if (cvm.claims.isEmpty()) {
                    item { EmptyState(Icons.Outlined.VolunteerActivism, "No claims yet", "Claim something from Nearby and its pickup details will appear here.") }
                }
                items(cvm.claims, key = { it.id }) { l -> ClaimCard(l, cvm) }
            }
        }
    }

    selected?.let { l ->
        ListingSheet(cvm.nearby.firstOrNull { it.id == l.id } ?: l, cvm, onDismiss = { selected = null })
    }
}

/** Ticket-style card for the public feed: the stub shows the (approximate) distance. */
@Composable
fun GiveawayCard(l: Listing, onClick: () -> Unit) {
    val c = PulseTheme.colors
    val hoursLeft = (l.pickupDeadline - System.currentTimeMillis()) / 3_600_000.0
    val urgent = hoursLeft < 6
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(c.card).border(1.dp, c.hairline, shape)
            .clickable(onClick = onClick).testTag("giveaway_${l.title}"),
    ) {
        Column(
            Modifier.width(76.dp).fillMaxHeight().background(if (urgent) c.accent else c.peach).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            val km = l.distanceKm ?: 0.0
            Text(if (km <= 0.5) "<1" else if (km % 1.0 == 0.0) km.toInt().toString() else km.toString(),
                fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Color(0xFF17140F))
            Text("KM", fontFamily = DmMono, fontSize = 12.sp, letterSpacing = 1.sp, color = Color(0xFF17140F))
        }
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (l.donorIsShop) { Spacer(Modifier.width(8.dp)); Badge("SHOP") }
            }
            Text(listOf(l.quantityText, l.areaLabel.ifBlank { null }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Pick up by ${deadlineLabel(l.pickupDeadline)}", style = MaterialTheme.typography.labelMedium, color = if (urgent) c.accent else c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(if (l.claimMode == ClaimMode.DIRECT) "FIRST COME" else "DONOR PICKS")
                l.myRequestStatus?.let { Badge(if (it == "pending") "REQUESTED" else it.uppercase()) }
            }
        }
    }
}

@Composable
fun Badge(text: String, accent: Boolean = false) {
    val c = PulseTheme.colors
    Text(
        text, fontFamily = DmMono, fontSize = 10.sp, letterSpacing = 1.sp,
        color = if (accent) Color(0xFF17140F) else c.ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (accent) c.accent else c.paper).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingSheet(l: Listing, cvm: CommunityViewModel, onDismiss: () -> Unit) {
    val c = PulseTheme.colors
    var message by remember { mutableStateOf("") }
    val busy = l.id in cvm.busy
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EyebrowText("${l.distanceLabel ?: ""} · pick up by ${deadlineLabel(l.pickupDeadline)}", color = c.accent)
            Text(l.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOf(l.quantityText, l.itemExpiry?.let { "best before $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            if (l.notes.isNotBlank()) Text("“${l.notes}”", style = MaterialTheme.typography.bodyLarge)
            PulseCard(padding = 14.dp) {
                Text((if (l.donorIsShop) "From ${l.donorName}" else "From ${l.donorName}") + (l.areaLabel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall)
                l.donorOpeningHours?.let { Text("Open $it", style = MaterialTheme.typography.bodySmall, color = c.muted) }
                Text("The exact address is shared with you once your claim is confirmed.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            when {
                l.claimMode == ClaimMode.DIRECT -> {
                    Text("First come, first served — the first person to claim gets it.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    PrimaryButton(if (busy) "Claiming…" else "Claim this", { cvm.claim(l) { onDismiss(); cvm.tab = GiveawayTab.GETTING } },
                        Modifier.fillMaxWidth(), accent = true, enabled = !busy)
                }
                l.myRequestStatus == "pending" -> {
                    Text("You've asked for this. ${l.donorName} will choose who gets it.", style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton("Withdraw my request", { cvm.withdraw(l); onDismiss() }, Modifier.fillMaxWidth())
                }
                l.myRequestStatus == "rejected" -> Text("${l.donorName} chose someone else for this one.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                else -> {
                    Text("${l.donorName} reviews requests and picks one person.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    PulseTextField(message, { message = it.take(200) }, "Message (optional), e.g. “Can collect at 6”")
                    PrimaryButton(if (busy) "Sending…" else "Request this", { cvm.request(l, message); onDismiss() },
                        Modifier.fillMaxWidth(), accent = true, enabled = !busy)
                }
            }
        }
    }
}

@Composable
private fun ShopDashboard(cvm: CommunityViewModel, onShopPost: () -> Unit) {
    val c = PulseTheme.colors
    val s = cvm.stats
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(c.peachSoft).padding(10.dp)) {
                androidx.compose.material3.Icon(Icons.Outlined.Storefront, null, tint = c.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Shop dashboard", style = MaterialTheme.typography.titleMedium)
                Text("Post today's surplus for local people to collect.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        Row {
            Stat("${s?.available ?: 0}", "live", Modifier.weight(1f))
            Stat("${s?.pendingRequests ?: 0}", "requests", Modifier.weight(1f))
            Stat("${s?.reserved ?: 0}", "booked", Modifier.weight(1f))
            Stat("${s?.pickedUp ?: 0}", "rescued", Modifier.weight(1f))
        }
        PrimaryButton("Post surplus items", onShopPost, Modifier.fillMaxWidth(), accent = true, icon = Icons.Outlined.Storefront)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = PulseTheme.colors.muted)
    }
}

/** A donor's own listing with its request queue and lifecycle actions. */
@Composable
private fun DonorListingCard(l: Listing, cvm: CommunityViewModel) {
    val c = PulseTheme.colors
    val busy = l.id in cvm.busy
    PulseCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium)
                Text("${l.quantityText.ifBlank { "1" }} · ${if (l.claimMode == ClaimMode.DIRECT) "first come" else "you pick"} · by ${deadlineLabel(l.pickupDeadline)}",
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Badge(statusLabel(l).uppercase(), accent = l.status == ListingStatus.RESERVED)
        }
        when (l.status) {
            ListingStatus.AVAILABLE -> {
                val pending = l.pendingRequests
                if (l.claimMode == ClaimMode.APPROVAL) {
                    EyebrowText(if (pending.isEmpty()) "No requests yet" else "${pending.size} request${if (pending.size > 1) "s" else ""} — accept one")
                    pending.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                if (r.message.isNotBlank()) Text("“${r.message}”", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            TextButton(onClick = { cvm.reject(l, r.id) }, enabled = !busy) { Text("Decline", color = c.muted) }
                            PrimaryButton("Accept", { cvm.accept(l, r.id) }, accent = true, enabled = !busy)
                        }
                    }
                }
                SecondaryButton("Withdraw giveaway", { cvm.cancelListing(l) })
            }
            ListingStatus.RESERVED -> {
                Text("${l.claimedByName ?: "Someone"} is collecting this. Ask them for code ${l.pickup?.code ?: "—"}.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("If they don't turn up, release it so someone else can have it.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Picked up", { cvm.pickedUp(l) }, Modifier.weight(1f), accent = true, enabled = !busy)
                    SecondaryButton("Release", {
                        val deadline = if (l.pickupDeadline <= System.currentTimeMillis()) System.currentTimeMillis() + 24 * 3_600_000L else null
                        cvm.release(l, deadline)
                    }, Modifier.weight(1f))
                }
            }
            else -> {}
        }
    }
}

/** Something the user claimed or asked for, with pickup details once confirmed. */
@Composable
private fun ClaimCard(l: Listing, cvm: CommunityViewModel) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val busy = l.id in cvm.busy
    PulseCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium)
                Text("From ${l.donorName} · by ${deadlineLabel(l.pickupDeadline)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            val label = when {
                l.claimedByMe -> statusLabel(l)
                l.myRequestStatus == "pending" -> "Waiting for donor"
                l.myRequestStatus == "rejected" -> "Not this time"
                else -> l.status.label
            }
            Badge(label.uppercase(), accent = l.claimedByMe && l.status == ListingStatus.RESERVED)
        }
        val p = l.pickup
        if (l.claimedByMe && l.status == ListingStatus.RESERVED && p != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    EyebrowText("Pickup address")
                    Text(p.address.ifBlank { "Ask the donor in person" }, style = MaterialTheme.typography.titleSmall)
                    if (p.instructions.isNotBlank()) Text(p.instructions, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    EyebrowText("Code")
                    Text(p.code ?: "—", fontFamily = DmMono, fontSize = 26.sp, letterSpacing = 4.sp, color = c.accent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Directions", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(l.title)})"))) }
                }, Modifier.weight(1f), icon = Icons.Outlined.Directions)
                PrimaryButton("Got it", { cvm.pickedUp(l) }, Modifier.weight(1f), accent = true, enabled = !busy)
            }
            TextButton(onClick = { cvm.cancelClaim(l) }, enabled = !busy) { Text("I can't make it — release it", color = c.muted) }
        } else if (l.myRequestStatus == "pending" && l.status == ListingStatus.AVAILABLE) {
            TextButton(onClick = { cvm.withdraw(l) }, enabled = !busy) { Text("Withdraw request", color = c.muted) }
        }
    }
}

/** Shown until the local profile is linked to an online account. */
@Composable
private fun ConnectCard(vm: MainViewModel, cvm: CommunityViewModel) {
    val c = PulseTheme.colors
    val user by vm.user.collectAsState()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    PulseCard {
        Text("Join the community", style = MaterialTheme.typography.titleLarge)
        Text("Giveaways go through PantryPulse's online service so nearby people can see them. Confirm your password to connect ${user?.email.orEmpty()}.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted)
        PulseTextField(password, { password = it }, "Your PantryPulse password",
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        PrimaryButton(if (busy) "Connecting…" else "Connect", {
            val u = user ?: return@PrimaryButton
            busy = true; error = null
            cvm.connect(u.email, password, u.displayName, null) { err -> busy = false; error = err; if (err == null) vm.message("Connected — welcome to the community!") }
        }, Modifier.fillMaxWidth(), accent = true, enabled = !busy && password.length >= 6)
    }
}
