# 승인 요청·응답의 클래스 흐름

현재 구현된 **승인 API(`POST /v1/payments`) → 0100 승인 요청 → 0110 승인 응답**을 기준으로 정리한다. 클래스 연결도는 책임의 분리를, 시퀀스 다이어그램은 호출 순서를 보여 준다. HTTP 계약과 설계 이유는 [03-api.md](03-api.md)에 있다.

## 클래스 연결도

![승인 처리의 클래스 연결도](diagrams/approval-class-flow.png)

[확대 가능한 SVG](diagrams/approval-class-flow.svg)

| 클래스 | 실제 역할 |
|---|---|
| [MerchantAuthInterceptor](../online-api/src/main/java/io/github/kkw855/settlement/online/web/MerchantAuthInterceptor.java) | `/v1/**` 요청마다 `X-Api-Key`의 SHA-256으로 가맹점을 찾아 요청 속성에 넣는다. 키가 없거나 틀리면 401이다. [WebConfig](../online-api/src/main/java/io/github/kkw855/settlement/online/web/WebConfig.java)가 등록한다. |
| [MerchantMapper](../core/src/main/java/io/github/kkw855/settlement/core/merchant/MerchantMapper.java) | `findByApiKeyHash()`로 API 키 해시에 맞는 가맹점을 조회한다. 해시는 [ApiKeys](../core/src/main/java/io/github/kkw855/settlement/core/merchant/ApiKeys.java)가 만든다. |
| [PaymentController](../online-api/src/main/java/io/github/kkw855/settlement/online/payment/PaymentController.java) | 본문([ApprovePaymentRequest](../online-api/src/main/java/io/github/kkw855/settlement/online/payment/ApprovePaymentRequest.java))을 `@Valid`로 검증하고 `Idempotency-Key`를 확인한 뒤 서비스를 부른다. 결과를 [PaymentResponse](../online-api/src/main/java/io/github/kkw855/settlement/online/payment/PaymentResponse.java)로 바꿔 201 + `Location`으로 응답한다. 조회(`GET`)는 [PaymentQueryService](../online-api/src/main/java/io/github/kkw855/settlement/online/payment/PaymentQueryService.java)에 맡긴다. |
| [ApiExceptionHandler](../online-api/src/main/java/io/github/kkw855/settlement/online/error/ApiExceptionHandler.java) | 예외를 RFC 9457 Problem Details + `code`로 바꾼다. 검증 오류에는 필드명과 이유만 담고 입력값은 담지 않는다 (I10). |
| [ApprovalService](../online-api/src/main/java/io/github/kkw855/settlement/online/payment/ApprovalService.java) | 요청 검증, 결제 저장, 멱등 처리, 카드사 호출, 최종 상태 반영을 조율한다. |
| [TxnIdGenerator](../core/src/main/java/io/github/kkw855/settlement/core/payment/TxnIdGenerator.java) | DB 시퀀스를 받아 거래고유번호를 만든다. |
| [CardProtector](../core/src/main/java/io/github/kkw855/settlement/core/card/CardProtector.java) | DB에 보관할 카드번호의 암호문·검색용 HMAC·마스킹 값을 만든다. |
| [PaymentMapper](../core/src/main/java/io/github/kkw855/settlement/core/payment/PaymentMapper.java) | [PaymentMapper.xml](../core/src/main/resources/mapper/PaymentMapper.xml)의 SQL로 Oracle `payment`를 저장·조회·변경한다. |
| [ApprovalGateway](../online-api/src/main/java/io/github/kkw855/settlement/online/card/ApprovalGateway.java) | 요청 전문을 조립하고 송수신 로그를 호출하며, 수신 전문을 업무 결과로 해석한다. |
| [CardMessageLayouts](../online-api/src/main/java/io/github/kkw855/settlement/online/card/CardMessageLayouts.java) | PG 쪽 전문 필드의 위치·길이·타입을 정의한다. |
| [MessageLogger](../online-api/src/main/java/io/github/kkw855/settlement/online/card/MessageLogger.java) | 카드번호를 가린 전문을 `SEND` 또는 `RECV` 로그로 저장하도록 요청한다. |
| [MessageLogMapper](../core/src/main/java/io/github/kkw855/settlement/core/message/MessageLogMapper.java) | [MessageLogMapper.xml](../core/src/main/resources/mapper/MessageLogMapper.xml)의 SQL로 Oracle `message_log`에 기록한다. |
| [CardCompanyClient](../online-api/src/main/java/io/github/kkw855/settlement/online/card/CardCompanyClient.java) | 거래마다 소켓을 열고 연결·전송·응답 읽기·종료를 수행한다. |
| [Framing](../codec/src/main/java/io/github/kkw855/settlement/codec/Framing.java) | TCP 스트림에서 `[ASCII 길이 4바이트][전문 본문]`을 쓰고 읽는다. |
| [SimulatorServer](../simulator/src/main/java/io/github/kkw855/settlement/simulator/SimulatorServer.java) | 카드사 쪽에서 연결을 받고, 전문을 읽어 업무 로직에 전달한 뒤 응답을 전송한다. |
| [CardCompany](../simulator/src/main/java/io/github/kkw855/settlement/simulator/CardCompany.java) | 승인·거절을 판단하고 카드사 원장에 기록하며, 응답 본문과 지연 여부를 반환한다. |
| [ApprovalLedger](../simulator/src/main/java/io/github/kkw855/settlement/simulator/ApprovalLedger.java) | 카드사 승인 기록을 메모리 맵에 보관한다. PG의 Oracle DB와는 별개이며 재시작하면 사라진다. |

