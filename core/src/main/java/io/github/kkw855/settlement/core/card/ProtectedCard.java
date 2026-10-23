package io.github.kkw855.settlement.core.card;

/**
 * DB에 저장하는 카드번호의 세 가지 형태 (ADR-0005).
 *
 * @param encrypted AES-256-GCM 암호문. 원문이 꼭 필요할 때만 복호화한다 (payment.card_enc)
 * @param hash HMAC-SHA256 검색용 해시. 같은 카드인지 비교한다 (payment.card_hash)
 * @param masked 화면·로그·매입 파일 비교용 (payment.card_masked)
 */
public record ProtectedCard(String encrypted, String hash, String masked) {}
