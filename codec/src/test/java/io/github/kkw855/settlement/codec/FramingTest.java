package io.github.kkw855.settlement.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FramingTest {

  private static final int MAX = 150;

  @Test
  @DisplayName("F1 쓰면 길이 4바이트 뒤에 전문이 붙는다")
  void writesLengthPrefix() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    Framing.write(out, ascii("A".repeat(150)));

    assertThat(out.toByteArray()).isEqualTo(ascii("0150" + "A".repeat(150)));
  }

  @Test
  @DisplayName("F2 한 바이트씩 나뉘어 와도 전문 하나를 온전히 읽는다 (TCP 조각)")
  void readsFragmentedFrame() throws IOException {
    InputStream in = new OneByteAtTimeInputStream(ascii("0150" + "B".repeat(150)));

    assertThat(Framing.read(in, MAX)).isEqualTo(ascii("B".repeat(150)));
  }

  @Test
  @DisplayName("F3 전문 두 개가 붙어 와도 경계대로 나눠 읽는다")
  void readsBackToBackFrames() throws IOException {
    InputStream in = new ByteArrayInputStream(ascii("0003ABC" + "0002DE"));

    assertThat(Framing.read(in, 3)).isEqualTo(ascii("ABC"));
    assertThat(Framing.read(in, 2)).isEqualTo(ascii("DE"));
  }

  @Test
  @DisplayName("F4 아무것도 받기 전에 닫히면 EOFException")
  void eofBeforeHeader() {
    assertThatThrownBy(() -> Framing.read(new ByteArrayInputStream(new byte[0]), MAX))
      .isInstanceOf(EOFException.class)
      .hasMessageContaining("0/4바이트");
  }

  @Test
  @DisplayName("F5 전문 도중에 닫히면 EOFException")
  void eofInsideBody() {
    InputStream in = new ByteArrayInputStream(ascii("0150" + "C".repeat(100)));

    assertThatThrownBy(() -> Framing.read(in, MAX))
      .isInstanceOf(EOFException.class)
      .hasMessageContaining("100/150");
  }

  @Test
  @DisplayName("F6 길이 필드가 숫자가 아니면 CodecException")
  void rejectsNonNumericLength() {
    InputStream in = new ByteArrayInputStream(ascii("01A0" + "D".repeat(150)));

    assertThatThrownBy(() -> Framing.read(in, MAX))
      .isInstanceOf(CodecException.class);
  }

  @Test
  @DisplayName("F7 길이가 최대보다 크면 읽기 전에 거부한다")
  void rejectsTooLongLength() {
    InputStream in = new ByteArrayInputStream(ascii("9999"));

    assertThatThrownBy(() -> Framing.read(in, MAX))
      .isInstanceOf(CodecException.class);
  }

  @Test
  @DisplayName("F8 4자리로 표현할 수 없는 전문은 보내지 않는다")
  void rejectsBodyOver9999() {
    assertThatThrownBy(() -> Framing.write(new ByteArrayOutputStream(), new byte[10_000]))
      .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }

  private static final class OneByteAtTimeInputStream extends InputStream {

    private final ByteArrayInputStream delegate;

    OneByteAtTimeInputStream(byte[] data) {
      this.delegate = new ByteArrayInputStream(data);
    }

    @Override
    public int read() {
      return delegate.read();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(byte[] b, int off, int len) {
      return delegate.read(b, off, Math.min(len, 1));
    }
  }
}
