package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.CromwellWorkflowApi;
import bio.terra.axonserver.model.ApiWorkflowIdAndLabel;
import bio.terra.axonserver.model.ApiWorkflowIdAndStatus;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.BearerTokenFactory;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class CromwellWorkflowController extends ControllerBase implements CromwellWorkflowApi {
  private final CromwellWorkflowService cromwellWorkflowService;
  private final WorkspaceManagerService wsmService;

  @Autowired
  public CromwellWorkflowController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      CromwellWorkflowService cromwellWorkflowService,
      WorkspaceManagerService wsmService) {
    super(bearerTokenFactory, request);
    this.cromwellWorkflowService = cromwellWorkflowService;
    this.wsmService = wsmService;
  }

  @Override
  public ResponseEntity<ApiWorkflowIdAndStatus> getWorkflowStatus(
      UUID workspaceId, UUID workflowId) {
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());
    try {
      // Check if the workflow label matches the workspace id.
      cromwellWorkflowService.validateWorkflowLabelMatchesWorkspaceId(workflowId, workspaceId);

      CromwellApiWorkflowIdAndStatus workflowStatus = cromwellWorkflowService.getStatus(workflowId);
      return new ResponseEntity<>(
          new ApiWorkflowIdAndStatus()
              .id(UUID.fromString(workflowStatus.getId()))
              .status(workflowStatus.getStatus()),
          HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException("Error %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }

  @Override
  public ResponseEntity<ApiWorkflowIdAndLabel> getWorkflowLabels(
      UUID workspaceId, UUID workflowId) {
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());
    try {
      // Check if the workflow label matches the workspace id.
      cromwellWorkflowService.validateWorkflowLabelMatchesWorkspaceId(workflowId, workspaceId);

      CromwellApiLabelsResponse workflowLabels = cromwellWorkflowService.getLabels(workflowId);
      return new ResponseEntity<>(
          new ApiWorkflowIdAndLabel()
              .id(UUID.fromString(workflowLabels.getId()))
              .labels(workflowLabels.getLabels()),
          HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException("Error %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }

  @Override
  public ResponseEntity<ApiWorkflowMetadataResponse> getWorkflowMetadata(
      UUID workspaceId,
      UUID workflowId,
      @Nullable List<String> includeKey,
      @Nullable List<String> excludeKey,
      @Nullable Boolean expandSubWorkflows) {
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());
    try {
      // Check if the workflow label matches the workspace id.
      cromwellWorkflowService.validateWorkflowLabelMatchesWorkspaceId(workflowId, workspaceId);
      CromwellApiWorkflowMetadataResponse workflowMetadata =
          cromwellWorkflowService.getMetadata(
              workflowId, includeKey, excludeKey, expandSubWorkflows);
      return new ResponseEntity<>(
          cromwellWorkflowService.toApiMetadataResponse(workflowMetadata), HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException("Error %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }

  @Override
  public ResponseEntity<ApiWorkflowQueryResponse> getWorkflowQuery(
      UUID workspaceId,
      @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date submission,
      @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date start,
      @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date end,
      @Nullable List<String> status,
      @Nullable List<String> name,
      @Nullable List<String> id,
      @Nullable List<String> label,
      @Nullable List<String> labelor,
      @Nullable List<String> excludeLabelAnd,
      @Nullable List<String> excludeLabelOr,
      @Nullable List<String> additionalQueryResultFields,
      @Nullable Boolean includeSubworkflows) {
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());
    try {
      CromwellApiWorkflowQueryResponse workflowQuery =
          cromwellWorkflowService.getQuery(
              workspaceId,
              submission,
              start,
              end,
              status,
              name,
              id,
              label,
              labelor,
              excludeLabelOr,
              excludeLabelAnd,
              additionalQueryResultFields,
              includeSubworkflows);

      return new ResponseEntity<>(
          cromwellWorkflowService.toApiQueryResponse(workflowQuery), HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException("Error %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }
}
