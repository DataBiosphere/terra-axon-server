package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidWdlException extends BadRequestException {
  public InvalidWdlException(String message) {
    super(message);
  }

  public InvalidWdlException(String message, Throwable cause) {
    super(message, cause);
  }
}
