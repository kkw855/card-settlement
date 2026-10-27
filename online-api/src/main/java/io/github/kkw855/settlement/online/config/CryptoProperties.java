package io.github.kkw855.settlement.online.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카드번호 보호 키 (application.yaml의 crypto). 값은 환경변수로만 받는다 (ADR-0005).
 *
 * <p>record의 자동 {@code toString()}은 키를 그대로 출력하므로 재정의한다. 설정 바인딩 오류나 디버그 로그로
 * 키가 새지 않게 한다.
 */
@ConfigurationProperties("crypto")
public record CryptoProperties(String cardEncryptionKey, String cardHmacKey) {

  @Override
  public String toString() {
    return "CryptoProperties[cardEncryptionKey=****, cardHmacKey=****]";
  }
}
