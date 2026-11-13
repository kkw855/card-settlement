package io.github.kkw855.settlement.online.error;

/**
 * 요청을 처리하지 않고 오류 응답으로 돌려보낸다. 응답 형식은 {@link ApiExceptionHandler}가 만든다.
 *
 * <p>메시지는 응답의 {@code detail}로 가맹점에게 그대로 보인다. 카드 정보를 넣지 않는다 (I10)
 */
public final class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
