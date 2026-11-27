package com.pantrypulse.app.data.remote

import com.pantrypulse.app.BuildConfig
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.domain.ProductUse
import com.pantrypulse.app.domain.UseGuide
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Finds what a medicine or cosmetic is for. Built-in guidance for common products comes first (instant, offline);
 * medicines it doesn't know are looked up in the US FDA drug-label database (openFDA, free, no key).
 */
class ProductUseClient {
    suspend fun find(name: String, brand: String?, kind: ItemKind): ProductUse? = when (kind) {
        ItemKind.FOOD -> null
        ItemKind.COSMETICS -> UseGuide.cosmetic(name) ?: brand?.let { UseGuide.cosmetic("$name $it") }
        // Built-in list first (instant, offline), then a Google Gemini summary via our server, then the FDA label database.
        ItemKind.MEDICINE -> UseGuide.medicine(name) ?: brand?.let { UseGuide.medicine(it) }
            ?: fromAi(UseGuide.cleanName(name).ifBlank { name }, brand)
            ?: fromFda(UseGuide.cleanName(name).ifBlank { name })
    }

    private suspend fun fromAi(name: String, brand: String?): ProductUse? = try {
        val body = JSONObject().put("name", name).apply { brand?.let { put("brand", it) } }
        parseAi(Http.postJson(BuildConfig.COMMUNITY_API_URL.trimEnd('/') + "/ai/medicine", body.toString()))
    } catch (e: IOException) {
        null // server not set up, rate limited or offline: fall back to the FDA lookup
    } catch (e: JSONException) {
        null
    }

    private suspend fun fromFda(term: String): ProductUse? = try {
        val safe = term.replace("\"", "")
        val query = "openfda.brand_name:\"$safe\" OR openfda.generic_name:\"$safe\""
        parseLabel(Http.get("https://api.fda.gov/drug/label.json?search=${Http.encode(query)}&limit=1"))
    } catch (e: IOException) {
        null // 404 = no label for that name; other IO errors just mean we couldn't check
    } catch (e: JSONException) {
        null
    }

    companion object {
        /** Reads the server's Gemini answer. Returns null when the AI wasn't sure what the product is. */
        fun parseAi(json: String): ProductUse? {
            val o = JSONObject(json)
            if (!o.optBoolean("known", false)) return null
            val uses = o.optString("uses").trim()
            if (uses.isEmpty()) return null
            return ProductUse(
                uses = uses,
                directions = o.optString("directions").trim().takeIf { it.isNotEmpty() },
                warnings = o.optString("warnings").trim().takeIf { it.isNotEmpty() },
                source = "AI summary by Google Gemini. It can make mistakes, so check the leaflet.",
            )
        }

        fun parseLabel(json: String): ProductUse? {
            val first = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
            fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
                first.optJSONArray(k)?.optString(0)?.let(::tidy)?.takeIf { it.isNotBlank() }
            }
            val uses = field("indications_and_usage", "purpose") ?: return null
            return ProductUse(
                uses = shorten(uses, 350),
                directions = field("dosage_and_administration")?.let { shorten(it, 300) },
                warnings = field("warnings", "warnings_and_cautions", "do_not_use")?.let { shorten(it, 300) },
                source = "US FDA drug label (openFDA)",
            )
        }

        private fun tidy(text: String) = text
            .replace(Regex("""^\s*(indications?\s*(&|and)\s*usage|uses?|purpose|warnings?|dosage\s*(&|and)\s*administration)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ").trim()

        private fun shorten(text: String, max: Int): String {
            if (text.length <= max) return text
            val cut = text.lastIndexOf(". ", max).takeIf { it > max / 2 }
            return if (cut != null) text.substring(0, cut + 1) else text.substring(0, max).trimEnd() + "…"
        }
    }
}
