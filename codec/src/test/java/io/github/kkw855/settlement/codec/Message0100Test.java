package io.github.kkw855.settlement.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

/**
 * docs/02-message-spec.md 3장의 0100 승인 요청 골든 샘플로 레이아웃·쓰기·읽기를 검증한다.
 *
 * <p>레이아웃은 PG와 시뮬레이터가 각자 정의하므로(ADR-0002) codec에는 없다. 여기서는 테스트용으로 정의한다.
 */
class Message0100Test {

  // 공통부 (0 ~ 49)
  static final Field.N MSG_TYPE = new Field.N("전문구분코드", 0, 4);
  static final Field.AN TXN_ID = new Field.AN("거래고유번호", 4, 20);
  static final Field.N SENT_AT = new Field.N("전송일시", 24, 14);
  static final Field.AN RESPONSE_CODE = new Field.AN("응답코드", 38, 4);
  static final Field.AN HEADER_RESERVED = new Field.AN("공통부 예비", 42, 8);

  // 개별부 (50 ~ 149)
  static final Field.AN MERCHANT_NO = new Field.AN("가맹점번호", 50, 10);
  static final Field.N CARD_NO = new Field.N("카드번호", 60, 16);
  static final Field.N EXPIRY = new Field.N("유효기간", 76, 4);
  static final Field.N INSTALLMENT = new Field.N("할부개월", 80, 2);
  static final Field.N AMOUNT = new Field.N("거래금액", 82, 12);
  static final Field.AN MERCHANT_NAME = new Field.AN("가맹점명", 94, 40);
  static final Field.AN BODY_RESERVED = new Field.AN("개별부 예비", 134, 16);

  static final Layout REQUEST_0100 = Layout.of("0100", 150,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    MERCHANT_NO, CARD_NO, EXPIRY, INSTALLMENT, AMOUNT, MERCHANT_NAME, BODY_RESERVED);

  /** 명세 표를 한 줄씩 옮긴 기대값. 테스트 대상(codec)을 쓰지 않고 만든다. */
  static final byte[] GOLDEN = concat(
    ascii("0150"),                                         // 길이 (프레이밍)
    ascii("0100"),                                         // 전문구분코드
    ascii("P2026100800000000001"),                         // 거래고유번호
    ascii("20261008103000"),                               // 전송일시
    ascii(" ".repeat(4)),                            // 응답코드 (요청은 공백)
    ascii(" ".repeat(8)),                            // 공통부 예비
    ascii("M000000001"),                                   // 가맹점번호
    ascii("4111111111111111"),                             // 카드번호 (테스트 카드)
    ascii("2812"),                                         // 유효기간
    ascii("00"),                                           // 할부개월
    ascii("000000010000"),                                 // 거래금액
    HexFormat.of().parseHex("C8ABB1E6B5BFC4ABC6E4"), // 가맹점명: 홍길동카페 (10바이트)
    ascii(" ".repeat(30)),                           //    + 공백 30
    ascii(" ".repeat(16))                            // 개별부 예비
  );

  @Test
  @DisplayName("골든 샘플은 154바이트, 가맹점명은 오프셋 94에서 시작한다 (명세 확인)")
  void goldenSampleMatchesSpec() {
    assertThat(GOLDEN).hasSize(154);
    assertThat(MERCHANT_NAME.offset()).isEqualTo(94);
  }

  @Test
  @DisplayName("W1 0100 승인 요청을 만들면 골든 샘플과 바이트 단위로 같다")
  void writesGoldenSample() {
    byte[] body = REQUEST_0100.writer()
      .set(MSG_TYPE, "0100")
      .set(TXN_ID, "P2026100800000000001")
      .set(SENT_AT, "20261008103000")
      .set(MERCHANT_NO, "M000000001")
      .set(CARD_NO, "4111111111111111")
      .set(EXPIRY, "2812")
      .set(INSTALLMENT, 0)
      .set(AMOUNT, 10_000)
      .set(MERCHANT_NAME, "홍길동카페")
      .toBytes();
    byte[] framed = concat(FieldCodec.encodeNumeric(body.length, 4), body);

    assertThat(framed).isEqualTo(GOLDEN);
  }

  @Test
  @DisplayName("W2 N 필드를 채우지 않으면 0으로 채우지 않고 예외")
  void rejectsMissingNumericField() {
    MessageWriter writer = REQUEST_0100.writer()
      .set(MSG_TYPE, "0100")
      .set(SENT_AT, "20261008103000")
      .set(CARD_NO, "4111111111111111")
      .set(EXPIRY, "2812")
      .set(INSTALLMENT, 0); // 거래금액 빠뜨림

    assertThatThrownBy(writer::toBytes)
      .isInstanceOf(CodecException.class)
      .hasMessageContaining("거래금액");
  }

  @Test
  @DisplayName("W3 다른 레이아웃의 필드는 예외")
  void rejectsForeignField() {
    Field.AN foreign = new Field.AN("원거래고유번호", 50, 20); // 0400 취소 요청의 필드

    assertThatThrownBy(() -> REQUEST_0100.writer().set(foreign, "P2026100800000000001"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("R1 골든 샘플을 읽으면 필드 값이 나온다")
  void readsGoldenSample() {
    byte[] body = Arrays.copyOfRange(GOLDEN, 4, GOLDEN.length);
    MessageReader reader = REQUEST_0100.reader(body);

    assertThat(reader.getDigits(MSG_TYPE)).isEqualTo("0100");
    assertThat(reader.getText(TXN_ID)).isEqualTo("P2026100800000000001");
    assertThat(reader.getText(RESPONSE_CODE)).isEmpty();
    assertThat(reader.getLong(AMOUNT)).isEqualTo(10_000L);
    assertThat(reader.getText(MERCHANT_NAME)).isEqualTo("홍길동카페");
  }

  @Test
  @DisplayName("R2 길이가 레이아웃과 다르면 예외")
  void rejectsWrongLength() {
    assertThatThrownBy(() -> REQUEST_0100.reader(new byte[149]))
      .isInstanceOf(CodecException.class);
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
