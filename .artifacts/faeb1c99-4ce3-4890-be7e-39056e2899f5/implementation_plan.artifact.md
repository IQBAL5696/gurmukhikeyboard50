# Implementation Plan - New Line after ੴ in Nitnem

The user wants the Gurbani text starting from "ੴ ਸਤਿਨਾਮੁ ..." to show the rest of the Bani in the next line. This will be implemented by ensuring that the "ੴ" symbol is followed by a newline, making it a standalone header, and ensuring proper separation between the initial invocation and the main text.

## Proposed Changes

### [Gurbani UI Component]

#### [MODIFY] [GurbaniUIUtils.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/GurbaniUIUtils.kt)
- Update `applyIkOnkarToSpannable` to insert a newline (`\n`) after the "ੴ" symbol instead of using a Word Joiner or Non-breaking space. This will push "ਸਤਿਨਾਮੁ" (or any following text) to the next line.

#### [MODIFY] [GurbaniSearchHelper.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/GurbaniSearchHelper.kt)
- Update `getGurbaniSpannable` to ensure that even in Larivaar mode, if a sentence starts with "ੴ", it is followed by a newline to maintain the traditional header appearance.
- Ensure that the "॥" verse markers for headings (like `॥ ਜਪੁ ॥`) are also followed by a newline if they appear at the start of the Bani.

## Verification Plan

### Manual Verification
- Open Nitnem (e.g., Japji Sahib).
- Verify that "ੴ" is displayed at the top center on its own line.
- Verify that "ਸਤਿਨਾਮੁ..." starts on the line below "ੴ".
- Toggle between Pad-ched and Larivaar modes and verify the "ੴ" still acts as a header.
- Check other Banis (Rehras Sahib, Anand Sahib) to ensure the opening "ੴ" or "ੴ ਸਤਿਗੁਰ ਪ੍ਰਸਾਦਿ ॥" is correctly formatted.
