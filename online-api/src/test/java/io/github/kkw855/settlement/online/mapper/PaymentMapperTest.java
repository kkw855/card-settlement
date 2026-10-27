package io.github.kkw855.settlement.online.mapper;

import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import io.github.kkw855.settlement.core.payment.Payment;
import io.github.kkw855.settlement.core.payment.PaymentMapper;
import io.github.kkw855.settlement.core.payment.PaymentStatus;
import io.github.kkw855.settlement.core.payment.TxnIdGenerator;
import io.github.kkw855.settlement.online.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PaymentMapperTest extends IntegrationTest {

  @Autowired
  MerchantMapper merchantMapper;
  @Autowired
  PaymentMapper paymentMapper;
  @Autowired
  TxnIdGenerator txnIdGenerator;

  long merchantId;

  @BeforeEach
  void merchant() {
    merchantMapper.insert(TestData.merchant("M000000001", "k".repeat(64)));
    merchantId = merchantMapper.findByMerchantNo("M000000001").orElseThrow().merchantId();
  }

  @Test
  @DisplayName("Y1 REQUESTED로 넣고 거래고유번호로 찾는다 (enum, LocalDateTime, null 컬럼까지 매핑)")
  void insertAndFind() {
    String txnId = txnIdGenerator.next();
    paymentMapper.insert(TestData.requested(txnId, merchantId, "key-1"));

    Payment found = paymentMapper.findByTxnId(txnId).orElseThrow();

    assertThat(found.paymentId()).isNotNull();
    assertThat(found.status()).isEqualTo(PaymentStatus.REQUESTED);
    assertThat(found.requestedAt()).isEqualTo(TestData.NOW);
    assertThat(found.approvalNo()).isNull();
    assertThat(found.version()).isZero();
  }

  @Test
  @DisplayName("Y2 같은 가맹점이 같은 멱등 키로 두 번 넣으면 DuplicateKeyException (I2)")
  void rejectsDuplicateIdempotencyKey() {
    paymentMapper.insert(TestData.requested(txnIdGenerator.next(), merchantId, "key-1"));

    assertThatThrownBy(() -> paymentMapper.insert(TestData.requested(txnIdGenerator.next(), merchantId, "key-1")))
      .isInstanceOf(DuplicateKeyException.class)
      .hasMessageContaining("UK_PAYMENT_IDEMPOTENCY"); // 이름까지 확인한다. 다른 제약(거래고유번호 중복)에 막혀도 통과하지 않게
    assertThat(paymentMapper.findByIdempotencyKey(merchantId, "key-1")).isPresent();
  }

  @Test
  @DisplayName("Y3 승인은 REQUESTED일 때만 바뀐다. 두 번째 승인은 0행 (I3)")
  void approvesOnlyFromRequested() {
    String txnId = txnIdGenerator.next();
    paymentMapper.insert(TestData.requested(txnId, merchantId, "key-1"));

    assertThat(paymentMapper.approve(txnId, "30000001", TestData.NOW.plusSeconds(1), "0000"))
      .isEqualTo(1);
    assertThat(paymentMapper.approve(txnId, "30000002", TestData.NOW.plusSeconds(2), "0000"))
      .isZero();

    Payment approved = paymentMapper.findByTxnId(txnId).orElseThrow();
    assertThat(approved.status()).isEqualTo(PaymentStatus.APPROVED);
    assertThat(approved.approvalNo()).isEqualTo("30000001");
    assertThat(approved.version()).isEqualTo(1);
  }

  @Test
  @DisplayName("Y4 기대한 상태가 아니면 바꾸지 않는다 (I3)")
  void changesStatusOnlyFromExpected() {
    String txnId = txnIdGenerator.next();
    paymentMapper.insert(TestData.requested(txnId, merchantId, "key-1"));

    assertThat(paymentMapper.changeStatus(txnId, PaymentStatus.UNKNOWN, PaymentStatus.NET_CANCELLED, "P002"))
      .isZero();
    assertThat(paymentMapper.changeStatus(txnId, PaymentStatus.REQUESTED, PaymentStatus.UNKNOWN, "P003"))
      .isEqualTo(1);

    assertThat(paymentMapper.findByTxnId(txnId).orElseThrow().status()).isEqualTo(PaymentStatus.UNKNOWN);
  }

  @Test
  @DisplayName("Y5 거래고유번호 P + 날짜 8자리 + 일련번호 11자리, 부를 때마다 다르다")
  void txnIdFormat() {
    String first = txnIdGenerator.next();
    String second = txnIdGenerator.next();

    assertThat(first).matches("P\\d{8}\\d{11}").hasSize(20);
    assertThat(second).isNotEqualTo(first);
  }
}
