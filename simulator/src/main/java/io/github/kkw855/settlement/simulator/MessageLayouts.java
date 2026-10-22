package io.github.kkw855.settlement.simulator;

import io.github.kkw855.settlement.codec.Field;
import io.github.kkw855.settlement.codec.Layout;

/**
 * 카드사 쪽 전문 레이아웃 (docs/02-message-spec.md 2~3장).
 *
 * <p>PG도 같은 명세로 레이아웃을 따로 정의한다. 코드를 공유하지 않아야 한쪽의 옮겨 적기 실수를
 * 골든 샘플 테스트가 잡아낸다 (ADR-0002).
 */
final class MessageLayouts {

  // 공통부 (0 ~ 49)
  static final Field.N MSG_TYPE = new Field.N("전문구분코드", 0, 4);
  static final Field.AN TXN_ID = new Field.AN("거래고유번호", 4, 20);
  static final Field.N SENT_AT = new Field.N("전송일시", 24, 14);
  static final Field.AN RESPONSE_CODE = new Field.AN("응답코드", 38, 4);
  static final Field.AN HEADER_RESERVED = new Field.AN("공통부 예비", 42, 8);

  // 0100 승인 요청 개별부
  static final Field.AN MERCHANT_NO = new Field.AN("가맹점번호", 50, 10);
  static final Field.N CARD_NO = new Field.N("카드번호", 60, 16);
  static final Field.N EXPIRY = new Field.N("유효기간", 76, 4);
  static final Field.N INSTALLMENT = new Field.N("할부개월", 80, 2);
  static final Field.N AMOUNT = new Field.N("거래금액", 82, 12);
  static final Field.AN MERCHANT_NAME = new Field.AN("가맹점명", 94, 40);
  static final Field.AN REQUEST_RESERVED = new Field.AN("0100 예비", 134, 16);

  // 0110 승인 응답 · 0410 취소 응답 개별부 (두 응답의 모양이 같다)
  static final Field.AN APPROVAL_NO = new Field.AN("승인번호", 50, 8);
  static final Field.N APPROVED_AT = new Field.N("승인일시", 58, 14);
  static final Field.AN RESPONSE_MESSAGE = new Field.AN("응답메시지", 72, 40);
  static final Field.AN RESPONSE_RESERVED = new Field.AN("응답 예비", 112, 38);

  // 0400 취소 요청 개별부
  static final Field.AN ORIGINAL_TXN_ID = new Field.AN("원거래고유번호", 50, 20); // 0420·0430도 같은 자리

  static final Field.AN ORIGINAL_APPROVAL_NO = new Field.AN("원승인번호", 70, 8);
  static final Field.N ORIGINAL_APPROVED_DATE = new Field.N("원승인일자", 78, 8);
  static final Field.AN CANCEL_MERCHANT_NO = new Field.AN("가맹점번호", 86, 10);
  static final Field.N CANCEL_AMOUNT = new Field.N("취소금액", 96, 12);
  static final Field.AN CANCEL_RESERVED = new Field.AN("0400 예비", 108, 42);

  // 0420 망취소 요청 개별부
  static final Field.N ORIGINAL_MSG_TYPE = new Field.N("원전문구분코드", 70, 4);
  static final Field.AN NET_CANCEL_MERCHANT_NO = new Field.AN("가맹점번호", 74, 10);
  static final Field.N NET_CANCEL_AMOUNT = new Field.N("거래금액", 84, 12);
  static final Field.AN NET_CANCEL_RESERVED = new Field.AN("0420 예비", 96, 54);

  // 0430 망취소 응답 개별부
  static final Field.AN NET_CANCEL_RESULT = new Field.AN("처리구분", 70, 2);
  static final Field.AN NET_CANCEL_RESPONSE_RESERVED = new Field.AN("0430 예비", 72, 78);

  static final int MESSAGE_LENGTH = 150;

  static final Layout REQUEST_0100 = Layout.of("0100", MESSAGE_LENGTH,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    MERCHANT_NO, CARD_NO, EXPIRY, INSTALLMENT, AMOUNT, MERCHANT_NAME, REQUEST_RESERVED);

  static final Layout RESPONSE_0110 = Layout.of("0110", MESSAGE_LENGTH,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    APPROVAL_NO, APPROVED_AT, RESPONSE_MESSAGE, RESPONSE_RESERVED);

  static final Layout REQUEST_0400 = Layout.of("0400", MESSAGE_LENGTH,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    ORIGINAL_TXN_ID, ORIGINAL_APPROVAL_NO, ORIGINAL_APPROVED_DATE, CANCEL_MERCHANT_NO, CANCEL_AMOUNT, CANCEL_RESERVED);

  static final Layout RESPONSE_0410 = Layout.of("0410", MESSAGE_LENGTH,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    APPROVAL_NO, APPROVED_AT, RESPONSE_MESSAGE, RESPONSE_RESERVED);

  static final Layout REQUEST_0420 = Layout.of("0420", MESSAGE_LENGTH,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    ORIGINAL_TXN_ID, ORIGINAL_MSG_TYPE, NET_CANCEL_MERCHANT_NO, NET_CANCEL_AMOUNT, NET_CANCEL_RESERVED);

  static final Layout RESPONSE_0430 = Layout.of("0430", MESSAGE_LENGTH,
    MSG_TYPE, TXN_ID, SENT_AT, RESPONSE_CODE, HEADER_RESERVED,
    ORIGINAL_TXN_ID, NET_CANCEL_RESULT, NET_CANCEL_RESPONSE_RESERVED);

  private MessageLayouts() {}
}
