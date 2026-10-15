package io.github.kkw855.settlement.simulator;

/**
 * 거래금액의 끝 세 자리로 고르는 테스트 시나리오 (docs/02-message-spec.md 4장).
 *
 * <p>승인 기록에 함께 남겨서, 망취소(2-3)와 매입 파일 생성(Phase 3)이 같은 시나리오를 이어서 쓴다.
 */
enum Scenario {
  LIMIT_EXCEEDED,             // 001: 한도 초과로 거절
  DELAYED,                    // 009: 승인하지만 응답을 늦게 보낸다 -> PG 타임아웃 -> 망취소
  UNCAPTURED,                 // 101: 승인, 매입 파일에서 누락
  AMOUNT_MISMATCH,            // 102: 승인, 매입 금액 +10원
  DELAYED_FAKE_NET_CANCEL,    // 109: 응답 지연, 망취소에 "취소함"이라 답하고 실제로는 취소하지 않음
  DELAYED_IGNORE_NET_CANCEL,  // 119: 응답 지연, 망취소에 응답하지 않음
  NORMAL;

  static Scenario of(long amount) {
    return switch ((int) (amount % 1000)) {
      case 1 -> LIMIT_EXCEEDED;
      case 9 -> DELAYED;
      case 101 -> UNCAPTURED;
      case 102 -> AMOUNT_MISMATCH;
      case 109 -> DELAYED_FAKE_NET_CANCEL;
      case 119 -> DELAYED_IGNORE_NET_CANCEL;
      default -> NORMAL;
    };
  }

  boolean delaysResponse() {
    return this == DELAYED || this == DELAYED_FAKE_NET_CANCEL || this == DELAYED_IGNORE_NET_CANCEL;
  }
}
