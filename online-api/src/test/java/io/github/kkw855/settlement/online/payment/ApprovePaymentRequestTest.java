package io.github.kkw855.settlement.online.payment;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 입력 검증의 경계값. Spring 없이 Bean Validation만 돌린다. */
class ApprovePaymentRequestTest {

  @SuppressWarnings("resource")
  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  @ParameterizedTest(name = "{0}개월 -> 통과 {1}", quoteTextArguments = false)
  @CsvSource({"0, true", "1, false", "2, true", "36, true", "37, false", "-1, false"})
  @DisplayName("R1 할부는 0(일시불) 또는 2~36개월")
  void installmentMonths(int months, boolean valid) {
    assertThat(violatedField(new ApprovePaymentRequest("4111111111111111", "2812",
      months, 100_000L)))
      .isEqualTo(valid ? Set.of() : Set.of("installmentMonths"));
  }

  @ParameterizedTest(name = "{0}원 -> 통과 {1}", quoteTextArguments = false)
  @CsvSource({"1, true", "0, false", "999999999999, true", "1000000000000, false"})
  @DisplayName("R2 금액은 1원 ~ 999,999,999,999원 (전문 거래금액 12자리)")
  void amount(long amount, boolean valid) {
    assertThat(violatedField(new ApprovePaymentRequest("4111111111111111", "2812",
      0, amount)))
      .isEqualTo(valid ? Set.of() : Set.of("amount"));
  }

  @Test
  @DisplayName("R3 toString()에 카드번호와 유효기간이 나오지 않는다")
  void toStringHidesCard() {
    assertThat(new ApprovePaymentRequest("4111111111111111", "2812",
      0, 10_000L).toString())
      .doesNotContain("4111111111111111")
      .doesNotContain("2812");
  }

  /**
   * 검증에 실패한 필드 이름
   */
  private static Set<String> violatedField(ApprovePaymentRequest request) {
    return VALIDATOR.validate(request).stream()
      .map(violation -> violation.getPropertyPath().toString())
      .collect(Collectors.toSet());
  }
}
