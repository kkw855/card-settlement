package io.github.kkw855.settlement.core.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kkw855.settlement.core.crypto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardProtectorTest {

  private static final CardNumber CARD = new CardNumber("4111111111111111");

  private final CardProtector protector = new CardProtector(new AesGcmEncryptor(TestKeys.aes()), new HmacHasher(TestKeys.hmac()));

  @Test
  @DisplayName("P1 toString()과 문자열 연결에는 마스킹 값만 나온다 (로그로 새지 않는다)")
  void toStringIsMasked() {
    assertThat(CARD.toString()).isEqualTo("411111******1111");
    assertThat("카드: " + CARD).doesNotContain("4111111111111111");
  }

  @Test
  @DisplayName("P2 숫자 16자리가 아니면 거부하고, 메시지에 값을 넣지 않는다")
  void rejectsInvalidNumber() {
    assertThatThrownBy(() -> new CardNumber("41111111111111"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageNotContaining("41111111111111");
  }

  @Test
  @DisplayName("P3 저장 형태 세 가지를 만들고, 암호문에서 원래 카드번호로 되돌린다")
  void protectsAndReveals() {
    ProtectedCard stored = protector.protect(CARD);

    assertThat(stored.masked()).isEqualTo("411111******1111");
    assertThat(stored.hash()).isEqualTo(protector.hash(CARD));
    assertThat(stored.encrypted()).doesNotContain("4111111111111111");
    assertThat(protector.reveal(stored.encrypted())).isEqualTo(CARD);
  }
}
