#!/usr/bin/env bash
set -euo pipefail

REPO=moonino/dropgate
AUTHOR=moonino
if [ $# -ne 3 ]; then
  echo "사용법: docs/ai-log/weekly.sh <주 라벨> <시작일> <종료일>" >&2
  exit 1
fi
WEEK=$1
FROM=$2
TO=$3

cd "$(git rev-parse --show-toplevel)"

merged_prs=$(gh pr list --repo "$REPO" --base main --state merged --limit 1000 --search "merged:${FROM}..${TO}" --json number --jq '.[].number')

verdicts_of() {
  gh api --paginate "repos/${REPO}/pulls/$1/comments" \
    --jq ".[] | select(.in_reply_to_id != null and .user.login == \"${AUTHOR}\") | .body | select(test(\"^(반영|기각)\")) | .[0:2]"
}

has_test_files() {
  gh pr view "$1" --repo "$REPO" --json files --jq '[.files[].path | select(test("src/test/"))] | length > 0' | grep -q true
}

declares_ai_test_draft() {
  gh pr view "$1" --repo "$REPO" --json body --jq .body | perl -0pe 's/<!--.*?-->//gs' | grep -q "테스트 초안"
}

accepted=0
rejected=0
test_prs=0
ai_test_prs=0
pr_patterns=()
for n in $merged_prs; do
  verdicts=$(verdicts_of "$n")
  accepted=$((accepted + $(grep -c '^반영' <<<"$verdicts" || true)))
  rejected=$((rejected + $(grep -c '^기각' <<<"$verdicts" || true)))
  if has_test_files "$n"; then
    test_prs=$((test_prs + 1))
    if declares_ai_test_draft "$n"; then
      ai_test_prs=$((ai_test_prs + 1))
    fi
  fi
  pr_patterns+=(-e "#${n}")
done

shopt -s nullglob
log_files=()
if [ ${#pr_patterns[@]} -gt 0 ]; then
  for f in docs/ai-log/2*.md; do
    if grep "대신 한 것" "$f" | grep -qw "${pr_patterns[@]}"; then
      log_files+=("$f")
    fi
  done
fi

rejected_logs=${#log_files[@]}
review_rejected_logs=0
top_cause=없음
if [ "$rejected_logs" -gt 0 ]; then
  review_rejected_logs=$(grep -h "날짜, 도구" "${log_files[@]}" | grep -c "자동 리뷰" || true)
  top_cause=$( (grep -h "왜 틀렸나" "${log_files[@]}" || true) | cut -d'|' -f3 | cut -d'.' -f1 | sed 's/^ *//; s/ *$//' | sort | uniq -c | sort -rn | head -1 | sed 's/^ *[0-9]* //')
  top_cause=${top_cause:-없음}
fi

if [ "$rejected" -ne "$review_rejected_logs" ]; then
  echo "경고: 기각 답글 ${rejected}건과 자동 리뷰 기각 일지 ${review_rejected_logs}건이 다르다" >&2
fi

pr_list=없음
if [ -n "$merged_prs" ]; then
  pr_list=$(printf '%s\n' $merged_prs | sed 's/^/#/' | paste -sd' ' -)
fi
printf '| %s | %s | %s | %s | %s | %s | %s | %s / %s |\n' \
  "$WEEK" "$pr_list" "$((accepted + rejected))" "$rejected" "$accepted" "$rejected_logs" "$top_cause" "$ai_test_prs" "$test_prs"
