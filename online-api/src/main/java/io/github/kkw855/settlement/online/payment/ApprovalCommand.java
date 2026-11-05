package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.card.CardNumber;

/** 가맹점의 승인 요청 (POST /v1/payments 본문). 유효기간도 카드 정보라 {@code toString()}에서 가린다. */
public record ApprovalCommand(CardNumber card, String expiry, int installmentMonths, long amount) {

  @SuppressWarnings("NullableProblems")
  @Override
  public String toString() {
    return "ApprovalCommand[card=%s, expiry=****, installmentMonths=%d, amount=%d]"
      .formatted(card, installmentMonths, amount);
  }
}
