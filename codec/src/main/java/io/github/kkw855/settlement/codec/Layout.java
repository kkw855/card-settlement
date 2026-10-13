package io.github.kkw855.settlement.codec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 전문 하나의 필드 목록. 만들 때 필드가 빈틈·겹침 없이 0부터 {@code length}까지 이어지는지 검사한다.
 *
 * <p>명세 표의 오프셋과 길이를 둘 다 옮겨 적고 서로 맞는지 확인하는 셈이라, 옮겨 적다 생긴 실수를 잡는다.
 */
public record Layout(String name, int length, List<Field> fields) {

  public Layout {
    fields = List.copyOf(fields);

    int expectedOffset = 0;
    Set<String> names = new HashSet<>();
    for (Field field: fields) {
      if (field.offset() != expectedOffset) {
        throw new IllegalArgumentException("%s: %s의 오프셋은 %d여야 하는데 %d다 (빈틈 또는 겹침)"
          .formatted(name, field.name(), expectedOffset, field.offset()));
      }
      if (!names.add(field.name())) {
        throw new IllegalArgumentException("%s: 필드 이름 중복 %s".formatted(name, field.name()));
      }
      expectedOffset += field.length();
    }
    if (expectedOffset != length) {
      throw new IllegalArgumentException(
        "%s: 필드 길이 합계 %d ≠ 전문 길이 %d".formatted(name, expectedOffset, length));
    }
  }

  public static Layout of(String name, int length, Field... fields) {
    return new Layout(name, length, List.of(fields));
  }

  public MessageWriter writer() {
    return new MessageWriter(this);
  }

  public MessageReader reader(byte[] message) {
    return new MessageReader(this, message);
  }

  void requireField(Field field) {
    if (!fields.contains(field)) {
      throw new IllegalArgumentException("%s 레이아웃에 없는 필드: %s".formatted(name, field.name()));
    }
  }
}
