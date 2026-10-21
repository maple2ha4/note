#!/usr/bin/env bash
set -euo pipefail
notes_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$notes_root/backend"
if [[ ! -f target/notes-1.0.0.jar || ! -f ../frontend/dist/index.html ]]; then
 echo 'Run bash scripts/setup.sh from the repository root first.' >&2
 exit 1
fi
umask 077
exec java -jar target/notes-1.0.0.jar
