package com.iqbal.gurmukhikeyboard50

import android.view.inputmethod.InputConnection
import android.util.Log

class GurmukhiInputHandler(
    private val onWordChanged: (word: String, charTyped: Boolean) -> Unit
) {

    private var lastCharForMatraLogic: Char? = null
    private val currentGurmukhiWord = StringBuilder()

    companion object {
        private const val TAG = "GurmukhiInputHandler"
    }

    private fun updateComposingText(ic: InputConnection) {
        ic.setComposingText(currentGurmukhiWord.toString(), 1)
        onWordChanged(currentGurmukhiWord.toString(), true)
    }

    fun handleCharacter(primaryCode: Int, ic: InputConnection?) {
        ic ?: return

        if (primaryCode == KEYCODE_KANNA_SHIFTED_KANNA_BINDI) {
            val lastChar = currentGurmukhiWord.lastOrNull()
            if (lastChar == A) {
                currentGurmukhiWord.deleteCharAt(currentGurmukhiWord.length - 1)
                currentGurmukhiWord.append("${AA}${BINDI}")
            } else {
                currentGurmukhiWord.append("${KANNA}${BINDI}")
            }
            lastCharForMatraLogic = currentGurmukhiWord.lastOrNull()
            updateComposingText(ic)
            return
        }

        val specialSequence: String? = when (primaryCode) {
            KEYCODE_RAARA_SHIFTED_HALANT_RARA -> "${HALANT}${RAARA}"
            KEYCODE_HAHA_SHIFTED_HALANT_HAHA -> "${HALANT}${HAHA}"
            KEYCODE_HALANT_VAVA -> "${HALANT}${VAVA}"
            KEYCODE_HALANT_YAYYA -> "${HALANT}${YAYYA}"
            else -> null
        }

        if (specialSequence != null) {
            currentGurmukhiWord.append(specialSequence)
            lastCharForMatraLogic = currentGurmukhiWord.lastOrNull()
            updateComposingText(ic)
            return
        }

        val charTypedActual = primaryCode.toChar()
        val prevChar = lastCharForMatraLogic
        var charToCommit: Char? = null
        var deletePrevious = false

        when {
            // ਪੈਰ ਹਲੰਤ + ਕੰਨਾ Combo Logic: if current word ends with ੍ + ਹ and user types ਾ
            currentGurmukhiWord.endsWith("${HALANT}${HAHA}") && charTypedActual == KANNA -> {
                charToCommit = charTypedActual
            }
            prevChar == IRI && charTypedActual == LAAN -> { charToCommit = EE; deletePrevious = true }
            prevChar == IRI && charTypedActual == SIHARI -> { charToCommit = I_LETTER; deletePrevious = true }
            prevChar == IRI && charTypedActual == BIHARI -> { charToCommit = II_LETTER; deletePrevious = true }
            prevChar == A && charTypedActual == KANNA -> { charToCommit = AA; deletePrevious = true }
            prevChar == A && charTypedActual == KANAURA -> { charToCommit = AU; deletePrevious = true }
            prevChar == A && charTypedActual == DULAWAN -> { charToCommit = AI; deletePrevious = true }
            prevChar == URA && charTypedActual == AUNKAR -> { charToCommit = UU_LETTER; deletePrevious = true }
            prevChar == URA && charTypedActual == DULAINKE -> { charToCommit = URAA_LETTER; deletePrevious = true }
            prevChar == URA && charTypedActual == HORA -> { charToCommit = O_INDEPENDENT; deletePrevious = true }
            else -> charToCommit = charTypedActual
        }

        if (deletePrevious && currentGurmukhiWord.isNotEmpty()) {
            currentGurmukhiWord.deleteCharAt(currentGurmukhiWord.length - 1)
        }

        charToCommit?.let { char ->
            currentGurmukhiWord.append(char)
            lastCharForMatraLogic = char
            updateComposingText(ic)
        }
    }

    fun handleDelete(ic: InputConnection?) {
        ic ?: return
        if (currentGurmukhiWord.isNotEmpty()) {
            currentGurmukhiWord.deleteCharAt(currentGurmukhiWord.length - 1)
            ic.setComposingText(currentGurmukhiWord.toString(), 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        val textBeforeCursor = ic.getTextBeforeCursor(1, 0)
        lastCharForMatraLogic = if (textBeforeCursor?.isNotEmpty() == true) textBeforeCursor[0] else null
        onWordChanged(currentGurmukhiWord.toString(), false)
    }

    fun reset() {
        lastCharForMatraLogic = null
        currentGurmukhiWord.clear()
    }

    fun getCurrentWord(): String = currentGurmukhiWord.toString()

    fun setLastCharForLogic(char: Char?) {
        lastCharForMatraLogic = char
    }

    fun setCurrentWord(word: String) {
        currentGurmukhiWord.clear()
        currentGurmukhiWord.append(word)
        lastCharForMatraLogic = word.lastOrNull()
        onWordChanged(currentGurmukhiWord.toString(), false)
    }

    fun appendCommittedText(text: String) {
        currentGurmukhiWord.append(text)
        text.lastOrNull()?.let { lastCharForMatraLogic = it }
        onWordChanged(currentGurmukhiWord.toString(), true)
    }
}
