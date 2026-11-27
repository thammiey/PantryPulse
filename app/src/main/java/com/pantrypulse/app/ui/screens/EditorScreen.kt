package com.pantrypulse.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.Expiry
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.ChipRow
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PillChip
import com.pantrypulse.app.ui.components.PrimaryButton
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.PulseTextField
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.components.stubFor
import com.pantrypulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    mainVm: MainViewModel,
    vm: EditorViewModel,
    scannedBarcode: String?,
    scannedExpiry: Long?,
    onConsumeScan: () -> Unit,
    onScanBarcode: () -> Unit,
    onScanExpiry: () -> Unit,
    onClose: () -> Unit,
) {
    val c = PulseTheme.colors
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val locations by mainVm.locations.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var newLocation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedBarcode, scannedExpiry) {
        if (scannedBarcode != null) vm.onBarcode(scannedBarcode)
        if (scannedExpiry != null) vm.expiry = LocalDate.ofEpochDay(scannedExpiry)
        if (scannedBarcode != null || scannedExpiry != null) onConsumeScan()
    }

    Scaffold(
        containerColor = c.paper,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Edit item" else "Add item", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Box(Modifier.padding(start = 12.dp)) { RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClose, size = 44.dp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper),
            )
        },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Barcode
            PulseCard {
                EyebrowText("Barcode")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseTextField(vm.barcode, { vm.barcode = it.filter(Char::isDigit) }, "Barcode number", Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(10.dp))
                    RoundButton(Icons.Outlined.QrCodeScanner, "Scan barcode", onScanBarcode, filled = true)
                }
                if (vm.barcode.length >= 8 && vm.lookup == LookupState.Idle) {
                    SecondaryButton("Look up product", { vm.onBarcode(vm.barcode) })
                }
                when (val l = vm.lookup) {
                    LookupState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp)); Text("Looking up product…", style = MaterialTheme.typography.bodySmall)
                    }
                    is LookupState.Found -> Text("✓ Filled in from ${l.source}", style = MaterialTheme.typography.bodySmall, color = c.fresh)
                    LookupState.NotFound -> Text("Not in the product database yet — fill in the details below.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    is LookupState.Failed -> Text(l.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    LookupState.Idle -> {}
                }
            }

            // Name & brand
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.imageUrl != null) {
                    AsyncImage(vm.imageUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(c.card))
                    Spacer(Modifier.width(12.dp))
                }
                PulseTextField(
                    vm.name, { vm.name = it }, "Name", Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )
            }
            PulseTextField(
                vm.brand, { vm.brand = it }, "Brand (optional)",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            // Category
            CategoryDropdown(vm.category, vm::selectCategory)

            // What medicines and cosmetics are for
            if (vm.category.kind != com.pantrypulse.app.domain.ItemKind.FOOD) {
                val isMedicine = vm.category.kind == com.pantrypulse.app.domain.ItemKind.MEDICINE
                val shown = if (vm.useInfoFor == vm.name.trim()) vm.useInfo else UseState.Idle
                PulseCard {
                    EyebrowText("What it's used for")
                    when (shown) {
                        UseState.Idle -> {
                            Text(
                                "Scan the barcode or type the name, then see what this is for and how it is generally used.",
                                style = MaterialTheme.typography.bodySmall, color = c.muted,
                            )
                            SecondaryButton("Show usage", { vm.loadUse() })
                        }
                        UseState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp)); Text("Looking it up…", style = MaterialTheme.typography.bodySmall)
                        }
                        is UseState.Found -> {
                            Text(shown.use.uses, style = MaterialTheme.typography.bodyMedium)
                            shown.use.directions?.let {
                                Text("How to use", style = MaterialTheme.typography.titleSmall)
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            shown.use.warnings?.let {
                                Text(if (isMedicine) "Good to know" else "Care", style = MaterialTheme.typography.titleSmall)
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Source: ${shown.use.source}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            if (isMedicine) Text(com.pantrypulse.app.domain.UseGuide.MEDICINE_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = c.accent)
                        }
                        UseState.None -> Text(
                            if (isMedicine) "No usage information found for this name. Check the leaflet in the box or ask a pharmacist. Typing the brand or generic name (for example \"paracetamol\") can help."
                            else "No usage information found for this product. Check the instructions on the pack.",
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                }
            }

            // Location
            EyebrowText("Stored in")
            ChipRow {
                locations.forEach { loc -> PillChip(loc.name, vm.locationId == loc.id, { vm.selectLocation(loc.id) }) }
                PillChip("+ New", false, { newLocation = "" })
            }

            // Quantity
            EyebrowText("Quantity")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Outlined.Remove, "Decrease quantity", {
                    val q = vm.quantity.replace(',', '.').toDoubleOrNull() ?: 1.0
                    vm.quantity = com.pantrypulse.app.ui.components.formatQty((q - 1).coerceAtLeast(1.0), "").trim()
                }, size = 48.dp)
                PulseTextField(vm.quantity, { vm.quantity = it }, "Amount", Modifier.weight(1f).padding(horizontal = 10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                RoundButton(Icons.Outlined.Add, "Increase quantity", {
                    val q = vm.quantity.replace(',', '.').toDoubleOrNull() ?: 0.0
                    vm.quantity = com.pantrypulse.app.ui.components.formatQty(q + 1, "").trim()
                }, size = 48.dp)
            }
            ChipRow { EditorViewModel.units.forEach { u -> PillChip(u, vm.unit == u, { vm.unit = u }) } }

            // Expiry
            PulseCard {
                EyebrowText("Expiry date")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (num, unit) = stubFor(vm.expiry)
                    Column(
                        Modifier.clip(RoundedCornerShape(18.dp)).background(if (vm.expiry == null) c.hairline else c.accent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(num, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                        Text(unit, style = MaterialTheme.typography.labelSmall, color = c.ink)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.expiry?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) ?: "Not set", style = MaterialTheme.typography.titleMedium)
                        Text(Expiry.label(vm.expiry), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Pick date", { showDatePicker = true }, Modifier.weight(1f), icon = Icons.Outlined.CalendarMonth)
                    SecondaryButton("Scan photo", onScanExpiry, Modifier.weight(1f), icon = Icons.Outlined.DocumentScanner)
                }
                val today = LocalDate.now()
                ChipRow {
                    PillChip("Tomorrow", false, { vm.expiry = today.plusDays(1) })
                    PillChip("+3 days", false, { vm.expiry = today.plusDays(3) })
                    PillChip("+1 week", false, { vm.expiry = today.plusWeeks(1) })
                    PillChip("+1 month", false, { vm.expiry = today.plusMonths(1) })
                    PillChip("Typical (${vm.category.shelfLifeDays}d)", false, { vm.expiry = vm.category.suggestedExpiry(today) })
                    if (vm.expiry != null) PillChip("Clear", false, { vm.expiry = null })
                }
            }

            PulseTextField(vm.notes, { vm.notes = it }, "Notes (optional)", singleLine = false, minLines = 2)

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            PrimaryButton(
                if (vm.isEditing) "Save changes" else "Add to pantry",
                { focusManager.clearFocus(); scope.launch(Dispatchers.Main) { if (vm.save()) { mainVm.message(if (vm.isEditing) "Saved." else "${vm.name.trim()} added."); onClose() } } },
                Modifier.fillMaxWidth().navigationBarsPadding(), accent = true,
            )
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (vm.expiry ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.expiry = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDatePicker = false
                }) { Text("OK", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = c.muted) } },
        ) { DatePicker(state) }
    }

    newLocation?.let { value ->
        AlertDialog(
            onDismissRequest = { newLocation = null },
            title = { Text("New storage spot") },
            text = { PulseTextField(value, { newLocation = it }, "e.g. Garage fridge, Desk drawer") },
            confirmButton = {
                TextButton(onClick = {
                    val n = value.trim()
                    if (n.isNotEmpty()) scope.launch(Dispatchers.Main) {
                        val hh = mainVm.session.value?.householdId ?: return@launch
                        val id = mainVm.container.pantry.addLocation(hh, n)
                        vm.selectLocation(id)
                    }
                    newLocation = null
                }) { Text("Add", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { newLocation = null }) { Text("Cancel", color = c.muted) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(selected: Category, onSelect: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        PulseTextField(
            "${selected.emoji}  ${selected.label}", {}, "Category",
            Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = PulseTheme.colors.card) {
            Category.entries.forEach { cat ->
                DropdownMenuItem(
                    text = { Text("${cat.emoji}  ${cat.label}  ·  ${cat.kind.label}") },
                    onClick = { onSelect(cat); expanded = false },
                )
            }
        }
    }
}
