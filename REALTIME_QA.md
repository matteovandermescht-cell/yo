# Live screen QA analysis

## Start
1. Install and open Own Game Controller.
2. Enable the floating overlay if you want the status panel on top of your game.
3. Tap **Start live screen analysis** in the app or in the floating menu.
4. Approve Android's screen-capture prompt. Android shows an ongoing notification while capture is active.
5. Open your own game. The overlay reports sampled red-like and green-like pixel percentages about once per second.
6. Tap **Stop live screen analysis** in the app to end capture.

## What it does
- Uses Android MediaProjection only after the user approves the system prompt.
- Samples screen pixels locally and reports approximate red/green color ratios.
- Processes frames in memory; it does not save the captured frames.
- This is a heuristic color analyzer, not a trained AI model and not semantic game-state recognition.
- It does not send taps, inject input, or automate gameplay.

## Current limitation
The analysis reports screen-wide color ratios. It does not yet know what those colors mean in a particular game. For meaningful QA assertions, integrate a test bridge into the game's own debug build and expose explicit state such as current screen, pause state, and test-case result.
