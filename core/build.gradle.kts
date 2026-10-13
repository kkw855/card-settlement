description = "도메인, MyBatis 매퍼, 공통 서비스"

dependencies {
  // 마이그레이션 SQL은 src/main/resources/db/migration에 둔다. 실행은 online-api가 기동할 때 한다 (ADR-0006).
  // 여기서는 테스트에서만 Flyway로 실제 Oracle에 적용해 본다.
  testImplementation(libs.flyway.core)
  testRuntimeOnly(libs.flyway.database.oracle)
  testRuntimeOnly(libs.ojdbc17)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.oracle.free)
}
