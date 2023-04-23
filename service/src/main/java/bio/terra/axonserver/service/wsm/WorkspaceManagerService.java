package bio.terra.axonserver.service.wsm;

import bio.terra.axonserver.app.configuration.WsmConfiguration;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.api.ControlledAwsResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.GcpContext;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.WorkspaceDescription;
import com.google.common.annotations.VisibleForTesting;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for interacting with the Terra Workspace Manager client. */
@Component
public class WorkspaceManagerService {

  public static final int AWS_RESOURCE_CREDENTIAL_DURATION_MIN = 900;
  public static final int AWS_RESOURCE_CREDENTIAL_DURATION_MAX = 3600;

  private final WsmConfiguration wsmConfig;

  @Autowired
  public WorkspaceManagerService(WsmConfiguration wsmConfig) {
    this.wsmConfig = wsmConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(wsmConfig.basePath());
  }

  /**
   * Get a resource from a workspace.
   *
   * @param accessToken user access token
   * @param workspaceId terra workspace id
   * @param resourceId terra resource id
   * @return WSM resource description
   * @throws NotFoundException if workspace or resource does not exist
   */
  public ResourceDescription getResource(String accessToken, UUID workspaceId, UUID resourceId) {
    try {
      return new ResourceApi(getApiClient(accessToken)).getResource(workspaceId, resourceId);
    } catch (ApiException apiException) {
      throw new NotFoundException("Unable to access workspace or resource.");
    }
  }

  /**
   * Get the GCP context for a workspace.
   *
   * @param workspaceId terra workspace id
   * @param accessToken user access token
   * @return WSM GCP context
   * @throws NotFoundException if workspace does not exist or user does not have access to workspace
   */
  public GcpContext getGcpContext(UUID workspaceId, String accessToken) {
    return getWorkspace(workspaceId, null, accessToken).getGcpContext();
  }

  /**
   * Get the highest IAM role on a workspace for a given user.
   *
   * @param accessToken user access token
   * @param workspaceId terra workspace ID
   * @return Highest IAM role of the user on the workspace
   * @throws NotFoundException if workspace does not exist or user does not have access to workspace
   */
  public IamRole getHighestRole(String accessToken, UUID workspaceId) {
    return getWorkspace(workspaceId, null, accessToken).getHighestRole();
  }

  @VisibleForTesting
  public AwsCredential getAwsS3StorageFolderCredential(
      String accessToken,
      UUID workspaceId,
      UUID resourceId,
      AwsCredentialAccessScope accessScope,
      Integer duration) {
    try {
      return new ControlledAwsResourceApi(getApiClient(accessToken))
          .getAwsS3StorageFolderCredential(workspaceId, resourceId, accessScope, duration);
    } catch (ApiException e) {
      throw new NotFoundException("Unable to access workspace or resource.");
    }
  }

  public WorkspaceDescription getWorkspace(
      UUID workspaceId, IamRole minimumHighestRole, String accessToken) {
    try {
      return new WorkspaceApi(getApiClient(accessToken))
          .getWorkspace(workspaceId, minimumHighestRole);
    } catch (ApiException apiException) {
      throw new ForbiddenException("Unable to access workspace %s.".formatted(workspaceId));
    }
  }

  public void checkWorkspaceReadAccess(UUID workspaceId, String accessToken) {
    getWorkspace(workspaceId, IamRole.READER, accessToken);
  }

  /**
   * Infer the highest level of access that can be requested when obtaining AWS resource
   * credentials, based on the user's highest IAM role in the workspace
   *
   * @param highestRole highest role of user in workspace, can be retrieved via {@link
   *     #getHighestRole}
   * @return an access scope that can be used in a call to {@link #getAwsResourceCredential}
   * @throws ForbiddenException if user does not have at least READER role on workspace
   */
  public static AwsCredentialAccessScope inferAwsCredentialAccessScope(
      @Nullable IamRole highestRole) {
    if (highestRole == null || highestRole.equals(IamRole.DISCOVERER)) {
      throw new ForbiddenException("User has insufficient workspace permissions");
    }

    return highestRole.equals(IamRole.READER)
        ? AwsCredentialAccessScope.READ_ONLY
        : AwsCredentialAccessScope.WRITE_READ;
  }

  /**
   * Request a temporary credential that provides access to an AWS controlled resource.
   *
   * @param accessToken user access token
   * @param resourceDescription description of resource to request credential for
   * @param accessScope access scope to request
   * @param duration requested lifetime duration for credeential in seconds (between {@link
   *     WorkspaceManagerService#AWS_RESOURCE_CREDENTIAL_DURATION_MIN} and {@link
   *     WorkspaceManagerService#AWS_RESOURCE_CREDENTIAL_DURATION_MAX} (inclusive)
   * @return a temporary credential
   * @throws InvalidResourceTypeException if resource is not a supported AWS resource type
   * @throws NotFoundException if workspace does not exist or user does not have access to workspace
   */
  public AwsCredential getAwsResourceCredential(
      String accessToken,
      ResourceDescription resourceDescription,
      AwsCredentialAccessScope accessScope,
      Integer duration) {
    ResourceMetadata resourceMetadata = resourceDescription.getMetadata();
    return switch (resourceMetadata.getResourceType()) {
      case AWS_S3_STORAGE_FOLDER -> getAwsS3StorageFolderCredential(
          accessToken,
          resourceMetadata.getWorkspaceId(),
          resourceMetadata.getResourceId(),
          accessScope,
          duration);
      default -> throw new InvalidResourceTypeException(
          String.format(
              "Resource type %s not supported", resourceMetadata.getResourceType().toString()));
    };
  }
}
