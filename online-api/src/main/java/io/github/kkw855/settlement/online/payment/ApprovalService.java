package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.core.card.CardProtector;
import io.github.kkw855.settlement.core.card.ProtectedCard;
import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.payment.Payment;
import io.github.kkw855.settlement.core.payment.PaymentMapper;
import io.github.kkw855.settlement.core.payment.PaymentStatus;
import io.github.kkw855.settlement.core.payment.TxnIdGenerator;
import io.github.kkw855.settlement.online.card.ApprovalGateway;
import io.github.kkw855.settlement.online.card.ApprovalGateway.Result;
import io.github.kkw855.settlement.online.card.ApprovalRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 승인 처리 (docs/03-api.md 3~4장).
 *
 * <pre>
 * 1. REQUESTED 저장 (커밋)     ← 같은 키면 DuplicateKeyException → 기존 결과로 분기
 * 2. 카드사 호출 (트랜잭션 없음)   ← 최대 수 초가 걸린다
 * 3. 결과 반영 (커밋)           ← UPDATE ... WHERE status = 'REQUESTED'
 * </pre>
 *
 * <p><b>이 클래스에 {@code @Transactional}을 붙이지 않는다.</b> 붙이면 카드사를 기다리는 동안 DB 커넥션을 붙잡는다.
 * 트랜잭션이 없으면 매퍼 호출 하나하나가 따로 커밋된다. 1번과 3번이 각각 SQL 한 문장이라 그것으로 충분하다.
 */
@Service
public class ApprovalService {

  private static final long MIN_INSTALLMENT_AMOUNT = 50_000;

  private final PaymentMapper paymentMapper;
  private final TxnIdGenerator txnIdGenerator;
  private final CardProtector cardProtector;
  private final ApprovalGateway gateway;
  private final Clock clock;

  public ApprovalService(PaymentMapper paymentMapper, TxnIdGenerator txnIdGenerator, CardProtector cardProtector,
                         ApprovalGateway gateway, Clock clock) {
    this.paymentMapper = paymentMapper;
    this.txnIdGenerator = txnIdGenerator;
    this.cardProtector = cardProtector;
    this.gateway = gateway;
    this.clock = clock;
  }

  public ApprovalOutcome approve(Merchant merchant, String idempotencyKey, ApprovalCommand command) {
    if (command.installmentMonths() > 0 && command.amount() < MIN_INSTALLMENT_AMOUNT) {
      throw new PaymentException(ErrorCode.INSTALLMENT_NOT_ALLOWED,
        String.format(Locale.KOREA, "할부는 %,d원 이상만 가능하다", MIN_INSTALLMENT_AMOUNT));
    }

    // 1. 먼저 저장한다. 같은 키가 있으면 UNIQUE 제약이 막는다 (조회 먼저 하지 않는다: 동시 요청에도 안전)
    String txnId = txnIdGenerator.next();
    ProtectedCard card = cardProtector.protect(command.card());
    try {
      paymentMapper.insert(Payment.requested(txnId, merchant.merchantId(), idempotencyKey,
        card.encrypted(), card.hash(), card.masked(),
        command.installmentMonths(), command.amount(), LocalDateTime.now(clock)));
    } catch (DuplicateKeyException e) {
      return replay(merchant, idempotencyKey, card.hash(), command, e);
    }

    // 2. 카드사 호출 (트랜잭션 밖)
    Result result = gateway.approve(new ApprovalRequest(txnId,
      merchant.merchantNo(), merchant.name(),
      command.card(), command.expiry(), command.installmentMonths(), command.amount()));

    // 3. 결과 반영
    int updated = switch (result) {
      case Result.Approved(String approvalNo, LocalDateTime approvedAt) ->
        paymentMapper.approve(txnId, approvalNo, approvedAt, ResultCodes.APPROVED);
      case Result.Declined(String responseCode) ->
        paymentMapper.changeStatus(txnId, PaymentStatus.REQUESTED,
          PaymentStatus.DECLINED, responseCode);
      case Result.NotSent() ->
        paymentMapper.changeStatus(txnId, PaymentStatus.REQUESTED,
          PaymentStatus.DECLINED, ResultCodes.NOT_SENT);
      case Result.Unknown(String reason) -> // 망취소는 2-7에서 붙인다. 지금은 미확정으로 남긴다
        paymentMapper.changeStatus(txnId, PaymentStatus.REQUESTED,
          PaymentStatus.UNKNOWN, ResultCodes.UNRESOLVED);
    };
    if (updated != 1) {
      throw new IllegalStateException("REQUESTED가 아닌 결제의 결과를 기록하려 했다: " + txnId);
    }
    return new ApprovalOutcome.Processed(paymentMapper.findByTxnId(txnId).orElseThrow());
  }

  /** 같은 키의 결제가 이미 있다. 본문이 같으면 그 결과를, 다르면 거절한다 (docs/03-api.md 3장). */
  private ApprovalOutcome replay(Merchant merchant, String idempotencyKey, String cardHash,
                                 ApprovalCommand command, DuplicateKeyException cause) {
    Payment existing = paymentMapper.findByIdempotencyKey(merchant.merchantId(), idempotencyKey)
      .orElseThrow(() -> cause); // 멱등 키가 아닌 다른 제약(거래고유번호 중복)이면 그대로 올린다

    boolean sameBody = existing.cardHash().equals(cardHash)
      && existing.amount() == command.amount()
      && existing.installmentMonths() == command.installmentMonths();
    if (!sameBody) {
      throw new PaymentException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 Idempotency-Key에 다른 요청 본문");
    }
    if (existing.status() == PaymentStatus.REQUESTED) {
      throw new PaymentException(ErrorCode.PAYMENT_IN_PROGRESS, "같은 Idempotency-Key의 요청을 처리하고 있다");
    }
    return new ApprovalOutcome.Replayed(existing);
  }
}
