package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AwsResourceApi;
import bio.terra.axonserver.model.ApiNotebookStatus;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.cloud.aws.AwsService;
import bio.terra.axonserver.service.exception.FeatureNotEnabledException;
import bio.terra.axonserver.service.features.FeatureService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.notebook.AwsSageMakerNotebook;
import bio.terra.axonserver.utils.notebook.NotebookStatus;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import com.google.common.annotations.VisibleForTesting;
import java.net.URL;
import java.util.Optional;
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

  private void checkAwsEnabled() {
    if (!featureService.awsEnabled()) {
      throw new FeatureNotEnabledException("AWS Feature not enabled.");
    }
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
    checkAwsEnabled();
    String accessToken = getAccessToken();
    ResourceDescription resourceDescription =
        wsmService.getResource(workspaceId, resourceId, accessToken);

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

  /** Do not use, public for spy testing */
  @VisibleForTesting
  public AwsSageMakerNotebook getNotebook(UUID workspaceId, UUID resourceId) {
    checkAwsEnabled();
    String accessToken = getAccessToken();
    return AwsSageMakerNotebook.create(
        wsmService, wsmService.getResource(workspaceId, resourceId, accessToken), accessToken);
  }

  /**
   * Start a sagemaker notebook instance
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra AWS Resource ID
   * @param wait wait for operation to complete
   */
  @Override
  public ResponseEntity<Void> putSageMakerNotebookStart(
      UUID workspaceId, UUID resourceId, Boolean wait) {
    getNotebook(workspaceId, resourceId).start(wait);
    return ResponseEntity.ok().build();
  }

  /**
   * Stop a sagemaker notebook instance
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra AWS Resource ID
   * @param wait wait for operation to complete
   */
  @Override
  public ResponseEntity<Void> putSageMakerNotebookStop(
      UUID workspaceId, UUID resourceId, Boolean wait) {
    getNotebook(workspaceId, resourceId).stop(wait);
    return ResponseEntity.ok().build();
  }

  /**
   * Get sagemaker notebook status.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra AWS Resource ID
   * @return notebook status
   */
  @Override
  public ResponseEntity<ApiNotebookStatus> getSageMakerNotebookStatus(
      UUID workspaceId, UUID resourceId) {
    NotebookStatus notebookStatus = getNotebook(workspaceId, resourceId).getStatus();

    ApiNotebookStatus.NotebookStatusEnum outEnum =
        Optional.ofNullable(
                ApiNotebookStatus.NotebookStatusEnum.fromValue(notebookStatus.toString()))
            .orElse(ApiNotebookStatus.NotebookStatusEnum.STATE_UNSPECIFIED);

    return new ResponseEntity<>(new ApiNotebookStatus().notebookStatus(outEnum), HttpStatus.OK);
  }

  /**
   * Get sagemaker notebook proxy URL.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra AWS Resource ID
   * @return url to access notebook
   */
  @Override
  public ResponseEntity<ApiSignedUrlReport> getSageMakerNotebookProxyUrl(
      UUID workspaceId, UUID resourceId) {
    String proxyUrl = getNotebook(workspaceId, resourceId).getProxyUrl();
    return new ResponseEntity<>(new ApiSignedUrlReport().signedUrl(proxyUrl), HttpStatus.OK);
  }
}
