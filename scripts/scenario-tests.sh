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
  ./gradlew -p "$root_dir" test --tests "ai.retell.batchcall.ScenarioTests.${pattern}"
else
  ./gradlew -p "$root_dir" test
fi
