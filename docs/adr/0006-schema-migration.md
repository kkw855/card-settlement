# ADR-0006: 스키마 마이그레이션 위치와 실행 주체

- 상태: 확정 (2026-10-13)

## 맥락

`online-api`와 `batch`가 같은 스키마를 쓴다. 마이그레이션 파일을 어디에 두고 누가 적용할지 정해야 한다. 금융권 운영 환경에서는 보통 DBA가 승인된 DDL을 직접 적용하고, 애플리케이션은 스키마를 바꾸지 않는다.

## 대안

- **앱마다 각자 마이그레이션**: 같은 테이블을 두 곳에서 정의하게 되어 언젠가 어긋난다.
- **Flyway Gradle 플러그인이나 Flyway Docker 이미지로 따로 실행**: 앱과 분리된다는 점에서 운영 방식에 가깝다. 대신 로컬 실행 단계가 하나 늘고 플러그인·이미지 의존이 생긴다.
- **두 앱 모두 기동할 때 적용**: Flyway가 잠금으로 동시 실행을 막아 주지만, 어느 앱이 스키마를 바꿨는지 흐려진다.

## 결정

- 마이그레이션 SQL은 `core/src/main/resources/db/migration`에 둔다. 두 앱 모두 `core`를 의존하므로 같은 파일이 클래스패스에 실린다.
- 로컬에서는 **`online-api`가 기동할 때만** 적용한다 (`spring-boot-starter-flyway`, Phase 2). `batch`는 `spring.flyway.enabled=false`.
- 마이그레이션 자체는 `core` 테스트가 검증한다. Testcontainers로 compose와 같은 Oracle 이미지를 띄워 적용하고, DB 제약 조건이 불변식을 지키는지 확인한다.
- **적용된 마이그레이션 파일은 고치지 않는다.** 바꿀 게 있으면 새 버전(V2, V3…)을 추가한다. 이미 적용된 파일을 고치면 Flyway가 체크섬 불일치로 실행을 멈춘다.

## 결과

- Phase 1에서는 compose DB에 테이블이 아직 없다. Phase 2에서 `online-api`를 처음 띄울 때 생긴다.
- `core` 테스트에 Docker가 필요하다. `./gradlew build` 전에 Docker Desktop이 켜져 있어야 한다.
- 운영으로 옮긴다면 앱의 Flyway를 끄고, 같은 SQL 파일을 DBA 적용 절차로 넘기면 된다.
