package io.github.kkw855.settlement.online;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.oracle.OracleContainer;

/**
 * 실제 Oracle(compose와 같은 이미지)을 띄워 앱 전체를 올리는 테스트의 공통 부모.
 *
 * <ul>
 *   <li>컨테이너는 static 블록에서 한 번만 띄운다. 이 클래스를 상속한 테스트 클래스들이 같은 DB를 함께
 *   쓴다 (테스트 클래스마다 띄우면 매번 10초씩 걸린다).
 *   <li>{@code @ServiceConnection}이 컨테이너의 JDBC URL·계정을 DataSource에 연결한다.
 *   application.yaml의 값보다 우선한다.
 *   <li>키는 테스트 전용 값이다. 운영 키와 같으면 안 된다.
 * </ul>
 */
@SpringBootTest(properties = {
  "crypto.card-encryption-key=Kz8XPJpTi06KBG3iledWAfT9oB+2nQnf+qvkHNqXOX4=",
  "crypto.card-hmac-key=mSk0cg04hDjDSg6RJ/x7IpZb4yJGCWDZGZRvnqnRXFk="
})
public abstract class IntegrationTest {

  @ServiceConnection
  static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim");

  static {
    ORACLE.start(); // JVM이 끝날 때 Testcontainers가 지운다
  }
}
