package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.model.ErrorReport;
import bio.terra.common.exception.AbstractGlobalExceptionHandler;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler<ErrorReport> {

  @Override
  public ErrorReport generateErrorReport(Throwable ex, HttpStatus statusCode, List<String> causes) {
    return new ErrorReport().message(ex.getMessage()).statusCode(statusCode.value()).causes(causes);
  }
}
