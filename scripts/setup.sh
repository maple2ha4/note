#!/usr/bin/env bash
set -euo pipefail
notes_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$notes_root"
python3 scripts/bootstrap.py
npm --prefix frontend ci --cache "${NOTES_NPM_CACHE:-/workspace/.npm}" --no-audit --no-fund
npm --prefix frontend run build
bash scripts/mvn.sh verify
