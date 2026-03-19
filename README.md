# Bank No Touch

A [RuneLite](https://runelite.net) plugin that tracks how often you withdraw items from your bank and surfaces the items you barely touch — along with how much GP you could earn if you sold them.

## Features

### Sell-off Panel
A dedicated sidebar panel shows every item in your bank that falls below your configured withdrawal threshold, ranked by total GE stack value (highest first).

Each item row displays:
- Item icon
- Name
- Withdrawal count and current bank quantity
- Unit price (from the Grand Exchange)
- **Total stack value** (unit price × quantity)

The panel header shows:
- **Items to Sell** — count of flagged items
- **Potential GP** — grand total value of all flagged stacks (formatted as K / M / B)

### Withdrawal Tracking
Every time you open your bank, the plugin takes a snapshot of its contents. When items leave the bank, it records:
- How many times each item has been withdrawn (`timesWithdrawn`)
- The total quantity withdrawn over time (`quantityWithdrawn`)
- The timestamp of the last withdrawal

Data persists across sessions via RuneLite's config storage.

### Optional Chat Report
When the bank closes, optionally print a least-used item summary to your in-game chat.

## Configuration

| Setting | Default | Description |
|---|---|---|
| Rarely-used threshold | 3 | Items withdrawn **fewer than this many times** appear in the sell-off panel. Set to 1 to show only never-touched items. |
| Show chat report on close | Off | Print a least-used summary to chat when the bank closes. |
| Chat report item count | 10 | How many items to include in the chat report. |
| Only track equipment | Off | Restrict withdrawal tracking to wearable/wieldable items only. |

## Installation

See the [Plugin Hub submission](https://runelite.net/plugin-hub) once available, or follow the [manual installation instructions](https://github.com/thefriendlyhedgehog/bank-no-touch#development) for local testing.

## Development

**Run a local dev client:**
```bash
./gradlew run          # macOS / Linux
.\gradlew.bat run      # Windows
```

**Build a distributable JAR:**
```bash
./gradlew shadowJar
# Output: build/libs/bank-no-touch-0.1.0-all.jar
```

**Run unit tests:**
```bash
./gradlew test
```

## Plugin Hub Submission Checklist

1. Push to a public GitHub repository.
2. Confirm the latest commit hash: `git rev-parse HEAD`
3. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub).
4. Create `plugins/bank-no-touch` in your fork:
   ```
   repository=https://github.com/thefriendlyhedgehog/bank-no-touch.git
   commit=<full 40-character hash>
   ```
5. Open a pull request to `runelite/plugin-hub`.
6. Verify both CI checks pass: `build.yml / build` and `RuneLite Plugin Hub Checks`.

## License

[BSD 2-Clause](LICENSE) © 2026 thefriendlyhedgehog
