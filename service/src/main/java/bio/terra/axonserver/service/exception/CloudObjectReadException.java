package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class CloudObjectReadException extends InternalServerErrorException {
  public CloudObjectReadException(String message) {
    super(message);
  }

  public CloudObjectReadException(String message, Throwable cause) {
    super(message, cause);
  }
}
