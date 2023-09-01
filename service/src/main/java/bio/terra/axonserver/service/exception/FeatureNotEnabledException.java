package bio.terra.axonserver.service.exception;

import bio.terra.common.exception.NotImplementedException;

// TODO(BENCH-1050) use FeatureNotSupportedException from TCL

public class FeatureNotEnabledException extends NotImplementedException {
  public FeatureNotEnabledException(String message) {
    super(message);
  }

  public FeatureNotEnabledException(String message, Throwable cause) {
    super(message, cause);
  }
}
