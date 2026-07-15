#!/usr/bin/env bash
# ============================================================================
#  Project service load-test runner
#  Reads loadtest.properties, runs JMeter headless, writes a JTL + HTML report.
#  Usage:  ./run.sh
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"

PROPS="loadtest.properties"
PLAN="project-loadtest.jmx"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTDIR="results/run-$STAMP"
JTL="$OUTDIR/results.jtl"
HTML="$OUTDIR/html"

# --- checks ---
if ! command -v jmeter >/dev/null 2>&1; then
  echo "ERROR: jmeter not found on PATH."
  echo "Install:  (Debian/Ubuntu) sudo apt install jmeter"
  echo "     or:  download from https://jmeter.apache.org/download_jmeter.cgi and add bin/ to PATH"
  exit 1
fi
if grep -q '<<< FILL' "$PROPS"; then
  echo "ERROR: $PROPS still has placeholder values (<<< FILL >>>). Fill them in first:"
  grep -n '<<< FILL' "$PROPS"
  exit 1
fi

# --- derive host_domain (strip scheme) from 'host' so JMeter domain field is clean ---
HOST_RAW="$(grep -E '^host=' "$PROPS" | cut -d= -f2-)"
HOST_DOMAIN="${HOST_RAW#http://}"; HOST_DOMAIN="${HOST_DOMAIN#https://}"; HOST_DOMAIN="${HOST_DOMAIN%%/*}"

mkdir -p "$OUTDIR"
echo "==> Running load test against $HOST_DOMAIN"
echo "==> Output: $OUTDIR"

jmeter -n -t "$PLAN" \
  -q "$PROPS" \
  -Jhost_domain="$HOST_DOMAIN" \
  -l "$JTL" \
  -e -o "$HTML" \
  -j "$OUTDIR/jmeter.log"

echo ""
echo "==> Done."
echo "    Raw results : $JTL"
echo "    HTML report : $HTML/index.html   (open in a browser)"
echo "    Persistence lag lines: grep PERSISTENCE_LAG_MS $OUTDIR/jmeter.log"
echo ""
echo "==> Quick per-API summary:"
./summarize.sh "$JTL" || true
