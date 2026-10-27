package io.github.kkw855.settlement.online;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 실행: {@code ./gradlew :online-api:bootRun} (compose의 Oracle이 떠 있어야 한다). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OnlineApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(OnlineApiApplication.class, args);
  }
}
