# Implementation Plan - Fix Calculator 3D Map and Theorem Visualization

The user reported that the "CALCULATOR 3D MAP IS NOT WROKING WELL IN ALL THERORIM". Investigation revealed several issues:
1.  **Theorem Integration**: When selecting formulas from the theorem/shape list, the app just pastes text into the display rather than opening the dedicated solver dialog.
2.  **3D Visualization for Theorems**: Many 3D-related formulas (like `foil3d` or 3D shape graphs) are not correctly recognized by `GraphView` because their labels (e.g., `3DPlot(`, `3DGraph(`) are not stripped, and they don't contain literal `x` and `y` before preprocessing.
3.  **Graphing Logic**: `GraphView` detects 3D mode based on raw input before preprocessing, causing it to miss theorems that expand into `x/y` expressions.
4.  **Visual Bugs**: `GraphView` has a missing column of lines in the 3D surface grid and a static "Noise Map" that fails to animate properly.

## Proposed Changes

### [Component Name] Graphing Engine

#### [MODIFY] [GraphView.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/GraphView.kt)
-   Update `setEquation` to:
    -   Add `3dplot(`, `3dgraph(`, `foil3d(`, `result(`, `solve(`, `expand(` to the `labels` list to ensure they are stripped correctly.
    -   Perform expression preprocessing *before* variable detection (`hasX && hasY`) to allow theorems to trigger `surfaceMode`.
    -   Add support for `foil3d` and other 3D-related function names directly if they aren't stripped.
-   Fix `draw3DSurface` grid:
    -   Update the loop to include the last column of vertical segments.
-   Fix `drawNoiseMap` and `generateNoiseBitmap`:
    -   Ensure `noiseSeed` or random generation incorporates `noiseTime` for animation.
    -   Add `postInvalidateOnAnimation()` call in `onDraw` to keep the animation running.

### [Component Name] Calculator UI

#### [MODIFY] [CalculatorActivity.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorActivity.kt)
-   Update `showShapeFormulaDialog` to call `showFormulaSolverDialog` instead of just updating the input field. This provides a better UX for theorems.
-   Ensure `showFormulaSolverDialog` handles variable substitution correctly for both "Solve" and "Graph" actions (add a "Graph" button to the dialog).

#### [MODIFY] [CalculatorPanel.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorPanel.kt)
-   Synchronize changes from `CalculatorActivity.kt` to `CalculatorPanel.kt` for consistency between the activity and the keyboard panel.

### [Component Name] Calculator Logic

#### [MODIFY] [CalculatorHelper.kt](file:///C:/Users/iqbal/AndroidStudioProjects/gurmukhikeyboard50/app/src/main/java/com/iqbal/gurmukhikeyboard50/CalculatorHelper.kt)
-   Add missing labels to the `labels` list in `preprocessExpression`.
-   Update `foil3d` regex to handle optional spaces and better formatting.

## Verification Plan

### Automated Tests
-   Verify `CalculatorHelper.preprocessExpression` for `foil3d(1, 1)` returns `(x + 1.0)*(y + 1.0)`.

### Manual Verification
1.  Open Calculator.
2.  Select "Algebra & Sequences" -> "3D FOIL".
3.  Observe that the Formula Solver dialog appears.
4.  Enter values and click "Solve" (verify result).
5.  Enter values and click "Graph" (verify 3D Surface Plot appears).
6.  Enter `noise(1)` in the calculator and verify the animated noise map appears and is not static.
7.  Check "Pythagorean Theorem" and verify it graphs correctly (if applicable).
