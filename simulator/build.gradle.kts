description = "가상 카드사: 전문 서버 + 매입 파일 생성기"

dependencies {
  // core는 의존하지 않는다. 시뮬레이터는 PG 내부(도메인, DB)를 몰라야 한다 (ADR-0002).
  implementation(project(":codec"))
}
