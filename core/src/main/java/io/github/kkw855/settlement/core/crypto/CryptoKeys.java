package io.github.kkw855.settlement.core.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 환경변수로 받은 Base64 문자열을 키로 바꾼다. 키는 저장소에 커밋하지 않는다 (ADR-0005).
 *
 * <p>암호화 키와 HMAC 키는 서로 다른 값이어야 한다. 하나가 새도 다른 쪽은 안전하게 남는다.
 * 만드는 법: {@code openssl rand -base64 32}
 */
public final class CryptoKeys {

  private static final int KEY_BYTES = 32; // AES-256, HMAC-SHA256 모두 256비트

  private CryptoKeys() {}

  public static SecretKey aes(String base64) {
    return new SecretKeySpec(decode(base64, "AES"), "AES");
  }

  public static SecretKey hmac(String base64) {
    return new SecretKeySpec(decode(base64, "HMAC"), "HmacSHA256");
  }

  private static byte[] decode(String base64, String purpose) {
    if (base64 == null || base64.isBlank()) {
      throw new CryptoException(purpose + " 키가 설정되지 않았다");
    }
    byte[] key;
    try {
      key = Base64.getDecoder().decode(base64.strip());
    } catch (IllegalArgumentException e) {
      throw new CryptoException(purpose + " 키가 Base64 형식이 아니다");
    }
    if (key.length != KEY_BYTES) {
      throw new CryptoException("%s 키는 %d바이트여야 한다 (지금 %d바이트)".formatted(purpose, KEY_BYTES, key.length));
    }
    return key;
  }
}
