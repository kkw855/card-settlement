package io.github.kkw855.settlement.online.payment;

/** 카드사 응답이 없을 때 PG가 붙이는 결과 코드 (docs/03-api.md 2장). 카드사 코드(숫자 4자리)와 겹치지 않게 P로 시작한다. */
final class ResultCodes {

  static final String APPROVED = "0000";
  static final String NOT_SENT = "P001";      // 카드사 연결 실패 (전송 안 됨)
  static final String NET_CANCELLED = "P002"; // 응답 확인 실패, 망취소 완료 (2-7)
  static final String UNRESOLVED = "P003";    // 응답 확인 실패, 망취소도 실패

  private ResultCodes() {}
}
