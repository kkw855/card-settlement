package io.github.kkw855.settlement.online.payment;

import java.util.Map;

/**
 * 결과 코드 (docs/03-api.md 2장). 카드사 응답코드(숫자 4자리)이거나,
 * 카드사 응답이 없을 때 PG가 붙이는 코드(P로 시작)다.
 *
 * <p>DB에는 코드만 저장하고, 응답의 메시지는 이 표에서 만든다.
 */
final class ResultCodes {

  static final String APPROVED = "0000";
  static final String NOT_SENT = "P001";      // 카드사 연결 실패 (전송 안 됨)
  static final String NET_CANCELLED = "P002"; // 응답 확인 실패, 망취소 완료 (2-7)
  static final String UNRESOLVED = "P003";    // 응답 확인 실패, 망취소도 실패

  private static final Map<String, String> MESSAGES = Map.of(
    APPROVED, "정상",
    "1001", "한도 초과",
    "1002", "유효기간 오류",
    "1003", "카드번호 오류",
    "2001", "원거래 없음",
    "2002", "이미 취소됨",
    "9999", "카드사 시스템 오류",
    NOT_SENT, "카드사 연결 실패",
    NET_CANCELLED, "카드사 응답 확인 실패, 망취소 완료",
    UNRESOLVED, "카드사 응답 확인 실패, 망취소도 실패"
  );

  private ResultCodes() {}

  /** 표에 없는 코드(카드사가 새로 만든 코드 등)도 응답은 나가야 하므로 예외를 던지지 않는다. */
  static String message(String code) {
    return MESSAGES.getOrDefault(code, "알 수 없는 결과 코드");
  }
}