## 정상 승인 순서

![정상 승인 요청·응답 시퀀스](diagrams/approval-sequence.png)

[확대 가능한 SVG](diagrams/approval-sequence.svg)

1. 가맹점이 `POST /v1/payments`를 보낸다. `MerchantAuthInterceptor.preHandle()`이 `X-Api-Key`를 SHA-256으로 바꿔 `MerchantMapper.findByApiKeyHash()`로 가맹점을 찾고, 요청 속성(`authenticatedMerchant`)에 넣는다.
2. Spring MVC가 컨트롤러 매개변수를 만든다. 가맹점은 요청 속성에서 꺼내고(`@RequestAttribute`), 본문은 `ApprovePaymentRequest`로 읽어 `@Valid`로 검증한다. `PaymentController.approve()`는 `Idempotency-Key`를 확인한 뒤 `ApprovalService.approve()`에 가맹점, 멱등 키, 승인 요청 값을 넘긴다.
3. 서비스가 정지 가맹점과 할부 조건을 검사하고 `TxnIdGenerator.next()`로 거래고유번호를 만든다. `CardProtector.protect()`로 DB 보관용 카드 정보를 준비한다.
4. `PaymentMapper.insert()`가 `REQUESTED` 결제를 저장한다. 컨트롤러도 서비스도 Spring 트랜잭션을 열지 않으므로 이 Mapper 호출이 끝날 때 커밋된다.
5. `ApprovalGateway.approve()`가 `CardMessageLayouts.APPROVAL_REQUEST.writer()`로 0100 전문 본문을 만든다.
6. Gateway가 먼저 `MessageLogger.sent()`를 호출한다. Logger는 마스킹한 복사본을 `MessageLogMapper.insert()`로 `message_log`에 저장한다.
7. Gateway가 `CardCompanyClient.send()`를 호출한다. Client는 연결 후 `Framing.write()`로 요청을 보내고, `Framing.read()`로 응답을 기다린다. 사용한 소켓은 닫는다.
8. 카드사 쪽 `SimulatorServer.serve()`가 요청을 읽고 `CardCompany.handle()`을 호출한다. 0100이면 `approve()`가 승인 여부를 판단하고, 승인 시 `ApprovalLedger.save()`로 기록한다.
9. `CardCompany`는 `Reply.Respond(응답 본문, 지연 시간)`를 반환한다. 실제 대기와 `Framing.write()`는 `SimulatorServer`가 수행한다. 대기는 `CardCompany.handle()`의 잠금 밖에서 일어난다.
10. Client가 `Outcome.Received(응답 바이트)`를 반환하면 Gateway의 `interpret()`가 초기 필드를 읽고 `RECV` 로그를 기록한 뒤 전문구분코드·거래고유번호·응답코드를 검증한다. 정상 응답은 `Result.Approved`가 된다.
11. 서비스가 `PaymentMapper.approve()`로 `REQUESTED → APPROVED`를 반영한다. 승인번호·승인일시·결과코드를 저장하고, 갱신한 결제를 다시 읽어 `ApprovalOutcome.Processed`로 반환한다.
12. 컨트롤러가 결제를 `PaymentResponse`로 바꿔 201 Created와 `Location: /v1/payments/{txnId}`로 응답한다. 카드번호는 마스킹 값만 나간다.

