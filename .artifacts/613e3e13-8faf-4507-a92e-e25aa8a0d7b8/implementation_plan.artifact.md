# Implementation Plan - Strictly Paragraph-Based Suggestions

This plan ensures that all keyboard suggestions and inline "ghost text" come exclusively from the provided `paragraph.txt` and `common_paragraphs.txt` files, ignoring hardcoded lists and external learned words.

## Proposed Changes

### Prediction Engine

#### [MODIFY] [PredictionEngine.java](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/PredictionEngine.java)
- Remove the hardcoded `switch-case` block that injects priority words for single characters like 'ਮ', 'ੳ', 'ੲ', etc.
- This ensures that if a word isn't in the paragraph, it won't be suggested just because its first letter was typed.

### IME Logic

#### [MODIFY] [MyKeyboardIME.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/MyKeyboardIME.kt)
- In `updateSuggestions`, modify the merging logic:
    - Prioritize `mainCompletions` (strictly from paragraphs).
    - Remove or de-prioritize `dbSuggestions` (learned words) to ensure the paragraph content is the primary driver.
    - Update the "Ghost Text" selection to only pick words that exist in the `MainSource` (Paragraphs).

### Language Model

#### [MODIFY] [PunjabiNextWordLM.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/PunjabiNextWordLM.kt)
- Update the `defaults` map to be empty or contain only entries confirmed to be in the paragraph. (Better: remove the hardcoded triggers entirely).

## Verification Plan

### Manual Verification
- Deploy the app.
- Type a character that is NOT the start of any word in your paragraph. Verify no suggestions appear.
- Type a character that IS in the paragraph. Verify only words from the paragraph appear as ghost text and in the suggestion bar.
