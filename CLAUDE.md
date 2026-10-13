# card-settlement

카드 결제 승인·대사·정산 시스템. 우리는 PG(결제대행사) 역할: 가상 카드사와 고정길이 전문으로 승인을 받고, 다음 날 매입 내역 파일과 대사한 뒤 가맹점 정산금과 지급 파일을 만든다.

## 문서 (작업 전에 읽을 것)
- `docs/00-domain.md` — 범위, 용어, 흐름, 상태 전이, 대사 유형, 불변식(I1~I10), 규모 계산
- `docs/01-erd.md` — ERD와 정산 계산 예시
- `docs/02-message-spec.md` — 전문·파일 바이트 명세, 골든 샘플, 시뮬레이터 테스트 규칙
- `docs/03-api.md` + `docs/api/openapi.yaml` — 승인·취소 API 계약과 설계 이유 (멱등성, 결과 코드, 응답 시간 상한)
- `docs/04-batch.md` — 배치 잡 구성, 재시작 규칙, 대사 분류, 정산·배분, 지급 파일, 인덱스, 측정 계획
- `docs/adr/` — 설계 결정. 결정을 바꾸면 새 ADR을 추가하고 기존 ADR 상태를 갱신한다.

## 스택
- Java 21, Spring Boot 4.1, Spring Batch, MyBatis, Oracle AI Database 26ai Free (버전 23.26), Flyway, Gradle(Kotlin DSL) 멀티모듈.
- 모듈: `codec`, `core`, `online-api`, `batch`, `simulator` (ADR-0002).
- 실행: 로컬 docker compose (Oracle). 홈랩에 의존하지 않는다.

## 로드맵
- Phase 0 설계 ✅
  - 도메인, ERD, 전문·파일 명세, ADR ✅
  - 승인 API 계약(`docs/03-api.md`, OpenAPI), 배치 잡 설계(`docs/04-batch.md`) ✅
- Phase 1 기반 ← 다음: 멀티모듈, docker compose(Oracle), Flyway V1, `codec` (골든 샘플 테스트)
- Phase 2 온라인: 시뮬레이터 전문 서버, 승인·취소 API, 타임아웃·망취소
- Phase 3 배치: 매입 적재 → 대사 → 정산 잡, 지급 파일 잡
- Phase 4 측정: 100만 건 합성 데이터, chunk 크기별 처리 시간 → README
- **완성선 (v1)**: Phase 0~4
- 완성선 이후: React 관리 화면(대사 불일치 처리, 정산 조회), 부분 취소, 매입 청구 파일, 음수 정산 상계, 상시 연결 전문 통신

## 작업 원칙
- 금액은 `long` + `BigDecimal`. `double` 금지 (ADR-0004).
- 카드번호·계좌번호는 평문 저장·로그 금지 (ADR-0005). 테스트 카드번호만 사용.
- 전문·파일 길이는 항상 **바이트** 기준(MS949).
- 성능 개선은 측정 → 원인 → 해결 → 재측정 순서로 하고, 측정 조건(Oracle Free 제한 포함)과 수치를 `docs/`에 남긴다.
