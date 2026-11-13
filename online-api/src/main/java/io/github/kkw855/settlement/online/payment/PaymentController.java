package io.github.kkw855.settlement.online.payment;

import io.github.kkw855.settlement.online.error.ApiException;
import io.github.kkw855.settlement.online.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.github.kkw855.settlement.core.merchant.Merchant;
import io.github.kkw855.settlement.online.web.MerchantAuthInterceptor;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * 승인·조회 API (docs/03-api.md, docs/api/openapi.yaml).
 *
 * <p>{@link Merchant}는 {@link MerchantAuthInterceptor}가 X-Api-Key로 찾아 둔 가맹점이다.
 * 이 메서드가 불릴 때는 인증이 이미 끝나 있다.
 */
@RestController
@RequestMapping("/v1/payments")
class PaymentController {

  /** 공백을 뺀 출력 가능한 ASCII 1~64자 (docs/api/openapi.yaml). DB 컬럼은 VARCHAR2(64) */
  private static final Pattern IDEMPOTENCY_KEY_FORMAT = Pattern.compile("[\\x21-\\x7E]{1,64}");

  private final ApprovalService approvalService;
  private final PaymentQueryService paymentQueryService;

  PaymentController(ApprovalService approvalService, PaymentQueryService paymentQueryService) {
    this.approvalService = approvalService;
    this.paymentQueryService = paymentQueryService;
  }

  /** 카드사까지 간 요청은 거절·미확정이어도 201이다. 성공 여부는 본문의 status로 판단한다 (docs/03-api.md 1장). */
  @SuppressWarnings("UastIncorrectHttpHeaderInspection")
  @PostMapping
  ResponseEntity<PaymentResponse> approve(
    @RequestAttribute(MerchantAuthInterceptor.MERCHANT) Merchant merchant,
    @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
    @Valid @RequestBody ApprovePaymentRequest request
  ) {
    ApprovalOutcome outcome = approvalService.approve(merchant,
      requireIdempotencyKey(idempotencyKey), request.toCommand());
    PaymentResponse body = PaymentResponse.from(outcome.payment());

    ResponseEntity.BodyBuilder response = ResponseEntity.created(URI.create("/v1/payments/" + body.txnId()));
    if (outcome instanceof ApprovalOutcome.Replayed) {
      response.header("Idempotent-Replayed", "true");
    }
    return response.body(body);
  }

  /** 정지된 가맹점도 조회는 할 수 있다. 정지 전 거래를 확인해야 하기 때문이다. */
  @GetMapping("/{txnId}")
  PaymentResponse get(
    @RequestAttribute(MerchantAuthInterceptor.MERCHANT) Merchant merchant,
    @PathVariable String txnId
  ) {
    return PaymentResponse.from(paymentQueryService.get(merchant, txnId));
  }

  /** 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED, 형식이 틀리면 400 VALIDATION_FAILED */
  private static String requireIdempotencyKey(String key) {
    if (key == null || key.isEmpty()) {
      throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key 헤더가 필요하다");
    }
    if (!IDEMPOTENCY_KEY_FORMAT.matcher(key).matches()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key는 공백 없는 ASCII 1~64자여야 한다");
    }
    return key;
  }
}
