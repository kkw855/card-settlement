package io.github.kkw855.settlement.online.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드와 HTTP 상태 (docs/api/openapi.yaml의 ErrorCode). 응답의 {@code code}로 나간다.
 *
 * <p>가맹점은 HTTP 상태가 아니라 이 코드로 분기한다. 같은 422라도 키 재사용과 할부 불가는 대응이 다르다.
 * 취소 관련 코드(NOT_CANCELLABLE 등)는 2-8에서 추가한다.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
  MERCHANT_SUSPENDED(HttpStatus.FORBIDDEN),
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
  PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT),
  IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT),
  INSTALLMENT_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
