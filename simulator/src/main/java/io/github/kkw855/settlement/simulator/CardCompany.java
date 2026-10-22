package io.github.kkw855.settlement.simulator;

import io.github.kkw855.settlement.codec.FieldCodec;
import io.github.kkw855.settlement.codec.Layout;
import io.github.kkw855.settlement.codec.MessageReader;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.github.kkw855.settlement.simulator.ApprovalLedger.Status;
import io.github.kkw855.settlement.simulator.ApprovalLedger.Approval;
import io.github.kkw855.settlement.simulator.ApprovalLedger.Cancel;

import static io.github.kkw855.settlement.simulator.MessageLayouts.*;

/**
 * 가상 카드사의 업무 로직. 요청 전문 하나를 받아 응답 방법({@link Reply})을 정한다.
 *
 * <p>네트워크를 모르게 분리해서, 소켓 없이 단위 테스트할 수 있게 했다.
 *
 * <p>{@link #handle}은 {@code synchronized}다. 원장을 읽고 확인하고 저장하는 사이에 다른 요청이 끼어들면
 * 같은 거래를 두 번 취소하는 일이 생길 수 있다. 잠근 동안에는 잠들거나 I/O를 하지 않는다.
 * 응답 지연은 {@link Reply}로 알려 주고, 서버가 잠금 밖에서 기다린다.
 */
final class CardCompany {

  /** 요청에 어떻게 답할지 */
  sealed interface Reply {

    /** {@code delay}만큼 기다렸다가 {@code body}를 보낸다 */
    record Respond(byte[] body, Duration delay) implements Reply {}

    /** 아무것도 보내지 않고 {@code duration}만큼 기다렸다가 연결을 끊는다 (시나리오 119의 망취소). */
    record Silent(Duration duration) implements Reply {}
  }

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyMM");
  private static final String NET_CANCEL_VOIDED = "01";     // 원거래를 취소함
  private static final String NET_CANCEL_NOT_FOUND = "02";  // 원거래가 없었음

  private final ApprovalLedger ledger;
  private final Clock clock;
  private final Duration responseDelay;
  private final AtomicLong approvalSequence = new AtomicLong(30_000_000);

  /**
   * @param responseDelay 지연 시나리오에서 응답을 늦출 시간. 운영은 5초, 테스트는 짧게 준다.
   */
  CardCompany(ApprovalLedger ledger, Clock clock, Duration responseDelay) {
    this.ledger = ledger;
    this.clock = clock;
    this.responseDelay = responseDelay;
  }

  synchronized Reply handle(byte[] request) {
    String msgType = FieldCodec.decodeNumeric(request, MSG_TYPE.offset(), MSG_TYPE.length());
    LocalDateTime now = LocalDateTime.now(clock);
    return switch (msgType) {
      case "0100" -> approve(REQUEST_0100.reader(request), now);
      case "0400" -> cancel(REQUEST_0400.reader(request), now);
      case "0420" -> netCancel(REQUEST_0420.reader(request), now);
      default -> throw new IllegalArgumentException("지원하지 않는 전문: " + msgType);
    };
  }

  // ---------------------------------------------------------------- 0100 승인

  private Reply approve(MessageReader request, LocalDateTime now) {
    String txnId = request.getText(TXN_ID);
    String cardNo = request.getDigits(CARD_NO);
    long amount = request.getLong(AMOUNT);
    Scenario scenario = Scenario.of(amount);

    ResponseCode code = decideApproval(cardNo, request.getDigits(EXPIRY), scenario, now);
    String approvalNo = null;
    if (code == ResponseCode.OK) {
      approvalNo = nextApprovalNo();
      ledger.save(new Approval(txnId, approvalNo, request.getText(MERCHANT_NO), mask(cardNo),
        amount, now, scenario, Status.APPROVED, null));
    }

    Duration delay = scenario.delaysResponse() ? responseDelay : Duration.ZERO;
    return new Reply.Respond(approvalResponse(RESPONSE_0110, "0110", txnId, code, approvalNo, now), delay);
  }

  private static ResponseCode decideApproval(String cardNo, String expiry, Scenario scenario, LocalDateTime now) {
    if (!Luhn.isValid(cardNo)) {
      return ResponseCode.INVALID_CARD;
    }
    if (isExpired(expiry, now)) {
      return ResponseCode.INVALID_EXPIRY;
    }
    if (scenario == Scenario.LIMIT_EXCEEDED) {
      return ResponseCode.LIMIT_EXCEEDED;
    }
    return ResponseCode.OK;
  }

  // ---------------------------------------------------------------- 0400 취소

