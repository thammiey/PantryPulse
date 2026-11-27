package com.pantrypulse.app.domain

/** What a medicine or cosmetic is for and how it is generally used. */
data class ProductUse(
    val uses: String,
    val directions: String? = null,
    val warnings: String? = null,
    val source: String,
)

/** Built-in, offline guidance for common products. Deliberately general: no doses, no diagnoses. */
object UseGuide {
    const val MEDICINE_DISCLAIMER = "General information, not medical advice. Always follow the label, and ask your doctor or pharmacist if unsure."
    const val COSMETIC_NOTE = "Stop using it if your skin gets irritated. Check the open-jar symbol on the pack for how many months it lasts once opened."

    private const val GENERAL = "General guidance"
    private class Entry(val words: List<String>, val use: ProductUse)

    private val medicines = listOf(
        Entry(listOf("paracetamol", "acetaminophen", "crocin", "dolo", "calpol", "tylenol", "panadol"), ProductUse(
            "Relieves mild to moderate pain (headache, toothache, body ache) and brings down fever.",
            "Take as directed on the label and keep the gap between doses that it recommends.",
            "Never go over the daily maximum on the label, and avoid other products that also contain paracetamol (many cold and flu remedies do).", GENERAL)),
        Entry(listOf("ibuprofen", "brufen", "advil", "nurofen", "combiflam"), ProductUse(
            "Eases pain, inflammation and fever, for example headache, muscle pain, period pain and toothache.",
            "Usually taken with or after food. Follow the label.",
            "Not suitable for everyone (stomach ulcers, kidney problems, asthma, pregnancy). Ask a doctor or pharmacist if unsure.", GENERAL)),
        Entry(listOf("aspirin", "disprin", "ecosprin"), ProductUse(
            "Relieves pain and fever. Low-dose aspirin is also prescribed to lower the risk of blood clots.",
            "Follow the label or your doctor's instructions.",
            "Not for children and teenagers. Can irritate the stomach. Take daily low-dose aspirin only if a doctor advised it.", GENERAL)),
        Entry(listOf("cetirizine", "zyrtec", "allegra", "fexofenadine", "loratadine", "levocetirizine", "antihistamine", "montair"), ProductUse(
            "Relieves allergy symptoms such as sneezing, runny nose, itchy eyes and hives.",
            "Follow the label. Some types are taken once a day.",
            "Can cause drowsiness. Be careful driving or using machinery until you know how it affects you.", GENERAL)),
        Entry(listOf("omeprazole", "pantoprazole", "pantop", "pan 40", "rabeprazole", "esomeprazole", "antacid", "digene", "gelusil", "eno", "pudin hara"), ProductUse(
            "Reduces stomach acid. Used for acidity, heartburn and indigestion.",
            "Acid reducers like omeprazole or pantoprazole are usually taken on an empty stomach before a meal. Antacids are taken after food. Follow the label.",
            "See a doctor if symptoms last more than a couple of weeks or come with weight loss, vomiting or black stools.", GENERAL)),
        Entry(listOf("ors", "electral", "oral rehydration"), ProductUse(
            "Replaces fluids and salts lost through diarrhoea, vomiting or heavy sweating.",
            "Mix with the exact amount of clean water printed on the pack, then sip through the day.",
            "Don't add extra water, sugar or salt. Get medical help for severe or long-lasting dehydration.", GENERAL)),
        Entry(listOf("amoxicillin", "azithromycin", "augmentin", "ciprofloxacin", "doxycycline", "cefixime", "antibiotic"), ProductUse(
            "Antibiotics treat bacterial infections. They do not work on colds or flu, which are viral.",
            "Take exactly as prescribed and finish the whole course, even if you feel better.",
            "Use only when prescribed. Don't share them or keep leftovers for later.", GENERAL)),
        Entry(listOf("cough", "benadryl", "dextromethorphan", "ascoril", "grilinctus", "corex"), ProductUse(
            "Eases cough. Some syrups calm a dry cough, others loosen phlegm for a chesty cough.",
            "Use the measuring cap that comes with it and follow the label.",
            "Many cough syrups are not for young children. Check the age on the label.", GENERAL)),
        Entry(listOf("loperamide", "imodium"), ProductUse(
            "Slows diarrhoea.",
            "Follow the label. Drink plenty of fluids.",
            "Not for children, or if there is a fever or blood in the stool. See a doctor if it doesn't settle within two days.", GENERAL)),
        Entry(listOf("vitamin", "multivitamin", "b12", "calcium", "zinc", "iron tablet", "folic", "supplement", "becosules", "shelcal", "limcee"), ProductUse(
            "A dietary supplement that tops up vitamins or minerals your diet may be missing.",
            "Take at the same time each day, as the label says.",
            "More is not better. Don't exceed the labelled amount, and check with a doctor if pregnant or on other medicines.", GENERAL)),
        Entry(listOf("betadine", "povidone", "dettol", "antiseptic", "savlon"), ProductUse(
            "Cleans cuts, scrapes and minor wounds to help prevent infection.",
            "Clean the area first, then apply as the label says. Dilute liquid antiseptics as directed.",
            "For external use only. Don't use on deep wounds or burns without medical advice.", GENERAL)),
        Entry(listOf("ointment", "balm", "volini", "moov", "iodex", "pain relief gel", "diclofenac"), ProductUse(
            "Applied to the skin to ease muscle and joint aches, sprains and minor pain.",
            "Rub a thin layer gently into the sore area. Wash your hands afterwards.",
            "Don't apply to broken skin, eyes or mouth, or under tight bandages.", GENERAL)),
    )

