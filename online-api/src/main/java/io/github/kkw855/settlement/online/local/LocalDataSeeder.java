package io.github.kkw855.settlement.online.local;

import io.github.kkw855.settlement.core.crypto.AesGcmEncryptor;
import io.github.kkw855.settlement.core.crypto.CryptoKeys;
import io.github.kkw855.settlement.core.merchant.ApiKeys;
import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import io.github.kkw855.settlement.core.merchant.MerchantStatus;
import io.github.kkw855.settlement.online.config.CryptoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 로컬 개발용 가맹점을 넣는다. local 프로필에서만 돈다 ({@code ./gradlew :online-api:bootRun}이 켠다).
 *
 * <ul>
 *   <li>가맹점은 M000000001 홍길동카페다 (docs/02-message-spec.md의 골든 샘플). 이미 있으면 넣지 않는다.
 *   <li>API 키 원문은 .env의 LOCAL_MERCHANT_API_KEY에만 있다. DB에는 SHA-256만 넣는다 (docs/03-api.md 6장).
 *   <li>지급 계좌는 앱이 쓰는 CARD_ENCRYPTION_KEY로 암호화한다. 지급 파일 잡이 로컬에서도 복호화할 수 있어야 한다.
 * </ul>
 */
@Component
@Profile("local")
class LocalDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);
  private static final String MERCHANT_NO = "M000000001";

  private final MerchantMapper merchantMapper;
  private final CryptoProperties cryptoProperties;
  private final String apiKey;

  LocalDataSeeder(MerchantMapper merchantMapper, CryptoProperties cryptoProperties,
                  @Value("${LOCAL_MERCHANT_API_KEY:}") String apiKey) {
    this.merchantMapper = merchantMapper;
    this.cryptoProperties = cryptoProperties;
    this.apiKey = apiKey;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (apiKey.isBlank()) {
      throw new IllegalStateException("LOCAL_MERCHANT_API_KEY가 없다. .env에 넣는다 (openssl rand -hex 32)");
    }
    String apiKeyHash = ApiKeys.hash(apiKey);

    Optional<Merchant> existing = merchantMapper.findByMerchantNo(MERCHANT_NO);
    if (existing.isPresent()) {
      // 키만 바꾸고 그대로 뜨면 curl이 이유 없이 401을 받는다. 원인을 기동할 때 알려 준다
      if (!existing.get().apiKeyHash().equals(apiKeyHash)) {
        throw new IllegalStateException(MERCHANT_NO + "의 API 키가 LOCAL_MERCHANT_API_KEY와 다르다. "
          + ".env의 키를 되돌리거나, 로컬 DB를 지우고 다시 띄운다 (docker compose down -v)");
      }
      return;
    }

    AesGcmEncryptor encryptor = new AesGcmEncryptor(CryptoKeys.aes(cryptoProperties.cardEncryptionKey()));
    merchantMapper.insert(new Merchant(null, MERCHANT_NO, "홍길동카페", 2,
      "004", encryptor.encrypt("12345601234567"), "1234******4567",
      "홍길동", MerchantStatus.ACTIVE, apiKeyHash));
    log.info("로컬 가맹점 {}을 넣었다", MERCHANT_NO);
  }
}
