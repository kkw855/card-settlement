package io.github.kkw855.settlement.online.config;

import io.github.kkw855.settlement.core.card.CardProtector;
import io.github.kkw855.settlement.core.crypto.AesGcmEncryptor;
import io.github.kkw855.settlement.core.crypto.CryptoKeys;
import io.github.kkw855.settlement.core.crypto.HmacHasher;
import io.github.kkw855.settlement.online.card.CardCompanyClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring을 모르는 클래스(core, online.card)를 빈으로 등록한다.
 *
 * <p>그 클래스들에 {@code @Component}를 붙이지 않고 여기서 만든다. core는 Spring 없이도 쓰고 테스트할 수 있어야 한다.
 */
@Configuration(proxyBeanMethods = false)
class BeanConfig {

  @Bean
  CardCompanyClient cardCompanyClient(CardCompanyProperties properties) {
    return new CardCompanyClient(properties.host(), properties.port(),
      properties.connectTimeout(), properties.readTimeout());
  }

  /**
   * 키가 없거나 형식이 틀리면 여기서 CryptoException이 나서 앱이 뜨지 않는다 (잘못된 설정으로 운영하지 않는다)
   */
  @Bean
  CardProtector cardProtector(CryptoProperties properties) {
    return new CardProtector(
      new AesGcmEncryptor(CryptoKeys.aes(properties.cardEncryptionKey())),
      new HmacHasher(CryptoKeys.hmac(properties.cardHmacKey()))
    );
  }
}
