package io.github.kkw855.settlement.core.crypto;

import javax.crypto.SecretKey;

/** 테스트 전용 키. 운영 키는 환경변수로 주입하고 저장소에 두지 않는다. */
public final class TestKeys {

  // openssl rand -base64 32 로 만든 테스트용 값. 운영 키와 같으면 안 된다
  public static final String AES_BASE64 = "Kz8XPJpTi06KBG3iledWAfT9oB+2nQnf+qvkHNqXOX4=";
  public static final String HMAC_BASE64 = "mSk0cg04hDjDSg6RJ/x7IpZb4yJGCWDZGZRvnqnRXFk=";
  /** 위 두 키와 다른 키. "다른 키로는 안 된다"를 확인할 때 쓴다. */
  public static final String OTHER_BASE64 = "E8yl4H2okr0RtP6jcmCcozHTUHlbgwwZ+jvyEuuYIfE=";

  private TestKeys() {}

  public static SecretKey aes() {
    return CryptoKeys.aes(AES_BASE64);
  }

  public static SecretKey hmac() {
    return CryptoKeys.hmac(HMAC_BASE64);
  }
}
