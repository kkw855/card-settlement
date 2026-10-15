package io.github.kkw855.settlement.simulator;

/**
 * 카드번호 검증 숫자 (Luhn). 오른쪽에서 두 번째 자리부터 한 칸씩 건너 2배하고, 2배한 값이 9보다 크면 9를 뺀다.
 * 모든 자리를 더해 10으로 나누어떨어지면 유효하다. 입력 실수(한 자리 오타, 이웃 자리 뒤바뀜)를 잡는 용도다.
 */
final class Luhn {

  private Luhn() {}

  static boolean isValid(String digits) {
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int digit = digits.charAt(i) - '0';
      if (doubled) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }
}
