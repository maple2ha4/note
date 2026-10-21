#!/usr/bin/env bash
set -euo pipefail
notes_root="$(cd "$(dirname "$0")/.." && pwd)"
notes_tools="${NOTES_TOOLS_DIR:-/workspace/tools}"
notes_cache="${NOTES_MAVEN_CACHE:-/workspace/.m2/repository}"
notes_java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
compiler_options=()
# Cloud images may contain the compiler module but omit the cross-release API archive.
# Source/target 21 still produces Java 21 bytecode against this Java 21 runtime.
if [[ ! -f "$notes_java_home/lib/ct.sym" ]]; then
 compiler_options=(-Dmaven.compiler.release= -Dmaven.compiler.source=21 -Dmaven.compiler.target=21)
fi
exec "$notes_tools/apache-maven-3.9.11/bin/mvn" -B -ntp -s "$notes_tools/maven-settings.xml" "-Dmaven.repo.local=$notes_cache" "${compiler_options[@]}" -f "$notes_root/backend/pom.xml" "$@"
