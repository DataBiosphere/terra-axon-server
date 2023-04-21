package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AwsResourceApi;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.exception.FeatureNotEnabledException;
import bio.terra.axonserver.service.features.FeatureService;
import bio.terra.common.iam.BearerTokenFactory;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AwsResourceController extends ControllerBase implements AwsResourceApi {

  private final FeatureService featureService;

  @Autowired
  public AwsResourceController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      FeatureService featureService) {
    super(bearerTokenFactory, request);
    this.featureService = featureService;
  }

  @Override
  public ResponseEntity<ApiSignedUrlReport> getSignedConsoleUrl(UUID workspaceId, UUID resourceId) {

    if (!featureService.awsEnabled()) {
      throw new FeatureNotEnabledException("AWS Feature not enabled.");
    }

    // TODO (TERRA-515): Implement the API logic in a service class.  Use this as a test to aid in
    // plumbing Flagsmith integration in the meantime.
    ApiSignedUrlReport actualResult =
        new ApiSignedUrlReport().signedUrl("https://console.aws.amazon.com");
    return new ResponseEntity<>(actualResult, HttpStatus.OK);
  }
}
