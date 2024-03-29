package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.CromwellWorkflowApi;
import bio.terra.axonserver.model.ApiSubmitWorkflowRequestBody;
import bio.terra.axonserver.model.ApiWorkflowIdAndLabel;
import bio.terra.axonserver.model.ApiWorkflowIdAndStatus;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowParsedInputsResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService;
import bio.terra.axonserver.service.cromwellworkflow.WorkflowReservedLabelKeys;
import bio.terra.axonserver.service.exception.InvalidWdlException;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.StringUtils;
import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final ObjectMapper objectMapper;

  @Autowired
  public CromwellWorkflowController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      CromwellWorkflowService cromwellWorkflowService,
      FileService fileService,
      WorkspaceManagerService wsmService,
      ObjectMapper objectMapper) {
    super(bearerTokenFactory, request);
    this.cromwellWorkflowService = cromwellWorkflowService;
    this.wsmService = wsmService;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResponseEntity<ApiWorkflowIdAndStatus> getWorkflowStatus(
      UUID workspaceId, UUID workflowId) {
    cromwellWorkflowService.validateWorkspaceAccessAndWorkflowLabelMatches(
        workflowId, workspaceId, getToken().getToken());
    try {
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
    cromwellWorkflowService.validateWorkspaceAccessAndWorkflowLabelMatches(
        workflowId, workspaceId, getToken().getToken());
    try {
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
    cromwellWorkflowService.validateWorkspaceAccessAndWorkflowLabelMatches(
        workflowId, workspaceId, getToken().getToken());
    try {
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
      workspaceValidationLabel.add(
          "%s:%s".formatted(WorkflowReservedLabelKeys.WORKSPACE_ID_LABEL_KEY, workspaceId));
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

  @Override
  public ResponseEntity<ApiWorkflowParsedInputsResponse> parseInputs(
      UUID workspaceId, String gcsPath) {
    BearerToken token = getToken();
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, token.getToken());

    try {
      Map<String, String> parsedInputs =
          cromwellWorkflowService.parseInputs(workspaceId, gcsPath, token);
      ApiWorkflowParsedInputsResponse result =
          new ApiWorkflowParsedInputsResponse().inputs(parsedInputs);
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (IOException | InvalidWdlException e) {
      throw new ApiException("Error parsing inputs. %s, %s".formatted(e.getMessage(), e));
    }
  }

  @Override
  public ResponseEntity<ApiWorkflowIdAndStatus> submitWorkflow(
      UUID workspaceId, ApiSubmitWorkflowRequestBody body) {
    BearerToken token = getToken();
    // Check if the user has write access to the workspace.
    wsmService.checkWorkspaceWriteAccess(workspaceId, token.getToken());
    try {
      String workflowGcsUri = body.getWorkflowGcsUri();
      String workflowUrl = body.getWorkflowUrl();
      Boolean workflowOnHold = body.isWorkflowOnHold();

      // Convert workflowOptions to a snake_case map, this is what cromwell expects
      var requestOptions = body.getWorkflowOptions();
      Map<String, Object> workflowOptions = new HashMap<>();
      if (requestOptions != null) {
        Map<String, Object> workflowOptionsMap =
            objectMapper.convertValue(requestOptions, new TypeReference<>() {});

        for (Map.Entry<String, Object> entry : workflowOptionsMap.entrySet()) {
          workflowOptions.put(StringUtils.camelToSnake(entry.getKey()), entry.getValue());
        }
      }

      var workflowInputs = body.getWorkflowInputs();
      if (workflowInputs == null) {
        workflowInputs = new HashMap<>();
      }

      var labels = body.getLabels();
      if (labels == null) {
        labels = new HashMap<>();
      }

      String workflowType =
          body.getWorkflowType() == null ? null : body.getWorkflowType().toString();
      String workflowTypeVersion =
          body.getWorkflowTypeVersion() == null ? null : body.getWorkflowTypeVersion().toString();

      UUID requestedWorkflowId = body.getRequestedWorkflowId();

      CromwellApiWorkflowIdAndStatus workflowStatus =
          cromwellWorkflowService.submitWorkflow(
              workspaceId,
              workflowGcsUri,
              workflowUrl,
              workflowOnHold,
              workflowInputs,
              workflowOptions,
              workflowType,
              workflowTypeVersion,
              labels,
              requestedWorkflowId,
              token);
      return new ResponseEntity<>(
          new ApiWorkflowIdAndStatus()
              .id(UUID.fromString(workflowStatus.getId()))
              .status(workflowStatus.getStatus()),
          HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException("Error: %s".formatted(e.getMessage()));
    } catch (bio.terra.cromwell.client.ApiException e) {
      throw new ApiException("Error %s: %s".formatted(e.getCode(), e.getResponseBody()));
    }
  }
}
