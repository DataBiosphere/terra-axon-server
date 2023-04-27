package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AwsResourceApi;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.cloud.aws.AwsService;
import bio.terra.axonserver.service.exception.FeatureNotEnabledException;
import bio.terra.axonserver.service.features.FeatureService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import java.net.URL;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

/**
 * Controller for the AwsResource Api. This controller is responsible for handling AWS-specific
 * resource API endpoints.
 */
@Controller
public class AwsResourceController extends ControllerBase implements AwsResourceApi {

  private final FeatureService featureService;
  private final WorkspaceManagerService wsmService;
  private final AwsService awsService;

  @Autowired
  public AwsResourceController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      FeatureService featureService,
      WorkspaceManagerService wsmService,
      AwsService awsService) {
    super(bearerTokenFactory, request);
    this.featureService = featureService;
    this.wsmService = wsmService;
    this.awsService = awsService;
  }

  /**
   * Gets a signed URL providing access to a single AWS resource in the AWS Console. Access matches
   * the user's highest level of access in the Workspace.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra AWS Resource ID
   * @return a ResponseEntity containing the signed URL to access the resource in the AWS Console
   * @throws FeatureNotEnabledException if AWS feature flag is not set in the environment
   * @throws NotFoundException if workspace or resource does not exist or user does not have access
   *     to either
   * @throws ForbiddenException if user does not have at least READER role on workspace
   */
  @Override
  public ResponseEntity<ApiSignedUrlReport> getSignedConsoleUrl(UUID workspaceId, UUID resourceId) {

    if (!featureService.awsEnabled()) {
      throw new FeatureNotEnabledException("AWS Feature not enabled.");
    }

    String accessToken = getAccessToken();
    ResourceDescription resourceDescription =
        wsmService.getResource(accessToken, workspaceId, resourceId);

    AwsCredentialAccessScope accessScope =
        WorkspaceManagerService.inferAwsCredentialAccessScope(
            wsmService.getHighestRole(workspaceId, IamRole.READER, accessToken));

    AwsCredential awsCredential =
        wsmService.getAwsResourceCredential(
            resourceDescription,
            accessScope,
            WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MIN,
            accessToken);

    URL signedConsoleUrl =
        awsService.createSignedConsoleUrl(
            resourceDescription, awsCredential, AwsService.MAX_CONSOLE_SESSION_DURATION);

    return new ResponseEntity<>(
        new ApiSignedUrlReport().signedUrl(signedConsoleUrl.toString()), HttpStatus.OK);
  }
}
