package io.github.kkw855.settlement.online.card;

import java.time.Clock;
import java.time.LocalDateTime;

import io.github.kkw855.settlement.codec.CodecException;
import io.github.kkw855.settlement.codec.MessageReader;
import io.github.kkw855.settlement.online.card.CardCompanyClient.Outcome;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;

/**
 * 0420 망취소 요청을 만들어 보내고, 0430 응답을 해석한다 (docs/02-message-spec.md 3~4장).
 *
 * <p>한 번만 주고받는다. 실패하면 부르는 쪽이 같은 요청을 다시 보낸다. 카드사는 같은 망취소를 여러 번 받아도
 * 결과가 같으므로 다시 보내도 안전하다.
 *
 * <p>0430의 처리구분은 01(원거래를 취소함)이든 02(원거래가 없었음)든 결과가 같다. 어느 쪽이든 원거래는 무효다.
 */
public final class NetCancelGateway {

  public sealed interface Result {

    /** 카드사 쪽에서 원거래가 무효다 (처리구분 01 또는 02). */
    record Voided() implements Result {}

    /** 원거래가 무효가 됐는지 확인하지 못했다. 다시 보내야 한다. */
    record Failed(String reason) implements Result {}
  }

  private final CardCompanyClient client;
  private final MessageLogger messageLogger;
  private final Clock clock;

  public NetCancelGateway(CardCompanyClient client, MessageLogger messageLogger, Clock clock) {
    this.client = client;
    this.messageLogger = messageLogger;
    this.clock = clock;
  }

  public Result netCancel(NetCancelRequest request) {
    String txnId = request.originalTxnId(); // 공통부에도 원거래고유번호를 쓴다. 로그도 원거래와 같은 번호로 묶인다
    byte[] message = NET_CANCEL_REQUEST.writer()
      .set(MSG_TYPE, "0420")
      .set(TXN_ID, txnId)
      .set(SENT_AT, LocalDateTime.now(clock).format(TIMESTAMP))
      .set(ORIGINAL_TXN_ID, txnId)
      .set(ORIGINAL_MSG_TYPE, request.originalMsgType())
      .set(NET_CANCEL_MERCHANT_NO, request.merchantNo())
      .set(NET_CANCEL_AMOUNT, request.amount())
      .toBytes();

    messageLogger.sent(txnId, "0420", message);
    long start = System.nanoTime();
    Outcome outcome = client.send(message);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    return switch (outcome) {
      case Outcome.NotSent(Exception cause) -> new Result.Failed("보내지 못했다: " +
        cause.getClass().getSimpleName());
      case Outcome.Unknown(Exception cause) -> new Result.Failed("응답을 받지 못했다: " +
        cause.getClass().getSimpleName());
      case Outcome.Received(byte[] response) -> interpret(txnId, response, elapsedMs);
    };
  }

  private Result interpret(String txnId, byte[] response, long elapsedMs) {
    try {
      MessageReader reader = NET_CANCEL_RESPONSE.reader(response);
      String msgType = reader.getDigits(MSG_TYPE);
      String responseCode = reader.getText(RESPONSE_CODE);
      messageLogger.received(txnId, msgType, response, responseCode, elapsedMs);

      if (!"0430".equals(msgType) || !txnId.equals(reader.getText(TXN_ID))
        || !txnId.equals(reader.getText(ORIGINAL_TXN_ID))) {
        return new Result.Failed("응답 전문이 요청과 맞지 않는다");
      }
      if (!"0000".equals(responseCode)) {
        return new Result.Failed("응답코드가 0000이 아니다");
      }
      String result = reader.getText(NET_CANCEL_RESULT);
      if (!"01".equals(result) && !"02".equals(result)) {
        return new Result.Failed("처리구분이 01·02가 아니다");
      }
      return new Result.Voided();
    } catch (CodecException e) {
      return new Result.Failed("응답 전문을 해석할 수 없다: " + e.getClass().getSimpleName());
    }
  }
}
