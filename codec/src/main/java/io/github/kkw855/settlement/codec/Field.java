package io.github.kkw855.settlement.codec;

/**
 * 전문·파일 레이아웃의 필드 하나. 명세 표의 오프셋·길이(바이트)를 그대로 옮긴다.
 *
 * <p>N과 AN을 서로 다른 타입으로 둬서, AN 필드에 {@code long}을 넣는 실수를 컴파일러가 막게 한다.
 */
public sealed interface Field permits Field.N, Field.AN {

  String name();

  int offset();

  int length();

  /** 숫자. 오른쪽 정렬, 왼쪽 0 채움. */
  record N(String name, int offset, int length) implements Field {
    public N {
      validate(name, offset, length);
    }
  }

  /** 문자. 왼쪽 정렬, 오른쪽 공백 채움. */
  record AN(String name, int offset, int length) implements Field {
    public AN {
      validate(name, offset, length);
    }
  }

  private static void validate(String name, int offset, int length) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("필드 이름이 비어 있다");
    }
    if (offset < 0 || length <= 0) {
      throw new IllegalArgumentException("%s: offset=%d, length=%d".formatted(name, offset, length));
    }
  }
}
