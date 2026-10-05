# MODULE 빌드 인자 검사를 RUN 단계로 앞당기지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-07, Claude Code 자동 리뷰 |
| 제안 요지 | ARG MODULE 바로 뒤에 `RUN test -n "${MODULE}"`를 두어 빌드 인자 누락을 첫 단계에서 실패시킨다. |
| 왜 틀렸나 | 동시성 가정 오류. BuildKit은 RUN 단계가 끝나기를 기다리지 않고 뒤 COPY 단계의 원본 체크섬을 병렬로 계산한다. MODULE이 비면 `COPY /src/ /src/` 경로 오류가 먼저 보고되고 검사 단계는 CANCELED로 끝나므로 오류 메시지가 바뀌지 않는다. |
| 어떻게 알았나 | [리뷰 제안 1번](https://github.com/moonino/dropgate/pull/45#issuecomment-5996886174)을 아래 명령으로 실행했다. Docker Desktop 4.84.0, Engine 29.6.2. 실패 단계는 `[build 14/15] COPY /src/ /src/`였고 `[build 3/15] RUN test -n`은 CANCELED였다. 세 번 반복해도 같았다. |
| 대신 한 것 | 검사 없이 Dockerfile을 유지한다. CI는 항상 `--build-arg MODULE`을 넘긴다. PR #45 |

```sh
sed -i '' 's|^ARG MODULE$|ARG MODULE\nRUN test -n "${MODULE}"|' Dockerfile
docker build --progress=plain -t dropgate/missing-module:check . 2>&1 | grep -E '^#[0-9]+ (\[build|ERROR|CANCELED)'
git checkout -- Dockerfile
```
