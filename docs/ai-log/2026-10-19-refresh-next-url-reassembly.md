# 검증한 URL의 경로를 다시 합치면 외부 이동이 된다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 지적(인라인) |
| 제안 요지 | next를 web 출처 기준 URL로 파싱해 origin을 비교한 뒤 pathname, search, hash만 이동 대상으로 쓴다 |
| 왜 틀렸나 | 설계 불일치. 외부 이동을 막아야 하는데 정규화된 pathname이 두 슬래시로 시작하면 재조립한 상대 경로를 브라우저나 URL 생성자가 다른 출처로 해석한다 |
| 어떻게 알았나 | 실행. Node에서 next=/.//evil.com을 https://dropgate.example 기준으로 파싱하면 origin은 같은데 pathname은 //evil.com이다. 이 경로를 다시 URL로 파싱하면 origin이 https://evil.com으로 바뀐다. 검증한 URL 자체는 https://dropgate.example//evil.com으로 같은 출처를 유지한다 |
| 대신 한 것 | 외부 이동 지적은 반영하되 경로 재조립 제안은 쓰지 않고 origin을 검증한 URL 자체를 이동 대상으로 사용하도록 정했다. PR #120 |

지적: https://github.com/moonino/dropgate/pull/120#discussion_r4310319397

반영 답글: https://github.com/moonino/dropgate/pull/120#discussion_r4310331152

재현 명령:

```sh
node --input-type=module <<'JS'
const origin = 'https://dropgate.example';
const parsed = new URL('/.//evil.com', origin);
const reconstructed = new URL(parsed.pathname + parsed.search + parsed.hash, origin);
console.log(parsed.origin, parsed.pathname, reconstructed.origin, parsed.href);
JS
```
