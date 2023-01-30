package bio.terra.axonserver.service.wsm;

import bio.terra.axonserver.app.configuration.WsmConfiguration;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.ResourceDescription;
import java.util.UUID;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for interacting with the Terra Workspace Manager client. */
@Component
public class WorkspaceManagerService {
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
}
