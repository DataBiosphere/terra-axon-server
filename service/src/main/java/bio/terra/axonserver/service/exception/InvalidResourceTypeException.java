package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidResourceTypeException extends BadRequestException {
  public InvalidResourceTypeException(String message) {
    super(message);
  }

  public InvalidResourceTypeException(String message, Throwable cause) {
    super(message, cause);
  }
}
