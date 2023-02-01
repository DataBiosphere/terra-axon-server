package bio.terra.axonserver.utils;

import bio.terra.stairway.Step;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Utilities for interacting with Google Cloud APIs within {@link Step}s. */
public class GcpUtils {

  private GcpUtils() {}

  // Google pet service account scopes for accessing Google Cloud APIs.
  private static final List<String> PET_SA_SCOPES =
      ImmutableList.of(
          "openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");

  public static List<String> getPetScopes() {
    return PET_SA_SCOPES;
  }

  public static GoogleCredentials getGoogleCredentialsFromToken(String token) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(token, null);
    return GoogleCredentials.create(accessToken);
  }
}
