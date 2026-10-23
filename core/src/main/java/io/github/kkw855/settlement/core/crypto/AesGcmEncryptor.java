package io.github.kkw855.settlement.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM으로 문자열을 암호화한다. 결과는 DB에 넣을 수 있게 Base64 문자열이다.
 *
 * <pre>
 * [버전 1바이트][IV 12바이트][암호문 + 인증 태그 16바이트] -> Base64
 * </pre>
 *
 * <ul>
 *   <li>IV는 매번 새로 뽑는다. 같은 카드번호도 암호문이 매번 달라서, 암호문끼리 비교해 같은 카드인지 알 수 없다.
 *       검색이 필요하면 {@link HmacHasher}를 쓴다.
 *   <li>GCM은 인증 태그로 위변조를 잡는다. 암호문이 한 바이트라도 바뀌면 복호화가 실패한다.
 *   <li>버전 바이트는 나중에 키를 바꿀 때(키 교체) 어느 키로 암호화했는지 구분하는 자리다. v1은 1뿐이다.
 * </ul>
 *
 * <p>{@link Cipher}는 스레드 안전하지 않아 호출마다 만든다. 키({@link SecretKey})와 {@link SecureRandom}은 공유해도 된다.
 */
public final class AesGcmEncryptor {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final byte VERSION = 1;
  private static final int IV_BYTES = 12;       // GCM 권장 IV 길이 (96비트)
  private static final int TAG_BITS = 128;      // 16바이트

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public AesGcmEncryptor(SecretKey key) {
    this.key = key;
  }

  public String encrypt(String plaintext) {
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = ByteBuffer.allocate(1 + IV_BYTES + sealed.length)
        .put(VERSION).put(iv).put(sealed).array();
      return Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("암호화 실패", e);
    }
  }

  public String decrypt(String encoded) {
    byte[] in;
    try {
      in = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new CryptoException("암호문이 Base64 형식이 아니다");
    }
    if (in.length < 1 + IV_BYTES + TAG_BITS / 8 || in[0] != VERSION) {
      throw new CryptoException("알 수 없는 암호문 형식이다");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 1, IV_BYTES));
      byte[] plain = cipher.doFinal(in, 1 + IV_BYTES, in.length - 1 - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      // 키가 다르거나 암호문이 바뀌면 AEADBadTagException이 난다
      throw new CryptoException("복호화 실패 (키가 다르거나 암호문이 손상됐다)", e);
    }
  }
}
