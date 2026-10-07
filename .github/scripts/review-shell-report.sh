#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "사용법: .github/scripts/review-shell-report.sh <execution jsonl>" >&2
  exit 1
fi
execution_file=$1

denials=$(jq -rs 'flatten | map(select(.type == "result")) | last | .permission_denials // [] | length' "$execution_file")
echo "권한 거부: ${denials}건"
jq -rs 'flatten | map(select(.type == "result")) | last | .permission_denials[]? | "  거부: \(.tool_name) \(.tool_input.command // .tool_input.description // "")"' "$execution_file"

echo "하위 에이전트 셸 명령:"
jq -rs 'flatten
  | map(select(.type == "assistant" and .parent_tool_use_id != null))
  | map(.message.content[]? | select(.type == "tool_use" and .name == "Bash") | .input.command)
  | if length == 0 then "  없음" else .[] | "  " + . end' "$execution_file"