    private val cosmetics = listOf(
        Entry(listOf("sunscreen", "sunblock", "spf", "sun screen"), ProductUse(
            "Protects skin from sunburn and UV damage that causes early ageing.",
            "Apply generously to dry skin about 15 minutes before going out, and reapply every 2 hours or after swimming or sweating.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("face wash", "facewash", "cleanser", "cleansing"), ProductUse(
            "Removes dirt, oil and make-up from the face.",
            "Massage onto damp skin for about 30 seconds, then rinse with water. Use once or twice a day.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("shaving"), ProductUse(
            "Softens facial hair so a razor glides smoothly and irritates the skin less.",
            "Apply to wet skin, shave, then rinse well.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("serum"), ProductUse(
            "A concentrated treatment for a specific skin concern, such as hydration, dark spots or ageing.",
            "Apply a few drops to clean skin before your moisturiser, morning or night as the label says.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("toner", "face mist"), ProductUse(
            "Refreshes skin after cleansing and helps it absorb the next products.",
            "Pat or spritz onto clean skin before serum or moisturiser.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("moisturi", "lotion", "cream", "body butter", "gel"), ProductUse(
            "Hydrates the skin and helps keep it soft and protected.",
            "Apply to clean, slightly damp skin and massage in until absorbed. Use once or twice a day.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("shampoo"), ProductUse(
            "Cleans the scalp and hair.",
            "Massage into wet hair and scalp, then rinse thoroughly. Use as often as your hair needs.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("conditioner", "hair mask"), ProductUse(
            "Softens and detangles hair and reduces frizz.",
            "After shampooing, apply to the lengths and ends, leave for 1 to 3 minutes, then rinse.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("hair oil"), ProductUse(
            "Nourishes the scalp and hair and reduces dryness.",
            "Massage a small amount into the scalp or lengths. Wash out with shampoo after the time on the label.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("toothpaste", "tooth paste"), ProductUse(
            "Cleans teeth and helps prevent cavities and gum problems.",
            "Use a pea-sized amount and brush for two minutes, twice a day. Spit out, don't swallow.",
            "Keep children's toothpaste amounts small and supervise young children.", GENERAL)),
        Entry(listOf("mouthwash", "mouth wash"), ProductUse(
            "Freshens breath and helps reduce plaque and bacteria in the mouth.",
            "Rinse for about 30 seconds after brushing, then spit it out.", "Not for swallowing, and not for young children.", GENERAL)),
        Entry(listOf("deodorant", "antiperspirant", "roll-on", "roll on"), ProductUse(
            "Controls underarm odour (and sweat, if it is an antiperspirant).",
            "Apply to clean, dry underarms. Avoid freshly shaved or broken skin.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("sanitizer", "sanitiser"), ProductUse(
            "Kills most germs on hands when soap and water aren't available.",
            "Rub a coin-sized amount over all parts of your hands until completely dry.",
            "Alcohol-based and flammable. Keep away from eyes and flames. Wash hands with soap if they look dirty.", GENERAL)),
        Entry(listOf("soap", "body wash", "shower gel", "hand wash", "handwash"), ProductUse(
            "Cleans the skin.",
            "Lather on wet skin, then rinse well.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("lipstick", "lip balm", "lip gloss", "lip tint", "lip liner"), ProductUse(
            "Adds colour, shine or moisture to the lips.",
            "Apply directly or with a brush. Reapply as needed. Balms with SPF also protect from the sun.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("foundation", "concealer", "compact", "bb cream", "cc cream", "primer"), ProductUse(
            "Evens out skin tone and covers blemishes as a make-up base.",
            "Apply to moisturised skin and blend with fingers, a sponge or brush. Remove at night.", COSMETIC_NOTE, GENERAL)),
        Entry(listOf("mascara", "eyeliner", "kajal", "kohl", "eye shadow", "eyeshadow"), ProductUse(
            "Eye make-up that defines or colours lashes and eyelids.",
            "Apply carefully along the lashes or lids and remove fully before bed.",
            "Eye products have a short life once opened (often 3 to 6 months). Replace them after an eye infection and don't share them.", GENERAL)),
        Entry(listOf("nail polish", "nail paint", "nail enamel"), ProductUse(
            "Colours and protects the nails.",
            "Apply thin coats on clean, dry nails and let each coat dry.", "Use in a ventilated room. Remove with a nail polish remover.", GENERAL)),
        Entry(listOf("perfume", "cologne", "eau de", "body spray", "body mist"), ProductUse(
            "A fragrance for skin and clothes.",
            "Spray on pulse points such as wrists and neck from about 15 cm away.", "Keep away from eyes and flames. Store out of direct sunlight.", GENERAL)),
        Entry(listOf("face mask", "sheet mask", "clay mask", "face pack"), ProductUse(
            "A treatment applied for a short time for hydration or deep cleansing.",
            "Apply to clean skin, leave for the time on the pack, then rinse or remove. Use once or twice a week.", COSMETIC_NOTE, GENERAL)),
    )

    fun medicine(name: String): ProductUse? = find(medicines, name)
    fun cosmetic(name: String): ProductUse? = find(cosmetics, name)

    private fun find(entries: List<Entry>, name: String): ProductUse? {
        val t = name.lowercase()
        return entries.firstOrNull { e -> e.words.any { t.contains(it) } }?.use
    }

    /** "Crocin 500 mg Tablets" becomes "Crocin": strengths and dosage forms get in the way of searching. */
    fun cleanName(name: String): String = name
        .replace(Regex("""\d+([.,]\d+)?\s*(mg|mcg|g|ml|iu)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\d+([.,]\d+)?\s*%"""), " ")
        .replace(Regex("""\b(tablets?|capsules?|syrup|suspension|drops|ointment|cream|gel|injection|strips?|ip|bp|usp)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""[\s"]+"""), " ")
        .trim()
}
