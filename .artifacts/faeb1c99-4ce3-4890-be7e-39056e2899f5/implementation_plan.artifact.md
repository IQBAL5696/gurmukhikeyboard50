# Implementation Plan - Add "Daily Life Lessons from Gurbani" Section

Add a new section to the Nitnem panel that displays inspiring and educational verses from Gurbani with their meanings and life lessons.

## Proposed Changes

### [Nitnem Component]

#### [MODIFY] [NitnemEngine.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/NitnemEngine.kt)
- **Add Menu Item**: Add "ਜੀਵਨ ਸਿੱਖਿਆ (Gurbani Life Lessons)" to the main Nitnem menu list.
- **Implementation**:
    - Create `showGurbaniLessonsDialog(anchorView: View?)`.
    - This dialog will load data from the newly created `daily_educational_verses.json`.
    - Use a `RecyclerView` to display the list of verses in a beautiful "framed card" format.
    - Each card will show the Gurbani verse, its simple meaning, and the practical life lesson.
- **Styling**: Ensure the UI matches the established "Photo Frame" theme and supports Light/Dark modes.

## Verification Plan

### Manual Verification
1.  Open the Nitnem section.
2.  Find and click on **"ਜੀਵਨ ਸਿੱਖਿਆ (Gurbani Life Lessons)"**.
3.  Verify that a list of verses appears.
4.  Check that each verse clearly shows its meaning and the lesson for daily life.
5.  Test theme switching to ensure colors adapt correctly.
