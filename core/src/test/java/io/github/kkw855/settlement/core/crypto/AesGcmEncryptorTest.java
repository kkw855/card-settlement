package io.github.kkw855.settlement.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

class AesGcmEncryptorTest {

  private static final String CARD = "4111111111111111"; // 16자

  private final AesGcmEncryptor encryptor = new AesGcmEncryptor(TestKeys.aes());

  @Test
  @DisplayName("E1 암호화한 값을 복호화하면 원문")
  void roundTrip() {
    assertThat(encryptor.decrypt(encryptor.encrypt(CARD))).isEqualTo(CARD);
  }

  @Test
  @DisplayName("E2 같은 원문도 암호화할 때마다 결과가 다르다 (IV를 매번 새로 뽑는다)")
  void randomizedEachTime() {
    assertThat(encryptor.encrypt(CARD)).isNotEqualTo(encryptor.encrypt(CARD));
  }

  @Test
  @DisplayName("E3 암호문이 한 바이트라도 바뀌면 복호화하지 않는다 (인증 태그)")
  void detectsTampering() {
    byte[] bytes = Base64.getDecoder().decode(encryptor.encrypt(CARD));
    bytes[bytes.length - 1] ^= 0x01; // 마지막 바이트의 한 비트를 뒤집는다
    String tampered = Base64.getEncoder().encodeToString(bytes);

    assertThatThrownBy(() -> encryptor.decrypt(tampered))
      .isInstanceOf(CryptoException.class)
      .hasMessageNotContaining(CARD);
  }

  @Test
  @DisplayName("E4 다른 키로는 복호화할 수 없다")
  void otherKeyCannotDecrypt() {
    AesGcmEncryptor other = new AesGcmEncryptor(CryptoKeys.aes(TestKeys.OTHER_BASE64));
    assertThatThrownBy(() -> other.decrypt(encryptor.encrypt(CARD)))
      .isInstanceOf(CryptoException.class);
  }

  @Test
  @DisplayName("E5 카드번호 암호문은 60자로 card_enc(VARCHAR2 200)에 들어간다")
  void fitsColumn() {
    // 버전 1 + IV 12 + 암호문 16 + 태그 16 = 45바이트 → Base64 60자
    assertThat(encryptor.encrypt(CARD)).hasSize(60);
  }

  @Test
  @DisplayName("E6 버전 바이트가 다르면 거부한다")
  void rejectsUnknownVersion() {
    byte[] bytes = Base64.getDecoder().decode(encryptor.encrypt(CARD));
    bytes[0] = 9;

    assertThatThrownBy(() -> encryptor.decrypt(Base64.getEncoder().encodeToString(bytes)))
      .isInstanceOf(CryptoException.class);
  }
}
