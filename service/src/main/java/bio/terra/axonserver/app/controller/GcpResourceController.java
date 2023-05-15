package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.GcpResourceApi;
import bio.terra.axonserver.model.ApiNotebookStatus;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.notebook.GoogleAIPlatformNotebook;
import bio.terra.axonserver.utils.notebook.NotebookStatus;
import bio.terra.common.iam.BearerTokenFactory;
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

  @Autowired
  public GcpResourceController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      WorkspaceManagerService wsmService) {
    super(bearerTokenFactory, request);
    this.wsmService = wsmService;
  }

  private GoogleAIPlatformNotebook getNotebook(UUID workspaceId, UUID resourceId) {
    String accessToken = getAccessToken();
    return GoogleAIPlatformNotebook.create(
        wsmService.getResource(workspaceId, resourceId, accessToken), accessToken);
  }

  @Override
  public ResponseEntity<Void> putNotebookStart(UUID workspaceId, UUID resourceId, Boolean wait) {
    getNotebook(workspaceId, resourceId).start(wait);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> putNotebookStop(UUID workspaceId, UUID resourceId, Boolean wait) {
    getNotebook(workspaceId, resourceId).stop(wait);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<ApiNotebookStatus> getNotebookStatus(UUID workspaceId, UUID resourceId) {
    NotebookStatus notebookStatus = getNotebook(workspaceId, resourceId).getStatus();

    ApiNotebookStatus.NotebookStatusEnum outEnum =
        Optional.ofNullable(
                ApiNotebookStatus.NotebookStatusEnum.fromValue(notebookStatus.toString()))
            .orElse(ApiNotebookStatus.NotebookStatusEnum.STATE_UNSPECIFIED);

    return new ResponseEntity<>(new ApiNotebookStatus().notebookStatus(outEnum), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiSignedUrlReport> getNotebookProxyUrl(UUID workspaceId, UUID resourceId) {
    String proxyUrl = getNotebook(workspaceId, resourceId).getProxyUrl();
    return new ResponseEntity<>(new ApiSignedUrlReport().signedUrl(proxyUrl), HttpStatus.OK);
  }
}
