package bio.terra.axonserver.utils;

import bio.terra.stairway.Step;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

/** Utilities for interacting with Google Cloud APIs within {@link Step}s. */
public class GcpUtils {

  private GcpUtils() {}

  public static GoogleCredentials getGoogleCredentialsFromToken(String token) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(token, null);
    return GoogleCredentials.create(accessToken);
  }
}
