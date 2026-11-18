package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.codec.MessageReader;
import io.github.kkw855.settlement.core.message.MessageLog;
import io.github.kkw855.settlement.core.message.MessageLogMapper;
import io.github.kkw855.settlement.online.card.CardCompanyClient.Outcome;
import io.github.kkw855.settlement.online.card.NetCancelGateway.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.github.kkw855.settlement.online.card.CardMessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.Mockito.*;

/**
 * 0420을 바르게 만드는지, 0430을 언제 믿는지 확인한다.
 *
 * <p>DB 없이 돈다. 카드사 클라이언트는 Mockito로 바꾸고, 전문 로그는 메모리 목록에 모은다.
 * 결제 상태까지 이어지는 흐름은 2-7c에서 ApprovalServiceTest가 실제 Oracle로 확인한다.
 */
class NetCancelGatewayTest {

  private static final String TXN = "P2026111700000000001";
  private static final String OTHER_TXN = "P2026111700000000002";
  private static final Clock CLOCK = Clock.fixed(
    Instant.parse("2026-11-17T01:30:00Z"),
    ZoneId.of("Asia/Seoul")
  );
  private static final NetCancelRequest REQUEST = new NetCancelRequest(TXN, "0100",
    "M000000001", 10_009);

  private final CardCompanyClient client = mock(CardCompanyClient.class);
  private final MessageLogMapper messageLogMapper = mock(MessageLogMapper.class);
  private final List<MessageLog> logs = new ArrayList<>();
  private final NetCancelGateway gateway = new NetCancelGateway(client,
    new MessageLogger(messageLogMapper), CLOCK);

  @BeforeEach
  void collectLogs() {
    doAnswer(invocation ->
      logs.add(invocation.getArgument(0))).when(messageLogMapper).insert(any());
  }

  @Test
  @DisplayName("N1 0420은 원거래를 가리킨다. 공통부의 거래고유번호도 원거래고유번호다")
  void buildRequest() {
    when(client.send(any())).thenReturn(new Outcome.Received(response("0430", TXN,
      "0000", TXN, "01")));

    gateway.netCancel(REQUEST);

    ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
    verify(client).send(sent.capture()); // 한 번만 보낸다. 재시도는 부르는 쪽이 한다
    MessageReader request = NET_CANCEL_REQUEST.reader(sent.getValue());
    assertThat(request.getDigits(MSG_TYPE)).isEqualTo("0420");
    assertThat(request.getText(TXN_ID)).isEqualTo(TXN);
    assertThat(request.getDigits(SENT_AT)).isEqualTo("20261117103000");
    assertThat(request.getText(ORIGINAL_TXN_ID)).isEqualTo(TXN);
    assertThat(request.getDigits(ORIGINAL_MSG_TYPE)).isEqualTo("0100");
    assertThat(request.getText(NET_CANCEL_MERCHANT_NO)).isEqualTo("M000000001");
    assertThat(request.getLong(NET_CANCEL_AMOUNT)).isEqualTo(10_009);
  }

  @ParameterizedTest(name = "처리구분 {0}")
  @ValueSource(strings = {"01", "02"})
  @DisplayName("N2 처리구분이 01이든 02든 Voided. SEND·RECV 로그는 원거래 번호로 남는다")
  void voided(String result) {
    when(client.send(any())).thenReturn(new Outcome.Received(response("0430", TXN,
      "0000", TXN, result)));

    assertThat(gateway.netCancel(REQUEST)).isInstanceOf(Result.Voided.class);
    assertThat(logs)
      .extracting(MessageLog::txnId, MessageLog::msgType, MessageLog::direction, MessageLog::responseCode)
      .containsExactly(tuple(TXN, "0420", "SEND", null), tuple(TXN, "0430", "RECV", "0000"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("notReceived")
  @DisplayName("N3 보내지 못했거나 응답을 받지 못하면 Failed. SEND 로그만 남는다")
  void failedWithoutResponse(Outcome outcome) {
    when(client.send(any())).thenReturn(outcome);

    assertThat(gateway.netCancel(REQUEST)).isInstanceOf(Result.Failed.class);
    assertThat(logs).extracting(MessageLog::direction).containsExactly("SEND");
  }

  static Stream<Named<Outcome>> notReceived() {
    return Stream.of(
      named("연결 실패 (NotSent)", new Outcome.NotSent(new ConnectException("Connection refused"))),
      named("응답 시간 초과 (Unknown)", new Outcome.Unknown(new SocketTimeoutException("Read timed out")))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("untrustedResponses")
  @DisplayName("N4 0430을 믿을 수 없으면 Failed. 받은 전문은 RECV 로그로 남는다")
  void failedWhenResponseIsUntrusted(byte[] response) {
    when(client.send(any())).thenReturn(new Outcome.Received(response));

    assertThat(gateway.netCancel(REQUEST)).isInstanceOf(Result.Failed.class);
    assertThat(logs).extracting(MessageLog::direction).containsExactly("SEND", "RECV");
  }

  /**
   * 정상 0430에서 값 하나씩만 바꾼다. 그래야 검사 하나가 빠졌을 때 그 줄이 실패한다.
   */
  static Stream<Named<byte[]>> untrustedResponses() {
    return Stream.of(
      named("전문구분코드가 0430이 아니다", response("0110", TXN,
        "0000", TXN, "01")),
      named("공통부 거래고유번호가 다르다", response("0430", OTHER_TXN,
        "0000", TXN, "01")),
      named("원거래고유번호가 다르다", response("0430", TXN,
        "0000", OTHER_TXN, "01")),
      named("응답코드가 0000이 아니다", response("0430", TXN,
        "9999", TXN, "01")),
      named("처리구분이 01·02가 아니다", response("0430", TXN,
        "0000", TXN, "03")),
      named("처리구분이 비어 있다", response("0430", TXN,
        "0000", TXN, ""))
    );
  }

  @Test
  @DisplayName("N5 전문구분코드가 숫자가 아니어서 읽지 못하면 Failed. RECV 로그는 남지 않는다 (알려진 한계)")
  void failedWhenResponseIsUnreadable() {
    byte[] broken = response("0430", TXN, "0000", TXN, "01");
    broken[0] = 'X';
    when(client.send(any())).thenReturn(new Outcome.Received(broken));

    assertThat(gateway.netCancel(REQUEST)).isInstanceOf(Result.Failed.class);
    assertThat(logs).extracting(MessageLog::direction).containsExactly("SEND");
  }

  private static byte[] response(String msgType, String txnId, String responseCode,
                                 String originalTxnId, String result) {
    return NET_CANCEL_RESPONSE.writer()
      .set(MSG_TYPE, msgType)
      .set(TXN_ID, txnId)
      .set(SENT_AT, "20261117103001")
      .set(RESPONSE_CODE, responseCode)
      .set(ORIGINAL_TXN_ID, originalTxnId)
      .set(NET_CANCEL_RESULT, result)
      .toBytes();
  }
}
