package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidConvertToFormat extends BadRequestException {
  public InvalidConvertToFormat(String message) {
    super(message);
  }

  public InvalidConvertToFormat(String message, Throwable cause) {
    super(message, cause);
  }
}
