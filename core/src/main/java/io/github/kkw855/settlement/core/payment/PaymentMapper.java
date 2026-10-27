package io.github.kkw855.settlement.core.payment;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SQL은 resources/mapper/PaymentMapper.xml에 있다.
 *
 * <p>여러 파라미터를 {@code @Param} 없이 이름으로 쓴다. 루트 빌드의 {@code -parameters} 컴파일 옵션 덕분이다.
 */
public interface PaymentMapper {

  /** 거래고유번호 일련번호. 형식은 {@link TxnIdGenerator}가 만든다. */
  long nextTxnSequence();

  /**
   * 같은 (가맹점, 멱등 키)가 이미 있으면 DuplicateKeyException (I2).
   */
  void insert(Payment payment);

  Optional<Payment> findByTxnId(String txnId);

  Optional<Payment> findByIdempotencyKey(long merchantId, String idempotencyKey);

  /**
   * REQUESTED -> APPROVED. 바뀐 행 수를 돌려준다. 0이면 이미 다른 상태다 (I3).
   */
  int approve(String txnId, String approvalNo, LocalDateTime approvedAt, String resultCode);

  /**
   * {@code expected} 상태일 때만 {@code next}로 바꾼다. 바뀐 행 수를 돌려준다 (I3).
   */
  int changeStatus(String txnId, PaymentStatus expected, PaymentStatus next, String resultCode);
}
