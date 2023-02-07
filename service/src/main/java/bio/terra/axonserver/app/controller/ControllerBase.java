package bio.terra.axonserver.app.controller;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import javax.servlet.http.HttpServletRequest;

/**
 * Super class for controllers containing common code. The code in here requires the @Autowired
 * beans from the @Controller classes, so it is better as a superclass rather than static methods.
 */
public class ControllerBase {

  private final HttpServletRequest request;
  private final BearerTokenFactory bearerTokenFactory;

  public ControllerBase(BearerTokenFactory bearerTokenFactory, HttpServletRequest request) {
    this.bearerTokenFactory = bearerTokenFactory;
    this.request = request;
  }

  public BearerToken getToken() {
    return bearerTokenFactory.from(request);
  }
}
