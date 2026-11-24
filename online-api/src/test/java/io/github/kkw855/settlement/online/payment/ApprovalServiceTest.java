package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.codec.MessageReader;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.tuple;
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
    verify(cardCompanyClient).send(any()); // 0100 한 번뿐. 0420을 보내지 않는다
  }

  @Test
  @DisplayName("V4 응답 타임아웃: 원거래를 가리키는 0420을 보내고, 무효가 확인되면 NET_CANCELLED(P002)")
  void netCancelsOnTimeout() {
    when(cardCompanyClient.send(any()))
      .thenReturn(new Outcome.Unknown(new SocketTimeoutException("Read timed out")))
      .thenAnswer(invocation -> netCancelled(invocation.getArgument(0)));

    Payment payment = approve("key-1", 10_009, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.NET_CANCELLED);
    assertThat(payment.resultCode()).isEqualTo("P002");

    ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
    verify(cardCompanyClient, times(2)).send(sent.capture()); // 0100, 0420. 무효가 확인되면 다시 보내지 않는다
    MessageReader netCancel = NET_CANCEL_REQUEST.reader(sent.getAllValues().get(1));
    assertThat(netCancel.getDigits(MSG_TYPE)).isEqualTo("0420");
    assertThat(netCancel.getText(ORIGINAL_TXN_ID)).isEqualTo(payment.txnId());
    assertThat(netCancel.getDigits(ORIGINAL_MSG_TYPE)).isEqualTo("0100");
    assertThat(netCancel.getText(NET_CANCEL_MERCHANT_NO)).isEqualTo(merchant.merchantNo());
    assertThat(netCancel.getLong(NET_CANCEL_AMOUNT)).isEqualTo(payment.amount());
  }

  @Test
  @DisplayName("V5 응답의 거래고유번호가 다르면 정상 코드여도 승인하지 않고 망취소한다 (NET_CANCELLED)")
  void netCancelsWhenResponseDoesNotMatch() {
    when(cardCompanyClient.send(any()))
      .thenAnswer(invocation ->
        new Outcome.Received(response("P2026100899999999999", "0000", "30000001"))
      )
      .thenAnswer(invocation -> netCancelled(invocation.getArgument(0)));

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.NET_CANCELLED);
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

  @ParameterizedTest(name = "응답코드 {0}")
  @ValueSource(strings = {"거절", ""})
  @DisplayName("V12 응답코드가 숫자 4자리가 아니면 망취소한다 (NET_CANCELLED). 0110 RECV 로그는 남기고 응답코드 칸은 비운다")
  void netCancelsWhenResponseCodeIsMalformed(String responseCode) {
    respondWith(responseCode, null);

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.NET_CANCELLED);
    assertThat(messageLogMapper.findByTxnId(payment.txnId()))
      .extracting(MessageLog::msgType, MessageLog::direction, MessageLog::responseCode)
      .containsExactly(
        tuple("0100", "SEND", null), tuple("0110", "RECV", null),
        tuple("0420", "SEND", null), tuple("0430", "RECV", "0000")
      );
  }

  @ParameterizedTest(name = "승인번호 {0}")
  @ValueSource(strings = {"승인번호", ""})
  @DisplayName("V13 정상(0000) 응답이어도 승인번호가 영문·숫자 1~8자리가 아니면 승인하지 않고 망취소한다 (NET_CANCELLED)")
  void netCancelsWhenApprovalNoIsMalformed(String approvalNo) {
    respondWith("0000", approvalNo);

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.NET_CANCELLED);
    assertThat(payment.approvalNo()).isNull();
  }

  @Test
  @DisplayName("V14 망취소도 끝내 확인하지 못하면 UNKNOWN(P003). 0420은 최초 1회 + 재시도 3회, 원거래 번호로 로그가 남는다")
  void staysUnknownWhenNetCancelFails() {
    when(cardCompanyClient.send(any())).thenReturn(new Outcome.Unknown(new SocketTimeoutException("Read timed out")));

    Payment payment = approve("key-1", 10_000, 0).payment();

    assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
    assertThat(payment.resultCode()).isEqualTo("P003");
    assertThat(messageLogMapper.findByTxnId(payment.txnId()))
      .extracting(MessageLog::msgType)
      .containsExactly("0100", "0420", "0420", "0420", "0420");
  }

  @Test
  @DisplayName("V15 0420을 보낼 때 UNKNOWN은 이미 커밋되어 있다. 망취소 중에 앱이 멈춰도 결과를 모르는 거래로 남는다")
  void commitsUnknownBeforeNetCancel() {
    AtomicReference<String> statusDuringNetCancel = new AtomicReference<>();

    when(cardCompanyClient.send(any()))
      .thenReturn(new Outcome.Unknown(new SocketTimeoutException("Read timed out")))
      .thenAnswer(invocation -> {
        byte[] request = invocation.getArgument(0);
        String txnId = NET_CANCEL_REQUEST.reader(request).getText(ORIGINAL_TXN_ID);
        statusDuringNetCancel.set(statusFromNewConnection(txnId)); // 커밋 전이면 다른 연결에서 안 보인다

        return new Outcome.Unknown(new SocketTimeoutException("Read timed out"));
      });

    approve("key-1", 10_009, 0);

    assertThat(statusDuringNetCancel).hasValue("UNKNOWN");
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

  /**
   * 커넥션 풀에서 새 연결을 따로 받아 조회한다. 서비스가 쓰는 연결과 다른 세션이다.
   */
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
   * 0100에는 받은 거래고유번호를 그대로 돌려주는 0110으로 답한다. 그 뒤에 오는 0420에는 0430 01로 답한다.
   */
  private void respondWith(String responseCode, String approvalNo) {
    when(cardCompanyClient.send(any()))
      .thenAnswer(invocation ->
        new Outcome.Received(response(txnIdOf(invocation.getArgument(0)), responseCode, approvalNo))
      )
      .thenAnswer(invocation -> netCancelled(invocation.getArgument(0)));
  }

  /**
   * 0420에 0430 01(원거래를 취소함)로 답한다.
   */
  private static Outcome netCancelled(byte[] request) {
    String originalTxnId = NET_CANCEL_REQUEST.reader(request).getText(ORIGINAL_TXN_ID);
    return new Outcome.Received(NET_CANCEL_RESPONSE.writer()
      .set(MSG_TYPE, "0430")
      .set(TXN_ID, originalTxnId)
      .set(SENT_AT, "20261008103005")
      .set(RESPONSE_CODE, "0000")
      .set(ORIGINAL_TXN_ID, originalTxnId)
      .set(NET_CANCEL_RESULT, "01")
      .toBytes());
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
