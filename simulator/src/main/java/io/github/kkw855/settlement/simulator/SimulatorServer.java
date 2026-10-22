package io.github.kkw855.settlement.simulator;

import io.github.kkw855.settlement.codec.Framing;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 카드사 전문 서버. 연결 하나에 요청 하나, 응답 하나를 주고받고 닫는다 (ADR-0003, 거래마다 연결).
 *
 * <p>연결마다 가상 스레드 하나를 쓴다. 지연 시나리오에서 몇 초씩 잠들어도 OS 스레드를 붙잡지 않는다.
 */
final class SimulatorServer implements AutoCloseable {

  private static final Logger log = System.getLogger(SimulatorServer.class.getName());
  private static final int IDLE_TIMEOUT_MILLIS = 10_000; // 연결만 하고 아무것도 안 보내는 클라이언트 대비
  private static final int BACKLOG = 50;

  private final ServerSocket serverSocket;
  private final CardCompany cardCompany;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * @param port 0이면 비어 있는 포트를 OS가 고른다 (테스트용).
   * 루프백(127.0.0.1)에만 연다. 같은 네트워크의 다른 기기에서 접속하지 못하게 한다 (compose의 DB 포트와 같은 이유)
   */
  SimulatorServer(int port, CardCompany cardCompany) throws IOException {
    this.serverSocket = new ServerSocket(port, BACKLOG, InetAddress.getLoopbackAddress());
    this.cardCompany = cardCompany;
  }

  int port() {
    return serverSocket.getLocalPort();
  }

  void start() {
    executor.submit(this::acceptLoop);
    log.log(Level.INFO, () -> "카드사 시뮬레이터 시작: port=" + port());
  }

  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      try {
        Socket socket = serverSocket.accept();
        executor.submit(() -> serve(socket));
      } catch (SocketException e) {
        return; // close()가 serverSocket을 닫으면 accept()가 여기로 빠져나온다
      } catch (IOException e) {
        log.log(Level.WARNING, "연결 수락 실패", e);
      }
    }
  }

  private void serve(Socket socket) {
    try (socket) {
      socket.setSoTimeout(IDLE_TIMEOUT_MILLIS);
      byte[] request = Framing.read(socket.getInputStream(), MessageLayouts.MESSAGE_LENGTH);
      switch (cardCompany.handle(request)) {
        case CardCompany.Reply.Respond(byte[] body, Duration delay) -> {
          sleep(delay);
          Framing.write(socket.getOutputStream(), body);
        }
        case CardCompany.Reply.Silent(Duration duration) -> sleep(duration); // 응답 없이 기다렸다가 닫는다
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException e) {
      // 지연 시나리오에서는 PG가 먼저 타임아웃으로 연결을 끊어서 쓰기가 실패한다. 정상이다.
      // 카드번호가 들어 있을 수 있으므로 전문 내용은 남기지 않는다.
      log.log(Level.INFO, () -> "연결 처리 종료: " + e);
    }
  }

  private static void sleep(Duration duration) throws InterruptedException {
    if (!duration.isZero()) {
      Thread.sleep(duration);
    }
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
    executor.shutdownNow();
  }
}
