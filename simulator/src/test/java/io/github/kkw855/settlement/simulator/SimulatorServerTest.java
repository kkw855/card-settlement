package io.github.kkw855.settlement.simulator;

import static io.github.kkw855.settlement.simulator.MessageLayouts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kkw855.settlement.codec.Framing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/** 실제 TCP 소켓으로 주고받는다. 지연 시간은 테스트가 빨리 끝나도록 짧게 준다. */
class SimulatorServerTest {

  private static final Duration DELAY = Duration.ofMillis(500);

  private final ApprovalLedger ledger = new ApprovalLedger();
  private SimulatorServer server;

  @BeforeEach
  void start() throws IOException {
    server = new SimulatorServer(0, new CardCompany(ledger, Clock.systemDefaultZone(), DELAY));
    server.start();
  }

  @AfterEach
  void stop() throws IOException {
    server.close();
  }

  @Test
  @DisplayName("S1 0100을 보내면 0110이 돌아온다")
  void roundTrip() throws IOException {
    byte[] response = send(Requests.approval("P2026100800000000001", 10_000), 1_000);

    assertThat(RESPONSE_0110.reader(response).getDigits(MSG_TYPE)).isEqualTo("0110");
    assertThat(RESPONSE_0110.reader(response).getText(RESPONSE_CODE)).isEqualTo("0000");
  }

  @Test
  @DisplayName("S2 응답이 늦으면 클라이언트는 타임아웃이 나지만, 카드사 쪽은 이미 승인했다 (망취소가 필요한 이유)")
  void timeoutButApproved() {
    assertThatThrownBy(() -> send(Requests.approval("P2026100800000000002", 10_009), 200))
      .isInstanceOf(SocketTimeoutException.class);

    assertThat(ledger.find("P2026100800000000002")).isPresent();
  }

  @Test
  @DisplayName("S3 동시에 20건이 와도 모두 처리하고 승인번호가 겹치지 않는다")
  void handlesConcurrentConnections() {
    try (ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<byte[]>> response = IntStream.rangeClosed(1, 20)
        .mapToObj(i -> clients.submit(() -> send(Requests.approval("P20261008%011d".formatted(i), 10_000), 2_000)))
        .toList();

      List<String> approvalNos = response.stream()
        .map(future -> RESPONSE_0110.reader(join(future)).getText(APPROVAL_NO))
        .toList();

      assertThat(approvalNos).hasSize(20).doesNotHaveDuplicates();
    }
  }

  @Test
  @DisplayName("S4 끝자리 119의 망취소: 서버가 아무것도 보내지 않아 클라이언트는 타임아웃이 난다")
  void silentNetCancel() throws IOException {
    send(Requests.approval("P2026100800000000003", 10_119), 1_000); // 응답이 500ms 늦지만 1초 안에 온다

    assertThatThrownBy(() -> send(Requests.netCancel("P2026100800000000003", "0100", 10_119), 200))
      .isInstanceOf(SocketTimeoutException.class);
  }

  /** 거래마다 연결: 연결 -> 요청 -> 응답 -> 닫기. */
  private byte[] send(byte[] request, int readTimeoutMillis) throws IOException {
    try (Socket socket = new Socket("localhost", server.port())) {
      socket.setSoTimeout(readTimeoutMillis);
      Framing.write(socket.getOutputStream(), request);
      return Framing.read(socket.getInputStream(), MESSAGE_LENGTH);
    }
  }

  private static byte[] join(Future<byte[]> future) {
    try {
      return future.get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
