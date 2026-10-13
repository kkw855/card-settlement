package io.github.kkw855.settlement.codec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LayoutTest {

  @Test
  @DisplayName("L1 필드 사이에 빈틈이 있으면 예외")
  void rejectsGap() {
    assertThatThrownBy(() -> Layout.of("TEST", 10,
      new Field.N("a", 0, 4),
      new Field.AN("b", 5, 5)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("b");
  }

  @Test
  @DisplayName("L2 필드가 겹치면 예외")
  void rejectsOverlap() {
    assertThatThrownBy(() -> Layout.of("TEST", 10,
      new Field.N("a", 0, 4),
      new Field.AN("b", 3, 7)))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("L3 필드 길이 합계가 전문 길이와 다르면 예외")
  void rejectsLengthMismatch() {
    assertThatThrownBy(() -> Layout.of("TEST", 10,
      new Field.N("a", 0,4),
      new Field.AN("b", 4, 5)))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("L4 필드 이름이 겹치면 예외")
  void rejectsDuplicateName() {
    assertThatThrownBy(() -> Layout.of("Test", 10,
      new Field.N("a", 0, 4),
      new Field.AN("a", 4, 6)))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
