package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.payment.Payment;

/** 승인 요청의 결과. 둘 다 HTTP 201이고, 재현이면 Idempotent-Replayed 헤더를 붙인다 (docs/03-api.md 3장). */
public sealed interface ApprovalOutcome {

  Payment payment();

  /** 이번 요청으로 카드사까지 다녀왔다. 승인·거절·미확정 모두 여기에 해당한다. */
  record Processed(Payment payment) implements ApprovalOutcome {}

  /** 같은 Idempotency-Key로 이미 끝난 결제를 돌려준다. 카드사에 다시 보내지 않았다. */
  record Replayed(Payment payment) implements ApprovalOutcome {}
}
