# Implementation Plan: Set Dark Mode Background to Black

This plan ensures that the keyboard background color is pure black (#000000) when in dark mode, specifically focusing on the "Dark" and "Midnight OLED" themes which are used in dark mode.

## User Review Required

> [!NOTE]
> This change will affect both the "Dark" theme (mapped to system dark mode by default) and the "Midnight OLED" theme.

## Proposed Changes

### Resources

#### [MODIFY] [colors.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/values/colors.xml)
- Change `keyboard_background_dark` from `#121212` to `#000000`.
- Change `keyboard_background_midnight` from `#121212` to `#000000`.

#### [DELETE] [styles.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/values-night/styles.xml)
- Remove the redundant and incomplete `KeyboardTheme.Dark` definition in `values-night`. This ensures the more comprehensive definition in `res/values/themes.xml` (which inherits from `Base.Theme.GurmukhiKeyboard50`) is used consistently.

#### [MODIFY] [themes.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/values-night/themes.xml)
- Update `Base.Theme.GurmukhiKeyboard50` in `values-night` to set `keyboardViewBackground` to `#000000`. This provides a safe default for any components relying on the base theme in night mode.

## Verification Plan

### Manual Verification
- Deploy the app to a device or emulator.
- Set the system theme to Dark Mode.
- Verify that the keyboard background is pure black.
- Select the "Dark" theme manually and verify the background is black.
- Select the "Midnight OLED" theme manually and verify the background is black.
