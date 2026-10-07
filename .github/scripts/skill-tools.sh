#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "사용법: .github/scripts/skill-tools.sh <스킬 파일>" >&2
  exit 1
fi
sed -n 's/^allowed-tools: //p' "$1" | tr ',' '\n' | sed 's/^ *//; s/ *$//' | sed '/^$/d'
