package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.card.CardNumber;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /v1/payments 본문 (docs/api/openapi.yaml의 ApprovalRequest).
 *
 * <p>숫자를 {@code Integer}·{@code Long}으로 받고 {@code @NotNull}로 막는다. 필드가 빠지면 늘
 * "installmentMonths: 필수다"처럼 어느 필드인지 알려주는 400이 나간다. {@code int}로 받으면
 * Jackson 설정에 따라 결과가 갈린다. Jackson 3 기본값은 본문 전체를 거부해 어느 필드인지 알 수 없고,
 * FAIL_ON_NULL_FOR_PRIMITIVES를 끄면(Jackson 2의 기본값) 0이 들어가 일시불로 승인된다.
 *
 * <p>카드번호는 형식만 본다. Luhn 검증은 카드사가 한다 (docs/03-api.md 8장).
 */
public record ApprovePaymentRequest(
  @NotNull(message = "필수다")
  @Pattern(regexp = "\\d{16}", message = "숫자 16자리여야 한다")
  String cardNumber,

  @NotNull(message = "필수다")
  @Pattern(regexp = "\\d{2}(0[1-9]|1[0-2])", message = "yyMM 형식이어야 한다")
  String cardExpiry,

  @NotNull(message = "필수다")
  @InstallmentMonths
  Integer installmentMonths,

  @NotNull(message = "필수다")
  @Min(value = 1, message = "1원 이상이어야 한다")
  @Max(value = 999_999_999_999L, message = "999,999,999,999원 이하여야 한다") // 전문 거래 금액 12자리
  Long amount
) {

  /** 검증을 통과한 뒤에만 부른다. 카드번호가 16자리가 아니면 CardNumber가 예외를 던진다. */
  ApprovalCommand toCommand() {
    return new ApprovalCommand(new CardNumber(cardNumber), cardExpiry, installmentMonths, amount);
  }

  /** 검증 전에는 카드번호 형식을 믿을 수 없어 마스킹하지 않고 통째로 가린다. (I10) */
  @SuppressWarnings("NullableProblems")
  @Override
  public String toString() {
    return "ApprovePaymentRequest[cardNumber=****, cardExpiry=****, installmentMonths=%s, amount=%s]"
      .formatted(installmentMonths, amount);
  }
}
