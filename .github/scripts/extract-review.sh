#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "사용법: .github/scripts/extract-review.sh <execution json>" >&2
  exit 1
fi
execution_file=$1
REVIEW_HEADING='## Code review'

last_result='flatten | map(select(.type == "result")) | last'
subtype=$(jq -rs "${last_result} | .subtype // \"없음\"" "$execution_file")
review=$(jq -rs "${last_result} | .result // \"\"" "$execution_file")

if [ "$(printf '%s\n' "$review" | head -1)" != "$REVIEW_HEADING" ]; then
  echo "::error::리뷰 결과가 '${REVIEW_HEADING}' 줄로 시작하지 않는다. 종료 유형: ${subtype}" >&2
  exit 1
fi
printf '%s\n' "$review"
