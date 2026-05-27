#!/usr/bin/env bash
# tests/run.sh — Autonomous highlight alignment test runner
#
# Usage:
#   ./tests/run.sh          # run tests headlessly
#   ./tests/run.sh --headed # run with browser visible
#
# What it does:
#   1. Generates the test PDF (app/src/main/assets/test.pdf)
#   2. Starts the HTTP server on :8080 (if not already running)
#   3. Installs npm deps (if needed)
#   4. Runs Playwright tests
#   5. Prints PASS/FAIL summary

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$REPO_DIR/app/src/main/assets"

echo "=== JustPdf Highlight Alignment Tests ==="
echo ""

# ── 1. Generate test PDF ──────────────────────────────────────────────────
echo "→ Generating test PDF..."
"$SCRIPT_DIR/.venv/bin/python3" "$SCRIPT_DIR/generate-test-pdf.py"
echo ""

# ── 2. Ensure HTTP server is running on :8080 from the assets directory ───
# Always kill any existing server to ensure it's serving from the right dir.
if lsof -ti:8080 > /dev/null 2>&1; then
  echo "→ Stopping existing server on :8080..."
  kill $(lsof -ti:8080) 2>/dev/null || true
  sleep 1
fi
echo "→ Starting HTTP server on :8080 from $ASSETS_DIR..."
cd "$ASSETS_DIR"
python3 -m http.server 8080 > /dev/null 2>&1 &
SERVER_PID=$!
cd "$REPO_DIR"
sleep 2
# Verify it's serving correctly.
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/viewer.html)
if [ "$HTTP_STATUS" != "200" ]; then
  echo "  ERROR: HTTP server returned $HTTP_STATUS for viewer.html"
  exit 1
fi
echo "  OK (PID: $SERVER_PID, viewer.html → $HTTP_STATUS)"
echo ""

# ── 3. Install npm deps ───────────────────────────────────────────────────
cd "$SCRIPT_DIR"
if [ ! -d "node_modules" ]; then
  echo "→ Installing npm dependencies..."
  npm install
  npx playwright install chromium
  echo ""
fi

# ── 4. Run Playwright tests ───────────────────────────────────────────────
echo "→ Running Playwright tests..."
echo ""

EXTRA_ARGS=""
if [[ "$1" == "--headed" ]]; then
  EXTRA_ARGS="--headed"
fi

if npx playwright test $EXTRA_ARGS; then
  echo ""
  echo "✅ ALL TESTS PASSED — highlight alignment is correct"
  EXIT_CODE=0
else
  echo ""
  echo "❌ TESTS FAILED — see output above for misaligned spans"
  EXIT_CODE=1
fi

# ── 5. Kill server if we started it ──────────────────────────────────────
if [ -n "$SERVER_PID" ]; then
  kill "$SERVER_PID" 2>/dev/null || true
fi

exit $EXIT_CODE
