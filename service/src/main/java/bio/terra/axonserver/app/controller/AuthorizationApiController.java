package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AuthorizationApi;
import bio.terra.axonserver.model.ApiTokenReport;
import bio.terra.common.exception.ApiException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.math.BigDecimal;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AuthorizationApiController implements AuthorizationApi {
  private final HttpServletRequest servletRequest;

  @Value("${AXON_UI_CLIENT_ID}")
  private static String AXON_UI_CLIENT_ID;

  @Value("${AXON_UI_CLIENT_SECRET}")
  private static String AXON_UI_CLIENT_SECRET;

  @Value("${AXON_UI_CLIENT_ID}")
  public void setClientId(String clientId) {
    AuthorizationApiController.AXON_UI_CLIENT_ID = clientId;
  }

  @Value("${AXON_UI_CLIENT_SECRET}")
  public void setClientSecret(String clientSecret) {
    AuthorizationApiController.AXON_UI_CLIENT_SECRET = clientSecret;
  }

  @Autowired
  public AuthorizationApiController(HttpServletRequest servletRequest) {
    this.servletRequest = servletRequest;
  }

  @Override
  public ResponseEntity<ApiTokenReport> exchangeAuthCode(String authCode) {
    var request =
        new GoogleAuthorizationCodeTokenRequest(
            new NetHttpTransport(),
            new GsonFactory(),
            AXON_UI_CLIENT_ID,
            AXON_UI_CLIENT_SECRET,
            authCode,
            "postmessage");
    try {
      var result = buildApiTokenResult(request.execute());
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException(e.getMessage(), e);
    }
  }

  @Override
  public ResponseEntity<ApiTokenReport> getRefreshedAccessToken(String refreshToken) {
    var request =
        new GoogleRefreshTokenRequest(
            new NetHttpTransport(),
            new GsonFactory(),
            refreshToken,
            AXON_UI_CLIENT_ID,
            AXON_UI_CLIENT_SECRET);
    try {
      var result = buildApiTokenResult(request.execute());
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException(e.getMessage(), e);
    }
  }

  private ApiTokenReport buildApiTokenResult(GoogleTokenResponse response) {
    return new ApiTokenReport()
        .accessToken(response.getAccessToken())
        .expiresIn(BigDecimal.valueOf(response.getExpiresInSeconds()))
        .tokenType(response.getTokenType())
        .scope(response.getScope())
        .refreshToken(response.getRefreshToken())
        .idToken(response.getIdToken());
  }
}
