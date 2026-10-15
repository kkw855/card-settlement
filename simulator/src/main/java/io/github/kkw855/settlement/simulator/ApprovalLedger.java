package io.github.kkw855.settlement.simulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 카드사가 승인한 거래 기록. 취소·망취소(2-3)와 매입 파일 생성(Phase 3)이 이 기록을 본다.
 *
 * <p>여러 연결을 가상 스레드가 동시에 처리하므로 {@link java.util.concurrent.ConcurrentHashMap}을 쓴다. v1은 메모리에만 둔다.
 */
final class ApprovalLedger {

  /** 카드번호는 마스킹 값만 남긴다. 매입 파일에도 마스킹 값이 나간다. */
  record Approval(
    String txnId,
    String approvalNo,
    String merchantNo,
    String cardMasked,
    long amount,
    LocalDateTime approvedAt,
    Scenario scenario
  ) {}

  private final Map<String, Approval> approvals = new ConcurrentHashMap<>();

  void record(Approval approval) {
    approvals.put(approval.txnId(), approval);
  }

  Optional<Approval> find(String txnId) {
    return Optional.ofNullable(approvals.get(txnId));
  }

  List<Approval> all() {
    return List.copyOf(approvals.values());
  }
}
