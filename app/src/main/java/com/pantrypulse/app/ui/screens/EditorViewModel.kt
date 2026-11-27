package com.pantrypulse.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypulse.app.AppContainer
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Category
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface UseState {
    data object Idle : UseState
    data object Loading : UseState
    data class Found(val use: com.pantrypulse.app.domain.ProductUse) : UseState
    data object None : UseState
}

sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Found(val source: String) : LookupState
    data object NotFound : LookupState
    data class Failed(val reason: String) : LookupState
}

class EditorViewModel(private val container: AppContainer, private val productId: Long?) : ViewModel() {
    companion object {
        val units = listOf("pcs", "g", "kg", "ml", "L", "pack", "bottle", "tub", "can", "box", "jar", "bag", "strip")
    }

    var loaded by mutableStateOf(productId == null)
        private set
    var name by mutableStateOf("")
    var brand by mutableStateOf("")
    var barcode by mutableStateOf("")
    var category by mutableStateOf(Category.OTHER)
        private set
    var locationId by mutableStateOf<Long?>(null)
        private set
    private var locationTouched = false
    var quantity by mutableStateOf("1")
    var unit by mutableStateOf("pcs")
    var expiry by mutableStateOf<LocalDate?>(null)
    var imageUrl by mutableStateOf<String?>(null)
    var notes by mutableStateOf("")
    var lookup by mutableStateOf<LookupState>(LookupState.Idle)
        private set
    var useInfo by mutableStateOf<UseState>(UseState.Idle)
        private set
    /** The name [useInfo] was looked up for, so a stale answer isn't shown after the name changes. */
    var useInfoFor by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var existing: ProductEntity? = null

    val isEditing get() = productId != null

    init {
        if (productId != null) viewModelScope.launch {
            container.pantry.product(productId)?.let { p ->
                existing = p
                name = p.name; brand = p.brand.orEmpty(); barcode = p.barcode.orEmpty()
                category = p.category; locationId = p.locationId; locationTouched = true
                quantity = com.pantrypulse.app.ui.components.formatQty(p.quantity, "").trim()
                unit = p.unit; expiry = p.expiryDate; imageUrl = p.imageUrl; notes = p.notes
            }
            loaded = true
        } else viewModelScope.launch { applyDefaultLocation() }
    }

    fun selectCategory(value: Category) {
        category = value
        if (!locationTouched) viewModelScope.launch { applyDefaultLocation() }
    }

    fun selectLocation(id: Long?) {
        locationId = id
        locationTouched = true
    }

    private suspend fun applyDefaultLocation() {
        val hh = container.session.current().householdId ?: return
        val locs = container.pantry.locations(hh)
        locationId = locs.firstOrNull { it.name.equals(category.defaultLocation.label, true) }?.id ?: locationId
    }

    /** Looks up what a medicine or cosmetic is for, from its name. Does nothing for food. */
    fun loadUse() {
        val n = name.trim()
        if (n.isEmpty() || category.kind == com.pantrypulse.app.domain.ItemKind.FOOD) return
        useInfoFor = n
        useInfo = UseState.Loading
        val kind = category.kind
        viewModelScope.launch {
            useInfo = runCatching { container.productUse.find(n, brand.trim().ifBlank { null }, kind) }
                .getOrNull()?.let { UseState.Found(it) } ?: UseState.None
        }
    }

    /** Fills the form from a scanned barcode: our own history first, then Open Food Facts. */
    fun onBarcode(code: String) {
        barcode = code
        lookup = LookupState.Loading
        viewModelScope.launch {
            val hh = container.session.current().householdId
            val previous = hh?.let { container.pantry.latestByBarcode(it, code) }
            if (previous != null) {
                name = previous.name; brand = previous.brand.orEmpty(); category = previous.category
                unit = previous.unit; imageUrl = previous.imageUrl
                previous.locationId?.let { locationId = it; locationTouched = true }
                lookup = LookupState.Found("your pantry history")
                loadUse()
                return@launch
            }
            lookup = try {
                val info = container.openFoodFacts.lookup(code)
                if (info == null) LookupState.NotFound else {
                    name = info.name
                    brand = info.brand.orEmpty()
                    imageUrl = info.imageUrl
                    selectCategory(info.category)
                    info.packageSize?.let { parsePackage(it) }
                    loadUse()
                    LookupState.Found(info.source)
                }
            } catch (e: Exception) {
                LookupState.Failed("Couldn't reach the product database. Check your connection or type the details in.")
            }
        }
    }

    /** "500 g" → quantity 500, unit g; "6 x 330 ml" → 6 pcs. */
    private fun parsePackage(text: String) {
        val multi = Regex("(\\d+)\\s*[xX×]").find(text)
        if (multi != null) { quantity = multi.groupValues[1]; unit = "pcs"; return }
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(kg|g|ml|cl|l)\\b", RegexOption.IGNORE_CASE).find(text) ?: return
        val u = m.groupValues[2].lowercase()
        quantity = m.groupValues[1].replace(',', '.')
        unit = when (u) { "l" -> "L"; "cl" -> { quantity = ((quantity.toDoubleOrNull() ?: 0.0) * 10).toInt().toString(); "ml" }; else -> u }
    }

    suspend fun save(): Boolean {
        error = null
        val qty = quantity.replace(',', '.').toDoubleOrNull()
        when {
            name.isBlank() -> { error = "Please enter a name."; return false }
            qty == null || qty <= 0 -> { error = "Quantity must be more than zero."; return false }
        }
        val session = container.session.current()
        val hh = session.householdId ?: return false
        val user = session.userId ?: return false
        val base = existing ?: ProductEntity(householdId = hh, name = "", addedBy = user, purchaseDate = LocalDate.now())
        return try {
            container.pantry.save(
                base.copy(
                    name = name.trim(), brand = brand.trim().ifBlank { null }, barcode = barcode.trim().ifBlank { null },
                    category = category, locationId = locationId, quantity = qty!!, unit = unit, expiryDate = expiry,
                    imageUrl = imageUrl, notes = notes.trim(),
                )
            )
            true
        } catch (e: Exception) {
            error = e.message
            false
        }
    }
}
