package io.github.kkw855.settlement.core.merchant;

/**
 * 가맹점 (merchant 테이블).
 *
 * @param merchantId 새로 넣을 때는 null (DB가 IDENTITY로 만든다)
 * @param payoutAccountEnc 지급 계좌 AES-GCM 암호문 (ADR-0005)
 * @param apiKeyHash X-Api-Key의 SHA-256 (docs/03-api.md 6장)
 */
public record Merchant(
  Long merchantId,
  String merchantNo,
  String name,
  int settlementCycleDays,
  String payoutBankCode,
  String payoutAccountEnc,
  String payoutAccountMasked,
  String holderName,
  MerchantStatus status,
  String apiKeyHash
) {}
