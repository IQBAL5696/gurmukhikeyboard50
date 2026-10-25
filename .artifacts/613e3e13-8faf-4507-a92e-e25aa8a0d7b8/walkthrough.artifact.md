# Walkthrough - Strictly Paragraph-Based Suggestions

I have updated the keyboard's prediction and ghost text logic to strictly use words from your provided paragraphs (`paragraph.txt` and `common_paragraphs.txt`).

## Key Changes

### 1. Removal of Hardcoded Lists
- Removed all manual character triggers from `PredictionEngine.java`. The keyboard no longer injects arbitrary words like 'ਮੈਂ' or 'ਉਹ' unless they are present in your source text.
- Cleared default predictions from the language model to ensure it only uses loaded data.

### 2. Strict Paragraph Filtering
- **Suggestion Engine:** In `MyKeyboardIME.kt`, I updated the logic to filter all potential suggestions (even learned ones) against the `MainSource` (Paragraphs). Only words existing in your text will be suggested.
- **Ghost Text:** The inline predictive text now only displays words found in the paragraphs.
- **Auto-Correction:** Auto-correction now only triggers if the corrected version of the word exists in your source text.

### 3. Priority Alignment
- Main Source words are prioritized and highlighted in blue in the suggestion bar to distinguish them as confirmed correct words from your text.

## Verification Results
- **Build:** Success.
- **Logic:** Suggestions now dynamically adapt based *only* on the content of your paragraphs. If a word is not in the text files, it will not appear as a suggestion or ghost text.
