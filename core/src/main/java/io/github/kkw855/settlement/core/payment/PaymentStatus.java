package io.github.kkw855.settlement.core.payment;

/** 결제 상태 (docs/00-domain.md 4장). DB에는 이름 그대로 저장한다. */
public enum PaymentStatus {
  REQUESTED,
  APPROVED,
  DECLINED,
  UNKNOWN,
  NET_CANCELLED,
  CANCELLED
}
