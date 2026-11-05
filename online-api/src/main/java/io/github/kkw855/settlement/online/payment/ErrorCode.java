package io.github.kkw855.settlement.online.payment;

/** 카드사에 보내기 전에 거절하는 이유 (docs/api/openapi.yaml의 ErrorCode). HTTP 응답은 2-6d에서 붙인다. */
public enum ErrorCode {
  IDEMPOTENCY_KEY_REUSED, // 422: 같은 키에 다른 본문
  PAYMENT_IN_PROGRESS,    // 409: 같은 키의 처음 요청이 아직 처리 중
  INSTALLMENT_NOT_ALLOWED // 422: 5만 원 미만 할부
}
