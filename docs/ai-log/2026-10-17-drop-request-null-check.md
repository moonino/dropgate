# 드롭 등록 요청의 requireValidated를 중복 검사로 보고 지우지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | 다음 PR에서 Bean Validation이 들어오면 CreateDropRequest의 `requireValidated`(`checkNotNull`)와 같은 null 조건을 두 곳에서 검사하게 되니 헬퍼 정리를 미리 정한다. |
| 왜 틀렸나 | 설계 불일치. 일곱 원인 중 꼭 맞는 것이 없어 가장 가까운 것을 골랐다. 제안은 AGENTS.md의 "같은 조건을 두 곳에서 검증하지 않는다"를 근거로 들었지만, 중복을 없애려고 한쪽을 지우면 설계와 맞지 않거나 실패 맥락을 잃는다. 05-API 명세는 400 VALIDATION_FAILED의 errors에 필드별 메시지를 담게 하므로 필드를 nullable로 받고 Bean Validation이 null을 거절해야 한다. `requireValidated`는 입력 검증이 아니라 검증을 거치지 않고 `toCommand`를 부른 경우를 맥락 있는 예외로 잡는 불변식 확인이라, 지우면 같은 상황이 맥락 없는 NPE가 된다. 이 둘째 이유는 설계 문서가 아니라 실패를 맥락 있는 예외로 알린다는 판단에 근거한다. 두 검사는 맡은 일이 달라 위 규칙에 해당하지 않는다고 판단했다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안 3번](https://github.com/moonino/dropgate/pull/74#issuecomment-6230603982)을 docs/design/05-API-명세.md의 `400 VALIDATION_FAILED, errors에 필드별 메시지` 행과 대조했다. 다음 PR 브랜치(feat/8-drop-validation)의 CreateDropRequest도 `@field:NotNull`과 `requireValidated`를 함께 둔다. 실행이나 측정은 하지 않았다. |
| 대신 한 것 | nullable 필드, Bean Validation, `requireValidated`를 그대로 두고 기각 이유를 커밋 `test: 리뷰 제안을 반영한다 (#8)`과 PR 본문 리뷰 반영 절에 적었다. PR #74 |
