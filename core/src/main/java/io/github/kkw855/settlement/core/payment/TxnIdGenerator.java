package io.github.kkw855.settlement.core.payment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 거래고유번호 20자리: {@code P} + 날짜 8자리 + 일련번호 11자리 (docs/02-message-spec.md 2장). */
public final class TxnIdGenerator {

  private final PaymentMapper paymentMapper;
  private final Clock clock;

  public TxnIdGenerator(PaymentMapper paymentMapper, Clock clock) {
    this.paymentMapper = paymentMapper;
    this.clock = clock;
  }

  public String next() {
    String date = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
    return "P" + date + "%011d".formatted(paymentMapper.nextTxnSequence());
  }
}
