package io.github.kkw855.settlement.simulator;

import static io.github.kkw855.settlement.simulator.MessageLayouts.*;

/** 테스트용 0100 승인 요청. */
final class Requests {

  private Requests() {}

  static byte[] approval(String txnId, String cardNo, String expiry, long amount) {
    return REQUEST_0100.writer()
      .set(MSG_TYPE, "0100")
      .set(TXN_ID, txnId)
      .set(SENT_AT, "20261008103000")
      .set(MERCHANT_NO, "M000000001")
      .set(CARD_NO, cardNo)
      .set(EXPIRY, expiry)
      .set(INSTALLMENT, 0)
      .set(AMOUNT, amount)
      .set(MERCHANT_NAME, "홍길동카페")
      .toBytes();
  }

  static byte[] approval(String txnId, long amount) {
    return approval(txnId, "4111111111111111", "2812", amount);
  }

  static byte[] cancel(String cancelTxnId, String originalTxnId, String approvalNo, String approvedDate, long amount) {
    return REQUEST_0400.writer()
      .set(MSG_TYPE, "0400")
      .set(TXN_ID, cancelTxnId)
      .set(SENT_AT, "20261008150000")
      .set(ORIGINAL_TXN_ID, originalTxnId)
      .set(ORIGINAL_APPROVAL_NO, approvalNo)
      .set(ORIGINAL_APPROVED_DATE, approvedDate)
      .set(CANCEL_MERCHANT_NO, "M000000001")
      .set(CANCEL_AMOUNT, amount)
      .toBytes();
  }

  /** 망취소의 공통부 거래고유번호는 원거래고유번호와 같다 (docs/02-message-spec.md 3장) */
  static byte[] netCancel(String originalTxnId, String originalMsgType, long amount) {
    return REQUEST_0420.writer()
      .set(MSG_TYPE, "0420")
      .set(TXN_ID, originalTxnId)
      .set(SENT_AT, "20261008103004")
      .set(ORIGINAL_TXN_ID, originalTxnId)
      .set(ORIGINAL_MSG_TYPE, originalMsgType)
      .set(NET_CANCEL_MERCHANT_NO, "M000000001")
      .set(NET_CANCEL_AMOUNT, amount)
      .toBytes();
  }
}
