package bio.terra.axonserver.service.cromwellworkflow;

import bio.terra.axonserver.app.configuration.CromwellConfiguration;
import bio.terra.axonserver.model.ApiCallMetadata;
import bio.terra.axonserver.model.ApiFailureMessages;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponseSubmittedFiles;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResult;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.exception.BadRequestException;
import bio.terra.cromwell.api.WorkflowsApi;
import bio.terra.cromwell.client.ApiClient;
import bio.terra.cromwell.client.ApiException;
import io.swagger.client.model.CromwellApiCallMetadata;
import io.swagger.client.model.CromwellApiFailureMessages;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponseSubmittedFiles;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wrapper service for calling cromwell. When applicable, the precondition for calling the client
 * is:
 *
 * <p>- the user has access to the workspace
 *
 * <p>- the requested workflow has a label matching to the workspace id.
 *
 * <p>This service appends/overrides a label with a workspace id onto all submitted workflows.
 */
@Component
public class CromwellWorkflowService {
  private final CromwellConfiguration cromwellConfig;
  private final WorkspaceManagerService wsmService;

  public static final String WORKSPACE_ID_LABEL_KEY = "terra-workspace-id";
  private static final String CROMWELL_CLIENT_API_VERSION = "v1";

  @Autowired
  public CromwellWorkflowService(
      CromwellConfiguration cromwellConfig, WorkspaceManagerService wsmService) {
    this.cromwellConfig = cromwellConfig;
    this.wsmService = wsmService;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(cromwellConfig.basePath());
  }

