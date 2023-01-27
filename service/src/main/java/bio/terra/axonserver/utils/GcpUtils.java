package bio.terra.axonserver.utils;

import bio.terra.axonserver.service.iam.AuthenticatedUserRequest;
import bio.terra.stairway.Step;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

/** Utilities for interacting with Google Cloud APIs within {@link Step}s. */
public class GcpUtils {

  private GcpUtils() {}

  public static GoogleCredentials getGoogleCredentialsFromUserRequest(
      AuthenticatedUserRequest userRequest) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(userRequest.getRequiredToken(), null);
    return GoogleCredentials.create(accessToken);
  }
}
