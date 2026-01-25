package com.iqbal.gurmukhikeyboard50

object PunjabiNextWordLM {

    private val model = mapOf(
        "ik" to listOf("ਓਅੰਕਾਰ", "ਓਅੰਕਾਰਿ"),
        "ਸਤਿ" to listOf("ਨਾਮੁ"),
        "ਨਾਮੁ" to listOf("ਕਰਤਾ"),
        "ਕਰਤਾ" to listOf("ਪੁਰਖੁ"),
        "ਨਿਰਭਉ" to listOf("ਨਿਰਵੈਰੁ"),
        "ਅਕਾਲ" to listOf("ਮੂਰਤਿ"),
        "ਵਾਹਿਗੁਰੂ" to listOf("ਜੀ", "ਸਿਮਰਨ"),
        "ਗੁਰ" to listOf("ਪ੍ਰਸਾਦਿ", "ਬਾਣੀ", "ਕਿਰਪਾ"),
        "ਖਾਲਸਾ" to listOf("ਪੰਥ", "ਜੀ", "ਮੇਰੋ"),
        "ਸਤਿਨਾਮ" to listOf("ਵਾਹਿਗੁਰੂ"),
        "ਹਰਿ" to listOf("ਨਾਮੁ", "ਸਿਮਰਨੁ", "ਕਿਰਪਾ"),
        "ਧੰਨ" to listOf("ਗੁਰੂ", "ਧੰਨ", "ਭਾਗ"),
        "ਜਪੁ" to listOf("ਜੀ", "ਸਾਹਿਬ"),
        "ਨਿਤਨੇਮ" to listOf("ਸਾਹਿਬ", "ਬਾਣੀ"),
        "ਸ੍ਰੀ" to listOf("ਗੁਰੂ", "ਦਰਬਾਰ"),
        "ਅੰਮ੍ਰਿਤ" to listOf("ਵੇਲਾ", "ਬਾਣੀ", "ਛਕਿਆ"),
        "ਸਾਧ" to listOf("ਸੰਗਤਿ"),
        "ਸੰਤ" to listOf("ਸਾਹਿਬ", "ਸਰਨਿ"),
        "ਰਾਮ" to listOf("ਦਾਸ", "ਨਾਮੁ"),
        "ਅਨੰਦ" to listOf("ਭਇਆ", "ਸਾਹਿਬ"),
        "ਸਰਬੱਤ" to listOf("ਦਾ", "ਭਲਾ"),
        "ਤੇਰਾ" to listOf("ਭਾਣਾ"),
        "ਨਾਨਕ" to listOf("ਨਾਮ", "ਨੀਚੁ"),
        "ਹੁਕਮੈ" to listOf("ਅੰਦਰਿ"),
        "ਬਿਨੁ" to listOf("ਬਾਣੀ", "ਨਾਵੈ"),
        "ਮੇਰਾ" to listOf("ਮੁਖੁ", "ਮਨੁ"),
        "ਸੇਵਾ" to listOf("ਕਰਿ", "ਥਾਇ"),
        "ਕਿਰਪਾ" to listOf("ਕਰਿ", "ਕੀਤੀ"),
        "ਬਖਸ਼ਿਸ਼" to listOf("ਕਰੋ"),
        "ਅਰਦਾਸ" to listOf("ਕਰਨੀ"),
        "ਸਬਦ" to listOf("ਗੁਰੂ"),
        "ਜੀਉ" to listOf("ਮੇਰਾ")
    )

    /**
     * Predicts the next Gurmukhi words based on the previous Gurmukhi word.
     */
    fun predict(previousGurmukhi: String): List<String> {
        val cleaned = previousGurmukhi.trim()
        return model[cleaned] ?: emptyList()
    }

    /**
     * Checks if a word exists in our prediction model.
     */
    fun hasPrediction(word: String): Boolean = model.containsKey(word.trim())
}
