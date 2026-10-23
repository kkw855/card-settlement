package io.github.kkw855.settlement.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

class HmacHasherTest {

  @Test
  @DisplayName("H1 RFC 4231 표준 테스트 벡터 (Test Case 2)와 같다")
  void matchesRfc4231() {
    HmacHasher hasher = new HmacHasher(new SecretKeySpec("Jefe".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

    assertThat(hasher.hash("what do ya want for nothing?"))
      .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
  }

  @Test
  @DisplayName("H2 같은 카드번호는 항상 같은 해시 (검색할 수 있다), 소문자 16진수 64자")
  void deterministic() {
    HmacHasher hasher = new HmacHasher(TestKeys.hmac());

    String hash = hasher.hash("4111111111111111");

    assertThat(hasher.hash("4111111111111111")).isEqualTo(hash);
    assertThat(hash).matches("[0-9a-f]{64}");
  }

  @Test
  @DisplayName("H3 키가 다르면 해시도 다르다 (키 없이는 대입해 볼 수 없다)")
  void dependsOnKey() {
    HmacHasher hasher = new HmacHasher(TestKeys.hmac());
    HmacHasher other = new HmacHasher(CryptoKeys.hmac(TestKeys.OTHER_BASE64));

    assertThat(hasher.hash("4111111111111111")).isNotEqualTo(other.hash("4111111111111111"));
  }
}
