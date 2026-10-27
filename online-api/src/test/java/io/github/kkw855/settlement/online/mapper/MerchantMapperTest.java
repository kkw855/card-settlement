package io.github.kkw855.settlement.online.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import io.github.kkw855.settlement.core.merchant.MerchantStatus;
import io.github.kkw855.settlement.online.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional // 테스트가 끝나면 롤백한다
class MerchantMapperTest extends IntegrationTest {

  @Autowired
  MerchantMapper merchantMapper;

  @Test
  @DisplayName("G1 넣은 가맹점을 API 키 해시로 찾는다 (record로 매핑, IDENTITY로 ID 생성)")
  void insertAndFindByApiKeyHash() {
    merchantMapper.insert(TestData.merchant("M000000001", "k".repeat(64)));

    Merchant found = merchantMapper.findByApiKeyHash("k".repeat(64)).orElseThrow();

    assertThat(found.merchantId()).isNotNull();
    assertThat(found.merchantNo()).isEqualTo("M000000001");
    assertThat(found.name()).isEqualTo("홍길동카페");
    assertThat(found.status()).isEqualTo(MerchantStatus.ACTIVE);
  }

  @Test
  @DisplayName("G2 없는 API 키는 빈 Optional")
  void unknownKey() {
    assertThat(merchantMapper.findByApiKeyHash("z".repeat(64))).isEmpty();
  }
}
