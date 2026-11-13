package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.merchant.ApiKeys;
import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import io.github.kkw855.settlement.core.merchant.MerchantStatus;
import io.github.kkw855.settlement.core.payment.Payment;
import io.github.kkw855.settlement.core.payment.PaymentMapper;
import io.github.kkw855.settlement.core.payment.TxnIdGenerator;
import io.github.kkw855.settlement.online.IntegrationTest;
import io.github.kkw855.settlement.online.card.CardCompanyClient;
import io.github.kkw855.settlement.online.card.CardCompanyClient.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HTTP 계약을 확인한다 (docs/api/openapi.yaml). 상태 코드, 헤더, 오류의 {@code code}, 카드번호가 새지 않는지.
 *
 * <p>업무 규칙의 세부(멱등 분기, 결과 반영)는 {@link ApprovalServiceTest}가 본다. 여기서는 그 결과가
 * HTTP로 어떻게 나가는지만 본다.
 */
@AutoConfigureMockMvc
class PaymentControllerTest extends IntegrationTest {

  private static final String CARD_NUMBER = "4111111111111111";

  @MockitoBean
  CardCompanyClient cardCompanyClient;

  @Autowired
  MockMvcTester mvc;
  @Autowired
  MerchantMapper merchantMapper;
  @Autowired
  PaymentMapper paymentMapper;
  @Autowired
  TxnIdGenerator txnIdGenerator;

  /** 테스트마다 새 가맹점의 API 키. 서로 겹치지 않게 한다. */
  String apiKey;

  @BeforeEach
  void newMerchant() {
    apiKey = insertMerchant(MerchantStatus.ACTIVE);
  }

  @Test
  @DisplayName("C1 승인: 201, Location, KST 시각, 결과 코드와 메시지. 응답에 카드번호 원문이 없다")
  void approves() {
    responseWith("0000", "30000001");

    MvcTestResult result = approve("key-1", body(CARD_NUMBER, 10_000, 0));

    assertThat(result).hasStatus(HttpStatus.CREATED)
      .doesNotContainHeader("Idempotent-Replayed");
    String txnId = txnIdOf(result);
    assertThat(result).hasHeader("Location", "/v1/payments/" + txnId);
    assertThat(result).bodyJson()
      .hasPathSatisfying("$.status", value ->
        assertThat(value).isEqualTo("APPROVED"))
      .hasPathSatisfying("$.cardMasked", value ->
        assertThat(value).isEqualTo("411111******1111"))
      .hasPathSatisfying("$.approvalNo", value ->
        assertThat(value).isEqualTo("30000001"))
      .hasPathSatisfying("$.approvedAt", value ->
        assertThat(value).isEqualTo("2026-10-08T10:30:01+09:00"))
      .hasPathSatisfying("$.requestedAt", value ->
        assertThat(value).asString().endsWith("+09:00"))
      .hasPathSatisfying("$.result.code", value ->
        assertThat(value).isEqualTo("0000"))
      .hasPathSatisfying("$.result.message", value ->
        assertThat(value).isEqualTo("정상"));
    assertThat(result).bodyText().doesNotContain(CARD_NUMBER);
  }

  @Test
  @DisplayName("C2 같은 키로 다시 보내면 201 + Idempotent-Replayed. 카드사에는 한 번만 보낸다")
  void replays() {
    responseWith("0000", "30000001");

    String firstTxnId = txnIdOf(approve("key-1", body(CARD_NUMBER, 10_000, 0)));
    MvcTestResult second = approve("key-1", body(CARD_NUMBER, 10_000, 0));

    assertThat(second)
      .hasStatus(HttpStatus.CREATED)
      .hasHeader("Idempotent-Replayed", "true");
    assertThat(txnIdOf(second)).isEqualTo(firstTxnId);
    assertThat(second).bodyJson()
      .hasPathSatisfying("$.status", value ->
        assertThat(value).isEqualTo("APPROVED"));
    verify(cardCompanyClient, times(1)).send(any());
  }