  public CromwellApiWorkflowIdAndStatus getStatus(UUID workflowId)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .status(CROMWELL_CLIENT_API_VERSION, workflowId.toString());
  }

  /**
   * Get the metadata for a given workflow.
   *
   * @param workflowId requested workflow.
   * @param includeKey filters metadata to only return fields with names which begins with this
   *     value.
   * @param excludeKey filters metadata to not return any field with a name which begins with this
   *     value
   * @param expandSubWorkflows metadata for sub workflows will be fetched and inserted automatically
   *     in the metadata response.
   * @return metadata response with applied queries.
   * @throws bio.terra.cromwell.client.ApiException Exception thrown by Cromwell client.
   */
  public CromwellApiWorkflowMetadataResponse getMetadata(
      UUID workflowId,
      @Nullable List<String> includeKey,
      @Nullable List<String> excludeKey,
      @Nullable Boolean expandSubWorkflows)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .metadata(
            CROMWELL_CLIENT_API_VERSION,
            workflowId.toString(),
            includeKey,
            excludeKey,
            expandSubWorkflows);
  }

  /**
   * Queries workflows based on user-supplied criteria, and additionally requires the corresponding
   * workspace id label (e.g., "{WORKSPACE_ID_LABEL_KEY}:{workspaceId}"). For now, do not accept
   * user supplied label queries. Only the controller may provide label queries.
   */
  public CromwellApiWorkflowQueryResponse getQuery(
      @Nullable Date submission,
      @Nullable Date start,
      @Nullable Date end,
      @Nullable List<String> status,
      @Nullable List<String> name,
      @Nullable List<String> id,
      @Nullable List<String> label,
      @Nullable List<String> labelor,
      @Nullable List<String> excludeLabelAnd,
      @Nullable List<String> excludeLabelOr,
      @Nullable List<String> additionalQueryResultFields,
      @Nullable Boolean includeSubworkflows)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .queryGet(
            CROMWELL_CLIENT_API_VERSION,
            submission,
            start,
            end,
            status,
            name,
            id,
            label,
            labelor,
            excludeLabelAnd,
            excludeLabelOr,
            additionalQueryResultFields,
            includeSubworkflows);
  }

  /** Retrieve the labels of a workflow. */
  public CromwellApiLabelsResponse getLabels(UUID workflowId)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .labels(CROMWELL_CLIENT_API_VERSION, workflowId.toString());
  }

  /**
   * Checks if the workflow has the required workspace id label (e.g.,
   * "terra-workspace-id:workspaceId").
   */
  private void validateWorkflowLabelMatchesWorkspaceId(UUID workflowId, UUID workspaceId) {
    try {
      Map<String, String> labels = getLabels(workflowId).getLabels();
      if (labels.get(WORKSPACE_ID_LABEL_KEY) == null
          || !labels.get(WORKSPACE_ID_LABEL_KEY).equals(workspaceId.toString())) {
        throw new BadRequestException(
            "Workflow %s is not a member of workspace %s".formatted(workflowId, workspaceId));
      }
    } catch (ApiException e) {
      throw new BadRequestException(
          "Workflow %s is not a member of workspace %s".formatted(workflowId, workspaceId));
    }
  }

  /**
   * Check if the user has workspace access and if the workflow has the required workspace id label
   * (e.g., "terra-workspace-id:workspaceId").
   *
   * @param workflowId identifier of the workflow
   * @param workspaceId workspace where the workflow located
   * @param accessToken access token
   */
  public void validateWorkspaceAccessAndWorkflowLabelMatches(
      UUID workflowId, UUID workspaceId, String accessToken) {
    // Check workspace access.
    wsmService.checkWorkspaceReadAccess(workspaceId, accessToken);
    // Then check if the workflow has the corresponding workspace id.
    validateWorkflowLabelMatchesWorkspaceId(workflowId, workspaceId);
  }

  public static ApiWorkflowQueryResponse toApiQueryResponse(
      CromwellApiWorkflowQueryResponse workflowQuery) {
    List<ApiWorkflowQueryResult> results =
        workflowQuery.getResults() == null
            ? null
            : workflowQuery.getResults().stream()
                .map(
                    r ->
                        new ApiWorkflowQueryResult()
                            .id(r.getId())
                            .name(r.getName())
                            .status(r.getStatus())
                            .submission(r.getSubmission())
                            .start(r.getStart())
                            .end(r.getEnd())
                            .labels(r.getLabels()))
                .toList();

    return new ApiWorkflowQueryResponse()
        .results(results)
        .totalResultsCount(workflowQuery.getTotalResultsCount());
  }

  public static ApiWorkflowMetadataResponse toApiMetadataResponse(
      CromwellApiWorkflowMetadataResponse metadataResponse) {
    Map<String, List<CromwellApiCallMetadata>> cromwellCallMetadataMap =
        metadataResponse.getCalls();

    // Convert each value of the map from list of cromwell call metadata into a list of api call
    // metadata.
    Map<String, List<ApiCallMetadata>> callMetadataMap =
        cromwellCallMetadataMap == null
            ? null
            : cromwellCallMetadataMap.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        entry ->
                            entry.getValue().stream()
                                .map(
                                    m ->
                                        new ApiCallMetadata()
                                            .inputs(m.getInputs())
                                            .executionStatus(m.getExecutionStatus())
                                            .backend(m.getBackend())
                                            .backendStatus(m.getBackendStatus())
                                            .start(m.getStart())
                                            .end(m.getEnd())
                                            .jobId(m.getJobId())
                                            .failures(toApiFailureMessages(m.getFailures()))
                                            .returnCode(m.getReturnCode())
                                            .callRoot(m.getCallRoot())
                                            .stdout(m.getStdout())
                                            .stderr(m.getStderr())
                                            .backendLogs(m.getBackendLogs()))
                                .toList()));

    CromwellApiWorkflowMetadataResponseSubmittedFiles cromwellSubmittedFiles =
        metadataResponse.getSubmittedFiles();

    var submittedFiles =
        cromwellSubmittedFiles == null
            ? null
            : new ApiWorkflowMetadataResponseSubmittedFiles()
                .workflow(cromwellSubmittedFiles.getWorkflow())
                .options(cromwellSubmittedFiles.getOptions())
                .inputs(cromwellSubmittedFiles.getInputs())
                .workflowType(cromwellSubmittedFiles.getWorkflowType())
                .root(cromwellSubmittedFiles.getRoot())
                .workflowUrl(cromwellSubmittedFiles.getWorkflowUrl())
                .labels(cromwellSubmittedFiles.getLabels());

    return new ApiWorkflowMetadataResponse()
        .id(UUID.fromString(metadataResponse.getId()))
        .status(metadataResponse.getStatus())
        .submission(metadataResponse.getSubmission())
        .start(metadataResponse.getStart())
        .end(metadataResponse.getEnd())
        .inputs(metadataResponse.getInputs())
        .outputs(metadataResponse.getOutputs())
        .submittedFiles(submittedFiles)
        .calls(callMetadataMap)
        .failures(toApiFailureMessages(metadataResponse.getFailures()));
  }

  private static List<ApiFailureMessages> toApiFailureMessages(
      List<CromwellApiFailureMessages> failureMessages) {
    if (failureMessages == null) {
      return null;
    }
    return failureMessages.stream()
        .map(
            failure ->
                new ApiFailureMessages()
                    .message(failure.getMessage())
                    .causedBy(toApiFailureMessages(failure.getCausedBy())))
        .toList();
  }
}
