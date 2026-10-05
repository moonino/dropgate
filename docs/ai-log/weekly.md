# AI 활용 주간 기록

주마다 한 행. 모든 열은 그 주(월요일에서 일요일)에 main에 머지된 PR을 기준으로 센다. 기각 사례 본문은 이 폴더의 날짜 파일.

| 주 | 머지 PR | 자동 리뷰 지적 | 기각 | 반영 | 기각 일지 전체 | 기각 원인 1위 | AI 초안 테스트 PR / 테스트 PR |
|---|---|---|---|---|---|---|---|
| w1 | | | | | | | |

세는 법. 날짜와 N은 예시다. N은 머지 PR 목록의 각 번호이고, 합은 그 PR들을 더한 것이다.

- 머지 PR: `gh pr list --state merged --search "merged:2026-10-06..2026-10-12" --json number --jq '.[].number'`
- 자동 리뷰 지적: 작성자가 반영 또는 기각으로 답글을 단 지적 수. 답글 규칙은 CONTRIBUTING 4절. push마다 다시 달린 같은 지적은 답글이 없으므로 세지 않는다. `gh api --paginate repos/moonino/dropgate/pulls/N/comments --jq '.[] | select(.in_reply_to_id != null and .user.login == "moonino") | .body' | grep -cE "^(반영|기각)"`의 합
- 반영: 같은 명령에서 `grep -c "^반영"`의 합
- 기각: 같은 명령에서 `grep -c "^기각"`의 합. 지적 = 반영 + 기각. 기각마다 이 폴더에 파일이 하나 있어야 하므로 `grep -l "자동 리뷰" docs/ai-log/2*.md | xargs grep -lw "#N" | wc -l`의 합과 같아야 한다
- 기각 일지 전체: #N을 적은 파일 수. 자동 리뷰 기각에 코딩 에이전트 제안 기각을 더한 것. `grep -lw "#N" docs/ai-log/2*.md | wc -l`의 합
- 기각 원인 1위: `grep -h "왜 틀렸나" $(grep -lw "#N" docs/ai-log/2*.md) | cut -d'|' -f3 | awk '{print $1}' | sort | uniq -c | sort -rn | head -1`. README 원인 표의 첫 단어는 서로 겹치지 않는다
- AI 초안 테스트 PR / 테스트 PR: 분모는 `gh pr view N --json files --jq '[.files[].path | select(test("src/test/"))] | length > 0'`이 true인 PR 수. 분자는 분모에 든 PR 중 `gh pr view N --json body --jq '.body | gsub("<!--.*?-->"; ""; "s") | test("테스트 초안")'`이 true인 PR 수. gsub은 템플릿 주석 안의 같은 문구를 거른다
- 시간 절감은 비교군이 없으므로 적지 않는다
