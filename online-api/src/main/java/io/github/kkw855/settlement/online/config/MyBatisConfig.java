package io.github.kkw855.settlement.online.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * core의 매퍼 인터페이스를 등록한다. 매퍼는 online-api와 batch가 함께 쓰므로 core에 있다 (ADR-0002).
 *
 * <p>core 패키지 아래의 <b>모든 인터페이스</b>가 매퍼로 등록된다. core에 매퍼가 아닌 인터페이스가 생기면
 * {@code annotationClass}나 {@code markerInterface}로 범위를 좁힌다.
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("io.github.kkw855.settlement.core")
class MyBatisConfig {}
