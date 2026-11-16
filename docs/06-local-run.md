# 06. 로컬 실행과 스모크 테스트

승인 API를 내 컴퓨터에 띄우고, 실제 HTTP 요청으로 기본 거래를 확인하는 방법이다. Docker와 JDK 21만 있으면 되고 홈랩이나 외부 서비스는 쓰지 않는다.

## 1. 구성

| 프로세스 | 띄우는 명령 | 주소 |
|---|---|---|
| Oracle | `docker compose up -d` | `127.0.0.1:1521` |
| 가상 카드사 (simulator) | `./gradlew :simulator:run` | TCP `8583` |
| 승인 API (online-api) | `./gradlew :online-api:bootRun` | HTTP `8080` |

[scripts/smoke-test.sh](../scripts/smoke-test.sh)가 승인 API에 curl로 요청한다. 승인 API는 카드사에 0100 전문을 보내고 결과를 Oracle에 남긴다.

## 2. 준비

- Docker, JDK 21 (Gradle 툴체인이 21로 컴파일한다)
- `jq` (`brew install jq`), `curl`, `uuidgen` (macOS 기본 포함)

`.env.example`을 루트에 `.env`로 복사하고 값을 채운다. compose와 앱이 같은 파일을 읽는다. `.env`는 `.gitignore`에 있어 커밋되지 않는다 ([ADR-0005](adr/0005-card-data-protection.md)).

| 항목 | 용도 | 만드는 법 |
|---|---|---|
| `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD` | compose가 만드는 Oracle 관리자·앱 계정 | 직접 정한다 |
| `CARD_ENCRYPTION_KEY` | 카드번호·지급 계좌 암호화 (AES-256-GCM) | `openssl rand -base64 32` |
| `CARD_HMAC_KEY` | 같은 카드를 찾는 HMAC. 암호화 키와 다른 값이어야 한다 | `openssl rand -base64 32` |
| `LOCAL_MERCHANT_API_KEY` | 로컬 가맹점 M000000001의 API 키 | `openssl rand -hex 32` |

키를 추가할 때는 값이 화면에 남지 않게 파일에 바로 넣는다. 앞의 `\n`은 `.env` 마지막 줄에 줄바꿈이 없을 때를 대비한 것이다.

```bash
printf '\nLOCAL_MERCHANT_API_KEY=%s\n' "$(openssl rand -hex 32)" >> .env
```

## 3. 띄우기

터미널 3개에서 순서대로 띄운다.

1. `docker compose up -d`: 처음 띄우면 DB가 준비될 때까지 1~2분 걸린다. `docker compose ps`에 `healthy`가 보이면 다음으로 넘어간다.
2. `./gradlew :simulator:run`: 로그에 `카드사 시뮬레이터 시작: port=8583`이 나온다.
3. `./gradlew :online-api:bootRun`: `local` 프로필로 뜬다 ([online-api/build.gradle.kts](../online-api/build.gradle.kts)의 bootRun 설정).

bootRun은 기동하면서 두 가지를 한다.

- Flyway가 아직 적용하지 않은 마이그레이션을 적용한다 ([ADR-0006](adr/0006-schema-migration.md)).
- [LocalDataSeeder](../online-api/src/main/java/io/github/kkw855/settlement/online/local/LocalDataSeeder.java)가 가맹점 M000000001(홍길동카페, [02-message-spec.md](02-message-spec.md)의 골든 샘플)을 넣는다. 처음 띄울 때 `Started OnlineApiApplication` 다음 줄에 `로컬 가맹점 M000000001을 넣었다`가 나온다.
  - API 키는 SHA-256만 저장한다 ([03-api.md](03-api.md) 6장). 지급 계좌는 `CARD_ENCRYPTION_KEY`로 암호화해서 넣으므로 지급 파일 잡이 로컬에서도 복호화할 수 있다.
  - 가맹점이 이미 있으면 로그 없이 넘어간다. 이때 DB의 키 해시가 `.env`의 키와 같은지 확인하고, 다르면 기동을 멈춘다 (6장).

IntelliJ에서 `OnlineApiApplication`을 직접 실행할 때는 두 가지를 맞춘다.

- Active profiles에 `local`을 넣는다 (또는 VM 옵션 `-Dspring.profiles.active=local`).
- Working directory를 프로젝트 루트로 한다. 앱은 `.env`를 실행 위치에서 찾는다 (`spring.config.import: optional:file:.env[.properties]`).

## 4. 스모크 테스트

```bash
scripts/smoke-test.sh
```

| # | 확인 | 요청 | 기대 응답 |
|---|---|---|---|
| 1 | 승인 | 10,000원, 일시불 | 201 `APPROVED` / `0000` |
| 2 | 멱등 재현 | 1과 같은 키·같은 본문 | 201, 1과 같은 `txnId`·상태, `Idempotent-Replayed: true` |
| 3 | 조회 | `GET /v1/payments/{txnId}` | 200, `cardMasked` = `411111******1111` |
| 4 | 카드사 거절 | 10,001원 (끝자리 001) | 201 `DECLINED` / `1001` |
| 5 | 카드사 응답 지연 | 10,009원 (끝자리 009) | 201 `UNKNOWN` / `P003` |
| 6 | 인증 실패 | 틀린 API 키 | 401 `UNAUTHORIZED` |
| 7 | 소수 금액 | `10000.5` | 400 `VALIDATION_FAILED` |
| 8 | 할부 제한 | 10,000원, 3개월 | 422 `INSTALLMENT_NOT_ALLOWED` |
| 9 | 키 재사용 | 1과 같은 키, 20,000원 | 422 `IDEMPOTENCY_KEY_REUSED` |