  @Test
  @DisplayName("C3 카드사 거절도 201이다. 성공 여부는 status로 판단한다")
  void declinedIsStillCreated() {
    responseWith("1001", null);

    MvcTestResult result = approve("key-1", body(CARD_NUMBER, 10_000, 0));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson()
      .hasPathSatisfying("$.status", value ->
        assertThat(value).isEqualTo("DECLINED"))
      .hasPathSatisfying("$.approvalNo", value ->
        assertThat(value).isNull())
      .hasPathSatisfying("$.result.code", value ->
        assertThat(value).isEqualTo("1001"))
      .hasPathSatisfying("$.result.message", value ->
        assertThat(value).isEqualTo("한도 초과"));
  }

  @Test
  @DisplayName("C4 API 키가 없거나 틀리면 401. 본문이 틀려도 인증을 먼저 본다")
  void unauthorized() {
    assertProblem(
      mvc.post().uri("/v1/payments").header("Idempotency-Key", "key-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(CARD_NUMBER, 10_000, 0))
        .exchange()
    , HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");

    assertProblem(
      approveWithKey("wrong-key", "key-1", body(CARD_NUMBER, 10_000, 0))
      , HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"
    );
    assertProblem(
      approveWithKey("wrong-key", "key-1", "{}")
      , HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"
    );
    assertProblem(
      mvc.get().uri("/v1/payments/P2026100800000000001").exchange()
      , HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"
    );
    verify(cardCompanyClient, never()).send(any());
  }

  @Test
  @DisplayName("C5 정지된 가맹점은 승인이 403이고 조회는 된다")
  void suspendedMerchant() {
    apiKey = insertMerchant(MerchantStatus.SUSPENDED);
    String txnId = insertRequestedPayment(); // 정지 전에 들어온 결제

    assertProblem(approve("key-1", body(CARD_NUMBER, 10_000, 0))
      , HttpStatus.FORBIDDEN, "MERCHANT_SUSPENDED");
    assertThat(mvc.get().uri("/v1/payments/" + txnId).header("X-Api-Key", apiKey))
      .hasStatus(HttpStatus.OK)
      .bodyJson()
      .hasPathSatisfying("$.status", value ->
        assertThat(value).isEqualTo("REQUESTED"))
      .hasPathSatisfying("$.result", value ->
        assertThat(value).isNull());
    verify(cardCompanyClient, never()).send(any());
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class) // 이 테스트 동안의 출력만 잡는다
  @DisplayName("C6 형식 오류는 400 VALIDATION_FAILED. 필드와 이유만 담고, 입력값은 응답과 로그 어디에도 없다")
  void validationErrors(CapturedOutput output) {
    String invalidCard = CARD_NUMBER + "2"; // 17자리

    MvcTestResult result = approve("key-1", """
      {"cardNumber":"%s","cardExpiry":"2813","installmentMonths":1,"amount":0}
      """.formatted(invalidCard));

    assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertThat(result).bodyJson().isLenientlyEqualTo("""
      {"errors": [
        {"field": "amount", "reason": "1원 이상이어야 한다"},
        {"field": "cardExpiry", "reason": "yyMM 형식이어야 한다"},
        {"field": "cardNumber", "reason": "숫자 16자리여야 한다"},
        {"field": "installmentMonths", "reason": "0(일시불) 또는 2~36이어야 한다"}
      ]}
      """);
    assertThat(result).bodyText().doesNotContain(CARD_NUMBER);
    assertThat(output).doesNotContain(CARD_NUMBER);
  }

  @Test
  @DisplayName("C7 필드가 빠지면 400. 숫자 필드도 0으로 채우지 않고 '필수다'로 막는다")
  void missingFields() {
    MvcTestResult result = approve("key-1", "{}");

    assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertThat(result).bodyJson().isLenientlyEqualTo("""
      {"errors": [
        {"field": "cardNumber", "reason": "필수다"},
        {"field": "cardExpiry", "reason": "필수다"},
        {"field": "installmentMonths", "reason": "필수다"},
        {"field": "amount", "reason": "필수다"} 
      ]}
      """);
  }

  @Test
  @DisplayName("C8 금액에 소수가 오면 400. 10000.5원을 10000원으로 잘라 승인하지 않는다")
  void rejectsFractionalAmount() {
    responseWith("0000", "30000001"); // 소수를 잘라 받으면 10000원으로 승인까지 간다

    MvcTestResult result = approve("key-1", """
      {"cardNumber":"%s","cardExpiry":"2812","installmentMonths":0,"amount":10000.5}
      """.formatted(CARD_NUMBER));

    assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    verify(cardCompanyClient, never()).send(any());
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class) // 이 테스트 동안의 출력만 잡는다
  @DisplayName("C9 JSON을 읽을 수 없으면 400 VALIDATION_FAILED. 입력값을 응답에 옮기지 않는다")
  void unreadableBody(CapturedOutput output) {
    MvcTestResult result = approve("key-1", """
      {"cardNumber":"%s","cardExpiry":"2812","installmentMonths":"abc","amount":10000}
      """.formatted(CARD_NUMBER));

    assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertThat(result).bodyText().doesNotContain("abc").doesNotContain(CARD_NUMBER);
    assertThat(output).doesNotContain(CARD_NUMBER);
  }

  @Test
  @DisplayName("C10 Idempotency-Key가 없으면 IDEMPOTENCY_KEY_REQUIRED, 형식이 틀리면 VALIDATION_FAILED")
  void idempotencyKeyRules() {
    assertProblem(mvc.post().uri("/v1/payments").header("X-Api-Key", apiKey)
      .contentType(MediaType.APPLICATION_JSON).content(body(CARD_NUMBER, 10_000, 0))
      .exchange(),
    HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    assertProblem(approve("key 1", body(CARD_NUMBER, 10_000, 0)), // 공백
      HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertProblem(approve("k".repeat(65), body(CARD_NUMBER, 10_000, 0)),
      HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    verify(cardCompanyClient, never()).send(any());
  }

  @Test
  @DisplayName("C11 처음 요청이 카드사를 기다리는 동안 같은 키로 오면 409 + Retry-After")
  void inProgress() {
    AtomicReference<MvcTestResult> retry = new AtomicReference<>();
    when(cardCompanyClient.send(any())).thenAnswer(invocation -> {
      retry.set(approve("key-1", body(CARD_NUMBER, 10_000, 0))); // 처음 요청은 아직 REQUESTED
      return new Outcome.Received(response(txnIdOf(invocation.<byte[]>getArgument(0)),
        "0000", "30000001"));
    });

    assertThat(approve("key-1", body(CARD_NUMBER, 10_000, 0)))
      .hasStatus(HttpStatus.CREATED);

    assertProblem(retry.get(), HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS");
    assertThat(retry.get()).hasHeader("Retry-After", "5");
  }

  @Test
  @DisplayName("C12 업무 규칙 위반은 422. 5만 원 미만 할부는 INSTALLMENT_NOT_ALLOWED")
  void installmentNotAllowed() {
    assertProblem(approve("key-1", body(CARD_NUMBER, 49_999, 3)),
      HttpStatus.UNPROCESSABLE_CONTENT, "INSTALLMENT_NOT_ALLOWED");
  }

  @Test
  @DisplayName("C13 조회: 내 결제는 200, 다른 가맹점의 결제와 없는 결제는 똑같이 404")
  void getPayment() {
    responseWith("0000", "30000001");
    String txnId = txnIdOf(approve("key-1", body(CARD_NUMBER, 10_000, 0)));
    String otherApiKey = insertMerchant(MerchantStatus.ACTIVE);

    assertThat(mvc.get().uri("/v1/payments/" + txnId).header("X-Api-Key", apiKey).exchange())
      .hasStatus(HttpStatus.OK)
      .bodyJson()
      .hasPathSatisfying("$.txnId", value ->
        assertThat(value).isEqualTo(txnId));
    assertProblem(mvc.get().uri("/v1/payments/" + txnId).header("X-Api-Key", otherApiKey).exchange(),
      HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");
    assertProblem(mvc.get().uri("/v1/payments/" + "P2026100899999999999").header("X-Api-Key", apiKey).exchange(),
      HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  @DisplayName("C14 예상하지 못한 예외는 500 INTERNAL_ERROR. 예외 메시지를 응답에 옮기지 않는다")
  void unexpectedError(CapturedOutput output) {
    when(cardCompanyClient.send(any())).thenThrow(new IllegalStateException("내부 정보"));

    MvcTestResult result = approve("key-1", body(CARD_NUMBER, 10_000, 0));

    assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    assertThat(result).bodyText().doesNotContain("내부 정보");
    assertThat(output).contains("처리하지 못한 예외"); // 원인은 로그에 남는다
  }

  // ---------------------------------------------------------------- 도우미

  /**
   * 오류 응답의 공통 형식 (docs/api/openapi.yaml의 Problem).
   */
  private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
    assertThat(result).hasStatus(status)
      .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
      .bodyJson()
      .hasPathSatisfying("$.status", value ->
        assertThat(value).isEqualTo(status.value()))
      .hasPathSatisfying("$.code", value ->
        assertThat(value).isEqualTo(code));
  }

  /**
   * 새 가맹점을 넣고 그 가맹점의 API 키를 돌려준다. DB에는 해시만 들어간다.
   */
  private String insertMerchant(MerchantStatus status) {
    String key = "test-" + UUID.randomUUID();
    String merchantNo = "C%09d".formatted(ThreadLocalRandom.current().nextInt(1_000_000_000));
    merchantMapper.insert(new Merchant(null, merchantNo, "홍길동카페", 2,
      "004", "enc", "123-****-7890", "홍길동", status,
      ApiKeys.hash(key)));
    return key;
  }

  /** 현재 API 키의 가맹점에 처리 중인 결제를 직접 넣는다. */
  private String insertRequestedPayment() {
    Merchant merchant = merchantMapper.findByApiKeyHash(ApiKeys.hash(apiKey)).orElseThrow();
    String txnId = txnIdGenerator.next();
    paymentMapper.insert(Payment.requested(txnId, merchant.merchantId(), "earlier-key",
      "enc", "a".repeat(64),
      "411111******1111", 0, 10_000, LocalDateTime.of(2026, 10, 8, 10, 30, 0)));
    return txnId;
  }

  private void responseWith(String responseCode, String approvalNo) {
    when(cardCompanyClient.send(any())).thenAnswer(invocation ->
      new Outcome.Received(response(txnIdOf(invocation.<byte[]>getArgument(0)), responseCode, approvalNo)));
  }

  private MvcTestResult approve(String idempotencyKey, String json) {
    return approveWithKey(apiKey, idempotencyKey, json);
  }

  private MvcTestResult approveWithKey(String apiKey, String idempotencyKey, String json) {
    return mvc.post().uri("/v1/payments")
      .header("X-Api-Key", apiKey)
      .header("Idempotency-Key", idempotencyKey)
      .contentType(MediaType.APPLICATION_JSON)
      .content(json)
      .exchange();
  }

  private static String body(String cardNumber, long amount, int installmentMonths) {
    return """
      {"cardNumber":"%s","cardExpiry":"2812","installmentMonths":%d,"amount":%d}
      """.formatted(cardNumber, installmentMonths, amount);
  }

  /**
   * 응답 본문 txnId. 없으면 여기서 실패한다.
   */
  private static String txnIdOf(MvcTestResult result) {
    return assertThat(result).bodyJson().extractingPath("$.txnId").asString().actual();
  }

  private static String txnIdOf(byte[] request) {
    return APPROVAL_REQUEST.reader(request).getText(TXN_ID);
  }

  private static byte[] response(String txnId, String responseCode, String approvalNo) {
    return APPROVAL_RESPONSE.writer()
      .set(MSG_TYPE, "0110")
      .set(TXN_ID, txnId)
      .set(SENT_AT, "20261008103001")
      .set(RESPONSE_CODE, responseCode)
      .set(APPROVAL_NO, approvalNo)
      .set(APPROVED_AT, "20261008103001")
      .set(RESPONSE_MESSAGE, "정상")
      .toBytes();
  }
}
