# Fix ANR in GraphView

Optimize `GraphView` drawing logic to prevent ANR when the graph is panned far from the origin or zoomed out. The main issues are inefficient loops in `drawAxes` and redundant string processing in `drawGraph`.

## Proposed Changes

### [CalculatorHelper](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorHelper.kt)

#### [MODIFY] [CalculatorHelper.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorHelper.kt)
- Add a `preprocessExpression` method to perform the regex and string replacements once.
- Update `evaluateFull` to accept a preprocessed expression to avoid repeated overhead during graph drawing.

### [GraphView](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/GraphView.kt)

#### [MODIFY] [GraphView.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/GraphView.kt)
- **Optimize `drawAxes`**: Replace the current `while` loops (which can iterate thousands of times uselessly) with range-based loops that only iterate over visible units.
- **Optimize `drawGraph`**:
    - Preprocess the equation string using `CalculatorHelper.preprocessExpression` before the drawing loop.
    - Limit the number of points evaluated in `drawGraph` to a reasonable maximum (e.g., 2000 points) to maintain performance regardless of zoom level.
- **Constraint Scale**: Ensure `scaleX` and `scaleY` are kept within safe bounds (currently 1f to 5000f, which is okay, but the drawing logic must be robust).

---

## Verification Plan

### Automated Tests
- Run existing unit tests (if any) to ensure calculator logic is still correct.
- I will check if there are any tests.

### Manual Verification
1. Deploy the app.
2. Open the Graph feature.
3. Pan the graph far away from the center (e.g., drag for a long time).
4. Zoom out fully.
5. Verify that the app remains responsive and no ANR occurs.
6. Verify that axes labels and the graph itself are still drawn correctly in the visible area.
