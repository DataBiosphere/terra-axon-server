package bio.terra.axonserver.app.controller;

import static bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService.WORKSPACE_ID_LABEL_KEY;

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
import java.util.ArrayList;
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
    try {
      cromwellWorkflowService.validateWorkspaceAccessAndWorkflowLabelMatches(
          workflowId, workspaceId, getToken().getToken());
      CromwellApiWorkflowIdAndStatus workflowStatus = cromwellWorkflowService.getStatus(workflowId);
      return new ResponseEntity<>(
          new ApiWorkflowIdAndStatus()
              .id(UUID.fromString(workflowStatus.getId()))
              .status(workflowStatus.getStatus()),
          HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException(
          "Error getting workflow status. %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }

  @Override
  public ResponseEntity<ApiWorkflowIdAndLabel> getWorkflowLabels(
      UUID workspaceId, UUID workflowId) {
    try {
      cromwellWorkflowService.validateWorkspaceAccessAndWorkflowLabelMatches(
          workflowId, workspaceId, getToken().getToken());
      CromwellApiLabelsResponse workflowLabels = cromwellWorkflowService.getLabels(workflowId);
      return new ResponseEntity<>(
          new ApiWorkflowIdAndLabel()
              .id(UUID.fromString(workflowLabels.getId()))
              .labels(workflowLabels.getLabels()),
          HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException(
          "Error getting workflow labels. %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }

  @Override
  public ResponseEntity<ApiWorkflowMetadataResponse> getWorkflowMetadata(
      UUID workspaceId,
      UUID workflowId,
      @Nullable List<String> includeKey,
      @Nullable List<String> excludeKey,
      @Nullable Boolean expandSubWorkflows) {
    try {
      cromwellWorkflowService.validateWorkspaceAccessAndWorkflowLabelMatches(
          workflowId, workspaceId, getToken().getToken());
      CromwellApiWorkflowMetadataResponse workflowMetadata =
          cromwellWorkflowService.getMetadata(
              workflowId, includeKey, excludeKey, expandSubWorkflows);
      return new ResponseEntity<>(
          CromwellWorkflowService.toApiMetadataResponse(workflowMetadata), HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException(
          "Error getting workflow metadata. %s: %s".formatted(e.getCode(), e.getResponseBody()));
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
      @Nullable List<String> additionalQueryResultFields,
      @Nullable Boolean includeSubworkflows) {
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());
    try {
      List<String> workspaceValidationLabel = new ArrayList<>();
      // Restrict the subset to only workflows with the corresponding workspace id label.
      workspaceValidationLabel.add("%s:%s".formatted(WORKSPACE_ID_LABEL_KEY, workspaceId));
      CromwellApiWorkflowQueryResponse workflowQuery =
          cromwellWorkflowService.getQuery(
              submission,
              start,
              end,
              status,
              name,
              id,
              workspaceValidationLabel,
              /*labelor=*/ null,
              /*excludeLabelAnd=*/ null,
              /*excludeLabelOr=*/ null,
              additionalQueryResultFields,
              includeSubworkflows);

      return new ResponseEntity<>(
          CromwellWorkflowService.toApiQueryResponse(workflowQuery), HttpStatus.OK);
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException(
          "Error querying workflows. %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }
}
