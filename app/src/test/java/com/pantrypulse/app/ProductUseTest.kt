package com.pantrypulse.app

import com.pantrypulse.app.data.remote.OpenFoodFactsClient
import com.pantrypulse.app.data.remote.ProductUseClient
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.ItemKind
import com.pantrypulse.app.domain.ProductCode
import com.pantrypulse.app.domain.UseGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductUseTest {
    @Test fun knowsCommonMedicinesByBrandOrGenericName() {
        assertTrue(UseGuide.medicine("Crocin 500 mg Tablets")!!.uses.contains("fever"))
        assertTrue(UseGuide.medicine("Cetirizine 10mg")!!.uses.contains("allergy"))
        assertNull(UseGuide.medicine("Mystery pill"))
    }

    @Test fun knowsCommonCosmetics() {
        assertTrue(UseGuide.cosmetic("Neutrogena Ultra Sheer Sunscreen SPF 50")!!.directions!!.contains("reapply"))
        assertTrue(UseGuide.cosmetic("Dove Shampoo")!!.uses.contains("hair"))
        // "shaving cream" must be read as shaving cream, not as a moisturiser
        assertTrue(UseGuide.cosmetic("Gillette Shaving Cream")!!.uses.contains("razor"))
        assertNull(UseGuide.cosmetic("Widget"))
    }

    @Test fun cleansMedicineNamesForSearching() {
        assertEquals("Crocin", UseGuide.cleanName("Crocin 500 mg Tablets"))
        assertEquals("Betnovate", UseGuide.cleanName("Betnovate 0.1% cream"))
    }

    @Test fun parsesFdaDrugLabel() {
        val json = """{"results":[{"indications_and_usage":["INDICATIONS & USAGE Temporarily relieves minor aches and pains."],
            "warnings":["Warnings Liver warning: this product contains acetaminophen."]}]}"""
        val use = ProductUseClient.parseLabel(json)!!
        assertEquals("Temporarily relieves minor aches and pains.", use.uses)
        assertTrue(use.warnings!!.startsWith("Liver warning"))
        assertNull(ProductUseClient.parseLabel("""{"results":[]}"""))
    }

    @Test fun beautyDatabaseHitsAreFiledAsCosmetics() {
        val json = """{"status":1,"product":{"product_name":"Daily Moisturising Lotion","brands":"Acme","categories_tags":["en:body-care"],"quantity":"200 ml"}}"""
        val p = OpenFoodFactsClient.parse("8901234567890", json, "Open Beauty Facts", ItemKind.COSMETICS)!!
        assertEquals(ItemKind.COSMETICS, p.category.kind)
        assertEquals("Open Beauty Facts", p.source)
        assertNotNull(p.name)
    }

    @Test fun dataMatrixWithLeadingControlCharacterStillGivesTheProductNumber() {
        assertEquals("8901234567890", ProductCode.fromQr("\u001d0108901234567890\u001d17261231"))
        assertEquals(Category.SKINCARE, Category.SKINCARE) // keeps the Category import honest
    }
}

class AiUseTest {
    @Test fun readsAKnownGeminiAnswer() {
        val use = ProductUseClient.parseAi("""{"known":true,"uses":"Relieves allergy symptoms.","directions":"","warnings":"May cause drowsiness."}""")!!
        assertEquals("Relieves allergy symptoms.", use.uses)
        assertNull(use.directions)
        assertTrue(use.source.contains("Gemini"))
    }

    @Test fun ignoresAnAnswerWhenTheAiIsNotSure() {
        assertNull(ProductUseClient.parseAi("""{"known":false}"""))
        assertNull(ProductUseClient.parseAi("""{"known":true,"uses":" "}"""))
    }
}
