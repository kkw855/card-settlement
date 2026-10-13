# ADR-0001: Java 21 + Spring Boot + MyBatis + Oracle

- 상태: 확정 (2026-10-01)

## 맥락

이 프로젝트의 목적은 국내 금융권 SI 공고가 요구하는 기술과 도메인을 보여주는 것이다. 그 공고들의 공통 요구는 Java, Spring, MyBatis, Oracle, 배치, 대외 전문 연동이다.

## 대안

- **Kotlin + JPA + Postgres**: 다른 개인 프로젝트(리워드 플랫폼)가 이미 이 조합이다. 같은 조합을 반복하면 커버하는 공고가 늘지 않는다.
- **JPA (Oracle)**: 가능하지만, 금융권은 SQL을 직접 보고 튜닝하는 문화라 MyBatis가 주류다. 정산·대사 같은 집계 쿼리도 SQL로 쓰는 편이 명확하다.
- **Postgres**: 개발 편의는 좋지만 금융권 계정계는 Oracle이 압도적이다.

## 결정

- **Java 21** (LTS). record, sealed interface, 가상 스레드를 쓴다.
- **Spring Boot 4.1**, Spring Batch, MyBatis (Boot 4 호환 starter 버전은 Phase 1에서 확인하고, 호환 문제가 있으면 Boot 3.5로 내린다).
  - 확인 (2026-10-01): `mybatis-spring-boot-starter` 4.1.0이 Spring Boot 4.1.0 기준으로 빌드되어 있다 → **Boot 4.1 유지.** Boot 4.1 BOM이 관리하는 버전: Spring Batch 6.0, Flyway 12.4, Oracle JDBC 23.26, JUnit 6.0.
- **Oracle Database 23ai Free** — `gvenzl/oracle-free` 이미지 (Apple Silicon 지원). DB 마이그레이션은 Flyway.
  - 확인 (2026-10-13): `23-slim` 태그는 이제 **Oracle AI Database 26ai Free (23.26.3)**다. Oracle이 2025년 10월 23ai의 후속 릴리스를 26ai로 이름 붙였다. 버전 번호는 23.26이라 23ai와 같은 계열이고, Boot 4.1 BOM의 Oracle JDBC(23.26.3)와 버전이 맞는다.
- 빌드는 Gradle (Kotlin DSL) 멀티모듈.

## 결과

- Oracle Free는 **CPU 2스레드, 메모리 2GB, 사용자 데이터 12GB**로 제한된다. 성능 측정은 항상 이 조건과 함께 기록한다. 조건이 고정되어 있어 측정을 재현하기는 오히려 쉽다.
- Oracle에는 Postgres의 배제 제약(EXCLUDE)이 없어 선분이력 기간 겹침(I9)을 DB 제약만으로 막을 수 없다. 코드와 검증 쿼리로 막는다.
