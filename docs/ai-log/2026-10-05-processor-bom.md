# 사용하지 않는 프로세서 구성을 미리 추가하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-05, Claude Code 자동 리뷰 |
| 제안 요지 | annotationProcessor나 kapt에도 BOM을 적용한다. |
| 왜 틀렸나 | 과잉. 별도 프로세서 구성에 BOM이 자동 적용되지 않는다는 설명은 맞지만 현재 프로세서 의존성과 kapt 플러그인이 없어 이번 변경에 필요하지 않다. |
| 어떻게 알았나 | [리뷰 4번](https://github.com/moonino/dropgate/pull/37#issuecomment-5994143104)과 buildSrc 컨벤션, 모듈 빌드 파일을 대조했다. 현재 사용 경로는 implementation과 이를 상속하는 테스트뿐이다. |
| 대신 한 것 | 프로세서 도입 시 해당 구성과 BOM을 함께 추가한다. PR #37 |
