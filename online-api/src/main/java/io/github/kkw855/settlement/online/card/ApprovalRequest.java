package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.core.card.CardNumber;

/**
 * 0100에 담을 값. 카드사 전문 계층은 결제 도메인을 모르고 이 값만 안다.
 *
 * <p>유효기간도 카드 정보라서 {@code toString()}에서 가린다. 카드번호는 {@link CardNumber}가 스스로 가린다.
 */
public record ApprovalRequest(
  String txnId, String merchantNo, String merchantName, CardNumber card,
  String expiry, int installmentMonths, long amount
) {

  @SuppressWarnings("NullableProblems")
  @Override
  public String toString() {
    return "ApprovalRequest[txnId=%s, merchantNo=%s, card=%s, expiry=****, installmentMonths=%d, amount=%d]"
      .formatted(txnId, merchantNo, card, installmentMonths, amount);
  }
}
