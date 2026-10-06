package io.github.kkw855.settlement.codec;

/**
 * 고정길이 필드를 만들거나 읽을 수 없을 때 던진다.
 *
 * <p>메시지에는 위치·길이·이유만 넣고 <b>필드 값은 넣지 않는다.</b>
 * 카드번호 필드에도 쓰이므로, 값이 들어가면 예외 로그로 카드번호가 샌다 (I10)
 */
public final class CodecException extends RuntimeException {

  public CodecException(String message) {
    super(message);
  }

  public CodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
