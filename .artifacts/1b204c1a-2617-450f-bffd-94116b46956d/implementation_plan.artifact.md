# Implementation Plan - Gurbani Dictionary Feature

The user wants to add a Gurbani Dictionary feature to look up meanings of difficult words in scriptures.

## Proposed Changes

### [Nitnem Component]

#### [MODIFY] [NitnemEngine.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/NitnemEngine.kt)
- **Add Dictionary to Menu**: Add "ਗੁਰਬਾਣੀ ਡਿਕਸ਼ਨਰੀ (ਅਰਥ ਕੋਸ਼)" to the main Nitnem menu list.
- **Implementation**:
    - Create `showGurbaniDictionaryDialog(anchorView: View?)`.
    - This dialog will have a search bar (`EditText`) and a `RecyclerView` to show word meanings.
    - Load data from a new assets file `gurbani_meanings.json`.
- **Search Logic**: Filter the dictionary as the user types.

#### [NEW] [gurbani_meanings.json](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/assets/gurbani_meanings.json)
- Create a starter dictionary with meanings of common difficult Gurbani words (e.g., "ਕਰਤਾ", "ਪੁਰਖੁ", "ਨਿਰਭਉ", "ਨਿਰਵੈਰੁ", etc.).

## Verification Plan

### Manual Verification
1.  Open the Nitnem section.
2.  Find and click on **"ਗੁਰਬਾਣੀ ਡਿਕਸ਼ਨਰੀ (ਅਰਥ ਕੋਸ਼)"**.
3.  Search for a word (e.g., "ਨਿਰਭਉ").
4.  Verify the meaning is displayed correctly (e.g., "ਬਿਨਾਂ ਕਿਸੇ ਡਰ ਦੇ").
5.  Check both Light and Dark modes for UI consistency.
