package io.github.kkw855.settlement.simulator;

import io.github.kkw855.settlement.codec.MessageReader;
import io.github.kkw855.settlement.simulator.ApprovalLedger.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static io.github.kkw855.settlement.simulator.CardCompanyTest.respond;
import static io.github.kkw855.settlement.simulator.MessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 0400 취소와 0420 망취소. */
class CancellationTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T01:30:00Z"), ZoneId.of("Asia/Seoul"));
  private static final String TODAY = "20261008";
  private static final Duration DELAY = Duration.ofSeconds(5);

  private final ApprovalLedger ledger = new ApprovalLedger();
  private final CardCompany cardCompany = new CardCompany(ledger, CLOCK, DELAY);

  @Nested
  @DisplayName("0400 취소")
  class Cancel {

    @Test
    @DisplayName("X1 승인한 거래를 취소하면 0000, 취소 승인번호를 새로 발급한다")
    void cancels() {
      String approvalNo = approve("P2026100800000000001", 10_000);

      MessageReader response = cancel("P2026100800000000011", "P2026100800000000001", approvalNo, 10_000);

      assertThat(response.getDigits(MSG_TYPE)).isEqualTo("0410");
      assertThat(response.getText(TXN_ID)).isEqualTo("P2026100800000000011"); // 취소 자신의 거래고유번호
      assertThat(response.getText(RESPONSE_CODE)).isEqualTo("0000");
      assertThat(response.getText(APPROVAL_NO)).isNotEmpty().isNotEqualTo(approvalNo);
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.CANCELLED);
    }

    @Test
    @DisplayName("X2 이미 취소한 거래를 다시 취소하면 2002")
    void rejectsSecondCancel() {
      String approvalNo = approve("P2026100800000000001", 10_000);
      cancel("P2026100800000000011", "P2026100800000000001", approvalNo, 10_000);

      MessageReader response = cancel("P2026100800000000012", "P2026100800000000001", approvalNo, 10_000);

      assertThat(response.getText(RESPONSE_CODE)).isEqualTo("2002");
    }

    @Test
    @DisplayName("X3 원거래가 없거나 승인번호가 다르면 2001")
    void rejectsUnknownOriginal() {
      String approvalNo = approve("P2026100800000000001", 10_000);

      assertThat(cancel("P2026100800000000011", "P2026100899999999999", approvalNo, 10_000).getText(RESPONSE_CODE))
        .isEqualTo("2001");
      assertThat(cancel("P2026100800000000012", "P2026100800000000001", "99999999", 10_000).getText(RESPONSE_CODE))
        .isEqualTo("2001");
    }

    @Test
    @DisplayName("X4 취소 금액이 원거래와 다르면 9999 (v1은 전체 취소만)")
    void rejectsPartialCancel() {
      String approvalNo = approve("P2026100800000000001", 10_000);

      MessageReader response = cancel("P2026100800000000011", "P2026100800000000001", approvalNo, 50_000);

      assertThat(response.getText(RESPONSE_CODE)).isEqualTo("9999");
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.APPROVED);
    }
  }

  @Nested
  @DisplayName("0420 망취소")
  class NetCancel {

    @Test
    @DisplayName("N1 승인의 망취소: 01(취소함), 원장은 무효(VOIDED)")
    void voidsApproval() {
      approve("P2026100800000000001", 10_009);

      MessageReader response = netCancel("P2026100800000000001", "0100", 10_009);

      assertThat(response.getDigits(MSG_TYPE)).isEqualTo("0430");
      assertThat(response.getText(RESPONSE_CODE)).isEqualTo("0000");
      assertThat(response.getText(NET_CANCEL_RESULT)).isEqualTo("01");
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.VOIDED);
    }

    @Test
    @DisplayName("N2 카드사에 닿지 않은 거래의 망취소: 02(원거래가 없었음)도 정상 응답")
    void notFound() {
      MessageReader response = netCancel("P2026100800000000001", "0100", 10_000);

      assertThat(response.getText(RESPONSE_CODE)).isEqualTo("0000");
      assertThat(response.getText(NET_CANCEL_RESULT)).isEqualTo("02");
    }

    @Test
    @DisplayName("N3 같은 망취소를 다시 보내도 결과가 같다 (재시도해도 안전)")
    void idempotent() {
      approve("P2026100800000000001", 10_009);

      assertThat(netCancel("P2026100800000000001", "0100", 10_009).getText(NET_CANCEL_RESULT))
        .isEqualTo("01");
      assertThat(netCancel("P2026100800000000001", "0100", 10_009).getText(NET_CANCEL_RESULT))
        .isEqualTo("01");
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.VOIDED);
    }

    @Test
    @DisplayName("N4 망취소된 거래는 취소할 수 없다 (2001)")
    void cannotCancelVoided() {
      String approvalNo = approve("P2026100800000000001", 10_009);
      netCancel("P2026100800000000001", "0100", 10_009);

      MessageReader response = cancel("P2026100800000000011", "P2026100800000000001", approvalNo, 10_009);

      assertThat(response.getText(RESPONSE_CODE)).isEqualTo("2001");
    }

    @Test
    @DisplayName("N5 끝자리 109: 01이라고 답하지만 실제로는 취소하지 않는다 -> 다음 날 매입됨")
    void fakeNetCancel() {
      approve("P2026100800000000001", 10_109);

      assertThat(netCancel("P2026100800000000001", "0100", 10_109).getText(NET_CANCEL_RESULT))
        .isEqualTo("01");
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.APPROVED);
    }

    @Test
    @DisplayName("N6 끝자리 119: 망취소에 응답하지 않는다")
    void ignoresNetCancel() {
      approve("P2026100800000000001", 10_119);

      CardCompany.Reply reply =
        cardCompany.handle(Requests.netCancel("P2026100800000000001", "0100", 10_119));

      assertThat(reply).isEqualTo(new CardCompany.Reply.Silent(DELAY));
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.APPROVED);
    }

    @Test
    @DisplayName("N7 취소의 망취소: 취소가 무효가 되어 승인 상태로 돌아가고, 다시 취소할 수 있다")
    void revertsCancel() {
      String approvalNo = approve("P2026100800000000001", 10_000);
      cancel("P2026100800000000011", "P2026100800000000001", approvalNo, 10_000);

      MessageReader response = netCancel("P2026100800000000011", "0400", 10_000);

      assertThat(response.getText(NET_CANCEL_RESULT)).isEqualTo("01");
      assertThat(statusOf("P2026100800000000001")).isEqualTo(Status.APPROVED);
      assertThat(cancel("P2026100800000000012", "P2026100800000000001", approvalNo, 10_000).getText(RESPONSE_CODE))
        .isEqualTo("0000");
    }
  }

  // ---------------------------------------------------------------- 도우미

  /**
   * 승인하고 승인번호를 돌려준다.
   */
  private String approve(String txnId, long amount) {
    byte[] body = respond(cardCompany.handle(Requests.approval(txnId, amount))).body();
    return RESPONSE_0110.reader(body).getText(APPROVAL_NO);
  }

  private MessageReader cancel(String cancelTxnId, String originalTxnId, String approvalNo, long amount) {
    byte[] request = Requests.cancel(cancelTxnId, originalTxnId, approvalNo, TODAY, amount);
    return RESPONSE_0410.reader(respond(cardCompany.handle(request)).body());
  }

  private MessageReader netCancel(String originalTxnId, String originalMsgType, long amount) {
    byte[] request = Requests.netCancel(originalTxnId, originalMsgType, amount);
    return RESPONSE_0430.reader(respond(cardCompany.handle(request)).body());
  }

  private Status statusOf(String txnId) {
    return ledger.find(txnId).orElseThrow().status();
  }
}
