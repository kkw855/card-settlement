package io.github.kkw855.settlement.simulator;

import io.github.kkw855.settlement.codec.MessageReader;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.kkw855.settlement.simulator.MessageLayouts.*;

/**
 * 가상 카드사의 업무 로직. 요청 전문 하나를 받아 응답 전문 하나를 만든다.
 *
 * <p>네트워크를 모르게 분리해서, 소켓 없이 단위 테스트할 수 있게 했다.
 * 응답을 늦게 보내야 하는 경우(시나리오 009 등)도 직접 기다리지 않고 {@link Reply#delay()}로 알려 준다.
 */
final class CardCompany {

  /** 보낼 응답과, 보내기 전에 기다릴 시간. */
  record Reply(byte[] body, Duration delay) {}

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyMM");

  private final ApprovalLedger ledger;
  private final Clock clock;
  private final Duration responseDelay;
  private final AtomicLong approvalSequence = new AtomicLong(30_000_000);

  CardCompany(ApprovalLedger ledger, Clock clock, Duration responseDelay) {
    this.ledger = ledger;
    this.clock = clock;
    this.responseDelay = responseDelay;
  }

  Reply handle(byte[] request) {
    String msgType = REQUEST_0100.reader(request).getDigits(MSG_TYPE);
    //noinspection SwitchStatementWithTooFewBranches
    return switch (msgType) {
      case "0100" -> approve(REQUEST_0100.reader(request));
      default -> throw new IllegalArgumentException("지원하지 않는 전문: " + msgType);
    };
  }

  private Reply approve(MessageReader request) {
    String txnId = request.getText(TXN_ID);
    String cardNo = request.getDigits(CARD_NO);
    long amount = request.getLong(AMOUNT);
    Scenario scenario = Scenario.of(amount);
    LocalDateTime now = LocalDateTime.now(clock);

    ResponseCode code = decide(cardNo, request.getDigits(EXPIRY), scenario, now);
    String approvalNo = null;
    if (code == ResponseCode.APPROVED) {
      approvalNo = String.valueOf(approvalSequence.incrementAndGet());
      ledger.record(new ApprovalLedger.Approval(txnId, approvalNo, request.getText(MERCHANT_NO), mask(cardNo), amount, now, scenario));
    }

    byte[] body = RESPONSE_0110.writer()
      .set(MSG_TYPE, "0110")
      .set(TXN_ID, txnId)
      .set(SENT_AT, now.format(TIMESTAMP_FORMAT))
      .set(RESPONSE_CODE, code.code)
      .set(APPROVAL_NO, approvalNo)
      .set(APPROVED_AT, now.format(TIMESTAMP_FORMAT))
      .set(RESPONSE_MESSAGE, code.message)
      .toBytes();
    Duration delay = scenario.delaysResponse() ? responseDelay : Duration.ZERO;
    return new Reply(body, delay);
  }

  private static ResponseCode decide(String cardNo, String expiry, Scenario scenario, LocalDateTime now) {
    if (!Luhn.isValid(cardNo)) {
      return ResponseCode.INVALID_CARD;
    }
    if (isExpired(expiry, now)) {
      return ResponseCode.INVALID_EXPIRY;
    }
    if (scenario == Scenario.LIMIT_EXCEEDED) {
      return ResponseCode.LIMIT_EXCEEDED;
    }
    return ResponseCode.APPROVED;
  }

  /**
   * 유효기간 yyMM. 그달 말일까지 쓸 수 있다. 형식이 틀려도(예: 13월) 유효기간 오류로 본다.
   */
  private static boolean isExpired(String expiry, LocalDateTime now) {
    try {
      return YearMonth.parse(expiry, EXPIRY_FORMAT).isBefore(YearMonth.from(now));
    } catch (DateTimeParseException e) {
      return true;
    }
  }

  private static String mask(String cardNo) {
    return cardNo.substring(0, 6) + "******" + cardNo.substring(12);
  }
}
