#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
skill=.claude/commands/code-review.md
workflow=.github/workflows/claude-code-review.yml

workflow_tools=$(sed -n 's/.*--allowedTools "\([^"]*\)".*/\1/p' "$workflow")
if [ -z "$workflow_tools" ]; then
  echo "실패: 워크플로에 --allowedTools 목록이 없다" >&2
  exit 1
fi

failures=0
while IFS= read -r tool; do
  if grep -qF -- ",${tool}," <<<",${workflow_tools},"; then
    echo "통과: 워크플로가 ${tool}을 허용한다"
  else
    echo "실패: 스킬이 쓰는 ${tool}이 워크플로 허용 목록에 없다" >&2
    failures=$((failures + 1))
  fi
done < <(.github/scripts/skill-tools.sh "$skill")

if [ "$failures" -ne 0 ]; then
  exit 1
fi
