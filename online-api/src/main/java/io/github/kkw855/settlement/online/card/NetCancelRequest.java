package io.github.kkw855.settlement.online.card;

/**
 * 0420에 담을 값. 결과를 모르는 원거래(0100 승인 또는 0400 취소)를 카드사 쪽에서 무효로 만들 때 쓴다.
 *
 * @param originalTxnId 원거래고유번호. 0420 공통부의 거래고유번호도 이 값이다 (docs/02-message-spec.md 3장)
 * @param originalMsgType 원전문구분코드. 0100 또는 0400
 * @param amount 원거래 금액
 */
public record NetCancelRequest(String originalTxnId, String originalMsgType,
                               String merchantNo, long amount) {}
