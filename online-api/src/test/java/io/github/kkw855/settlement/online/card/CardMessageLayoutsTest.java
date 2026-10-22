package io.github.kkw855.settlement.online.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

/** PG 쪽 레이아웃을 docs/02-message-spec.md 3장의 골든 샘플로 확인한다. 시뮬레이터와 따로 옮겨 적은 것을 검증한다. */
class CardMessageLayoutsTest {

  /**
   * 명세 표를 한 줄씩 옮긴 0100 본문 (길이 접두 4바이트는 뺐다).
   */
  private static final byte[] GOLDEN_0100_BODY = concat(
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
    HexFormat.of().parseHex("C8ABB1E6B5BFC4ABC6E4"), // 가맹점명: 홍길동카페
    ascii(" ".repeat(30)),
    ascii(" ".repeat(16)));                          // 0100 예비

  @Test
  @DisplayName("PG가 만든 0100은 골든 샘플과 바이트 단위로 같다")
  void approvalRequestMatchesGoldenSample() {
    byte[] body = APPROVAL_REQUEST.writer()
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

    assertThat(body).isEqualTo(GOLDEN_0100_BODY);
  }

  @Test
  @DisplayName("전문 6종이 모두 150바이트로 빈틈없이 정의되어 있다")
  void allLayoutsAre150Bytes() {
    // Layout은 만들 때 빈틈·겹침과 전체 길이를 검사한다. 이 클래스가 로딩되면 6종 모두 통과한 것이다.
    assertThat(List.of(APPROVAL_REQUEST, APPROVAL_RESPONSE, CANCEL_REQUEST, CANCEL_RESPONSE, NET_CANCEL_REQUEST, NET_CANCEL_RESPONSE))
      .allSatisfy(layout -> assertThat(layout.length()).isEqualTo(MESSAGE_LENGTH));
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
