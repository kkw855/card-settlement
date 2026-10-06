package io.github.kkw855.settlement.codec;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 고정길이 필드 하나를 MS949 바이트로 바꾸고 되돌린다. 길이와 오프셋은 모두 바이트 기준이다.
 *
 * <ul>
 *   <li>N: ASCII 숫자만. 오른쪽 정렬, 왼쪽을 {@code '0'}으로 채운다. 자릿수가 넘치면 예외 (금액은 자르지 않는다).</li>
 *   <li>AN: 왼쪽 정렬, 오른쪽을 공백으로 채운다. 넘치면 문자 경계에서 자른다.</li>
 * </ul>
 */
public final class FieldCodec {

  public static final Charset MS949 = Charset.forName("MS949");

  private static final byte SPACE = ' ';

  private FieldCodec() {}

  // ---------------------------------------------------------------- N

  public static byte[] encodeNumeric(String digits, int length) {
    checkLength(length);
    if (digits == null || digits.isEmpty()) {
      throw new CodecException("N 필드 값이 비어 있다 (length=" + length + ")");
    }
    if (!isAsciiDigits(digits)) {
      throw new CodecException("N 필드에 숫자가 아닌 문자가 있다 (length=" + length + ")");
    }
    if (digits.length() > length) {
      throw new CodecException("N 필드 자릿수 초과 (digits=%d, length=%d)".formatted(digits.length(), length));
    }
    return ("0".repeat(length - digits.length()) + digits).getBytes(StandardCharsets.US_ASCII);
  }

  /** 금액용. N에는 부호가 없으므로 음수는 예외다 (매입취소는 매입구분으로 표현한다) */
  public static byte[] encodeNumeric(long value, int length) {
    if (value < 0) {
      throw new CodecException("N 필드는 음수를 표현할 수 없다 (length=" + length + ")");
    }
    return encodeNumeric(Long.toString(value), length);
  }

  /** 숫자 문자열을 그대로 돌려준다. long 변환은 호출하는 쪽에서 한다 (카드번호, 일시도 N이다). */
  public static String decodeNumeric(byte[] src, int offset, int length) {
    checkRange(src, offset, length);
    if (!IntStream.range(offset, offset + length).map(i -> src[i]).allMatch(c -> c >= '0' && c <= '9')) {
      throw new CodecException("N 필드에 숫자가 아닌 바이트가 있다 (offset=%d, length=%d)".formatted(offset, length));
    }
    return new String(src, offset, length, StandardCharsets.US_ASCII);
  }

  /**
   * {@code null}이나 빈 문자열은 공백으로 채운 필드가 된다 (예: 거절 응답의 승인번호).
   */
  public static byte[] encodeAlphanumeric(String value, int length) {
    checkLength(length);
    byte[] out = new byte[length];
    Arrays.fill(out, SPACE);
    if (value == null || value.isEmpty()) {
      return out;
    }

    // getBytes(MS949)는 표현할 수 없는 문자를 조용히 '?'로 바꾼다. REPORT로 바꿔서 오류로 받는다.
    // 인코더는 스레드 안전하지 않으므로 호출마다 새로 만든다 (Charset 자체는 안전하다).
    CharsetEncoder encoder = MS949.newEncoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT);

    // 출력 버퍼가 모자라면 인코더는 문자 하나를 통째로 쓸 수 없을 때 OVERFLOW로 멈춘다.
    // 2바이트 문자를 반만 쓰는 일이 없으므로, 그대로 두면 문자 경계에서 잘리고 나머지는 공백으로 남는다.
    // 잘려 나간 뒷부분은 검사하지 않는다 (보내지 않는 부분이다).
    ByteBuffer target = ByteBuffer.wrap(out);
    CoderResult result = encoder.encode(CharBuffer.wrap(value), target, true);
    if (result.isUnderflow()) {
      result = encoder.flush(target);
    }
    if (result.isError()) {
      throw new CodecException("AN 필드에 MS949로 표현할 수 없는 문자가 있다 (length=%d)".formatted(length));
    }
    return out;
  }

  /**
   * 오른쪽 공백(0x20)만 제거한다. 왼쪽 공백과 전각 공백(U+3000)은 값의 일부로 둔다.
   */
  public static String decodeAlphanumeric(byte[] src, int offset, int length) {
    checkRange(src, offset, length);

    // 0x20은 MS949 둘째 바이트 범위(0x41~)에 없으므로, 디코딩 전에 바이트를 잘라도 글자가 깨지지 않는다.
    int end = offset + length;
    while (end > offset && src[end - 1] == SPACE) {
      end--;
    }

    CharsetDecoder decoder = MS949.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT);

    try {
      return decoder.decode(ByteBuffer.wrap(src, offset, end - offset)).toString();
    } catch (CharacterCodingException e) {
      throw new CodecException("AN 필드를 MS949로 해석할 수 없다 (offset=%d, length=%d)".formatted(offset, length), e);
    }
  }

  /** {@code Character.isDigit}은 전각 숫자('１')도 숫자로 본다. 전문에는 ASCII 0~9만 허용한다. */
  private static boolean isAsciiDigits(String s) {
    return s.chars().allMatch(c -> c >= '0' && c <= '9');
  }

  private static void checkLength(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("필드 길이는 1 이상이어야 한다: " + length);
    }
  }

  private static void checkRange(byte[] src, int offset, int length) {
    Objects.requireNonNull(src, "src");
    checkLength(length);
    Objects.checkFromIndexSize(offset, length, src.length);
  }
}
