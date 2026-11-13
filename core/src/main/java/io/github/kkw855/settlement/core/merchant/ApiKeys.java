package io.github.kkw855.settlement.core.merchant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 가맹점 API 키(X-Api-Key). DB에는 키가 아니라 SHA-256 해시만 둔다 (docs/03-api.md 6장).
 *
 * <p>카드번호와 달리 HMAC이 필요 없다. 카드번호는 모르는 자리가 6개라 후보가 10⁶개뿐이어서 해시만 쓰면 전수 대입으로
 * 되돌릴 수 있다. API 키는 무작위 32바이트라 후보가 2²⁵⁶개여서 되돌릴 수 없다.
 */
public final class ApiKeys {

  private ApiKeys() {}

  /**
   * 소문자 16진수 64자 (merchant.api_key_hash, CHAR(64))
   */
  public static String hash(String apiKey) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(apiKey.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256은 모든 JVM이 지원해야 하는 알고리즘이다", e);
    }
  }
}
