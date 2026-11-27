package com.pantrypulse.app.ui.community

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pantrypulse.app.data.community.ClaimMode
import com.pantrypulse.app.data.community.LatLon
import com.pantrypulse.app.data.community.NewListing
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.components.formatQty
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Pickup settings shared by a single giveaway and a shop's batch post. */
class PickupSettings(initialDeadline: Long) {
    var deadline by mutableStateOf(initialDeadline)
    var claimMode by mutableStateOf(ClaimMode.DIRECT)
    var areaLabel by mutableStateOf("")
    var address by mutableStateOf("")
    var instructions by mutableStateOf("")
    var location by mutableStateOf<LatLon?>(null)
}

private val NOTE_CHIPS = listOf("Unopened", "Sealed", "Homemade", "Vegetarian", "Contains nuts", "Keep chilled")

/** Default deadline: the end of the item's best-before day, but no more than 2 days away. */
fun defaultDeadline(expiry: LocalDate?, now: LocalDateTime = LocalDateTime.now()): Long {
    val zone = ZoneId.systemDefault()
    val twoDays = now.plusDays(2).withHour(20).withMinute(0)
    val byExpiry = expiry?.atTime(20, 0)
    val chosen = listOfNotNull(byExpiry, twoDays).filter { it.isAfter(now.plusHours(1)) }.minOrNull() ?: now.plusHours(3)
    return chosen.atZone(zone).toInstant().toEpochMilli()
}

