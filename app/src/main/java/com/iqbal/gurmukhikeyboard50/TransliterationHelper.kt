package com.iqbal.gurmukhikeyboard50

import android.content.Context
import org.json.JSONObject
import java.io.InputStream

object TransliterationHelper {

    /* ================= USER SETTINGS ================= */

    // Toggle Gurbani strict mode
    var gurbaniStrictMode: Boolean = false

    // Per-word learning (can later be persisted)
    private val learnedMap = mutableMapOf<String, String>()

    /* ================= DICTIONARY OVERRIDE ================= */

    private var overrideDictionary = mutableMapOf<String, String>()

    /* ================= CONSONANTS ================= */

    private val consonants = mapOf(
        "kh" to "ਖ", "gh" to "ਘ", "ch" to "ਚ", "jh" to "ਝ",
        "th" to "ਥ", "dh" to "ਧ", "ph" to "ਫ", "bh" to "ਭ",
        "sh" to "ਸ਼",

        "k" to "ਕ", "g" to "ਗ", "c" to "ਚ", "j" to "ਜ",
        "t" to "ਤ", "d" to "ਦ", "n" to "ਨ",
        "p" to "ਪ", "b" to "ਬ", "m" to "ਮ",
        "y" to "ਯ", "r" to "ਰ", "l" to "ਲ",
        "v" to "ਵ", "w" to "ਵ",
        "s" to "ਸ", "h" to "ਹ",

        // Modern Punjabi letters
        "z" to "ਜ਼",
        "f" to "ਫ਼",
        "R" to "ੜ",
        "L" to "ਲ਼",
        "S" to "ਸ਼",
        "Kh" to "ਖ਼",
        "Gh" to "ਗ਼"
    )

    /* ================= VOWELS ================= */

    private val independentVowels = mapOf(
        "a" to "ਅ", "aa" to "ਆ",
        "i" to "ਇ", "ii" to "ਈ",
        "u" to "ਉ", "uu" to "ਊ",
        "e" to "ਏ", "ai" to "ਐ",
        "o" to "ਓ", "au" to "ਔ"
    )

    private val vowelSigns = mapOf(
        "aa" to "ਾ",
        "i" to "ਿ", "ii" to "ੀ",
        "u" to "ੁ", "uu" to "ਊ",
        "e" to "ੇ", "ai" to "ੈ",
        "o" to "ੋ", "au" to "ੌ"
    )

    /* ================= NASAL INTELLIGENCE ================= */

    private val nasalFollowers = listOf("g", "gh", "k", "kh")

    /* ================= KEYS ================= */

    private val allKeys =
        (consonants.keys + independentVowels.keys + vowelSigns.keys)
            .distinct()
            .sortedByDescending { it.length }

    /* ================= PUBLIC API ================= */

    /**
     * Loads the Gurbani dictionary from assets. Call this in IME onCreate.
     */
    fun init(context: Context) {
        try {
            val inputStream: InputStream = context.assets.open("gurbani_dictionary.json")
            val size: Int = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)
            val jsonObject = JSONObject(json)
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                overrideDictionary[key] = jsonObject.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to basic set if file fails to load
            overrideDictionary["waheguru"] = "ਵਾਹਿਗੁਰੂ"
            overrideDictionary["ik"] = "ਇਕ"
        }
    }

    fun learnWord(roman: String, gurmukhi: String) {
        learnedMap[roman.lowercase()] = gurmukhi
    }

    fun transliterate(input: String): String {
        if (input.isBlank()) return ""

        val words = input.split(" ")
        val outWords = words.map { word ->
            transliterateWord(word)
        }

        return outWords.joinToString(" ")
    }

    /* ================= CORE ENGINE ================= */

    private fun transliterateWord(word: String): String {

        val lower = word.lowercase()

        // 1️⃣ Per-word learned memory
        learnedMap[lower]?.let { return it }

        // 2️⃣ Dictionary override
        overrideDictionary[lower]?.let { return it }

        val out = StringBuilder()
        var i = 0
        var lastVowel: String? = null

        while (i < word.length) {

            /* ---------- AUTO TIPPI / BINDI ---------- */
            if (i > 0 && word[i].lowercaseChar() == 'n') {
                val next = word.substring(i + 1).lowercase()

                // n + g/gh/k/kh → BINDI
                if (nasalFollowers.any { next.startsWith(it) }) {
                    out.append("ਂ")
                    i++
                    continue
                }

                // short vowel before → TIPPI
                if (lastVowel in listOf("a", "i", "u")) {
                    out.append("ੰ")
                    i++
                    continue
                }

                // default → BINDI
                out.append("ਂ")
                i++
                continue
            }

            val ch = word[i]

            /* ---------- NON LETTER ---------- */
            if (!ch.isLetter()) {
                out.append(ch)
                lastVowel = null
                i++
                continue
            }

            var matched = false

            for (key in allKeys) {
                if (word.startsWith(key, i, true)) {

                    val lk = key.lowercase()

                    when {
                        consonants.containsKey(lk) -> {

                            // Gurbani strict filter
                            if (gurbaniStrictMode && lk in listOf("z", "f", "kh", "gh")) {
                                // fallback to base letters
                                out.append(
                                    when (lk) {
                                        "z" -> "ਜ"
                                        "f" -> "ਫ"
                                        "kh" -> "ਖ"
                                        "gh" -> "ਘ"
                                        else -> consonants[lk]
                                    }
                                )
                            } else {
                                out.append(consonants[lk])
                            }
                            lastVowel = null
                        }

                        vowelSigns.containsKey(lk) -> {
                            out.append(vowelSigns[lk])
                            lastVowel = lk
                        }

                        independentVowels.containsKey(lk) -> {
                            out.append(independentVowels[lk])
                            lastVowel = lk
                        }
                    }

                    i += key.length
                    matched = true
                    break
                }
            }

            if (!matched) {
                out.append(ch)
                lastVowel = null
                i++
            }
        }

        return out.toString()
    }
}
