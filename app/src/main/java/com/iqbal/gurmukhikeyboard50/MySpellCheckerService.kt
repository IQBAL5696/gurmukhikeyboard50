package com.iqbal.gurmukhikeyboard50

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.service.textservice.SpellCheckerService
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo

class MySpellCheckerService : SpellCheckerService() {
    
    private var predictionEngine: PredictionEngine? = null

    override fun onCreate() {
        super.onCreate()
        predictionEngine = PredictionEngine(this)
    }

    override fun createSession(): Session {
        return MySpellCheckerSession()
    }

    private inner class MySpellCheckerSession : Session() {
        override fun onCreate() {
            // Not needed for this implementation
        }

        override fun onGetSuggestions(textInfo: TextInfo, suggestionsLimit: Int): SuggestionsInfo {
            val text = textInfo.text ?: ""
            val isCorrect = predictionEngine?.isWordInDictionary(text) ?: false

            if (isCorrect) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            val suggestions = predictionEngine?.getSuggestions(text) ?: emptyList()
            val limit = if (suggestionsLimit <= 0) 5 else suggestionsLimit
            val resultArr = suggestions.take(limit).toTypedArray()

            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, resultArr)
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> {
            val results = mutableListOf<SentenceSuggestionsInfo>()
            for (textInfo in textInfos) {
                val suggestionsInfo = onGetSuggestions(textInfo, suggestionsLimit)
                results.add(SentenceSuggestionsInfo(arrayOf(suggestionsInfo), intArrayOf(0), intArrayOf(textInfo.text.length)))
            }
            return results.toTypedArray()
        }
    }
}
