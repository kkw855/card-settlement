package io.github.kkw855.settlement.simulator;

import static io.github.kkw855.settlement.simulator.MessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kkw855.settlement.codec.MessageReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

class CardCompanyTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T01:30:00Z"), ZoneId.of("Asia/Seoul"));
  private static final Duration DELAY = Duration.ofSeconds(5);

  private final ApprovalLedger ledger = new ApprovalLedger();
  private final CardCompany cardCompany = new CardCompany(ledger, CLOCK, DELAY);

  @Test
  @DisplayName("C1 정상 승인: 0000, 승인번호 발급, 기록에는 마스킹한 카드번호")
  void approves() {
    CardCompany.Reply reply = cardCompany.handle(Requests.approval("P2026100800000000001", 10_000));
    MessageReader response = RESPONSE_0110.reader(reply.body());

    assertThat(response.getDigits(MSG_TYPE)).isEqualTo("0110");
    assertThat(response.getText(TXN_ID)).isEqualTo("P2026100800000000001");
    assertThat(response.getText(RESPONSE_CODE)).isEqualTo("0000");
    assertThat(response.getText(APPROVAL_NO)).isEqualTo("30000001");
    assertThat(response.getDigits(APPROVED_AT)).isEqualTo("20261008103000"); // KST
    assertThat(reply.delay()).isZero();
    assertThat(ledger.find("P2026100800000000001")).hasValueSatisfying(approval ->
      assertThat(approval.cardMasked()).isEqualTo("411111******1111"));
  }

  @Test
  @DisplayName("C2 끝자리 001: 한도 초과로 거절, 승인번호 없음, 기록 없음")
  void declinesLimitExceeded() {
    CardCompany.Reply reply = cardCompany.handle(Requests.approval("P2026100800000000002", 10_001));
    MessageReader response = RESPONSE_0110.reader(reply.body());

    assertThat(response.getText(RESPONSE_CODE)).isEqualTo("1001");
    assertThat(response.getText(APPROVAL_NO)).isEmpty();
    assertThat(ledger.all()).isEmpty();
  }

  @Test
  @DisplayName("C3 Luhn 검증에 실패하면 1003")
  void rejectsInvalidCardNumber() {
    byte[] request = Requests.approval("P2026100800000000003", "4111111111111112", "2812", 10_000);

    assertThat(responseCode(cardCompany.handle(request))).isEqualTo("1003");
  }

  @Test
  @DisplayName("C4 유효기간: 지난달은 1002, 이번 달은 쓸 수 있다")
  void checkExpiry() {
    byte[] lastMonth = Requests.approval("P2026100800000000004", "4111111111111111", "2609", 10_000);
    byte[] thisMonth = Requests.approval("P2026100800000000005", "4111111111111111", "2610", 10_000);

    assertThat(responseCode(cardCompany.handle(lastMonth))).isEqualTo("1002");
    assertThat(responseCode(cardCompany.handle(thisMonth))).isEqualTo("0000");
  }

  @Test
  @DisplayName("C5 끝자리 009: 카드사는 승인하고 기록까지 남기지만 응답을 늦게 보낸다")
  void approveButDelayResponse() {
    CardCompany.Reply reply = cardCompany.handle(Requests.approval("P2026100800000000006", 10_009));

    assertThat(responseCode(reply)).isEqualTo("0000");
    assertThat(reply.delay()).isEqualTo(DELAY);
    assertThat(ledger.find("P2026100800000000006")).isPresent(); // PG는 결과를 모르지만 카드사 쪽은 승인됨
  }

  private String responseCode(CardCompany.Reply reply) {
    return RESPONSE_0110.reader(reply.body()).getText(RESPONSE_CODE);
  }
}
