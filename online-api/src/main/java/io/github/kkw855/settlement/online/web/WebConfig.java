package io.github.kkw855.settlement.online.web;

import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC 설정. /v1 아래 모든 요청은 API 키로 가맹점을 확인한다. */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

  private final MerchantMapper merchantMapper;

  WebConfig(MerchantMapper merchantMapper) {
    this.merchantMapper = merchantMapper;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new MerchantAuthInterceptor(merchantMapper)).addPathPatterns("/v1/**");
  }
}
