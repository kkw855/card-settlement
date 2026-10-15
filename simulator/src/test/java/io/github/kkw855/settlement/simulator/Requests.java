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
}
