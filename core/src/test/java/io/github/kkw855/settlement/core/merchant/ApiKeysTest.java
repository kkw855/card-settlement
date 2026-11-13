package io.github.kkw855.settlement.core.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeysTest {

  @Test
  @DisplayName("K1 SHA-256 표준 테스트 벡터와 같은 값을 소문자 16진수 64자로 만든다")
  void matchesStandardTestVector() {
    // FIPS 180-2 부록의 예: SHA-256("abc")
    assertThat(ApiKeys.hash("abc"))
      .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
