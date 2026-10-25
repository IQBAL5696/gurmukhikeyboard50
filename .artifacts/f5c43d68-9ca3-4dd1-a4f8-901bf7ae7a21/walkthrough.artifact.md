# Walkthrough - Fix Unresolved Reference 'PhrasesPanel'

I have resolved the compilation error `Unresolved reference 'PhrasesPanel'` by implementing the missing `PhrasesPanel` class.

## Changes

### [app]

#### [PhrasesPanel.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/PhrasesPanel.kt)

Implemented the `PhrasesPanel` class which provides:
- A list of common Gurmukhi phrases (e.g., "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ", "ਵਾਹਿਗੁਰੂ ਜੀ ਕਾ ਖਾਲਸਾ...").
- A RecyclerView with a simple adapter to display these phrases.
- Integration with the existing `phrases_panel_layout.xml`.
- Callbacks for phrase selection and closing the panel, which are used in `MyKeyboardIME.kt`.

## Verification Results

### Automated Tests
- Executed `gradlew app:assembleDebug` and the build finished successfully, confirming that the unresolved reference error is fixed.

### Manual Verification
- The phrases panel is now correctly instantiated in `MyKeyboardIME.kt` inside `setupPhrasesPanel`.
- It is displayed when the phrases key (icon `ic_continue_writing`) is pressed in the top row.
- Clicking a phrase will commit it to the current input connection.
