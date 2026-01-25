package com.iqbal.gurmukhikeyboard50

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.service.textservice.SpellCheckerService
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo

class MySpellCheckerService : SpellCheckerService() {
    override fun createSession(): Session {
        return MySpellCheckerSession()
    }

    private class MySpellCheckerSession : Session() {
        override fun onCreate() {
            // Not needed for this implementation
        }

        override fun onGetSuggestions(textInfo: TextInfo, suggestionsLimit: Int): SuggestionsInfo {
            // Dummy implementation for now
            return SuggestionsInfo(0, emptyArray())
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> {
            // Dummy implementation for now
            return emptyArray()
        }
    }
}
