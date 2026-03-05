# Bank No Touch (RuneLite Plugin)

Tracks bank withdrawals and reports your least-used withdrawn items.

## What it tracks
- `timesWithdrawn`: how many distinct withdrawal events included the item
- `quantityWithdrawn`: total number withdrawn over time
- `lastWithdrawn`: last timestamp seen withdrawn

## Behavior
- Takes a bank snapshot when the bank opens.
- On each bank container change, computes negative item deltas as withdrawals.
- Persists per-item stats in RuneLite config.
- Optionally prints a "least used" report when bank closes.

## Config
- `Show report when bank closes`
- `How many items to include`

## Build and test
- Run `./gradlew clean build`
- Unit tests currently cover withdrawal stat logic and serialization.

## Install checklist (local RuneLite testing)
1. Build the plugin jar: `./gradlew clean build`
2. Confirm artifact exists: `build/libs/bank-no-touch-0.1.0.jar`
3. In RuneLite, enable side-loading external plugins if your setup requires it.
4. Install/run the plugin from your external plugin dev flow (Plugin Hub dev or local side-load process).
5. Open bank, withdraw items, close bank, and verify the "least used" report appears in chat.
6. Adjust plugin config (`Show report when bank closes`, item count) and retest.

## Notes
- `runeLiteVersion` is set to `latest.release` in `build.gradle` to follow Plugin Hub guidance.

## Plugin Hub submission checklist
1. Push this repository to a public GitHub repo.
2. Copy the latest full commit hash:
   - `git rev-parse HEAD`
3. Fork `https://github.com/runelite/plugin-hub`.
4. In your fork, create a new file at `plugins/bank-no-touch` with:
   - `repository=https://github.com/<your-user>/<your-repo>.git`
   - `commit=<40-char-commit-hash>`
5. Commit only that new file in your fork and open a pull request to `runelite/plugin-hub`.
6. Wait for CI checks:
   - `.github/workflows/build.yml / build (pull_request)`
   - `RuneLite Plugin Hub Checks`
7. If you push plugin changes, update the `commit=` value in the same `plugins/bank-no-touch` file.
