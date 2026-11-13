package io.github.kkw855.settlement.online.payment;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 할부 개월: 0(일시불) 또는 2~36. 1개월 할부는 없다 (docs/api/openapi.yaml).
 *
 * <p>{@code @Min}·{@code @Max}로는 "1만 빼고"를 표현할 수 없어서 직접 만든다.
 * null은 {@code @NotNull}이 따로 막는다.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InstallmentMonths.Validator.class)
public @interface InstallmentMonths {

  String message() default "0(일시불) 또는 2~36이어야 한다";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /** 검사 로직. 어노테이션 안에 두면 둘이 늘 같이 다닌다. */
  final class Validator implements ConstraintValidator<InstallmentMonths, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
      return value == null || value == 0 || (value >= 2 && value <= 36);
    }
  }
}
