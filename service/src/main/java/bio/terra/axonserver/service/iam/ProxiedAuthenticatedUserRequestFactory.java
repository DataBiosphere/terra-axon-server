package bio.terra.axonserver.service.iam;

import bio.terra.axonserver.service.iam.AuthenticatedUserRequest.AuthType;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ProxiedAuthenticatedUserRequestFactory implements AuthenticatedUserRequestFactory {
  private static final String BEARER = "Bearer ";

  // Tries to create an AuthenticatedUserRequest from the requests bearer token, otherwise return an
  // empty AuthenticatedUserRequest
  public AuthenticatedUserRequest from(HttpServletRequest servletRequest) {
    return fromBearer(servletRequest)
        .orElse(new AuthenticatedUserRequest().token(Optional.empty()).authType(AuthType.NONE));
  }

  private Optional<AuthenticatedUserRequest> fromBearer(HttpServletRequest servletRequest) {
    String authHeader = servletRequest.getHeader(AuthHeaderKeys.AUTHORIZATION.getKeyName());
    if (StringUtils.startsWith(authHeader, BEARER)) {
      return Optional.of(
          new AuthenticatedUserRequest()
              .email(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_EMAIL.getKeyName()))
              .subjectId(servletRequest.getHeader(AuthHeaderKeys.OIDC_CLAIM_USER_ID.getKeyName()))
              .token(Optional.of(StringUtils.substring(authHeader, BEARER.length())))
              .authType(AuthType.BEARER));
    }

    return Optional.empty();
  }
}
