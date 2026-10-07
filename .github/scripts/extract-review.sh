#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "사용법: .github/scripts/extract-review.sh <execution json>" >&2
  exit 1
fi
execution_file=$1
REVIEW_HEADING='## Code review'
SOURCE_LINE='Claude Code(Opus 5.5) 자동 리뷰 결과다. 인라인 지적은 claude[bot]이 달고 이 요약은 워크플로가 올린다.'

last_result='flatten | map(select(.type == "result")) | last'
subtype=$(jq -rs "${last_result} | .subtype // \"없음\"" "$execution_file")
review=$(jq -rs "${last_result} | .result // \"\"" "$execution_file")

if [ "$(printf '%s\n' "$review" | head -1)" != "$REVIEW_HEADING" ]; then
  echo "::error::리뷰 결과가 '${REVIEW_HEADING}' 줄로 시작하지 않는다. 종료 유형: ${subtype}" >&2
  exit 1
fi
body=${review#"$REVIEW_HEADING"}
body=${body#"${body%%[![:space:]]*}"}
printf '%s\n\n%s\n\n%s\n' "$REVIEW_HEADING" "$SOURCE_LINE" "$body"
