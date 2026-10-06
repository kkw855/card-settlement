package io.github.kkw855.settlement.codec;

import static io.github.kkw855.settlement.codec.FieldCodec.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

class FieldCodecTest {

  private static final HexFormat HEX = HexFormat.of();

  @Nested
  @DisplayName("N (숫자)")
  class Numeric {

    @Test
    @DisplayName("N1 금액은 왼쪽을 0으로 채운다")
    void padsLeftWithZeros() {
      assertThat(encodeNumeric(10_000L, 12)).isEqualTo(ascii("000000010000"));
    }

    @Test
    @DisplayName("N2 자릿수가 꽉 찬 숫자 문자열은 그대로")
    void keepsFullDigits() {
      assertThat(encodeNumeric("1234567890123456", 16)).isEqualTo(ascii("1234567890123456"));
    }

    @Test
    @DisplayName("N3 자릿수를 넘으면 자르지 않고 예외")
    void rejectsOverflow() {
      assertThatThrownBy(() -> encodeNumeric(1_000_000_000_000L, 12)).isInstanceOf(CodecException.class);
    }

    @Test
    @DisplayName("N4 음수는 예외")
    void rejectsNegative() {
      assertThatThrownBy(() -> encodeNumeric(-1L, 12)).isInstanceOf(CodecException.class);
    }

    @Test
    @DisplayName("N5 숫자가 아닌 문자는 예외 (전각 숫자 포함)")
    void rejectsNonDigits() {
      assertThatThrownBy(() -> encodeNumeric("12A4", 4)).isInstanceOf(CodecException.class);
      assertThatThrownBy(() -> encodeNumeric("１２３４", 4)).isInstanceOf(CodecException.class);
    }

    @Test
    @DisplayName("N6 오프셋 위치의 숫자 문자열을 읽는다")
    void decodesAtOffset() {
      byte[] src = ascii("XX123456789012YY");

      assertThat(decodeNumeric(src, 2, 12)).isEqualTo("123456789012");
    }

    @Test
    @DisplayName("N7 숫자가 아닌 바이트는 예외, 메시지에 값을 넣지 않는다 (I10)")
    void rejectsNonDigitBytesWithoutLeakingValue() {
      byte[] src = ascii("12345678901A3456");

      assertThatThrownBy(() -> decodeNumeric(src, 0, 16))
        .isInstanceOf(CodecException.class)
        .hasMessageNotContaining("78901A");
    }

    @Test
    @DisplayName("N8 공백을 0으로 보지 않는다")
    void rejectsSpaces() {
      assertThatThrownBy(() -> decodeNumeric(ascii(" ".repeat(12)), 0, 12))
        .isInstanceOf(CodecException.class);
    }
  }

  @Nested
  @DisplayName("AN (문자)")
  class Alphanumeric {

    @Test
    @DisplayName("A1 오른쪽을 공백으로 채운다")
    void padsRightWithSpaces() {
      assertThat(encodeAlphanumeric("M001", 12)).isEqualTo(ascii("M001" + " ".repeat(8)));
    }

    @Test
    @DisplayName("A2 한글은 1자 2바이트")
    void encodesKoreanAsTwoBytes() {
      byte[] expected = concat(
        HEX.parseHex("C8ABB1E6B5BFC4ABC6E4"), // 홍길동카페 = 10바이트
        ascii(" ".repeat(30))
      );

      assertThat(encodeAlphanumeric("홍길동카페", 40)).isEqualTo(expected);
    }

    @Test
    @DisplayName("A3 넘치면 2바이트 문자를 반으로 자르지 않고 공백으로 채운다")
    void truncatesAtCharacterBoundary() {
      String value = "A" + "가".repeat(20); // 1 + 40 = 41바이트
      byte[] expected = concat(ascii("A"), HEX.parseHex("B0A1".repeat(19)), ascii(" ")); // 가 * 19 = 38바이트

      assertThat(encodeAlphanumeric(value, 40)).isEqualTo(expected);
    }

    @Test
    @DisplayName("A4 길이가 정확히 맞으면 패딩하지 않는다")
    void exactFit() {
      assertThat(encodeAlphanumeric("홍길동", 6)).isEqualTo(HEX.parseHex("C8ABB1E6B5BF"));
    }

    @Test
    @DisplayName("A5 null은 공백으로 채운다")
    void nullBecomesSpaces() {
      assertThat(encodeAlphanumeric(null, 8)).isEqualTo(ascii(" ".repeat(8)));
    }

    @Test
    @DisplayName("A6 MS949로 표현할 수 없는 문자는 '?'로 바꾸지 않고 예외")
    void rejectsUnmappableCharacter() {
      assertThatThrownBy(() -> encodeAlphanumeric("커피☕", 10))
        .isInstanceOf(CodecException.class);
    }

    @Test
    @DisplayName("A7 오른쪽 공백만 제거한다")
    void trimsTrailingSpacesOnly() {
      assertThat(decodeAlphanumeric(ascii("M001" + " ".repeat(8)), 0, 12))
        .isEqualTo("M001");
      assertThat(decodeAlphanumeric(ascii("  M001  "), 0, 8)).isEqualTo("  M001");
    }

    @Test
    @DisplayName("A8 공백뿐이면 빈 문자열")
    void allSpacesBecomesEmpty() {
      assertThat(decodeAlphanumeric(ascii(" ".repeat(8)), 0, 8)).isEmpty();
    }

    @Test
    @DisplayName("A9 둘째 바이트가 ASCII 'A'(0x41)여도 한 글자로 읽는다")
    void decodesTrailByteInAsciiRange() {
      byte[] src = concat(HEX.parseHex("8141"), ascii("  ")); // 갂 + 공백 2 = 4바이트

      assertThat(decodeAlphanumeric(src, 0, 4)).isEqualTo("갂");
    }

    @Test
    @DisplayName("A10 반쪽 글자는 예외")
    void rejectsBrokenCharacter() {
      byte[] src = concat(HEX.parseHex("81"), ascii(" ")); // 첫째 바이트만 있음

      assertThatThrownBy(() -> decodeAlphanumeric(src, 0, 2))
        .isInstanceOf(CodecException.class);
    }

    @Test
    @DisplayName("인코딩한 값을 디코딩하면 원래 값")
    void roundTrip() {
      byte[] encoded = encodeAlphanumeric("홍길동카페", 40);

      assertThat(decodeAlphanumeric(encoded, 0, 40)).isEqualTo("홍길동카페");
    }
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }
}
