# Fix Unresolved Reference 'PhrasesPanel'

The user is experiencing a compilation error `Unresolved reference 'PhrasesPanel'` in `MyKeyboardIME.kt`. Investigation reveals that `PhrasesPanel.kt` exists but is empty. This plan outlines the implementation of the `PhrasesPanel` class to resolve the error and provide the intended functionality.

## Proposed Changes

### [app]

#### [MODIFY] [PhrasesPanel.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/PhrasesPanel.kt)
- Implement the `PhrasesPanel` class.
- Add a internal `PhrasesAdapter` class to manage the list of phrases.
- Define a list of common Gurmukhi phrases.
- Inflate `phrases_panel_layout.xml` and set up the RecyclerView and close button.

## Verification Plan

### Automated Tests
- Perform a build to ensure the unresolved reference error is gone: `gradlew assembleDebug`

### Manual Verification
- Deploy the app to a device/emulator.
- Open the keyboard and navigate to the Phrases panel (using the phrases icon in the top row).
- Verify that the phrases are displayed and can be clicked to insert into the text field.
- Verify that the close button returns to the main keyboard.
