package io.github.kkw855.settlement.online.card;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;

import io.github.kkw855.settlement.codec.CodecException;
import io.github.kkw855.settlement.codec.MessageReader;
import io.github.kkw855.settlement.online.card.CardCompanyClient.Outcome;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 0100 승인 요청을 만들어 보내고, 0110 응답을 해석한다.
 *
 * <p>응답이 와도 믿을 수 없으면 {@link Result.Unknown}이다. 전문구분코드가 0110이 아니거나, 거래고유번호가 내가 보낸 것과
 * 다르거나, 필드가 깨져 있으면 카드사가 무엇을 처리했는지 알 수 없다. 이런 응답으로 승인을 기록하지 않는다.
 */
public final class ApprovalGateway {

  public sealed interface Result {

    record Approved(String approvalNo, LocalDateTime approvedAt) implements Result {}

    /** 카드사가 거절했다 (1001 한도 초과 등). */
    record Declined(String responseCode) implements Result {}

    /** 연결이 안 돼서 보내지 못했다. 카드사 쪽 결과는 "없음"으로 확정된다. */
    record NotSent() implements Result {}

    /** 보냈지만 결과를 확인하지 못했다. 망취소로 정리해야 한다. */
    record Unknown(String reason) implements Result {}
  }

  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final CardCompanyClient client;
  private final MessageLogger messageLogger;
  private final Clock clock;

  public ApprovalGateway(CardCompanyClient client, MessageLogger messageLogger, Clock clock) {
    this.client = client;
    this.messageLogger = messageLogger;
    this.clock = clock;
  }

  public Result approve(ApprovalRequest request) {
    byte[] message = APPROVAL_REQUEST.writer()
      .set(MSG_TYPE, "0100")
      .set(TXN_ID, request.txnId())
      .set(SENT_AT, LocalDateTime.now(clock).format(TIMESTAMP))
      .set(MERCHANT_NO, request.merchantNo())
      .set(CARD_NO, request.card().digits())
      .set(EXPIRY, request.expiry())
      .set(INSTALLMENT, request.installmentMonths())
      .set(AMOUNT, request.amount())
      .set(MERCHANT_NAME, request.merchantName())
      .toBytes();

    messageLogger.sent(request.txnId(), "0100", message);
    long start = System.nanoTime();
    Outcome outcome = client.send(message);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    return switch (outcome) {
      //noinspection unused
      case Outcome.NotSent notSent -> new Result.NotSent();
      case Outcome.Unknown(Exception cause) -> new Result.Unknown(cause.getClass().getSimpleName());
      case Outcome.Received(byte[] response) -> interpret(request.txnId(), response, elapsedMs);
    };
  }

  private Result interpret(String txnId, byte[] response, long elapsedMs) {
    try {
      MessageReader reader = APPROVAL_RESPONSE.reader(response);
      String responseCode = reader.getText(RESPONSE_CODE);
      messageLogger.received(txnId, reader.getDigits(MSG_TYPE), response, responseCode, elapsedMs);

      if (!"0110".equals(reader.getDigits(MSG_TYPE)) || !txnId.equals(reader.getText(TXN_ID))) {
        return new Result.Unknown("응답 전문이 요청과 맞지 않는다");
      }
      if (!"0000".equals(responseCode)) {
        return new Result.Declined(responseCode);
      }
      return new Result.Approved(reader.getText(APPROVAL_NO),
        LocalDateTime.parse(reader.getDigits(APPROVED_AT), TIMESTAMP));
    } catch (CodecException | DateTimeParseException e) {
      return new Result.Unknown("응답 전문을 해석할 수 없다: " + e.getClass().getSimpleName());
    }
  }
}
