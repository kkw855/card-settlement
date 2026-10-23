package io.github.kkw855.settlement.core.crypto;

/** 암호화·복호화·해시에 실패했다. 메시지에 평문이나 키를 넣지 않는다 (I10). */
public final class CryptoException extends RuntimeException {

  public CryptoException(String message) {
    super(message);
  }

  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
