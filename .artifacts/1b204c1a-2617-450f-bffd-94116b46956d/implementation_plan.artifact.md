# Implementation Plan - Wide Top Frame with Bani Title

The user wants to enhance the "photo frame" by making the top portion wider and displaying the name of the current Bani within that space.

## Proposed Changes

### [Nitnem Component]

#### [MODIFY] [NitnemEngine.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/NitnemEngine.kt)
- **Function Updates**:
    - Update `showGurbaniDialog` and `showContentDialog` to accept a `title: String` parameter.
    - Update call sites in `setupNitnemPanel` and `showRagasDialog` to pass the correct Bani name.
- **UI Enhancements**:
    - Inside `showContentDialog`, add a `TextView` at the top of the `root` layout to display the Bani title.
    - Adjust the `root` layout's top padding to create more space (e.g., 40dp) to accommodate the title and simulate a wider top frame.
    - Style the title text to be bold, centered, and color-coordinated with the frame/theme.
    - Add a background to the title container that blends seamlessly with the frame's stroke to create the "wider top" look.
- **Consistency**:
    - Apply a similar title header to the main Nitnem menu if it doesn't already have a prominent one inside the frame.

## Verification Plan

### Manual Verification
1.  Open the Nitnem section.
2.  Select **Japji Sahib**.
3.  Verify that the top part of the frame is noticeably wider and contains the text "ਜਪੁਜੀ ਸਾਹਿਬ".
4.  Navigate through different Banis (Rehras Sahib, Shabad Hazare) and ensure each displays its own name correctly at the top.
5.  Check both **Dark** and **Light** modes to ensure title legibility and frame consistency.
