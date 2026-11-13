package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.card.CardNumber;
import io.github.kkw855.settlement.core.card.CardProtector;
import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import io.github.kkw855.settlement.core.merchant.MerchantStatus;
import io.github.kkw855.settlement.core.message.MessageLog;
import io.github.kkw855.settlement.core.message.MessageLogMapper;
import io.github.kkw855.settlement.core.payment.Payment;
import io.github.kkw855.settlement.core.payment.PaymentMapper;
import io.github.kkw855.settlement.core.payment.PaymentStatus;
import io.github.kkw855.settlement.core.payment.TxnIdGenerator;
import io.github.kkw855.settlement.online.IntegrationTest;
import io.github.kkw855.settlement.online.card.CardCompanyClient;
import io.github.kkw855.settlement.online.card.CardCompanyClient.Outcome;
import io.github.kkw855.settlement.online.error.ApiException;
import io.github.kkw855.settlement.online.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 승인 처리를 실제 Oracle로 확인한다. 카드사 클라이언트만 가짜로 바꿔 응답을 고른다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다. 서비스가 "트랜잭션 없이 문장마다 커밋"하는 것까지 확인해야
 * 하기 때문이다.
 * 대신 테스트마다 새 가맹점을 만들어 서로 겹치지 않게 한다.
 */
class ApprovalServiceTest extends IntegrationTest {

  private static final CardNumber CARD = new CardNumber("4111111111111111");

  @MockitoBean
  CardCompanyClient cardCompanyClient;

  @Autowired
  ApprovalService approvalService;
  @Autowired
  MerchantMapper merchantMapper;
  @Autowired
  PaymentMapper paymentMapper;
  @Autowired
  MessageLogMapper messageLogMapper;
  @Autowired
  CardProtector cardProtector;
  @Autowired
  DataSource dataSource;

  Merchant merchant;
  @Autowired
  private TxnIdGenerator txnIdGenerator;

  @BeforeEach
  void newMerchant() {
    merchant = insertMerchant(MerchantStatus.ACTIVE);
  }

  @Test
  @DisplayName("V1 승인: APPROVED, 승인번호·승인일시 저장, 전문 로그 2건(카드번호는 가림)")
  void approves() {
    respondWith("0000", "30000001");

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
    assertThat(payment.approvalNo()).isEqualTo("30000001");
    assertThat(payment.approvedAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 10, 30, 1));
    assertThat(payment.resultCode()).isEqualTo("0000");

