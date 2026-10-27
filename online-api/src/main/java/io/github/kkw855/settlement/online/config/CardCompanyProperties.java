package io.github.kkw855.settlement.online.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** 카드사 연결 설정 (application.yaml의 card-company). 타임아웃 값은 docs/02-message-spec.md 1장 */
@ConfigurationProperties("card-company")
public record CardCompanyProperties(String host, int port, Duration connectTimeout, Duration readTimeout) {
  /** 키 이름이 틀리면 Spring은 오류 없이 null을 넣는다. 그대로 뜨지 않게 여기서 막는다 */
  public CardCompanyProperties {
    Objects.requireNonNull(host, "card-company.host가 없다");
    Objects.requireNonNull(connectTimeout, "card-company.connect-timeout이 없다");
    Objects.requireNonNull(readTimeout, "card-company.read-timeout이 없다");
    if (port <= 0) {
      throw new IllegalArgumentException("card-company.port가 없거나 잘못됐다");
    }
  }
}
