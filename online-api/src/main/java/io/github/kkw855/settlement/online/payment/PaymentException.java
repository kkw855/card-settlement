package io.github.kkw855.settlement.online.payment;

/** 카드사에 보내기 전에 요청을 거절한다. 메시지에 카드 정보를 넣지 않는다 (I10) */
public final class PaymentException extends RuntimeException {

  private final ErrorCode code;

  public PaymentException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
