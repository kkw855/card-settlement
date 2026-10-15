package io.github.kkw855.settlement.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/** 실행 {@code ./gradlew :simulator:run}. 포트는 ISO 8583(카드 전문 표준)의 번호를 빌렸다. */
public final class SimulatorApplication {

  private static final int DEFAULT_PORT = 8583;
  private static final Duration RESPONSE_DELAY = Duration.ofSeconds(5); // PG 응답 대기 3초보다 길게

  private SimulatorApplication() {}

  public static void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
    Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));
    CardCompany cardCompany = new CardCompany(new ApprovalLedger(), clock, RESPONSE_DELAY);

    @SuppressWarnings("resource")
    SimulatorServer server = new SimulatorServer(port, cardCompany);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.close();
      } catch (Exception ignored) {
        // 종료 중이다
      }
    }));
    server.start();
    Thread.currentThread().join(); // Ctrl+C로 끝날 때까지 기다린다
  }
}
