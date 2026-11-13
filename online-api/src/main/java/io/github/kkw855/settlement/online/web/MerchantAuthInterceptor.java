package io.github.kkw855.settlement.online.web;

import io.github.kkw855.settlement.core.merchant.ApiKeys;
import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.core.merchant.MerchantMapper;
import io.github.kkw855.settlement.online.error.ApiException;
import io.github.kkw855.settlement.online.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * X-Api-Key로 가맹점을 찾아 요청 속성에 넣는다 (docs/03-api.md 6장).
 * 컨트롤러는 {@code @RequestAttribute(MERCHANT)}로 받는다.
 *
 * <ul>
 *   <li>인터셉터는 컨트롤러 매개변수(요청 본문 포함)를 만들기 전에 돈다.
 *   그래서 인증(401)이 늘 본문 검증(400)보다 먼저다.
 *   <li>이 인터셉터가 빠지면 {@code @RequestAttribute}가 비어서 요청이 실패한다 (fail closed).
 *   가맹점을 요청 파라미터로 만들어 내는 길이 없다.
 *   <li>정지된 가맹점은 여기서 막지 않는다. 조회는 허용하고 승인·취소만 막아야 해서 서비스가 판단한다.
 * </ul>
 */
public final class MerchantAuthInterceptor implements HandlerInterceptor {

  public static final String API_KEY_HEADER = "X-Api-Key";
  public static final String MERCHANT = "authenticatedMerchant";

  private final MerchantMapper merchantMapper;

  public MerchantAuthInterceptor(MerchantMapper merchantMapper) {
    this.merchantMapper = merchantMapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (apiKey == null || apiKey.isBlank()) {
      throw unauthorized();
    }
    Merchant merchant = merchantMapper.findByApiKeyHash(ApiKeys.hash(apiKey))
      .orElseThrow(MerchantAuthInterceptor::unauthorized);
    request.setAttribute(MERCHANT, merchant);
    return true;
  }

  /** 키가 없는 경우와 틀린 경우를 구분하지 않는다. 어느 쪽이든 가맹점이 할 일은 같다. */
  private static ApiException unauthorized() {
    return new ApiException(ErrorCode.UNAUTHORIZED, "API 키가 없거나 맞지 않다");
  }
}
