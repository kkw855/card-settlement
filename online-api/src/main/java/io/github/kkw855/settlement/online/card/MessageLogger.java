package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.codec.FieldCodec;
import io.github.kkw855.settlement.core.message.MessageLog;
import io.github.kkw855.settlement.core.message.MessageLogMapper;

import java.util.Arrays;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.CARD_NO;

/**
 * 송수신 전문을 message_log에 남긴다. 0100의 카드번호는 가운데 6자리를 가린 뒤에 저장한다 (I10, ADR-0005).
 *
 * <p>카드번호가 들어 있는 전문은 0100뿐이다. 응답과 취소·망취소 전문에는 카드번호가 없다.
 */
public final class MessageLogger {

  private static final int MASK_FROM = CARD_NO.offset() + 6;  // 앞 6자리는 남긴다
  private static final int MASK_TO = CARD_NO.offset() + 12;   // 뒤 4자리는 남긴다

  private final MessageLogMapper mapper;

  public MessageLogger(MessageLogMapper mapper) {
    this.mapper = mapper;
  }

  public void sent(String txnId, String msgType, byte[] payload) {
    mapper.insert(new MessageLog(txnId, msgType, "SEND", mask(msgType, payload),
      null, null));
  }

  public void received(String txnId, String msgType, byte[] payload, String responseCode, long elapsedMs) {
    mapper.insert(new MessageLog(txnId, msgType, "RECV", mask(msgType, payload),
      responseCode, elapsedMs));
  }

  static String mask(String msgType, byte[] payload) {
    byte[] copy = Arrays.copyOf(payload, payload.length); // 보낼 원본은 건드리지 않는다
    if ("0100".equals(msgType)) {
      Arrays.fill(copy, MASK_FROM, MASK_TO, (byte) '*');
    }
    return new String(copy, FieldCodec.MS949);
  }
}
