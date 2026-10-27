package io.github.kkw855.settlement.online;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kkw855.settlement.core.card.CardProtector;
import io.github.kkw855.settlement.online.card.CardCompanyClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OnlineApiApplicationTest extends IntegrationTest {

  @Autowired
  Flyway flyway;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  CardCompanyClient cardCompanyClient;
  @Autowired
  CardProtector cardProtector;

  @Test
  @DisplayName("A1 앱이 뜨면서 Flyway가 V1을 적용한다")
  void appliesMigrationOnStartup() {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name = 'PAYMENT'", Integer.class))
      .isEqualTo(1);
  }

  @Test
  @DisplayName("A2 core와 카드사 클라이언트가 빈으로 등록된다")
  void registersBeans() {
    assertThat(cardCompanyClient).isNotNull();
    assertThat(cardProtector).isNotNull();
  }
}
