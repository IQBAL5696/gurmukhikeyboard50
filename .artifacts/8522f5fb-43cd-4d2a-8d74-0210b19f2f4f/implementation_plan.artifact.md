# Implementation Plan: Enhancing Gursikhi Historical Books Panel

The user wants to search and "upload" (import) historical Gursikhi books. The app already has a "Books (PDF)" section in the Nitnem panel, but it can be improved with better search capabilities and more historical content.

## Proposed Changes

### [app] Component: Nitnem Engine & Library

#### [MODIFY] [NitnemEngine.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/NitnemEngine.kt)
- **Expand `defaultBooks`**: Add more prominent historical books from the web search results (e.g., *Naveen Panth Parkash*, *Gyan Ratnavali*, etc.).
- **Enhance `showBooksDialog`**:
    - Add an `EditText` search bar at the top of the dialog to filter books by name.
    - Implement a filter mechanism that updates the `RecyclerView` as the user types.
    - Add a "Search Online" button that opens a web browser to Archive.org's Sikh collection, allowing users to find more books to download and import.
    - Make the "Import PDF" button more visually distinct to encourage "uploading" custom books.

### [app] Component: UI Resources

#### [NEW] [ic_search_online.xml](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/res/drawable/ic_search_online.xml) (Optional or use existing icons)
- Add a new icon for the "Search Online" feature if needed.

## Verification Plan

### Automated Tests
- No specific automated tests required for these UI-heavy changes, but I will ensure the code compiles successfully.

### Manual Verification
- **Open Books Panel**: Verify the new books appear in the list.
- **Search Functionality**: Type in the search bar and verify that the list of books (both default and imported) filters correctly.
- **Online Search**: Click the "Search Online" button and verify it opens the correct web archive.
- **Import PDF**: Verify that clicking "Import PDF" still triggers the file picker and successfully adds a book to "My Books".

## Open Questions
- Should the online search go to a specific site (like Archive.org or DiscoverSikhism) or perform a general Google search for Sikh PDFs? (I will default to Archive.org as it's the most reliable source for high-quality PDFs).
- Would the user like the search bar to also search *inside* downloaded PDFs? (This would be a much larger task requiring PDF indexing, so I'll stick to searching book titles for now).
