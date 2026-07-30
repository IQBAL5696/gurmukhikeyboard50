# Implementation Plan - Scientific Calculator Mode

This plan adds a "Scientific Mode" toggle to the calculator settings. When enabled, it reveals additional scientific function buttons (sqrt, x², sin, cos, tan, π, e, etc.) for both the standalone app and the keyboard panel.

## User Review Required

> [!IMPORTANT]
> The scientific mode will add an extra row of buttons. In the keyboard popup panel, this will increase the overall height of the calculator area.

## Proposed Changes

### Configuration & Settings

#### [MODIFY] [dialog_calculator_settings.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/layout/dialog_calculator_settings.xml)
- Add a `SwitchMaterial` for `scientific_mode` with label "Scientific Calculator".

### UI Components

#### [MODIFY] [activity_calculator.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/layout/activity_calculator.xml)
- Add a new `GridLayout` or `LinearLayout` row for scientific buttons: `√`, `x²`, `sin`, `cos`, `tan`, `log`, `π`.
- Set `android:visibility="collapsed"` by default.

#### [MODIFY] [calculator_panel_layout.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/layout/calculator_panel_layout.xml)
- Add identical scientific buttons row with `android:visibility="collapsed"`.

### Logic Implementation

#### [MODIFY] [CalculatorActivity.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorActivity.kt)
- Update `showQuickSettingsDialog` to handle the `scientific_mode` switch.
- Update `applyScientificMode(isEnabled: Boolean)` to show/hide the scientific buttons.
- Implement click listeners for scientific buttons.
    - `√`, `x²`, `sin`, `cos`, `tan`, `log`: Apply unary operation to the current input value using `kotlin.math` and update `currentInput`.
    - `π`, `e`: Set `currentInput` to the constant value.
- Update `applyCalculatorTheme` to style the new buttons.

#### [MODIFY] [CalculatorPanel.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorPanel.kt)
- Implement identical logic for settings toggle, visibility, and button functionality.

## Verification Plan

### Manual Verification
1. Open Calculator Settings and enable "Scientific Calculator".
2. Verify that a new row of buttons appears.
3. Test `√` with a number (e.g., `9` -> `3`).
4. Test `sin`, `cos`, `tan` (ensure they interpret input as degrees or radians - usually degrees for simple calculators).
5. Test `π` and `e` buttons.
6. Verify themes apply correctly to new buttons.
7. Verify that the keyboard popup panel also supports these functions when enabled.