    List<MessageLog> logs = messageLogMapper.findByTxnId(payment.txnId());
    assertThat(logs).extracting(MessageLog::direction).containsExactly("SEND", "RECV");
    assertThat(logs.getFirst().maskedPayload()).contains("411111******1111")
      .doesNotContain("4111111111111111");
  }

  @Test
  @DisplayName("V2 카드사 거절: DECLINED, 결과 코드는 카드사 응답코드")
  void declined() {
    respondWith("1001", null);

    Payment payment = approve("key-1", 10_001, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.resultCode()).isEqualTo("1001");
    assertThat(payment.approvalNo()).isNull();
  }

  @Test
  @DisplayName("V3 연결 실패: 망취소 없이 DECLINED(P001)")
  void notSent() {
    when(cardCompanyClient.send(any())).thenReturn(new Outcome.NotSent(new ConnectException("refused")));

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.resultCode()).isEqualTo("P001");
  }

  @Test
  @DisplayName("V4 응답 타임아웃: UNKNOWN(P003). 승인으로 취급하지 않는다 (망취소는 2-7)")
  void unknownOnTimeout() {
    when(cardCompanyClient.send(any())).thenReturn(new Outcome.Unknown(new SocketTimeoutException("Read timed out")));

    Payment payment = approve("key-1", 10_009, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
    assertThat(payment.resultCode()).isEqualTo("P003");
  }

  @Test
  @DisplayName("V5 응답의 거래고유번호가 다르면 정상 코드여도 승인하지 않는다 (UNKNOWN)")
  void unknownWhenResponseDoesNotMatch() {
    when(cardCompanyClient.send(any())).thenAnswer(invocation ->
      new Outcome.Received(response("P2026100899999999999", "0000", "30000001"))
    );

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
  }

  @Test
  @DisplayName("V6 같은 키로 다시 오면 카드사에 보내지 않고 처음 결과를 돌려준다")
  void replaysSameKey() {
    respondWith("0000", "30000001");

    ApprovalOutcome first = approve("key-1", 10_000, 0);
    ApprovalOutcome second = approve("key-1", 10_000, 0);

    assertThat(second).isInstanceOf(ApprovalOutcome.Replayed.class);
    assertThat(second.payment().txnId()).isEqualTo(first.payment().txnId());
    verify(cardCompanyClient, times(1)).send(any());
  }

  @Test
  @DisplayName("V7 같은 키에 다른 금액이면 IDEMPOTENCY_KEY_REUSED")
  void rejectsReusedKey() {
    respondWith("0000", "30000001");

    approve("key-1", 10_000, 0);

    assertThatThrownBy(() -> approve("key-1", 20_000, 0))
      .isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
  }

  @Test
  @DisplayName("V8 같은 키의 처음 요청이 아직 REQUESTED면 PAYMENT_IN_PROGRESS")
  void rejectsWhileInProgress() {
    String hash = cardProtector.hash(CARD);
    paymentMapper.insert(Payment.requested(txnIdGenerator.next(), merchant.merchantId(),
      "key-1", "enc", hash, CARD.masked(),
      0, 10_000, LocalDateTime.now())); // 다른 요청이 처리 중인 상태를 만든다

    assertThatThrownBy(() -> approve("key-1", 10_000, 0))
      .isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS));
    verify(cardCompanyClient, never()).send(any());
  }

  @Test
  @DisplayName("V9 5만 원 미만 할부는 저장도 전송도 하지 않고 거절한다")
  void rejectsSmallInstallment() {
    assertThatThrownBy(() -> approve("key-1", 49_999, 3))
      .isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo(ErrorCode.INSTALLMENT_NOT_ALLOWED));
    assertThat(paymentMapper.findByIdempotencyKey(merchant.merchantId(), "key-1"))
      .isEmpty();
    verify(cardCompanyClient, never()).send(any());
  }

  @Test
  @DisplayName("V10 카드사를 기다리는 동안에는 트랜잭션이 없고, REQUESTED는 이미 커밋되어 다른 연결에서도 보인다")
  void noTransactionWhileCallingCardCompany() {
    AtomicBoolean transactionActive = new AtomicBoolean(true);
    AtomicReference<String> statusSeenByOtherConnection = new AtomicReference<>();

    when(cardCompanyClient.send(any())).thenAnswer(invocation -> {
      String txnId = txnIdOf(invocation.getArgument(0));

      transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      statusSeenByOtherConnection.set(statusFromNewConnection(txnId)); // 커밋 전이면 다른 연결에서 안 보인다
      return new Outcome.Received(response(txnId, "0000", "30000001"));
    });

    approve("key-1", 10_000, 0);

    assertThat(transactionActive).isFalse();
    assertThat(statusSeenByOtherConnection).hasValue("REQUESTED");
  }

  @Test
  @DisplayName("V11 정지된 가맹점은 MERCHANT_SUSPENDED. 저장도 전송도 하지 않는다")
  void rejectsSuspendedMerchant() {
    merchant = insertMerchant(MerchantStatus.SUSPENDED);

    assertThatThrownBy(() -> approve("key-1", 10_000, 0))
      .isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo(ErrorCode.MERCHANT_SUSPENDED));
    assertThat(paymentMapper.findByIdempotencyKey(merchant.merchantId(), "key-1"))
      .isEmpty();
    verify(cardCompanyClient, never()).send(any());
  }

  // ---------------------------------------------------------------- 도우미

  private Merchant insertMerchant(MerchantStatus status) {
    String merchantNo = "S%09d".formatted(ThreadLocalRandom.current().nextInt(1_000_000_000));
    String apiKeyHash = HexFormat.of().formatHex(UUID.randomUUID().toString().getBytes())
      .substring(0, 64);
    merchantMapper.insert(new Merchant(null, merchantNo, "홍길동카페", 2,
      "004", "enc", "123-****-7890", "홍길동",
      status, apiKeyHash));
    return merchantMapper.findByMerchantNo(merchantNo).orElseThrow();
  }

  /** 커넥션 풀에서 새 연결을 따로 받아 조회한다. 서비스가 쓰는 연결과 다른 세션이다. */
  private String statusFromNewConnection(String txnId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement("SELECT status FROM payment WHERE txn_id = ?")) {
      statement.setString(1, txnId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private ApprovalOutcome approve(String idempotencyKey, long amount, int installmentMonths) {
    return approvalService.approve(merchant, idempotencyKey,
      new ApprovalCommand(CARD, "2812", installmentMonths, amount));
  }

  /**
   * 받은 요청의 거래고유번호를 그대로 돌려주는 0110을 만든다.
   */
  private void respondWith(String responseCode, String approvalNo) {
    when(cardCompanyClient.send(any())).thenAnswer(invocation ->
      new Outcome.Received(response(txnIdOf(invocation.getArgument(0)),
        responseCode, approvalNo))
    );
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
