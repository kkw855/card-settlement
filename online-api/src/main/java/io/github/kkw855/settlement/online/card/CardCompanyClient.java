package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.codec.Framing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * 카드사와 전문 하나를 주고받는다. 거래마다 연결하고 끊는다 (ADR-0003).
 *
 * <p>결과를 셋으로 나눈다. 기준은 "카드사가 이 전문을 받았을 가능성이 있는가"다 (docs/03-api.md 1장).
 * <ul>
 *   <li>{@link Outcome.Received}: 응답을 받았다. 내용(정상·거절)은 호출한 쪽이 해석한다.
 *   <li>{@link Outcome.NotSent}: 연결 자체가 안 됐다. 전문이 한 바이트도 나가지 않았으므로 결과는 "없음"으로 확정된다.
 *   <li>{@link Outcome.Unknown}: 연결된 뒤에 실패했다 (응답 타임아웃, 연결 끊김, 깨진 응답).
 *       카드사가 받아서 처리했을 수 있으므로 망취소로 정리해야 한다.
 * </ul>
 *
 * <p>상태가 없어서 여러 스레드가 함께 써도 된다.
 */
public final class CardCompanyClient {

  public sealed interface Outcome {

    record Received(byte[] response) implements Outcome {}

    record NotSent(Exception cause) implements Outcome {}

    record Unknown(Exception cause) implements Outcome {}
  }

  private final InetSocketAddress address;
  private final Duration connectTimeout;
  private final Duration readTimeout;

  /**
   * @param connectTimeout 연결 대기. 명세는 1초
   * @param readTimeout 응답 대기. 명세는 3초. 소켓 읽기 한 번에 걸리는 시간 기준이다 (아래 send 참고)
   */
  public CardCompanyClient(String host, int port, Duration connectTimeout, Duration readTimeout) {
    this.address = new InetSocketAddress(host, port);
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
  }

  /**
   * 요청 전문을 보내고 응답 전문을 받는다. 예외를 던지지 않고 결과를 {@link Outcome}으로 돌려준다.
   *
   * <p>{@code setSoTimeout}은 읽기 호출 한 번이 기다리는 시간이다. 상대가 1바이트씩 천천히 보내면 전체 시간은
   * 3초를 넘을 수 있다. v1의 카드사는 응답을 한 번에 보내므로 받아들이고, 알려진 한계로 둔다.
   */
  public Outcome send(byte[] request) {
    Socket socket = new Socket();
    try {
      socket.connect(address, Math.toIntExact(connectTimeout.toMillis()));
    } catch (IOException e) {
      closeQuietly(socket);
      return new Outcome.NotSent(e);
    }

    try (socket) {
      socket.setSoTimeout(Math.toIntExact(readTimeout.toMillis()));
      Framing.write(socket.getOutputStream(), request);
      return new Outcome.Received(Framing.read(socket.getInputStream(), CardMessageLayouts.MESSAGE_LENGTH));
    } catch (IOException | RuntimeException e) {
      // 쓰기 실패도 여기로 온다. 일부라도 나갔을 수 있으므로 "보내지 않았다"고 단정하지 않는다.
      return new Outcome.Unknown(e);
    }
  }

  private static void closeQuietly(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
      // 연결도 안 된 소켓이다
    }
  }
}
