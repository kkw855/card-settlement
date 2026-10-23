package io.github.kkw855.settlement.core.card;

import io.github.kkw855.settlement.core.crypto.AesGcmEncryptor;
import io.github.kkw855.settlement.core.crypto.HmacHasher;

/** 카드번호를 저장할 형태로 바꾸고, 필요할 때 되돌린다. 여러 스레드가 함께 써도 된다. */
public final class CardProtector {

  private final AesGcmEncryptor encryptor;
  private final HmacHasher hasher;

  public CardProtector(AesGcmEncryptor encryptor, HmacHasher hasher) {
    this.encryptor = encryptor;
    this.hasher = hasher;
  }

  public ProtectedCard protect(CardNumber cardNumber) {
    return new ProtectedCard(encryptor.encrypt(cardNumber.digits()), hash(cardNumber), cardNumber.masked());
  }

  /**
   * 같은 카드인지 비교할 때 쓴다 (예: 같은 Idempotency-Key의 본문이 같은지, docs/03-api.md 3장).
   */
  public String hash(CardNumber card) {
    return hasher.hash(card.digits());
  }

  public CardNumber reveal(String encrypted) {
    return new CardNumber(encryptor.decrypt(encrypted));
  }
}
