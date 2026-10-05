# AI 활용 주간 기록

주마다 한 행. 모든 열은 그 주(월요일에서 일요일)에 main에 머지된 PR을 기준으로 센다. 기각 사례 본문은 이 폴더의 날짜 파일.

| 주 | 머지 PR | 자동 리뷰 지적 | 기각 | 반영 | 기각 일지 전체 | 기각 원인 1위 | AI 초안 테스트 PR / 테스트 PR |
|---|---|---|---|---|---|---|---|
| w1 | | | | | | | |

세는 법. 날짜와 N은 예시다. N은 머지 PR 목록의 각 번호이고, 합은 그 PR들을 더한 것이다.

- 머지 PR: `gh pr list --state merged --search "merged:2026-10-06..2026-10-12" --json number --jq '.[].number'`
- 자동 리뷰 지적: `gh api --paginate repos/moonino/dropgate/pulls/N/comments --jq '.[] | select(.user.login == "claude[bot]" and .in_reply_to_id == null) | .id' | wc -l`의 합. push마다 다시 달린 같은 지적도 센다. 다시 달렸다는 것은 그 push에서 반영되지 않았다는 뜻이다
- 기각: "대신 한 것"에 #N을 적고 "날짜, 도구"가 자동 리뷰인 파일 수. `grep -l "자동 리뷰" docs/ai-log/2*.md | xargs grep -lw "#N" | wc -l`의 합
- 반영: 지적 - 기각. 머지된 PR의 지적은 CONTRIBUTING 4절에 따라 전부 반영 또는 기각된 상태라 미처리가 없다
- 기각 일지 전체: #N을 적은 파일 수. 자동 리뷰 기각에 코딩 에이전트 제안 기각을 더한 것. `grep -lw "#N" docs/ai-log/2*.md | wc -l`의 합
- 기각 원인 1위: 위 파일들의 "왜 틀렸나" 첫 단어(README 원인 표) 중 최다
- AI 초안 테스트 PR / 테스트 PR: 분모는 `gh pr view N --json files --jq '[.files[].path | select(test("src/test/"))] | length > 0'`이 true인 PR 수. 분자는 분모에 든 PR 중 `gh pr view N --json body --jq '.body | gsub("<!--.*?-->"; ""; "s") | test("테스트 초안")'`이 true인 PR 수. gsub은 템플릿 주석 안의 같은 문구를 거른다
- 시간 절감은 비교군이 없으므로 적지 않는다
