package io.github.kkw855.settlement.online.error;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

/**
 * 오류를 RFC 9457 Problem Details + {@code code}로 돌려준다 (docs/api/openapi.yaml의 Problem).
 *
 * <p>{@link ResponseEntityExceptionHandler}를 상속해 Spring MVC 표준 예외(405, 415 등)도 Problem Details로 나가게 한다.
 * 그쪽은 Spring 기본 응답이라 {@code code}가 없다.
 *
 * <p>이 클래스가 없으면 Spring의 기본 처리기가 검증 예외를 WARN 로그로 남기는데, 그 메시지에 거부된 값(카드번호일 수 있다)이
 * 들어 있다 (docs/03-api.md 7장)
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** errors 배열의 원소. 입력값(rejected value)은 담지 않는다. */
  record FieldViolation(String field, String reason) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApiException(ApiException e) {
    ResponseEntity.BodyBuilder response = ResponseEntity.status(e.code().status());
    if (e.code() == ErrorCode.PAYMENT_IN_PROGRESS) {
      response.header(HttpHeaders.RETRY_AFTER, "5"); // 처음 요청이 끝날 때쯤 다시 오라는 안내 (docs/03-api.md 3장)
    }
    return response.body(problem(e.code(), e.getMessage()));
  }

  /**
   * 예상치 못한 예외. 스택 트레이스는 로그에만 남기고, 응답에는 내부 정보를 넣지 않는다.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
    log.error("처리하지 못한 예외", e);
    return ResponseEntity.internalServerError().body(problem(ErrorCode.INTERNAL_ERROR, "일시적인 오류가 발생했다"));
  }

  /** {@code @Valid} 실패. 필드명과 이유만 담는다. 순서를 고정해 같은 요청에 늘 같은 응답이 나가게 한다. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
    HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
      .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
      .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::reason))
      .toList();
    ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "요청 형식이 맞지 않다");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  /** JSON 문법 오류나 타입 불일치. 예외 메시지에 입력값이 들어 있을 수 있어 응답에 옮기지 않는다 */
  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
    HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return ResponseEntity.badRequest().body(problem(ErrorCode.VALIDATION_FAILED,
      "요청 본문을 읽을 수 없다 (JSON 문법과 값의 타입을 확인)"));
  }

  private static ProblemDetail problem(ErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    problem.setProperty("code", code.name());
    return problem;
  }
}
