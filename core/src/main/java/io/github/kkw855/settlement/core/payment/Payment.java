package io.github.kkw855.settlement.core.payment;

import java.time.LocalDateTime;

/** 결제 (payment 테이블). 카드번호는 암호문·해시·마스킹 값만 들고 있다 (ADR-0005).
 *
 * @param paymentId 새로 넣을 때는 null (DB가 IDENTITY로 만든다). 업무 키는 txnId다
 */
public record Payment(
  Long paymentId,
  String txnId,
  long merchantId,
  String idempotencyKey,
  String cardEnc,
  String cardHash,
  String cardMasked,
  int installmentMonths,
  long amount,
  PaymentStatus status,
  String resultCode,
  String approvalNo,
  LocalDateTime approvedAt,
  LocalDateTime requestedAt,
  long version
) {

  /** 카드사에 보내기 전에 먼저 저장하는 결제 (docs/00-domain.md 4장). */
  public static Payment requested(String txnId, long merchantId, String idempotencyKey,
    String cardEnc, String cardHash, String cardMasked, int installmentMonths, long amount,
    LocalDateTime requestedAt) {
    return new Payment(null, txnId, merchantId, idempotencyKey, cardEnc, cardHash, cardMasked,
      installmentMonths, amount, PaymentStatus.REQUESTED, null, null, null,
      requestedAt, 0);
  }
}
