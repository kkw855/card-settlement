package io.github.kkw855.settlement.online.card;

import io.github.kkw855.settlement.online.card.NetCancelGateway.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * 원거래가 무효로 확인될 때까지 망취소를 보낸다 (docs/02-message-spec.md 1장).
 *
 * <p>최초 1회 보내고, 실패하면 {@code retryDelays}의 값만큼 기다렸다가 다시 보낸다. 명세는 1초 -> 2초
 * -> 4초라 최대 4회 보낸다. 카드사는 같은 망취소를 여러 번 받아도 결과가 같으므로 다시 보내도 안전하다.
 *
 * <p>승인과 취소가 함께 쓴다.
 */
public final class NetCanceller {

  /** 기다리는 방법. 운영에서는 {@code Thread::sleep}, 테스트에서는 기다리지 않고 기록만 한다. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }

  private static final Logger log = LoggerFactory.getLogger(NetCanceller.class);

  private final NetCancelGateway gateway;
  private final List<Duration> retryDelays;
  private final Sleeper sleeper;

  /**
   * @param retryDelays 재시도 전 대기 시간. 개수가 재시도 횟수다
   */
  public NetCanceller(NetCancelGateway gateway, List<Duration> retryDelays, Sleeper sleeper) {
    this.gateway = gateway;
    this.retryDelays = List.copyOf(retryDelays);
    this.sleeper = sleeper;
  }

  public Result netCancel(NetCancelRequest request) {
    Result result = gateway.netCancel(request);

    for (Duration duration : retryDelays) {
      if (result instanceof Result.Voided) {
        return result;
      }

      log.warn("망취소 실패. {}ms 뒤 다시 보낸다: txnId={}, {}",
        duration.toMillis(), request.originalTxnId(), result);

      try {
        sleeper.sleep(duration);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // 인터럽트 상태를 되살린다. 앱이 멈추는 중일 수 있으니 더 보내지 않는다
        return result;
      }

      result = gateway.netCancel(request);
    }

    return result;
  }
}
