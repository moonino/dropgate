#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
extract=.github/scripts/extract-review.sh
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT
failures=0

assert_passes() {
  local name=$1 file=$2 expected=$3
  if actual=$("$extract" "$file" 2>/dev/null) && [ "$actual" = "$expected" ]; then
    echo "통과: $name"
  else
    echo "실패: $name" >&2
    failures=$((failures + 1))
  fi
}

assert_fails() {
  local name=$1 file=$2
  if "$extract" "$file" >/dev/null 2>&1; then
    echo "실패: $name" >&2
    failures=$((failures + 1))
  else
    echo "통과: $name"
  fi
}

cat > "$workdir/array.json" <<'JSON'
[
  {"type": "system", "subtype": "init"},
  {"type": "assistant", "message": {"content": [{"type": "text", "text": "## Code review\n\n중간 메시지"}]}},
  {"type": "result", "subtype": "success", "result": "## Code review\n\n문제를 찾지 못했다."}
]
JSON
assert_passes "배열 입력에서 마지막 result 본문을 돌려준다" "$workdir/array.json" $'## Code review\n\nClaude Code(Opus 5.5) 자동 리뷰 결과다. 인라인 지적은 claude[bot]이 달고 이 요약은 워크플로가 올린다.\n\n문제를 찾지 못했다.'

cat > "$workdir/object.json" <<'JSON'
{"type": "result", "subtype": "success", "result": "## Code review\n\n지적 1건."}
JSON
assert_passes "단일 result 객체 입력도 받는다" "$workdir/object.json" $'## Code review\n\nClaude Code(Opus 5.5) 자동 리뷰 결과다. 인라인 지적은 claude[bot]이 달고 이 요약은 워크플로가 올린다.\n\n지적 1건.'

cat > "$workdir/stream.jsonl" <<'JSON'
{"type": "system", "subtype": "init"}
{"type": "result", "subtype": "success", "result": "## Code review\n\n줄 단위 입력."}
JSON
assert_passes "줄 단위 JSON 입력도 받는다" "$workdir/stream.jsonl" $'## Code review\n\nClaude Code(Opus 5.5) 자동 리뷰 결과다. 인라인 지적은 claude[bot]이 달고 이 요약은 워크플로가 올린다.\n\n줄 단위 입력.'

cat > "$workdir/no-heading.json" <<'JSON'
[{"type": "result", "subtype": "success", "result": "Waiting for the review agents."}]
JSON
assert_fails "머리글이 없는 결과는 실패한다" "$workdir/no-heading.json"

cat > "$workdir/heading-not-first.json" <<'JSON'
[{"type": "result", "subtype": "success", "result": "### 리뷰 결과\n## Code review"}]
JSON
assert_fails "머리글이 첫 줄이 아니면 실패한다" "$workdir/heading-not-first.json"

cat > "$workdir/max-turns.json" <<'JSON'
[{"type": "result", "subtype": "error_max_turns", "is_error": true}]
JSON
assert_fails "result 본문이 없으면 실패한다" "$workdir/max-turns.json"

cat > "$workdir/no-result.json" <<'JSON'
[{"type": "system", "subtype": "init"}]
JSON
assert_fails "result 메시지가 없으면 실패한다" "$workdir/no-result.json"

if [ "$failures" -ne 0 ]; then
  echo "실패 ${failures}건" >&2
  exit 1
fi
