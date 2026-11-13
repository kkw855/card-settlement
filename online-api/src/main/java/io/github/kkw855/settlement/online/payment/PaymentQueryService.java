package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.payment.Payment;
import io.github.kkw855.settlement.core.payment.PaymentMapper;
import io.github.kkw855.settlement.online.error.ApiException;
import io.github.kkw855.settlement.online.error.ErrorCode;
import org.springframework.stereotype.Service;

/** 결제 조회. 취소(2-8)도 같은 방법으로 결제를 찾는다. */
@Service
public class PaymentQueryService {

  private final PaymentMapper paymentMapper;

  public PaymentQueryService(PaymentMapper paymentMapper) {
    this.paymentMapper = paymentMapper;
  }

  /**
   * 다른 가맹점의 결제는 없는 것과 똑같이 404다. 403으로 답하면 "그 거래가 있다"는 정보를 주게 된다 (docs/03-api.md 6장).
   */
  public Payment get(Merchant merchant, String txnId) {
    return paymentMapper.findByTxnId(txnId)
      .filter(payment -> payment.merchantId() == merchant.merchantId())
      .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "거래가 없다"));
  }
}
