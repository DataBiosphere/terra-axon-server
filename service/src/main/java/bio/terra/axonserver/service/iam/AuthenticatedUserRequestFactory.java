package bio.terra.axonserver.service.iam;

import javax.servlet.http.HttpServletRequest;

public interface AuthenticatedUserRequestFactory {

  public AuthenticatedUserRequest from(HttpServletRequest servletRequest);
}
