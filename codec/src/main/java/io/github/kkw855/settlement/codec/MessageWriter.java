package io.github.kkw855.settlement.codec;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 레이아웃에 맞춰 전문 바이트를 만든다.
 *
 * <p>채우지 않은 AN 필드(응답코드, 예비)는 공백이 된다. 채우지 않은 N 필드는 예외다.
 * 금액 같은 숫자를 빠뜨린 전문이 0으로 채워져 나가면 안 되기 때문이다.
 */
public final class MessageWriter {

  private final Layout layout;
  private final Map<Field, byte[]> values = new HashMap<>();

  MessageWriter(Layout layout) {
    this.layout = layout;
  }

  public MessageWriter set(Field.N field, long value) {
    return put(field, FieldCodec.encodeNumeric(value, field.length()));
  }

  public MessageWriter set(Field.N field, String digits) {
    return put(field, FieldCodec.encodeNumeric(digits, field.length()));
  }

  public MessageWriter set(Field.AN field, String value) {
    return put(field, FieldCodec.encodeAlphanumeric(value, field.length()));
  }

  public byte[] toBytes() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Field field: layout.fields()) {
      byte[] encoded = values.get(field);
      if (encoded == null) {
        encoded = switch (field) {
          case Field.AN an -> FieldCodec.encodeAlphanumeric(null, an.length());
          case Field.N n -> throw new CodecException(
            "%s: N 필드 %s의 값이 없다".formatted(layout.name(), n.name()));
        };
      }
      out.writeBytes(encoded);
    }
    return out.toByteArray();
  }

  private MessageWriter put(Field field, byte[] encoded) {
    layout.requireField(field);
    values.put(field, encoded);
    return this;
  }
}
