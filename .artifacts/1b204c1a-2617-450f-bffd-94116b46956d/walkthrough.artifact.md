# Walkthrough - Wide Top Frame with Bani Title

I have enhanced the Nitnem "Photo Frame" UI by making the top part of the frame wider and integrating the name of each Bani (scripture) directly into it. This creates a more immersive and professional "framed book" look for the scriptures.

## Changes Made

### [Nitnem Component]

#### [NitnemEngine.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/NitnemEngine.kt)
- **Dynamic Titles**: Updated `showGurbaniDialog` and `showContentDialog` to accept a title parameter, ensuring every Bani displays its correct name (e.g., "ਜਪੁਜੀ ਸਾਹਿਬ", "ਰਹਿਰਾਸ ਸਾਹਿਬ") at the top.
- **Wide Top Frame UI**:
    - **Header Integration**: Added a centered `TextView` for the title at the top of the reading dialog.
    - **Enhanced Split Title Design**: Improved the title splitting logic to handle phrases with more than two words (e.g., "ਆਸਾ ਦੀ ਵਾਰ"). It now splits the name as evenly as possible into left and right parts, ensuring no middle words are lost while still keeping the center clear for camera notches.
    - **Ultra-Compact Padding**: Further reduced top padding to **18dp** as requested, making the top decorative bar sleek while ensuring the split title remains perfectly visible.
    - **Seamless Frame Extension**: Removed all margins and root padding from the top header so that its background blends perfectly with the outer frame's edges.
    - **Styling**: Used high-contrast colors (White/Cream on dark frame) and the custom Gurmukhi typeface for an elegant appearance.
- **Main Menu Enhancement**: Updated `applyPanelTheme` to apply a similar "wide top frame" style to the main Nitnem menu header, providing a consistent visual experience across the entire feature.
- **Improved Spacing**: Adjusted layout margins and paddings to ensure the new header doesn't overlap with scriptures or control buttons.

## Verification Results

### Manual Verification
- Verified that **Japji Sahib** shows its title in a wide frame at the top.
- Verified that **Rehras Sahib** and other Banis also show their respective titles correctly.
- Confirmed that the "Wide Top" header adapts beautifully to both **Light** and **Dark** themes.
- Checked that the main Nitnem menu ("ਨਿਤਨੇਮ ਅਤੇ ਬਾਣੀਆਂ") now also has the enhanced framed look.
