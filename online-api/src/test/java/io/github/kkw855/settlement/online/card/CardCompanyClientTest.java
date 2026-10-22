package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.codec.CodecException;
import io.github.kkw855.settlement.codec.Framing;
import io.github.kkw855.settlement.online.card.CardCompanyClient.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과 분류(받음 / 보내지 않음 / 미확정)를 확인한다.
 *
 * <p>시뮬레이터 대신 테스트 안의 가짜 카드사를 쓴다. "응답하지 않고 끊기", "깨진 응답"처럼
 * 시뮬레이터가 만들지 않는 상황까지 정확히 만들 수 있다. 시뮬레이터와의 연동은 2-9에서 확인한다.
 */
class CardCompanyClientTest {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration READ_TIMEOUT = Duration.ofMillis(300); // 명세는 3초, 테스트는 짧게
  private static final byte[] REQUEST = "0100".concat("X".repeat(146)).getBytes(StandardCharsets.US_ASCII);

  private FakeCardCompany fake;

  @AfterEach
  void stop() throws IOException {
    if (fake != null) {
      fake.close();
    }
  }

  @Test
  @DisplayName("K1 응답을 받으면 Received")
  void received() throws IOException {
    fake = FakeCardCompany.start(socket -> {
      byte[] request = Framing.read(socket.getInputStream(), 150);
      Framing.write(socket.getOutputStream(), request); // 받은 전문을 그대로 돌려준다
    });

    Outcome outcome = client(fake.port()).send(REQUEST);

    assertThat(outcome).isInstanceOfSatisfying(Outcome.Received.class,
      received -> assertThat(received.response()).isEqualTo(REQUEST));
  }

  @Test
  @DisplayName("K2 연결이 거부되면 NotSent (전문이 나가지 않았음이 확실하다)")
  void notSentWhenConnectionRefused() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort(); // 비어 있는 포트를 얻고 바로 닫는다
    }

    Outcome outcome = client(closedPort).send(REQUEST);

    assertThat(outcome).isInstanceOf(Outcome.NotSent.class);
  }

  @Test
  @DisplayName("K3 응답이 늦으면 응답 대기 시간 뒤에 Unknown (카드사가 처리했을 수 있다)")
  void unknownOnReadTimeout() throws IOException {
    fake = FakeCardCompany.start(socket -> {
      Framing.read(socket.getInputStream(), 150);
      Thread.sleep(2_000); // 받기만 하고 응답하지 않는다
    });

    long start = System.nanoTime();
    Outcome outcome = client(fake.port()).send(REQUEST);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(outcome).isInstanceOfSatisfying(Outcome.Unknown.class,
      unknown -> assertThat(unknown.cause()).isInstanceOf(SocketTimeoutException.class));
    assertThat(elapsed).isBetween(READ_TIMEOUT, Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("K4 응답 없이 연결을 끊으면 Unknown")
  void unknownWhenClosedWithoutResponse() throws IOException {
    fake = FakeCardCompany.start(socket -> Framing.read(socket.getInputStream(), 150));

    Outcome outcome = client(fake.port()).send(REQUEST);

    assertThat(outcome).isInstanceOfSatisfying(Outcome.Unknown.class,
      unknown -> assertThat(unknown.cause()).isInstanceOf(EOFException.class));
  }

  @Test
  @DisplayName("K5 응답의 길이 필드가 깨져 있으면 Unknown")
  void unknownOnBrokenFrame() throws IOException {
    fake = FakeCardCompany.start(socket -> {
      Framing.read(socket.getInputStream(), 150);
      socket.getOutputStream().write("01A0".getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
    });

    Outcome outcome = client(fake.port()).send(REQUEST);

    assertThat(outcome).isInstanceOfSatisfying(Outcome.Unknown.class,
      unknown -> assertThat(unknown.cause()).isInstanceOf(CodecException.class));
  }

  private static CardCompanyClient client(int port) {
    return new CardCompanyClient("localhost", port, CONNECT_TIMEOUT, READ_TIMEOUT);
  }

  /** 연결 하나를 받아 정해진 대로 행동하는 가짜 카드사 */
  private static final class FakeCardCompany implements AutoCloseable {

    @FunctionalInterface
    interface Behavior {
      void handle(Socket socket) throws Exception;
    }

    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private FakeCardCompany(ServerSocket serverSocket) {
      this.serverSocket = serverSocket;
    }

    static FakeCardCompany start(Behavior behavior) throws IOException {
      FakeCardCompany fake = new FakeCardCompany(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
      fake.executor.submit(() -> {
        try (Socket socket = fake.serverSocket.accept()) {
          behavior.handle(socket);
        }
        return null;
      });
      return fake;
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      executor.shutdownNow();
    }
  }
}
