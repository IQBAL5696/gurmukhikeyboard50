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

    fun handleCharacter(primaryCode: Int, ic: InputConnection?) {
        Log.d(TAG, "handleCharacter: START, currentWord before append: '${currentGurmukhiWord.toString()}', charCode: $primaryCode")
        ic ?: return

        // **FINAL, SIMPLIFIED, AND CORRECT LOGIC FOR SHIFTED KANNA BINDI**
        if (primaryCode == KEYCODE_KANNA_SHIFTED_KANNA_BINDI) {
            val textBefore = ic.getTextBeforeCursor(1, 0)?.toString()
            if (textBefore == A.toString()) {
                // Case: ਅ + ਾਂ -> ਆਂ
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0) // Delete 'ਅ'
                if (currentGurmukhiWord.isNotEmpty()) {
                    currentGurmukhiWord.deleteCharAt(currentGurmukhiWord.length - 1)
                }
                val textToCommit = "${AA}${BINDI}" // Create and commit 'ਆਂ'
                ic.commitText(textToCommit, 1)
                currentGurmukhiWord.append(textToCommit)
                lastCharForMatraLogic = textToCommit.last()
                ic.endBatchEdit()
            } else {
                // Case: Any other character or no character + ਾਂ
                val textToCommit = "${KANNA}${BINDI}" // Just commit 'ਾਂ'
                ic.commitText(textToCommit, 1)
                currentGurmukhiWord.append(textToCommit)
                lastCharForMatraLogic = textToCommit.last()
            }
            onWordChanged(currentGurmukhiWord.toString(), true)
            return // We are done
        }

        // Handle other special combined characters
        val textToCommit: String? = when (primaryCode) {
            KEYCODE_RAARA_SHIFTED_HALANT_RARA -> "${HALANT}${RAARA}"
            KEYCODE_HAHA_SHIFTED_HALANT_HAHA -> "${HALANT}${HAHA}"
            KEYCODE_HALANT_VAVA -> "${HALANT}${VAVA}"
            KEYCODE_HALANT_YAYYA -> "${HALANT}${YAYYA}"
            else -> null
        }

        if (textToCommit != null) {
            ic.commitText(textToCommit, 1)
            currentGurmukhiWord.append(textToCommit)
            lastCharForMatraLogic = textToCommit.last()
            onWordChanged(currentGurmukhiWord.toString(), true)
            Log.d(TAG, "handleCharacter: Committed special sequence '$textToCommit', currentWord: '$currentGurmukhiWord'")
            return // We're done
        }

        val charTypedActual = primaryCode.toChar()
        val prevChar = lastCharForMatraLogic
        var charToCommit: Char? = null
        var deletePrevious = false

        // Logic based on ImeConstants.kt
        when {
            prevChar == IRI && charTypedActual == LAAN -> {
                charToCommit = EE
                deletePrevious = true
            }
            prevChar == IRI && charTypedActual == SIHARI -> {
                charToCommit = I_LETTER
                deletePrevious = true
            }
            prevChar == IRI && charTypedActual == BIHARI -> {
                charToCommit = II_LETTER
                deletePrevious = true
            }
            prevChar == A && charTypedActual == KANNA -> {
                charToCommit = AA
                deletePrevious = true
            }
            prevChar == A && charTypedActual == KANAURA -> {
                charToCommit = AU
                deletePrevious = true
            }
            prevChar == A && charTypedActual == DULAWAN -> {
                charToCommit = AI
                deletePrevious = true
            }
            prevChar == URA && charTypedActual == AUNKAR -> {
                charToCommit = UU_LETTER
                deletePrevious = true
            }
            prevChar == URA && charTypedActual == DULAINKE -> {
                charToCommit = URAA_LETTER
                deletePrevious = true
            }
            prevChar == URA && charTypedActual == HORA -> { // ੳ + ੋ = ਓ
                charToCommit = O_INDEPENDENT
                deletePrevious = true
            }
            else -> charToCommit = charTypedActual
        }

        ic.beginBatchEdit()
        if (deletePrevious) {
            ic.deleteSurroundingText(1, 0)
            if (currentGurmukhiWord.isNotEmpty()) {
                currentGurmukhiWord.deleteCharAt(currentGurmukhiWord.length - 1)
            }
        }

        charToCommit?.let { char ->
            ic.commitText(char.toString(), 1)
            currentGurmukhiWord.append(char)
            lastCharForMatraLogic = char
            Log.d(TAG, "handleCharacter: BEFORE onWordChanged, currentWord: '${currentGurmukhiWord.toString()}'")
            onWordChanged(currentGurmukhiWord.toString(), true)
            Log.d(TAG, "handleCharacter: Committed '$char', currentWord: '$currentGurmukhiWord'")
        } ?: run {
            Log.d(TAG, "handleCharacter: No character committed for primaryCode $primaryCode due to no match or null charToCommit")
        }
        ic.endBatchEdit()
    }

    fun handleDelete(ic: InputConnection?) {
        ic ?: return

        if (currentGurmukhiWord.isNotEmpty()) {
            currentGurmukhiWord.deleteCharAt(currentGurmukhiWord.length - 1)
        }

        ic.deleteSurroundingText(1, 0)

        val textBeforeCursor = ic.getTextBeforeCursor(1, 0)
        lastCharForMatraLogic = if (textBeforeCursor?.isNotEmpty() == true) textBeforeCursor[0] else null

        Log.d(TAG, "handleDelete: currentWord: '$currentGurmukhiWord', lastCharForMatraLogic: $lastCharForMatraLogic")
        onWordChanged(currentGurmukhiWord.toString(), false)
    }

    fun reset() {
        lastCharForMatraLogic = null
        currentGurmukhiWord.clear()
        Log.d(TAG, "reset: Cleared state")
        onWordChanged("", false)
    }

    fun getCurrentWord(): String {
        return currentGurmukhiWord.toString()
    }

    fun setLastCharForLogic(char: Char?) {
        lastCharForMatraLogic = char
        Log.d(TAG, "setLastCharForLogic: $char")
    }

    fun setCurrentWord(word: String) {
        currentGurmukhiWord.clear()
        currentGurmukhiWord.append(word)
        lastCharForMatraLogic = word.lastOrNull()
        Log.d(TAG, "setCurrentWord: '$word', lastCharForMatraLogic: $lastCharForMatraLogic")
        onWordChanged(currentGurmukhiWord.toString(), false)
    }

    fun appendCommittedText(text: String) {
        currentGurmukhiWord.append(text)
        text.lastOrNull()?.let { lastCharForMatraLogic = it }
        Log.d(TAG, "appendCommittedText: Appended '$text', currentWord: '$currentGurmukhiWord', lastChar: $lastCharForMatraLogic")
        onWordChanged(currentGurmukhiWord.toString(), true)
    }
}
