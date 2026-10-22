package io.github.kkw855.settlement.simulator;

/** 카드사 응답코드 (docs/02-message-spec.md 2장). */
enum ResponseCode {
  OK("0000", "정상"),
  LIMIT_EXCEEDED("1001", "한도 초과"),
  INVALID_EXPIRY("1002", "유효기간 오류"),
  INVALID_CARD("1003", "카드번호 오류"),
  NO_ORIGINAL("2001", "원거래 없음"),
  ALREADY_CANCELLED("2002", "이미 취소됨"),
  SYSTEM_ERROR("9999", "시스템 오류");

  final String code;
  final String message;

  ResponseCode(String code, String message) {
    this.code = code;
    this.message = message;
  }
}