@Composable
fun GiveAwayFormScreen(vm: MainViewModel, cvm: CommunityViewModel, productId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val c = PulseTheme.colors
    val state by cvm.state.collectAsState()
    var product by remember { mutableStateOf<ProductEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }
    val settings = remember { PickupSettings(defaultDeadline(null)) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productId) {
        if (productId != null) {
            vm.container.pantry.product(productId)?.let { p ->
                product = p
                title = p.name
                quantity = formatQty(p.quantity, p.unit)
                settings.deadline = defaultDeadline(p.expiryDate)
            }
        }
        state.user?.takeIf { it.isShop }?.let { u ->
            settings.areaLabel = u.shopName.orEmpty()
            settings.address = u.shopAddress.orEmpty()
            if (u.lat != null && u.lon != null) settings.location = LatLon(u.lat, u.lon)
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.paper).statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, size = 44.dp)
        EyebrowText("Give away nearby")
        Text(if (product != null) "Pass on your ${product!!.name}" else "Give something away", style = MaterialTheme.typography.headlineLarge)
        product?.let { Text(Expiry.label(it.expiryDate), style = MaterialTheme.typography.bodySmall, color = c.accent) }

        PulseTextField(title, { title = it.take(80) }, "What are you giving away?")
        PulseTextField(quantity, { quantity = it.take(40) }, "Amount, e.g. 2 tubs, 500 g")
        EyebrowText("Good to know")
        ChipRow {
            NOTE_CHIPS.forEach { t -> PillChip(t, t in tags, { if (t in tags) tags.remove(t) else tags.add(t) }) }
        }
        PulseTextField(notes, { notes = it.take(200) }, "Notes (optional)", singleLine = false, minLines = 2)

        PickupSettingsForm(cvm, settings)

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton(if (posting) "Posting…" else "Post giveaway", {
            val loc = settings.location
            when {
                title.isBlank() -> error = "Add a name for the item."
                loc == null -> error = "Set the pickup location first."
                else -> {
                    posting = true; error = null
                    val allNotes = (tags + listOf(notes.trim()).filter { it.isNotEmpty() }).joinToString(" · ")
                    cvm.create(listOf(
                        NewListing(
                            title = title.trim(), category = product?.category?.name ?: "OTHER", quantityText = quantity.trim(), notes = allNotes,
                            itemExpiry = product?.expiryDate?.toString(), pickupDeadline = settings.deadline, lat = loc.lat, lon = loc.lon,
                            areaLabel = settings.areaLabel.trim(), pickupAddress = settings.address.trim(), pickupInstructions = settings.instructions.trim(),
                            claimMode = settings.claimMode, clientProductId = product?.id,
                        ),
                    )) { err ->
                        posting = false
                        if (err == null) { vm.message("“${title.trim()}” is live for people nearby."); onDone() } else error = err
                    }
                }
            }
        }, Modifier.fillMaxWidth().navigationBarsPadding(), accent = true, enabled = !posting)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupSettingsForm(cvm: CommunityViewModel, s: PickupSettings) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ensureLocationOn = rememberEnsureLocationOn(cvm.container.location)
    var locating by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }
    fun locate() {
        locating = true
        scope.launch(Dispatchers.Main) {
            s.location = cvm.currentLocation(context)
            locating = false
            if (s.location == null) cvm.message("Couldn't get your location. Turn on location services and try again.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ensureLocationOn { locate() } else cvm.message("Location permission is needed to place your giveaway.")
    }

    PulseCard {
        EyebrowText("Pickup point")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (s.location != null) c.accent else c.paper), contentAlignment = Alignment.Center) {
                Icon(if (s.location != null) Icons.Rounded.Check else Icons.Outlined.MyLocation, null, tint = c.ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    locating -> "Finding you…"
                    s.location != null -> "Location set. People only see a rough distance."
                    else -> "Use your current location as the pickup point."
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
        }
        SecondaryButton(if (s.location == null) "Use my location" else "Update location", {
            if (cvm.container.location.hasPermission(context)) ensureLocationOn { locate() }
            else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }, icon = Icons.Outlined.MyLocation)
        PulseTextField(s.areaLabel, { s.areaLabel = it.take(80) }, "Area shown publicly, e.g. Near City Library")
        PulseTextField(s.address, { s.address = it.take(200) }, "Exact address (private)",
            supportingText = "Only shown to the person whose claim is confirmed")
        PulseTextField(s.instructions, { s.instructions = it.take(300) }, "Pickup instructions (private)", singleLine = false)
    }

    PulseCard {
        EyebrowText("Pick up by")
        Text(deadlineLabel(s.deadline).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("deadline_label"))
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        ChipRow {
            PillChip("In 3 hours", false, { s.deadline = at(now.plusHours(3)) })
            if (now.hour < 20) PillChip("Tonight 9 pm", false, { s.deadline = at(now.toLocalDate().atTime(21, 0)) })
            PillChip("Tomorrow noon", false, { s.deadline = at(now.toLocalDate().plusDays(1).atTime(12, 0)) })
            PillChip("Tomorrow 8 pm", false, { s.deadline = at(now.toLocalDate().plusDays(1).atTime(20, 0)) })
            PillChip("Pick…", false, { pickDate = true })
        }
    }

    PulseCard {
        EyebrowText("Who gets it")
        ModeOption("Allow direct claim", "First come, first served. The first person to tap Claim gets it.", s.claimMode == ClaimMode.DIRECT) { s.claimMode = ClaimMode.DIRECT }
        ModeOption("Require my approval", "Requests queue up. When you accept one person, everyone else is told automatically.", s.claimMode == ClaimMode.APPROVAL) { s.claimMode = ClaimMode.APPROVAL }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    pickTime = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text("Next", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.muted) } },
        ) { DatePicker(state) }
    }
    pickTime?.let { date ->
        val state = rememberTimePickerState(initialHour = 20, initialMinute = 0)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickTime = null },
            containerColor = c.paper,
            title = { Text("Pickup time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = date.atTime(LocalTime.of(state.hour, state.minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (chosen <= System.currentTimeMillis()) cvm.message("Choose a time in the future.") else s.deadline = chosen
                    pickTime = null
                }) { Text("Set", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel", color = c.muted) } },
        )
    }
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val c = PulseTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.ink else c.hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp).testTag("mode_$title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) c.accent else c.paper).border(1.dp, c.hairline, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

/** Shopkeeper flow: pick several pantry items (or type extras) and post them in one go. */
@Composable
fun ShopPostScreen(vm: MainViewModel, cvm: CommunityViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val c = PulseTheme.colors
    val state by cvm.state.collectAsState()
    val products by vm.products.collectAsState()
    val candidates = products.filter { it.category.kind != ItemKind.MEDICINE }
    val selected = remember { mutableStateListOf<Long>() }
    val extras = remember { mutableStateListOf<Pair<String, String>>() }
    var extraName by remember { mutableStateOf("") }
    var extraQty by remember { mutableStateOf("") }
    val settings = remember { PickupSettings(defaultDeadline(LocalDate.now())) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        state.user?.let { u ->
            settings.areaLabel = u.shopName.orEmpty()
            settings.address = u.shopAddress.orEmpty()
            if (u.lat != null && u.lon != null) settings.location = LatLon(u.lat, u.lon)
        }
        // Pre-select stock that expires within two days.
        candidates.filter { (Expiry.daysLeft(it.expiryDate) ?: 99) in 0..2 }.forEach { selected.add(it.id) }
    }
    val count = selected.size + extras.size

    Column(
        Modifier.fillMaxSize().background(c.paper).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, size = 44.dp)
        EyebrowText(state.user?.shopName ?: "Shop")
        Text("Post today's surplus", style = MaterialTheme.typography.headlineLarge)
        Text("Each item becomes its own giveaway, so one person claims each.", style = MaterialTheme.typography.bodyMedium, color = c.muted)

        PulseCard(padding = 12.dp) {
            EyebrowText("From your stock")
            if (candidates.isEmpty()) Text("No stock in PantryPulse yet — add extra items below.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            candidates.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { if (p.id in selected) selected.remove(p.id) else selected.add(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(p.id in selected, { if (it) selected.add(p.id) else selected.remove(p.id) },
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.ink))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text("${formatQty(p.quantity, p.unit)} · ${Expiry.label(p.expiryDate)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }
        PulseCard(padding = 12.dp) {
            EyebrowText("Extra items")
            extras.forEachIndexed { i, (n, q) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$n${if (q.isNotBlank()) " · $q" else ""}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { extras.removeAt(i) }) { Text("Remove", color = c.muted) }
                }
            }
            PulseTextField(extraName, { extraName = it.take(80) }, "Item, e.g. Day-old croissants")
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseTextField(extraQty, { extraQty = it.take(40) }, "Amount", Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SecondaryButton("Add", { if (extraName.isNotBlank()) { extras.add(extraName.trim() to extraQty.trim()); extraName = ""; extraQty = "" } })
            }
        }

        PickupSettingsForm(cvm, settings)

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton(if (posting) "Posting…" else "Post $count item${if (count == 1) "" else "s"}", {
            val loc = settings.location
            when {
                count == 0 -> error = "Choose at least one item."
                loc == null -> error = "Set the pickup location first."
                else -> {
                    posting = true; error = null
                    fun draft(title: String, qty: String, category: String, expiry: String?, pid: Long?) = NewListing(
                        title, category, qty, "", expiry, settings.deadline, loc.lat, loc.lon, settings.areaLabel.trim(),
                        settings.address.trim(), settings.instructions.trim(), settings.claimMode, pid,
                    )
                    val drafts = candidates.filter { it.id in selected }.map {
                        draft(it.name, formatQty(it.quantity, it.unit), it.category.name, it.expiryDate?.toString(), it.id)
                    } + extras.map { (n, q) -> draft(n, q, "OTHER", null, null) }
                    cvm.create(drafts) { err ->
                        posting = false
                        if (err == null) { vm.message("$count item${if (count == 1) " is" else "s are"} live for local people."); onDone() } else error = err
                    }
                }
            }
        }, Modifier.fillMaxWidth().navigationBarsPadding(), accent = true, enabled = !posting)
    }
}
