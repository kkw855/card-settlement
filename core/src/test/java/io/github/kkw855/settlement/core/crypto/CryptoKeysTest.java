package io.github.kkw855.settlement.core.crypto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CryptoKeysTest {

  @Test
  @DisplayName("K1 키가 없으면 시작하지 않는다")
  void rejectsMissingKey() {
    assertThatThrownBy(() -> CryptoKeys.aes(null)).isInstanceOf(CryptoException.class);
    assertThatThrownBy(() -> CryptoKeys.aes("  ")).isInstanceOf(CryptoException.class);
  }

  @Test
  @DisplayName("K2 32바이트가 아니면 거부한다 (AES-128 키를 잘못 넣는 실수 방지)")
  void rejectsWrongLength() {
    assertThatThrownBy(() -> CryptoKeys.aes("AAAAAAAAAAAAAAAAAAAAAA==")) // 16바이트
      .isInstanceOf(CryptoException.class)
      .hasMessageContaining("16바이트");
  }

  @Test
  @DisplayName("K3 Base64가 아니면 거부하고, 메시지에 키 값을 넣지 않는다")
  void rejectsInvalidBase64() {
    assertThatThrownBy(() -> CryptoKeys.hmac("not-base64!!"))
      .isInstanceOf(CryptoException.class)
      .hasMessageNotContaining("not-base64");
  }
}
