package io.github.kkw855.settlement.core.card;

/**
 * 평문 카드번호. 메모리에만 잠깐 있고, 저장할 때는 {@link CardProtector}로 바꾼다.
 *
 * <p>{@code toString()}은 마스킹 값을 돌려준다. record가 자동으로 만드는 {@code toString()}은 모든 필드를 출력해서,
 * {@code log.info("{}", card)} 한 줄로 카드번호가 로그에 남는다 (I10, docs/03-api.md 7장).
 */
public record CardNumber(String digits) {

  public CardNumber {
    if (digits == null || !digits.matches("\\d{16}")) {
      throw new IllegalArgumentException("카드번호는 숫자 16자리여야 한다"); // 값은 메시지에 넣지 않는다
    }
  }

  /** 앞 6자리와 뒤 4자리만 남긴다. 예: 411111******1111 */
  public String masked() {
    return digits.substring(0, 6) + "******" + digits.substring(12);
  }

  @Override
  public String toString() {
    return masked();
  }
}
