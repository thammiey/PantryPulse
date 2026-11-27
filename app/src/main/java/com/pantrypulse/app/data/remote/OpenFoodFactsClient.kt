package com.pantrypulse.app.data.remote

import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.CategoryGuesser
import com.pantrypulse.app.domain.ItemKind
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class BarcodeProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: Category,
    val imageUrl: String?,
    val packageSize: String?,
    val source: String = "Open Food Facts",
)

/**
 * Looks a barcode up in the free Open Food Facts family of databases: food first, then cosmetics
 * (Open Beauty Facts), then everything else (Open Products Facts). Medicines are rarely listed
 * anywhere, so those are typed in or named by the person.
 */
class OpenFoodFactsClient {
    private class Source(val name: String, val host: String, val kind: ItemKind?)

    private val sources = listOf(
        Source("Open Food Facts", "world.openfoodfacts.org", null),
        Source("Open Beauty Facts", "world.openbeautyfacts.org", ItemKind.COSMETICS),
        Source("Open Products Facts", "world.openproductsfacts.org", null),
    )

    /** Returns null when no database knows the code. Throws only if none of them could be reached. */
    suspend fun lookup(barcode: String): BarcodeProduct? {
        var failure: Exception? = null
        var reachedOne = false
        for (s in sources) {
            try {
                val json = Http.get(
                    "https://${s.host}/api/v2/product/${Http.encode(barcode)}.json" +
                        "?fields=product_name,product_name_en,generic_name,brands,categories_tags,image_front_small_url,quantity"
                )
                reachedOne = true
                parse(barcode, json, s.name, s.kind)?.let { return it }
            } catch (e: IOException) {
                // These databases answer HTTP 404 for a product they don't have: that's "not found", not an outage.
                if (e.message?.contains("HTTP 404") == true) reachedOne = true else failure = e
            } catch (e: JSONException) {
                failure = e
            }
        }
        if (!reachedOne && failure != null) throw failure
        return null
    }

    companion object {
        fun parse(barcode: String, json: String, source: String = "Open Food Facts", kind: ItemKind? = null): BarcodeProduct? {
            val root = JSONObject(json)
            if (root.optInt("status", 0) != 1) return null
            val p = root.optJSONObject("product") ?: return null
            val name = listOf("product_name", "product_name_en", "generic_name")
                .map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val brand = p.optString("brands").split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val tagsJson = p.optJSONArray("categories_tags")
            val tags = (0 until (tagsJson?.length() ?: 0)).map { tagsJson!!.getString(it) }
            val fromTags = CategoryGuesser.fromOpenFoodFactsTags(tags, name)
            val category = if (kind == ItemKind.COSMETICS && fromTags.kind != ItemKind.COSMETICS) {
                CategoryGuesser.guess(name).takeIf { it.kind == ItemKind.COSMETICS } ?: Category.SKINCARE
            } else fromTags
            return BarcodeProduct(
                barcode = barcode,
                name = name,
                brand = brand,
                category = category,
                imageUrl = p.optString("image_front_small_url").takeIf { it.startsWith("http") },
                packageSize = p.optString("quantity").takeIf { it.isNotBlank() },
                source = source,
            )
        }
    }
}
