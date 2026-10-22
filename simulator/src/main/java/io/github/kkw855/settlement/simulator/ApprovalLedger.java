package io.github.kkw855.settlement.simulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 카드사가 승인한 거래 기록. 취소·망취소와 매입 파일 생성(Phase 3)이 이 기록을 본다.
 *
 * <p>상태를 바꾸는 판단(읽고 -> 확인하고 -> 저장)은 {@link CardCompany}가 잠금 안에서 한다.
 * 이 클래스는 저장만 하고, 다른 스레드가 언제 읽어도 안전하도록 {@link ConcurrentHashMap}을 쓴다. v1은 메모리에만 둔다.
 */
final class ApprovalLedger {

  enum Status {
    APPROVED,   // 승인됨 (취소가 망취소되어 되돌아온 경우 포함)
    CANCELLED,  // 0400으로 취소됨
    VOIDED      // 0420 망취소로 승인 자체가 무효가 됨
  }

  /** 취소 기록. 취소가 망취소되면 지운다 */
  record Cancel(String cancelTxnId, String approvalNo, LocalDateTime cancelledAt) {}

  /** 카드번호는 마스킹 값만 남긴다. 매입 파일에도 마스킹 값이 나간다. */
  record Approval(
    String txnId,
    String approvalNo,
    String merchantNo,
    String cardMasked,
    long amount,
    LocalDateTime approvedAt,
    Scenario scenario,
    Status status,
    Cancel cancel
  ) {

    Approval cancelled(Cancel cancel) {
      return new Approval(txnId, approvalNo, merchantNo, cardMasked, amount, approvedAt, scenario, Status.CANCELLED, cancel);
    }

    Approval voided() {
      return new Approval(txnId, approvalNo, merchantNo, cardMasked, amount, approvedAt, scenario, Status.VOIDED, cancel);
    }

    /** 취소가 망취소되어 승인 상태로 돌아간다. */
    Approval cancelReverted() {
      return new Approval(txnId, approvalNo, merchantNo, cardMasked, amount, approvedAt, scenario, Status.APPROVED, null);
    }
  }

  private final Map<String, Approval> approvals = new ConcurrentHashMap<>();

  /** 새 승인이면 추가하고, 같은 거래고유번호가 있으면 바뀐 상태로 덮어쓴다 */
  void save(Approval approval) {
    approvals.put(approval.txnId(), approval);
  }

  Optional<Approval> find(String txnId) {
    return Optional.ofNullable(approvals.get(txnId));
  }

  /**
   * 취소 거래고유번호로 찾는다. 취소의 망취소에만 쓰고 드물어서 전체를 훑는다.
   */
  Optional<Approval> findByCancelTxnId(String cancelTxnId) {
    return approvals.values().stream()
      .filter(approval -> approval.cancel() != null && approval.cancel().cancelTxnId().equals(cancelTxnId))
      .findFirst();
  }

  List<Approval> all() {
    return List.copyOf(approvals.values());
  }
}
