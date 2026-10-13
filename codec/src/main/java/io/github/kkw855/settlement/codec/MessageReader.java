package io.github.kkw855.settlement.codec;

import java.util.Objects;

/** 레이아웃에 맞춰 전문 바이트에서 필드 값을 읽는다. */
public final class MessageReader {

  private final Layout layout;
  private final byte[] message;

  MessageReader(Layout layout, byte[] message) {
    Objects.requireNonNull(message, "message");
    if (message.length != layout.length()) {
      throw new CodecException("%s: 길이가 %d바이트여야 하는데 %d바이트다"
        .formatted(layout.name(), layout.length(), message.length));
    }
    this.layout = layout;
    this.message = message.clone();
  }

  public String getText(Field.AN field) {
    layout.requireField(field);
    return FieldCodec.decodeAlphanumeric(message, field.offset(), field.length());
  }

  public String getDigits(Field.N field) {
    layout.requireField(field);
    return FieldCodec.decodeNumeric(message, field.offset(), field.length());
  }

  public long getLong(Field.N field) {
    return Long.parseLong(getDigits(field));
  }
}
