package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.BadRequestException;

public class MalformedPropertyException extends BadRequestException {
  public MalformedPropertyException(String message) {
    super(message);
  }

  public MalformedPropertyException(String message, Throwable cause) {
    super(message, cause);
  }
}
