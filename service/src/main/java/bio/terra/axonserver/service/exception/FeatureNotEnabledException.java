package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.NotImplementedException;

public class FeatureNotEnabledException extends NotImplementedException {
  public FeatureNotEnabledException(String message) {
    super(message);
  }

  public FeatureNotEnabledException(String message, Throwable cause) {
    super(message, cause);
  }
}
