package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.GcpResourceApi;
import bio.terra.axonserver.model.ApiClusterMetadata;
import bio.terra.axonserver.model.ApiClusterStatus;
import bio.terra.axonserver.model.ApiNotebookStatus;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.model.ApiUrl;
import bio.terra.axonserver.service.cloud.gcp.GcpService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.dataproc.ClusterStatus;
import bio.terra.axonserver.utils.dataproc.DataprocMetadataBuilderUtils;
import bio.terra.axonserver.utils.dataproc.GoogleDataprocClusterUtil;
import bio.terra.axonserver.utils.notebook.GoogleAIPlatformNotebookUtil;
import bio.terra.axonserver.utils.notebook.NotebookStatus;
import bio.terra.common.iam.BearerTokenFactory;
import com.google.api.services.dataproc.model.ClusterConfig;
import com.google.api.services.dataproc.model.NodeInitializationAction;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class GcpResourceController extends ControllerBase implements GcpResourceApi {
  private final WorkspaceManagerService wsmService;
  private final GcpService gcpService;

  @Autowired
  public GcpResourceController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      WorkspaceManagerService wsmService,
      GcpService gcpService) {
    super(bearerTokenFactory, request);
    this.wsmService = wsmService;
    this.gcpService = gcpService;
  }

  /** Do not use, public for spy testing */
  @VisibleForTesting
  public GoogleAIPlatformNotebookUtil getNotebook(UUID workspaceId, UUID resourceId) {
    GoogleCredentials credentials = gcpService.getPetSACredentials(workspaceId, getToken());
    return GoogleAIPlatformNotebookUtil.create(
        wsmService.getResource(workspaceId, resourceId, getAccessToken()), credentials);
  }

  /**
   * Start an ai notebook instance
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @param wait wait for operation to complete
   */
  @Override
  public ResponseEntity<Void> putAiNotebookStart(UUID workspaceId, UUID resourceId, Boolean wait) {
    getNotebook(workspaceId, resourceId).start(wait);
    return ResponseEntity.ok().build();
  }

  /**
   * Stop an ai notebook instance
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @param wait wait for operation to complete
   */
  @Override
  public ResponseEntity<Void> putAiNotebookStop(UUID workspaceId, UUID resourceId, Boolean wait) {
    getNotebook(workspaceId, resourceId).stop(wait);
    return ResponseEntity.ok().build();
  }

  /**
   * Get ai notebook status.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @return notebook status
   */
  @Override
  public ResponseEntity<ApiNotebookStatus> getAiNotebookStatus(UUID workspaceId, UUID resourceId) {
    NotebookStatus notebookStatus = getNotebook(workspaceId, resourceId).getStatus();

    ApiNotebookStatus.NotebookStatusEnum outEnum =
        Optional.ofNullable(
                ApiNotebookStatus.NotebookStatusEnum.fromValue(notebookStatus.toString()))
            .orElse(ApiNotebookStatus.NotebookStatusEnum.STATE_UNSPECIFIED);

    return new ResponseEntity<>(new ApiNotebookStatus().notebookStatus(outEnum), HttpStatus.OK);
  }

  /**
   * Get ai notebook proxy URL.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @return url to access notebook
   */
  @Override
  public ResponseEntity<ApiSignedUrlReport> getAiNotebookProxyUrl(
      UUID workspaceId, UUID resourceId) {
    String proxyUrl = getNotebook(workspaceId, resourceId).getProxyUrl();
    return new ResponseEntity<>(new ApiSignedUrlReport().signedUrl(proxyUrl), HttpStatus.OK);
  }

  /** Do not use, public for spy testing */
  @VisibleForTesting
  public GoogleDataprocClusterUtil getCluster(UUID workspaceId, UUID resourceId) {
    GoogleCredentials credentials = gcpService.getPetSACredentials(workspaceId, getToken());
    return GoogleDataprocClusterUtil.create(
        wsmService.getResource(workspaceId, resourceId, getAccessToken()), credentials);
  }

  /**
   * Start a dataproc cluster
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @param wait for operation to complete
   */
  @Override
  public ResponseEntity<Void> putDataprocClusterStart(
      UUID workspaceId, UUID resourceId, Boolean wait) {
    getCluster(workspaceId, resourceId).start(wait);
    return ResponseEntity.ok().build();
  }

  /**
   * Stop a dataproc cluster
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @param wait wait for operation to complete
   */
  @Override
  public ResponseEntity<Void> putDataprocClusterStop(
      UUID workspaceId, UUID resourceId, Boolean wait) {
    getCluster(workspaceId, resourceId).stop(wait);
    return ResponseEntity.ok().build();
  }

  /**
   * Get Dataproc cluster status.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @return notebook status
   */
  @Override
  public ResponseEntity<ApiClusterStatus> getDataprocClusterStatus(
      UUID workspaceId, UUID resourceId) {
    ClusterStatus clusterStatus = getCluster(workspaceId, resourceId).getStatus();

    ApiClusterStatus.StatusEnum outEnum =
        Optional.ofNullable(ApiClusterStatus.StatusEnum.fromValue(clusterStatus.toString()))
            .orElse(ApiClusterStatus.StatusEnum.STATE_UNSPECIFIED);

    return new ResponseEntity<>(new ApiClusterStatus().status(outEnum), HttpStatus.OK);
  }

  /**
   * Get a Dataproc cluster component web UI URL.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @param componentKey Dataproc optional component key
   * @return url to access component web UI
   */
  @Override
  public ResponseEntity<ApiUrl> getDataprocClusterComponentUrl(
      UUID workspaceId, UUID resourceId, String componentKey) {
    String componentUrl = getCluster(workspaceId, resourceId).getComponentUrl(componentKey);
    return new ResponseEntity<>(new ApiUrl().url(componentUrl), HttpStatus.OK);
  }

  /**
   * Get a Dataproc cluster's metadata.
   *
   * @param workspaceId Terra Workspace ID
   * @param resourceId Terra Resource ID
   * @return cluster metadata
   */
  @Override
  public ResponseEntity<ApiClusterMetadata> getDataprocClusterMetadata(
      UUID workspaceId, UUID resourceId) {
    ClusterConfig clusterConfig = getCluster(workspaceId, resourceId).getClusterConfig();

    // Build metadata response body
    ApiClusterMetadata metadata =
        new ApiClusterMetadata()
            .configBucket(clusterConfig.getConfigBucket())
            .tempBucket(clusterConfig.getTempBucket())
            .metadata(clusterConfig.getGceClusterConfig().getMetadata())
            .zoneUri(clusterConfig.getGceClusterConfig().getZoneUri())
            .managerNodeConfig(
                DataprocMetadataBuilderUtils.buildInstanceGroupConfig(
                    clusterConfig.getMasterConfig()))
            .primaryWorkerConfig(
                DataprocMetadataBuilderUtils.buildInstanceGroupConfig(
                    clusterConfig.getWorkerConfig()))
            .secondaryWorkerConfig(
                DataprocMetadataBuilderUtils.buildInstanceGroupConfig(
                    clusterConfig.getSecondaryWorkerConfig()))
            .lifecycleConfig(
                DataprocMetadataBuilderUtils.buildLifecycleConfig(
                    clusterConfig.getLifecycleConfig()))
            .softwareConfig(
                DataprocMetadataBuilderUtils.buildSoftwareConfig(
                    clusterConfig.getSoftwareConfig()));

    Optional.ofNullable(clusterConfig.getInitializationActions())
        .ifPresent(
            actions ->
                metadata.setInitializationActions(
                    actions.stream().map(NodeInitializationAction::getExecutableFile).toList()));

    Optional.ofNullable(clusterConfig.getAutoscalingConfig())
        .ifPresent(config -> metadata.setAutoscalingPolicy(config.getPolicyUri()));

    return new ResponseEntity<>(metadata, HttpStatus.OK);
  }
}
