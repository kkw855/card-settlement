package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.payment.Payment;
import io.github.kkw855.settlement.core.payment.PaymentStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 결제 응답 (docs/api/openapi.yaml의 Payment). 카드번호는 마스킹 값만 나간다.
 *
 * <p>{@code cancel}은 취소를 만드는 2-8에서 추가한다. 계약에서 필수 필드가 아니라 빠져 있어도 된다.
 *
 * @param result 마지막 결과. 아직 처리 중(REQUESTED)이면 null
 */
public record PaymentResponse(
  String txnId,
  PaymentStatus status,
  long amount,
  int installmentMonths,
  String cardMasked,
  String approvalNo,
  OffsetDateTime approvedAt,
  OffsetDateTime requestedAt,
  Result result
) {

  /**
   * DB 시각은 KST다 (docs/01-erd.md). 응답에는 오프셋을 붙여 받는 쪽이 시간대를 착각하지 않게 한다
   */
  private static final ZoneOffset KST = ZoneOffset.ofHours(9);

  public record Result(String code, String message) {}

  static PaymentResponse from(Payment payment) {
    Result result = payment.resultCode() == null
      ? null
      : new Result(payment.resultCode(), ResultCodes.message(payment.resultCode()));
    return new PaymentResponse(payment.txnId(), payment.status(),
      payment.amount(), payment.installmentMonths(), payment.cardMasked(),
      payment.approvalNo(), kst(payment.approvedAt()), kst(payment.requestedAt()),
      result);
  }

  private static OffsetDateTime kst(LocalDateTime time) {
    return time == null ? null : time.atOffset(KST);
  }
}
