package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.online.card.NetCancelGateway.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 몇 번 보내는지, 그 사이에 얼마나 기다리는지 확인한다.
 *
 * <p>실제로 기다리지 않는다. 기다리라고 한 시간만 목록에 모은다.
 */
class NetCancellerTest {
  private static final NetCancelRequest REQUEST = new NetCancelRequest("P2026111700000000001", "0100",
    "M000000001", 10_009);
  private static final List<Duration> DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2),
    Duration.ofSeconds(4));
  private static final Result VOIDED = new Result.Voided();
  private static final Result FAILED = new Result.Failed("응답을 받지 못했다: SocketTimeoutException");

  private final NetCancelGateway gateway = mock(NetCancelGateway.class);
  private final List<Duration> slept = new ArrayList<>();
  private final NetCanceller canceller = new NetCanceller(gateway, DELAYS, slept::add);

  @Test
  @DisplayName("R1 처음에 무효가 확인되면 다시 보내지 않는다")
  void voidedAtFirst() {
    when(gateway.netCancel(REQUEST)).thenReturn(VOIDED);

    assertThat(canceller.netCancel(REQUEST)).isEqualTo(VOIDED);
    verify(gateway, times(1)).netCancel(REQUEST);
    assertThat(slept).isEmpty();
  }

  @Test
  @DisplayName("R2 실패하면 1초, 2초 기다렸다가 다시 보낸다. 무효가 확인되면 멈춘다")
  void retriesUntilVoided() {
    when(gateway.netCancel(REQUEST)).thenReturn(FAILED, FAILED, VOIDED);

    assertThat(canceller.netCancel(REQUEST)).isEqualTo(VOIDED);
    verify(gateway, times(3)).netCancel(REQUEST);
    assertThat(slept).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  @Test
  @DisplayName("R3 끝까지 실패하면 최초 1회 + 재시도 3회로 끝낸다. 마지막 실패 뒤에는 기다리지 않는다")
  void givesUpAfterLastRetry() {
    when(gateway.netCancel(REQUEST)).thenReturn(FAILED);

    assertThat(canceller.netCancel(REQUEST)).isEqualTo(FAILED);
    verify(gateway, times(4)).netCancel(REQUEST);
    assertThat(slept).isEqualTo(DELAYS);
  }

  @Test
  @DisplayName("R4 기다리는 중에 인터럽트되면 더 보내지 않는다. 인터럽트 상태는 되살린다")
  void stopsWhenInterrupted() {
    when(gateway.netCancel(REQUEST)).thenReturn(FAILED);

    NetCanceller interrupted = new NetCanceller(gateway, DELAYS, duration -> {
      throw new InterruptedException();
    });

    Result result = interrupted.netCancel(REQUEST);

    assertThat(Thread.interrupted()).isTrue(); // 확인하면서 지운다. 남겨 두면 같은 스레드의 다음 테스트가 인터럽트된다
    assertThat(result).isEqualTo(FAILED);
    verify(gateway, times(1)).netCancel(REQUEST);
  }

  @Test
  @DisplayName("R5 대기 목록에 null이 있으면 만들 때 실패한다. 설정 실수가 망취소 도중이 아니라 앱을 띄울 때 드러난다")
  void rejectsNullDelay() {
    List<Duration> delays = new ArrayList<>(DELAYS);
    delays.add(null); // 설정 끝에 쉼표가 하나 더 붙으면(1s,2s,4s,) Spring이 이런 목록을 만든다

    assertThatThrownBy(() -> new NetCanceller(gateway, delays, slept::add))
      .isInstanceOf(NullPointerException.class);
  }
}
