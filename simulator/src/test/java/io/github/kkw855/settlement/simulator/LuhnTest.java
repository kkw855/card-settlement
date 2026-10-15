package io.github.kkw855.settlement.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LuhnTest {

  @ParameterizedTest
  @ValueSource(strings = {"4111111111111111", "5555555555554444", "79927398713"})
  @DisplayName("유효한 번호")
  void valid(String number) {
    assertThat(Luhn.isValid(number)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "4111111111111112", // 마지막 자리 오타
    "1411111111111111", // 앞 두 자리 뒤바뀜
    "79927398731"       // 끝 두 자리 뒤바뀜
  })
  @DisplayName("한 자리 오타나 이웃 자리 뒤바뀜은 잡는다")
  void invalid(String number) {
    assertThat(Luhn.isValid(number)).isFalse();
  }
}
