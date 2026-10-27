package io.github.kkw855.settlement.online.mapper;

import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.merchant.MerchantStatus;
import io.github.kkw855.settlement.core.payment.Payment;

import java.time.LocalDateTime;

/**
 * 매퍼 테스트용 데이터. 암호문·해시 자리에는 형식만 맞춘 가짜 값을 넣는다 (매퍼는 내용을 해석하지 않는다).
 */
final class TestData {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 10, 30, 0);

  private TestData() {}

  static Merchant merchant(String merchantNo, String apiKeyHash) {
    return new Merchant(null, merchantNo, "홍길동카페", 2, "004",
      "enc", "123-****-7890", "홍길동", MerchantStatus.ACTIVE, apiKeyHash);
  }

  static Payment requested(String txnId, long merchantId, String idempotencyKey) {
    return Payment.requested(txnId, merchantId, idempotencyKey, "enc", "a".repeat(64),
      "411111******1111", 0, 10_000, NOW);
  }
}