## 검사 순서: 저장하기 전에 거절한다

요청은 아래 순서로 검사한다. 1~5에서 막히면 결제 행도, 거래고유번호 채번도, 전문도 없다.

| 순서 | 검사 | 하는 곳 | 실패 응답 |
|---|---|---|---|
| 1 | API 키 | `MerchantAuthInterceptor` | 401 `UNAUTHORIZED` |
| 2 | 본문 형식 (JSON 읽기, `@Valid`) | Spring MVC 매개변수 생성 | 400 `VALIDATION_FAILED` |
| 3 | `Idempotency-Key` 유무·형식 | `PaymentController` | 400 `IDEMPOTENCY_KEY_REQUIRED` / `VALIDATION_FAILED` |
| 4 | 정지 가맹점 | `ApprovalService` | 403 `MERCHANT_SUSPENDED` |
| 5 | 5만 원 미만 할부 | `ApprovalService` | 422 `INSTALLMENT_NOT_ALLOWED` |
| 6 | 같은 멱등 키 | `payment`의 UNIQUE 제약 (INSERT) | 아래 "멱등 키" 절 |

- **인증이 늘 먼저다.** 인터셉터는 컨트롤러 매개변수를 만들기 전에 돈다. 그래서 키가 틀린 요청은 본문이 틀려도 401이다 ([03-api.md](03-api.md) 6장).
- **정지 검사는 INSERT보다 앞이어야 한다.** INSERT 뒤에서 거절해도 응답(403)은 같다. 하지만 카드사에 보내지 않은 `REQUESTED` 행이 남는다. 이 행에는 결과가 오지 않으므로, 같은 키로 다시 보내면 6번에서 계속 409다. 가맹점을 다시 활성화해도 그 키는 막힌다. [V11 테스트](../online-api/src/test/java/io/github/kkw855/settlement/online/payment/ApprovalServiceTest.java)가 정지 가맹점의 요청이 결제를 남기지 않는지 확인한다.
- **정지 검사는 인터셉터가 아니라 서비스가 한다.** 정지 가맹점도 정지 전 거래는 조회할 수 있어야 한다. 막을 요청이 무엇인지는 서비스가 안다.

## 전송 결과와 업무 결과는 다르다

`CardCompanyClient.Outcome`은 **네트워크에서 무엇을 확인했는지**, `ApprovalGateway.Result`는 **응답을 해석한 결과**, `Payment.status`는 **PG가 DB에 남긴 결제 상태**다.

| Client의 Outcome | Gateway의 Result | 저장할 Payment 상태·결과코드 |
|---|---|---|
| `Received` + 요청과 일치하는 정상 승인 응답 | `Approved` | `APPROVED` / `0000` |
| `Received` + 요청과 일치하는 거절 응답 | `Declined` | `DECLINED` / 카드사 응답코드 |
| `Received` + 불일치·필드 해석 실패 | `Unknown` | `UNKNOWN` / `P003` |
| 연결 실패로 `NotSent` | `NotSent` | `DECLINED` / `P001` |
| 연결 후 타임아웃·쓰기/읽기 실패 등으로 `Unknown` | `Unknown` | `UNKNOWN` / `P003` |

`Received`만으로 승인을 뜻하지 않는다. 반대로 PG가 타임아웃이어도 카드사는 이미 승인했을 수 있다. 시뮬레이터도 원장에 승인부터 기록하고 지연 응답을 보낸다.

결제 행을 만든 뒤에는 결과가 무엇이든 HTTP 201이다. 성공 여부는 본문의 `status`로 판단한다 ([03-api.md](03-api.md) 1장).

## 트랜잭션 경계