  /** 원거래는 거래고유번호로 찾고, 승인번호·승인일자·가맹점번호가 모두 맞아야 같은 거래로 본다. */
  private Reply cancel(MessageReader request, LocalDateTime now) {
    String cancelTxnId = request.getText(TXN_ID);
    Optional<Approval> original = ledger.find(request.getText(ORIGINAL_TXN_ID))
      .filter(approval -> approval.status() != Status.VOIDED)
      .filter(approval -> approval.approvalNo().equals(request.getText(ORIGINAL_APPROVAL_NO)))
      .filter(approval -> approval.approvedAt().format(DATE_FORMAT).equals(request.getDigits(ORIGINAL_APPROVED_DATE)))
      .filter(approval -> approval.merchantNo().equals(request.getText(CANCEL_MERCHANT_NO)));

    ResponseCode code = decideCancel(original, request.getLong(CANCEL_AMOUNT));
    String cancelApprovalNo = null;
    if (code == ResponseCode.OK) {
      cancelApprovalNo = nextApprovalNo();
      ledger.save(original.orElseThrow().cancelled(new Cancel(cancelTxnId, cancelApprovalNo, now)));
    }
    return new Reply.Respond(approvalResponse(RESPONSE_0410, "0410", cancelTxnId, code, cancelApprovalNo, now), Duration.ZERO);
  }

  private static ResponseCode decideCancel(Optional<Approval> original, long cancelAmount) {
    if (original.isEmpty()) {
      return ResponseCode.NO_ORIGINAL;
    }
    if (original.get().status() == Status.CANCELLED) {
      return ResponseCode.ALREADY_CANCELLED;
    }
    if (original.get().amount() != cancelAmount) {
      return ResponseCode.SYSTEM_ERROR; // v1은 전체 취소만 받는다
    }
    return ResponseCode.OK;
  }

  // ---------------------------------------------------------------- 0420 망취소

  /**
   * 원거래를 무효로 만든다. 결과는 "취소함(01)"과 "원거래가 없었음(02)" 둘 다 정상이다.
   * PG가 응답을 못 받아 다시 보내도 같은 결과가 나와야 한다 (재시도해도 안전).
   */
  private Reply netCancel(MessageReader request, LocalDateTime now) {
    String originalTxnId = request.getText(ORIGINAL_TXN_ID);
    String originalMsgType = request.getDigits(ORIGINAL_MSG_TYPE);
    return switch (originalMsgType) {
      case "0100" -> voidApproval(request, originalTxnId, now);
      case "0400" -> revertCancel(request, originalTxnId, now);
      default -> throw new IllegalArgumentException("망취소할 수 없는 전문: " + originalMsgType);
    };
  }

  private Reply voidApproval(MessageReader request, String txnId, LocalDateTime now) {
    Optional<Approval> found = ledger.find(txnId);
    if (found.isEmpty()) {
      return netCancelResponse(request, NET_CANCEL_NOT_FOUND, now); // 0100이 카드사에 닿지 않았다
    }
    Approval approval = found.get();
    return switch (approval.scenario()) {
      case DELAYED_IGNORE_NET_CANCEL -> new Reply.Silent(responseDelay);
      case DELAYED_FAKE_NET_CANCEL -> netCancelResponse(request, NET_CANCEL_VOIDED, now); // 취소했다고 답하지만 원장은 그대로
      default -> {
        if (approval.status() == Status.APPROVED) {
          ledger.save(approval.voided());
        }
        yield netCancelResponse(request, NET_CANCEL_VOIDED, now);
      }
    };
  }

  private Reply revertCancel(MessageReader request, String cancelTxnId, LocalDateTime now) {
    Optional<Approval> found = ledger.findByCancelTxnId(cancelTxnId);
    found.ifPresent(approval -> ledger.save(approval.cancelReverted()));
    return netCancelResponse(request, found.isPresent() ? NET_CANCEL_VOIDED : NET_CANCEL_NOT_FOUND, now);
  }

  // ---------------------------------------------------------------- 응답 만들기

  /** 0110과 0410은 모양이 같다. */
  private static byte[] approvalResponse(Layout layout, String msgType, String txnId, ResponseCode code, String approvalNo,
                                         LocalDateTime now) {
    return layout.writer()
      .set(MSG_TYPE, msgType)
      .set(TXN_ID, txnId)
      .set(SENT_AT, now.format(TIMESTAMP_FORMAT))
      .set(RESPONSE_CODE, code.code)
      .set(APPROVAL_NO, approvalNo)
      .set(APPROVED_AT, now.format(TIMESTAMP_FORMAT))
      .set(RESPONSE_MESSAGE, code.message)
      .toBytes();
  }

  private static Reply netCancelResponse(MessageReader request, String result, LocalDateTime now) {
    byte[] body = RESPONSE_0430.writer()
      .set(MSG_TYPE, "0430")
      .set(TXN_ID, request.getText(TXN_ID))
      .set(SENT_AT, now.format(TIMESTAMP_FORMAT))
      .set(RESPONSE_CODE, ResponseCode.OK.code)
      .set(ORIGINAL_TXN_ID, request.getText(ORIGINAL_TXN_ID))
      .set(NET_CANCEL_RESULT, result)
      .toBytes();
    return new Reply.Respond(body, Duration.ZERO);
  }

  // ---------------------------------------------------------------- 공통

  private String nextApprovalNo() {
    return String.valueOf(approvalSequence.incrementAndGet());
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
