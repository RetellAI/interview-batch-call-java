#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pattern="${1:-}"

if [[ -n "$pattern" ]]; then
  case "$pattern" in
    case-a) pattern="caseA" ;;
    case-b) pattern="caseB" ;;
    case-c) pattern="caseC" ;;
    case-d) pattern="caseD" ;;
  esac
  mvn -f "$root_dir/pom.xml" -Dtest="ScenarioTests#${pattern}" test
else
  mvn -f "$root_dir/pom.xml" test
fi
