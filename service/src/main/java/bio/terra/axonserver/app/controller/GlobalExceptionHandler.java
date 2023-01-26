package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.model.ApiErrorReport;
import bio.terra.common.exception.AbstractGlobalExceptionHandler;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler<ApiErrorReport> {

  @Override
  public ApiErrorReport generateErrorReport(
      Throwable ex, HttpStatus statusCode, List<String> causes) {
    return new ApiErrorReport()
        .message(ex.getMessage())
        .statusCode(statusCode.value())
        .causes(causes);
  }
}
