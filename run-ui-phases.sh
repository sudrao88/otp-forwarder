#!/bin/bash
# Unattended loop: implements one phase of UI_REDESIGN_PLAN.md per iteration,
# each in a fresh Claude Code session (clean context window).
# Stop anytime with Ctrl+C.

set -euo pipefail
cd "$(dirname "$0")"

PLAN_FILE="UI_REDESIGN_PLAN.md"
PROGRESS_FILE="UI_PROGRESS.md"
LOG_FILE="ui-phase-loop.log"
BRANCH="feature/ui-redesign"
TOTAL="${TOTAL:-7}"

if [[ ! -f "$PLAN_FILE" ]]; then
  echo "✗ Missing $PLAN_FILE — aborting."
  exit 1
fi

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "$BRANCH" ]]; then
  echo "✗ Expected branch '$BRANCH' but on '$CURRENT_BRANCH'. Switch branches first."
  exit 1
fi

[[ -f "$PROGRESS_FILE" ]] || echo "CURRENT_PHASE=0" > "$PROGRESS_FILE"

while :; do
  CUR=$(grep -oE '[0-9]+' "$PROGRESS_FILE" | head -1)
  NEXT=$((CUR + 1))

  if [[ $NEXT -gt $TOTAL ]]; then
    echo "✓ All $TOTAL phases complete."
    break
  fi

  echo ""
  echo "=========================================="
  echo "  Starting Phase $NEXT of $TOTAL"
  echo "  $(date)"
  echo "=========================================="

  claude -p --dangerously-skip-permissions \
    "Implement Phase $NEXT from $PLAN_FILE end-to-end. Follow the rules in CLAUDE.md and the Global rules at the top of $PLAN_FILE. \
     Read $PLAN_FILE first to find the exact scope, file list, and commit message for Phase $NEXT — do not implement any other phase. \
     Verify you are on branch '$BRANCH'; do not switch branches. \
     Run ./gradlew assembleDebug and ./gradlew test — both must pass before you commit. \
     Commit using the commit message specified for Phase $NEXT in $PLAN_FILE. \
     Finally, overwrite $PROGRESS_FILE so its first line is exactly: CURRENT_PHASE=$NEXT" \
    2>&1 | tee -a "$LOG_FILE"

  NEW=$(grep -oE '[0-9]+' "$PROGRESS_FILE" | head -1)
  if [[ "$NEW" != "$NEXT" ]]; then
    echo ""
    echo "✗ Phase $NEXT did not advance $PROGRESS_FILE. Stopping so you can inspect."
    echo "  Log: $LOG_FILE"
    echo "  Git: run 'git status' and 'git log -1' to see what happened."
    exit 1
  fi

  echo "✓ Phase $NEXT complete."
done
