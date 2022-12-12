package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.UnauthorizedException;

public class InvalidPropertyException extends UnauthorizedException {
  public InvalidPropertyException(String message) {
    super(message);
  }

  public InvalidPropertyException(String message, Throwable cause) {
    super(message, cause);
  }
}
