# Walkthrough - Gurbani Life Lessons Section

I have added a new "ਜੀਵਨ ਸਿੱਖਿਆ (Gurbani Lessons)" section to the Nitnem feature. This section provides inspiring verses from Guru Granth Sahib Ji with their meanings and practical life lessons.

## Changes Made

### [Nitnem Component]

#### [NitnemEngine.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/NitnemEngine.kt)
- **Menu Integration**: Added a new menu item "ਜੀਵਨ ਸਿੱਖਿਆ (Gurbani Lessons)" to the main Nitnem list.
- **Lessons Dialog**:
    - Created a new dialog (`showGurbaniLessonsDialog`) that displays educational content in an organized, framed card format.
    - Each card highlights the **Verse**, its **Meaning**, a practical **Life Lesson**, and the **Source** (Ang number).
    - Used a color-coded system (Yellow/Red for verses, Green for lessons) to make the content engaging and easy to read.
- **Theme Consistency**: The new section fully supports Light and Dark modes and follows the "Wide Photo Frame" UI style.

#### [daily_educational_verses.json](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/assets/daily_educational_verses.json)
- **Content Database**: Created a JSON database with 15+ high-quality verses and lessons covering topics like humility, service, equality, and honesty.

## Verification Results

### Manual Verification
- Verified that **"ਜੀਵਨ ਸਿੱਖਿਆ"** appears in the main menu.
- Confirmed that clicking it opens the list of lessons.
- Verified that each card is beautifully formatted and clearly shows the lesson.
- Confirmed that the UI adapts correctly when switching between Light and Dark themes.
