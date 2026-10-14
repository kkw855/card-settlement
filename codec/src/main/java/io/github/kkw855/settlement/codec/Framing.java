package io.github.kkw855.settlement.codec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TCP 프레이밍: {@code [길이 4바이트 숫자][전문 N바이트]} (docs/02-message-spec.md 1장).
 *
 * <p>TCP는 바이트 스트림이라 메시지 경계가 없다. 상대가 154바이트를 한 번에 보내도 받는 쪽에서는
 * 100바이트 + 54바이트로 나뉘어 올 수 있다. 그래서 길이를 먼저 읽고, 정확히 그만큼 모일 때까지 읽는다.
 */
public final class Framing {

  public static final int LENGTH_FIELD_SIZE = 4;
  private static final int MAX_BODY_LENGTH = 9999; // 길이 필드 4자리의 한도

  private Framing() {}

  public static void write(OutputStream out, byte[] body) throws IOException {
    if (body.length == 0 || body.length > MAX_BODY_LENGTH) {
      throw new IllegalArgumentException("전문 길이는 1~%d바이트여야 한다: %d".formatted(MAX_BODY_LENGTH, body.length));
    }
    out.write(FieldCodec.encodeNumeric(body.length, LENGTH_FIELD_SIZE));
    out.write(body);
    out.flush();
  }

  /**
   * 전문 하나를 읽는다. 소켓 읽기 타임아웃이 걸려 있으면 {@link java.net.SocketTimeoutException}이 그대로 올라간다.
   *
   * @param maxLength 받을 수 있는 최대 전문 길이. 깨진 길이 필드(예: 9999)로 큰 배열을 만들지 않게 막는다.
   * @throws EOFException 전문을 다 받기 전에 상대가 연결을 닫았다
   * @throws CodecException 길이 필드가 숫자가 아니거나 범위를 벗어났다
   */
  public static byte[] read(InputStream in, int maxLength) throws IOException {
    byte[] header = in.readNBytes(LENGTH_FIELD_SIZE); // read()와 달리 다 모일 때까지(또는 EOF까지) 반복해서 읽는다
    if (header.length < LENGTH_FIELD_SIZE) {
      throw new EOFException("길이 필드를 다 받기 전에 연결이 닫혔다 (%d/%d바이트)".formatted(header.length, LENGTH_FIELD_SIZE));
    }

    int length = Integer.parseInt(FieldCodec.decodeNumeric(header, 0, LENGTH_FIELD_SIZE));
    if (length == 0 || length > maxLength) {
      throw new CodecException("전문 길이가 범위를 벗어났다: %d (최대 %d)".formatted(length, maxLength));
    }

    byte[] body = in.readNBytes(length);
    if (body.length < length) {
      throw new EOFException("전문을 다 받기 전에 연결이 닫혔다 (%d/%d)".formatted(body.length, length));
    }
    return body;
  }
}
