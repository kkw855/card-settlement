package io.github.kkw855.settlement.core.message;

/**
 * 전문 송수신 기록 (message_log 테이블). 카드번호는 마스킹한 뒤에만 담는다 (I10).
 *
 * @param direction SEND 또는 RECV
 * @param responseCode 응답 전문의 응답코드. 요청이면 null
 * @param elapsedMs 응답까지 걸린 시간. 요청이면 null
 */
public record MessageLog(String txnId, String msgType, String direction, String maskedPayload,
                         String responseCode, Long elapsedMs) {}
