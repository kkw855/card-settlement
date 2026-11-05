package io.github.kkw855.settlement.online.card;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


class MessageLoggerTest {

  @Test
  @DisplayName("L1 0100의 카드번호는 가운데 6자리를 가리고, 보낼 원본 바이트는 그대로 둔다")
  void maskCardNumberOnlyInCopy() {
    byte[] message = APPROVAL_REQUEST.writer()
      .set(MSG_TYPE, "0100")
      .set(TXN_ID, "P2026100800000000001")
      .set(SENT_AT, "20261008103000")
      .set(MERCHANT_NO, "M000000001")
      .set(CARD_NO, "4111111111111111")
      .set(EXPIRY, "2812")
      .set(INSTALLMENT, 0)
      .set(AMOUNT, 10_000)
      .set(MERCHANT_NAME, "홍길동카페").toBytes();

    String masked = MessageLogger.mask("0100", message);

    assertThat(masked).contains("411111******1111").doesNotContain("4111111111111111")
      .contains("홍길동카페");
    assertThat(APPROVAL_REQUEST.reader(message).getDigits(CARD_NO)).isEqualTo("4111111111111111");
  }
}
