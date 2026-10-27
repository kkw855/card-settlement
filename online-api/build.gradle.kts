import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  alias(libs.plugins.spring.boot)
}

description = "승인·취소 REST API + 카드사 전문 클라이언트"

dependencies {
  implementation(project(":core"))
  implementation(project(":codec"))

  implementation(libs.spring.boot.starter.webmvc)
  implementation(libs.mybatis.spring.boot.starter)  // DataSource(HikariCP)와 JDBC도 함께 들어온다
  implementation(libs.spring.boot.starter.flyway)   // 기동할 때 V1을 적용한다 (ADR-0006)
  runtimeOnly(libs.flyway.database.oracle)
  runtimeOnly(libs.ojdbc17)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.oracle.free)
}

// 루트에서 실행해야 application.yaml의 optional:file:.env[.properties]가 루트의 .env를 찾는다
tasks.named<BootRun>("bootRun") {
  workingDir = rootDir
}
