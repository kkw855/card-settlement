package io.github.kkw855.settlement.core.crypto;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 검색용 해시 (blind index). 같은 입력은 항상 같은 값이 나와서 {@code WHERE card_hash = ?}로 찾을 수 있다.
 *
 * <p>키 없는 SHA-256이면 안 된다. 마스킹 값(앞 6, 뒤 4자리)이 같은 행에 있어서 모르는 자리는 6개뿐이고,
 * 후보 10⁶개를 다 해시해 보면 원문이 나온다. HMAC은 키가 없으면 후보를 대입해 볼 수조차 없다 (ADR-0005).
 *
 * <p>{@link Mac}은 스레드 안전하지 않아 호출마다 만든다.
 */
public final class HmacHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKey key;

  public HmacHasher(SecretKey key) {
    this.key = key;
  }

  /**
   * 소문자 16진수 64자. DB 컬럼은 CHAR(64).
   */
  public String hash(String value) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new CryptoException("HMAC 계산 실패", e);
    }
  }
}
