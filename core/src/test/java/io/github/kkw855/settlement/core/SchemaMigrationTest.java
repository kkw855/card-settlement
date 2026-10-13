package io.github.kkw855.settlement.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.sql.*;

/**
 * V1 마이그레이션을 실제 Oracle(compose와 같은 이미지)에 적용하고, DB가 지키는 불변식을 확인한다.
 *
 * <p>테스트마다 자동 커밋을 끄고 끝나면 롤백해서 서로 영향을 주지 않는다.
 */
@Testcontainers
class SchemaMigrationTest {

  @Container
  static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim");

  static MigrateResult migration;

  Connection connection;

  @BeforeAll
  static void migrate() {
    migration = Flyway.configure()
      .dataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword())
      .load()
      .migrate();
  }

  @BeforeEach
  void open() throws SQLException {
    connection = DriverManager.getConnection(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword());
    connection.setAutoCommit(false);
  }

  @AfterEach
  void rollback() throws SQLException {
    connection.rollback();
    connection.close();
  }

  @Test
  @DisplayName("M1 V1이 적용되고 테이블이 12개가 생긴다")
  void appliesV1() throws SQLException {
    assertThat(migration.success).isTrue();
    assertThat(migration.migrationsExecuted).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM user_tables WHERE table_name <> 'flyway_schema_history'"))
      .isEqualTo(12);
  }

  @Test
  @DisplayName("M2 같은 가맹점의 같은 Idempotency-Key는 한 번만 들어간다 (I2)")
  void rejectsDuplicateIdempotencyKey() throws SQLException {
    long merchantId = insertMerchant("M000000001");
    insertPayment(merchantId, "P2026100800000000001", "key-1");

    assertThatThrownBy(() -> insertPayment(merchantId, "P2026100800000000002", "key-1"))
      .isInstanceOf(SQLIntegrityConstraintViolationException.class)
      .hasMessageContaining("UK_PAYMENT_IDEMPOTENCY");
  }

  @Test
  @DisplayName("M3 실패한 취소 뒤에는 다시 취소할 수 있지만, 진행 중·성공한 취소는 하나뿐이다")
  void allowsRetryAfterFailedCancelOnly() throws SQLException {
    long merchantId = insertMerchant("M000000001");
    long paymentId = insertPayment(merchantId, "P2026100800000000001", "key-1");

    insertCancel(paymentId, "P2026100800000000011", "FAILED");
    insertCancel(paymentId, "P2026100800000000012", "NET_CANCELLED");
    insertCancel(paymentId, "P2026100800000000013", "CANCELLED");

    assertThatThrownBy(() -> insertCancel(paymentId, "P2026100800000000014", "REQUESTED"))
      .isInstanceOf(SQLIntegrityConstraintViolationException.class)
      .hasMessageContaining("UK_PAYMENT_CANCEL_ACTIVE");
  }

  @Test
  @DisplayName("M4 지급액이 '거래 - 취소 - 수수료 - 부가세'와 다르면 막는다(I6)")
  void rejectsWrongNetAmount() throws SQLException {
    long merchantId = insertMerchant("M000000001");

    insertSettlement(merchantId, "2026-10-08", 9734); // 10,100 - 0 - 333 - 33 = 9,734 (docs/01-erd.md 예시)

    assertThatThrownBy(() -> insertSettlement(merchantId, "2026-10-07", 9735))
      .isInstanceOf(SQLIntegrityConstraintViolationException.class)
      .hasMessageContaining("CK_SETTLEMENT_NET");
  }

  @Test
  @DisplayName("M5 한글 가맹점명 20자가 들어간다 (VARCHAR2 CHAR 단위)")
  void storesKoreanNameByCharacters() throws SQLException {
    long merchantId = insertMerchant("M000000001", "가".repeat(20));

    assertThat(queryLong("SELECT LENGTHB(name) FROM merchant WHERE merchant_id = %d".formatted(merchantId)))
      .isEqualTo(60); // AL32UTF8에서 한글 1자 = 3바이트
  }

  @Test
  @DisplayName("M6 날짜 컬럼에 시각이 붙으면 막는다")
  void rejectsTimeOnDateColumn() {
    assertThatThrownBy(() -> execute(
      "INSERT INTO holiday (holiday_date, name) VALUES (TIMESTAMP '2026-10-09 09:00:00', '한글날')"
    ))
      .isInstanceOf(SQLIntegrityConstraintViolationException.class)
      .hasMessageContaining("CK_HOLIDAY_DATE");
  }

  // ---------------------------------------------------------------- 테스트 데이터

  private long insertMerchant(String merchantNo) throws SQLException {
    return insertMerchant(merchantNo, "홍길동카페");
  }

  private long insertMerchant(String merchantNo, String name) throws SQLException {
    execute("""
        INSERT INTO merchant (merchant_no, name, settlement_cycle_days, payout_bank_code,
                              payout_account_enc, payout_account_masked, holder_name, status, api_key_hash)
        VALUES ('%s', '%s', 2, '004', 'enc', '123-****-7890', '홍길동', 'ACTIVE', '%s')
      """.formatted(merchantNo, name, "a".repeat(64)));
    return queryLong("SELECT merchant_id FROM merchant WHERE merchant_no = '%s'".formatted(merchantNo));
  }

  private long insertPayment(long merchantId, String txnId, String idempotencyKey) throws SQLException {
    execute("""
        INSERT INTO payment (txn_id, merchant_id, idempotency_key, card_enc, card_hash, card_masked,
                             installment_months, amount, status, result_code, approval_no, approved_at, requested_at)
        VALUES ('%s', %d, '%s', 'enc', '%s', '411111******1111',
                0, 10000, 'APPROVED', '0000', '30012345', SYSTIMESTAMP, SYSTIMESTAMP)
        """.formatted(txnId, merchantId, idempotencyKey, "b".repeat(64)));
    return queryLong("SELECT payment_id FROM payment WHERE txn_id = '%s'".formatted(txnId));
  }

  private void insertCancel(long paymentId, String cancelTxnId, String status) throws SQLException {
    execute("""
        INSERT INTO payment_cancel (payment_id, cancel_txn_id, amount, status)
        VALUES (%d, '%s', 10000, '%s')
        """.formatted(paymentId, cancelTxnId, status));
  }

  private void insertSettlement(long merchantId, String businessDate, long netAmount) throws SQLException {
    execute("""
        INSERT INTO settlement (merchant_id, business_date, payout_date, fee_rate, txn_count,
                                gross_amount, cancel_amount, fee_amount, vat_amount, net_amount)
        VALUES (%d, DATE '%s', DATE '2026-10-13', 0.033, 10, 10100, 0, 333, 33, %d)
        """.formatted(merchantId, businessDate, netAmount));
  }

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private long queryLong(String sql) throws SQLException {
    try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
