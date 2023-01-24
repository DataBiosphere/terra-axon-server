package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AuthorizationApi;
import bio.terra.axonserver.model.AnyObject;
// import bio.terra.axonserver.model.AuthTokens;
// import bio.terra.axonserver.model.RefreshedToken;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
  private String AXON_UI_CLIENT_ID;

  @Value("${AXON_UI_CLIENT_SECRET}")
  private String AXON_UI_CLIENT_SECRET;

  @Autowired
  public AuthorizationApiController(HttpServletRequest servletRequest) {
    this.servletRequest = servletRequest;
  }

  @Override
  public ResponseEntity<AnyObject> exchangeAuthCode(String authCode) {
    try {
      GoogleTokenResponse response =
          new GoogleAuthorizationCodeTokenRequest(
                  new NetHttpTransport(),
                  new GsonFactory(),
                  AXON_UI_CLIENT_ID,
                  AXON_UI_CLIENT_SECRET,
                  authCode,
                  "postmessage")
              .execute();

      return new ResponseEntity<>(new AnyObject().value(response), HttpStatus.OK);
    } catch (TokenResponseException e) {
      return new ResponseEntity<>(
          new AnyObject().value(e.getDetails()),
          e.getStatusCode() > 0 ? HttpStatus.valueOf(e.getStatusCode()) : HttpStatus.BAD_REQUEST);

    } catch (Exception e) {
      return new ResponseEntity<>(new AnyObject().value(e.getMessage()), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public ResponseEntity<AnyObject> getRefreshedAccessToken(String refreshToken) {
    try {
      TokenResponse response =
          new GoogleRefreshTokenRequest(
                  new NetHttpTransport(),
                  new GsonFactory(),
                  refreshToken,
                  AXON_UI_CLIENT_ID,
                  AXON_UI_CLIENT_SECRET)
              .execute();

      return new ResponseEntity<>(new AnyObject().value(response), HttpStatus.OK);
    } catch (TokenResponseException e) {
      return new ResponseEntity<>(
          new AnyObject().value(e.getDetails()),
          e.getStatusCode() > 0 ? HttpStatus.valueOf(e.getStatusCode()) : HttpStatus.BAD_REQUEST);

    } catch (Exception e) {
      return new ResponseEntity<>(new AnyObject().value(e.getMessage()), HttpStatus.BAD_REQUEST);
    }
  }
}
