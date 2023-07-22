package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.NotFoundException;

public class ComponentNotFoundException extends NotFoundException {
  public ComponentNotFoundException(String message) {
    super(message);
  }

  public ComponentNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