`PaymentController`와 `ApprovalService`에는 `@Transactional`이 없다. 그래서 HTTP 요청 경로에서는 결제 INSERT, 로그 INSERT, 결과 UPDATE 등 각 Mapper 호출이 개별 커밋되고, 카드사 응답을 기다리는 동안 이 호출 흐름을 감싸는 Spring 트랜잭션이 없다. [V10 테스트](../online-api/src/test/java/io/github/kkw855/settlement/online/payment/ApprovalServiceTest.java)가 그 시점에 `REQUESTED` 행이 별도 연결에서 보이는지 확인한다.

다른 코드가 트랜잭션을 연 채 서비스를 부르면 이 경계는 달라질 수 있다. 어노테이션이 없다는 이유만으로 기존 트랜잭션이 중단되는 것은 아니다. 또한 결제 저장·로그 저장·카드사 원장 변경 전체가 하나의 원자적 트랜잭션으로 묶이는 구조도 아니다.

## 멱등 키가 중복이면 카드사 호출을 건너뛴다

`UNIQUE(merchant_id, idempotency_key)`에 의해 결제 INSERT가 중복 키 예외를 내면, 서비스가 기존 결제를 조회해 분기한다.

- 카드 해시·금액·할부개월이 다르면 `IDEMPOTENCY_KEY_REUSED` 오류다 (422).
- 같은 요청이지만 기존 상태가 `REQUESTED`이면 `PAYMENT_IN_PROGRESS` 오류다 (409, `Retry-After: 5`).
- 그 밖에는 기존 결과를 `ApprovalOutcome.Replayed`로 반환한다. 컨트롤러는 처음과 같은 201 본문에 `Idempotent-Replayed: true`를 붙인다.

이 경로에서는 Gateway에 진입하지 않으므로 카드사에 다시 보내거나 새로운 송수신 로그를 남기지 않는다. 거래고유번호 생성과 카드 보호 처리는 중복 INSERT 시도 전에 이미 수행된다.

## 오류 응답

`ApiException`은 `ErrorCode`를 담고, HTTP 상태는 `ErrorCode`가 정한다. `ApiExceptionHandler`가 이를 Problem Details(`application/problem+json`)로 바꾸고 `code`를 붙인다.

- `@Valid` 실패는 400 `VALIDATION_FAILED`와 `errors`(필드·이유)로 나간다. 같은 요청에 늘 같은 응답이 나가도록 필드명, 이유 순으로 정렬한다.
- JSON 문법 오류나 타입 불일치(소수 금액 포함)도 400 `VALIDATION_FAILED`다. 예외 메시지에 입력값이 들어 있을 수 있어 응답에 옮기지 않는다.
- 예상하지 못한 예외는 500 `INTERNAL_ERROR`다. 스택 트레이스는 로그에만 남긴다.
- 지원하지 않는 메서드(405), 지원하지 않는 Content-Type(415), 키가 맞는데 없는 경로(404)는 Spring 기본 Problem이라 `code`가 없다 ([openapi.yaml](api/openapi.yaml)의 Problem).

## 로그를 읽을 때 주의할 점

- **`SEND`는 전달 성공 증거가 아니다.** 연결 시도보다 먼저 기록하므로 연결 실패에도 남을 수 있다.
- **`RECV`는 승인 성공 증거가 아니다.** `interpret()`에서 응답코드·전문구분코드를 초기 파싱한 뒤, 요청 일치 여부와 승인 여부를 검증하기 전에 기록한다.
- 연결 실패·응답 타임아웃에는 `RECV`가 없다. 응답 바이트를 받아도 초기 파싱 전에 실패하면 기록되지 않으며, 로그 기록 이후의 필드 해석에서 실패한 경우에는 `RECV`가 남을 수 있다.
- `elapsedMs`는 Gateway가 Client의 `send()` 호출 전후에 측정한 경과 시간이다. 카드사 내부 처리 시간만을 뜻하지 않는다.
- Logger는 `Arrays.copyOf()`로 새 배열을 만들고, 0100의 카드번호 가운데 6자리를 `*`로 가린다. 실제 전송에 쓰는 원본 배열은 변경하지 않는다.

현재 PG의 `Unknown` 처리는 미확정 상태 저장까지다. 망취소 호출은 향후 구현 대상으로 남아 있다. 시뮬레이터는 0400 취소와 0420 망취소도 지원하지만, 이 문서와 두 다이어그램은 승인 경로에 집중한다.
