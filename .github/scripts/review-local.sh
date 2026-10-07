#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "사용법: .github/scripts/review-local.sh <PR URL>" >&2
  exit 1
fi
pr_url=$1
cd "$(git rev-parse --show-toplevel)"
mkdir -p work
execution_file=work/review-execution.jsonl

tools=$(.github/scripts/skill-tools.sh .claude/commands/code-review.md | grep -v '^mcp__' | paste -sd, -)
CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1 claude -p "/code-review ${pr_url}" \
  --setting-sources project \
  --model claude-opus-5-5 \
  --max-turns 40 \
  --allowedTools "$tools" \
  --output-format stream-json --verbose < /dev/null > "$execution_file"

.github/scripts/extract-review.sh "$execution_file"
.github/scripts/review-shell-report.sh "$execution_file" >&2
