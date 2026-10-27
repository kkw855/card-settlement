package io.github.kkw855.settlement.core.merchant;

import java.util.Optional;

/** SQL은 resources/mapper/MerchantMapper.xml에 있다. */
public interface MerchantMapper {

  void insert(Merchant merchant);

  Optional<Merchant> findByApiKeyHash(String apiKeyHash);

  Optional<Merchant> findByMerchantNo(String merchantNo);
}