- 모두 맞으면 `ok` 9줄이 나오고 종료 코드는 0이다. 하나라도 틀리면 `FAIL` 줄에 실제 응답을 찍고 종료 코드 1로 끝난다. 그래서 CI나 다른 스크립트에서도 그대로 쓸 수 있다.
- 4초쯤 걸린다 (2026-11-16 확인). 그중 3초는 5번에서 PG가 카드사 응답을 기다리는 시간(read timeout)이다. 시뮬레이터는 5초 뒤에야 응답한다.
- 5번은 망취소를 구현하기 전의 결과다. 망취소를 붙이면 `NET_CANCELLED` / `P002`가 맞다 ([03-api.md](03-api.md) 2장). 그때 스크립트의 기대값도 바꾼다.
- 멱등 키는 DB에 영구 보관되므로 실행마다 `uuidgen`으로 새 키를 만든다. 결제는 실행마다 3건(1·4·5번)씩 쌓인다.
- 다른 주소로 띄웠으면 `BASE_URL=http://localhost:18080 scripts/smoke-test.sh`처럼 넘긴다.
- 금액 끝자리 규칙은 [02-message-spec.md](02-message-spec.md) 4장에 있다.

## 5. 통합 테스트와 무엇이 다른가

`./gradlew test`의 통합 테스트는 분기와 경계값을 하나씩 확인한다. 스모크 테스트는 실제로 실행하는 모습 그대로 기본 거래가 되는지 본다.

| | 통합 테스트 | 스모크 테스트 |
|---|---|---|
| DB | Testcontainers가 띄운 Oracle | compose Oracle |
| HTTP | MockMvc (서블릿 컨테이너 없이 호출) | 실제 Tomcat에 curl |
| 카드사 | `CardCompanyClient`를 Mockito로 대신한다 | 시뮬레이터 프로세스와 실제 TCP |
| 설정 | 테스트 전용 키 (`IntegrationTest`) | `.env`, `local` 프로필, bootRun 설정 |

PG([CardMessageLayouts](../online-api/src/main/java/io/github/kkw855/settlement/online/card/CardMessageLayouts.java))와 시뮬레이터([MessageLayouts](../simulator/src/main/java/io/github/kkw855/settlement/simulator/MessageLayouts.java))는 전문 정의를 따로 갖는다. 둘이 실제 소켓으로 0100을 보내고 0110을 받는지는 지금은 스모크 테스트에서만 확인한다.

## 6. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| bootRun이 `LOCAL_MERCHANT_API_KEY가 없다`를 남기고 멈춘다 | `.env`에 키가 없다 | 2장대로 넣는다 |
| bootRun이 `M000000001의 API 키가 LOCAL_MERCHANT_API_KEY와 다르다`를 남기고 멈춘다 | `.env`의 키를 바꿨는데 DB에는 예전 키의 해시가 있다 | 키를 되돌린다. 아니면 `docker compose down -v`로 DB 볼륨을 지우고 다시 띄운다 (스키마와 데이터가 모두 지워진다) |
| 1~5번이 FAIL이고 응답이 `DECLINED` / `P001` | 시뮬레이터가 꺼져 있어 카드사에 보내지 못했다 | `./gradlew :simulator:run` |
| 6번만 ok이고 나머지는 HTTP 401 | 키에 맞는 가맹점이 없다. 새 DB에 `local` 프로필 없이 띄우면 생긴다 | 3장의 프로필 설정 |
| `curl: (7) Failed to connect`를 찍고 바로 끝난다 | 승인 API가 떠 있지 않다 | `./gradlew :online-api:bootRun` |

## 7. 설계 이유

- **시드를 Flyway SQL이 아니라 Java로 넣는다.** 지급 계좌 암호문과 API 키 해시는 각자의 `.env` 키로 만들어야 하는데, 키는 커밋하지 않으므로 SQL 파일에 고정 값으로 둘 수 없다. 또 Flyway 마이그레이션은 모든 환경에 같은 이력으로 적용되므로, SQL로 넣으면 테스트 가맹점이 운영 DB에도 들어간다.
- **`local` 프로필에서만 돈다.** 운영 jar에 들어 있어도 `local` 프로필이 아니면 빈이 만들어지지 않는다. `application-local.yaml`은 `.gitignore`에 있어 쓰지 않고, `@Value`로 `.env`의 이름을 바로 읽는다.
- **키가 다르면 기동을 멈춘다 (fail fast).** 그대로 뜨면 스모크 테스트가 이유 없이 401만 받는다. 원인과 해결 방법을 기동할 때 알려 준다.
