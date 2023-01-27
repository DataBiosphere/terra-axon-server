package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.service.iam.AuthenticatedUserRequest;
import bio.terra.axonserver.service.iam.AuthenticatedUserRequestFactory;
import javax.servlet.http.HttpServletRequest;

/**
 * Super class for controllers containing common code. The code in here requires the @Autowired
 * beans from the @Controller classes, so it is better as a superclass rather than static methods.
 */
public class ControllerBase {
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  public ControllerBase(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory, HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  public AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  public String getAuthToken() {
    return this.request.getHeader("Authorization");
  }
}
