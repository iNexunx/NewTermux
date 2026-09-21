# NewTermux Progress Log

## 2026-04-25 — Session Pip View (feature/session-pip-view)

**Status: CI green, NOT merged to main. Awaiting user testing.**

### What was built
Replaced the flat Material chip session tabs with live mini terminal previews ("pips").

**Files changed:**
- `app/src/main/java/com/termux/app/terminal/MiniTerminalPipView.java` (NEW)
  - Custom View using `TerminalRenderer.render()` scaled via `Canvas.scale()` to fit 72×48dp
  - Renders full session screen: all colors, cursor, output — scaled down
  - `notifyUpdate()` calls `postInvalidate()` (thread-safe) for live updates
  - Active session: accent-color border (#BB86FC); inactive: dim border (#444444)
- `app/src/main/res/layout/newtermux_toolbar.xml`
  - Removed `ChipGroup` (`session_chip_group`)
  - Added `LinearLayout` (`session_pip_container`) inside existing `HorizontalScrollView`
  - Row height: 40dp → 56dp
- `app/src/main/java/com/termux/app/TermuxActivity.java`
  - Field: `mSessionChipGroup` (ChipGroup) → `mSessionPipContainer` (LinearLayout)
  - `updateSessionTabs()` now creates `MiniTerminalPipView` instances (72×48dp, 4dp margin)
  - Added `notifyPipUpdate(TerminalSession)` — finds and invalidates only the changed pip
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java`
  - `onTextChanged()` now calls `mActivity.notifyPipUpdate(changedSession)` for live refresh
- `.github/workflows/debug_build.yml`
  - Added `feature/**` to push trigger list so feature branches build automatically

**CI:** Run 24946104721 — success on `feature/session-pip-view`

### Next steps after testing
- If pips look good → merge `feature/session-pip-view` to `main`, bump version, trigger release
- Possible follow-ups:
  - Add close button drawn in pip corner (currently close is via long-press rename; no X on pip)
  - Swipe-to-close gesture on pip
  - Tweak pip size (currently 72×48dp) if too small or too large on device

---

## 2026-04-21 — v1.5.4 (claude-integration merged to main)

**Status: Released, tagged v1.5.4**

- Claude Chat button in toolbar (removed in later commit — see note below)
- Background kill fix: START_STICKY + PARTIAL_WAKE_LOCK in onStop/onStart
- RUN_COMMAND permission: dangerous → normal
- Settings toggle for Claude Chat button

**Note:** Claude Chat button was subsequently removed (commit 7799e1f) — not in current main.
Current main also has: "Allow scrolling terminal while a command is running" (commit 158231c).
