# Walkthrough - New Line after ੴ in Nitnem

I have updated the Nitnem Gurbani display logic to ensure that the **ੴ** symbol acts as a standalone header, with the following text (like "ਸਤਿਨਾਮੁ...") starting on a new line. This provides a cleaner and more traditional layout for the scriptures.

## Changes Made

### [Gurbani UI Component]

#### [GurbaniUIUtils.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/GurbaniUIUtils.kt)
- **Standalone Header**: Modified `applyIkOnkarToSpannable` to detect any spaces or word joiners following "ੴ" and replace them with a newline character (`\n`).
- **Direct Insertion**: If "ੴ" is immediately followed by a character without a space, a newline is inserted between them.
- **Improved Formatting**: This ensures that regardless of whether the Bani is in Pad-ched (split words) or Larivaar (connected words) mode, the opening invocation remains centered at the top on its own line.

## Verification Results

### Manual Verification
- Opened **Japji Sahib** and verified that **ੴ** is now at the top center, with **ਸਤਿਨਾਮੁ...** starting on the line below it.
- Verified the same behavior in **Rehras Sahib** and **Anand Sahib**.
- Confirmed that the formatting remains consistent when switching between **Dark** and **Light** themes.
- Confirmed that **Larivaar** mode also respects this new line rule for the opening invocation.
