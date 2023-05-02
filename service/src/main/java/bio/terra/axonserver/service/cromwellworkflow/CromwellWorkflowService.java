package bio.terra.axonserver.service.cromwellworkflow;

import bio.terra.axonserver.app.configuration.CromwellConfiguration;
import bio.terra.axonserver.model.ApiCallMetadata;
import bio.terra.axonserver.model.ApiFailureMessage;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResult;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.cromwell.api.WorkflowsApi;
import bio.terra.cromwell.client.ApiClient;
import bio.terra.cromwell.client.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.client.model.CromwellApiCallMetadata;
import io.swagger.client.model.CromwellApiFailureMessage;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
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
  private final FileService fileService;
  private final WorkspaceManagerService wsmService;
  private final SamService samService;

  public static final String WORKSPACE_ID_LABEL_KEY = "terra-workspace-id";
  private static final String CROMWELL_CLIENT_API_VERSION = "v1";

  @Autowired
  public CromwellWorkflowService(
      CromwellConfiguration cromwellConfig,
      FileService fileService,
      WorkspaceManagerService wsmService,
      SamService samService) {
    this.cromwellConfig = cromwellConfig;
    this.fileService = fileService;
    this.wsmService = wsmService;
    this.samService = samService;
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

  /**
   * Submits a single workflow to Cromwell. This appends (or overrides) the workspace id label, and
   * parts of the options configuration. Files are retrieved from GCS, and stored on the disk
   * temporarily before calling Cromwell.
   *
   * @param workspaceId workspace where the workflow will reside
   * @param workflowGcsUri URI pointing to the workflow source: a GCS object that is a WDL file.
   * @param workflowUrl URL which points to the workflow source.
   * @param workflowOnHold Put workflow on hold upon submission. By default, it is taken as false.
   * @param workflowInputs JSON string of inputs.
   * @param workflowOptions Object containing the options.
   * @param workflowType Workflow language for the submitted file (i.e., WDL)
   * @param workflowTypeVersion Version for the workflow language (draft-2 or 1.0).
   * @param labels JSON string of labels.
   * @param workflowDependenciesGcsUri URI pointing to the workflow dependencies: a GCS object that
   *     is a ZIP file.
   * @param requestedWorkflowId An ID to ascribe to this workflow. If not supplied, then a random ID
   *     will be generated.
   * @param token Bearer token.
   * @return The workflow ID, and status of submission.
   * @throws bio.terra.cromwell.client.ApiException Exception thrown by Cromwell client.
   * @throws IOException Exception thrown during file input/output.
   */
  public CromwellApiWorkflowIdAndStatus submitWorkflow(
      UUID workspaceId,
      String workflowGcsUri,
      String workflowUrl,
      Boolean workflowOnHold,
      String workflowInputs,
      Map<String, Object> workflowOptions,
      String workflowType,
      String workflowTypeVersion,
      String labels,
      String workflowDependenciesGcsUri,
      UUID requestedWorkflowId,
      BearerToken token)
      throws bio.terra.cromwell.client.ApiException, IOException {

    if (workflowGcsUri == null && workflowUrl == null) {
      throw new BadRequestException("workflowGcsUri or workflowUrl needs to be provided.");
    }

    // Create temp files from JSON of: workflowInputs, workflowOptions, labels, source,
    // and dependencies.
    // - labels and options will be modified before being sent to Cromwell.

    File tempInputsFile = null;
    if (workflowInputs != null) {
      tempInputsFile = createSafeTempFile("workflow-label-", "-terra");
      try (OutputStream out = new FileOutputStream(tempInputsFile)) {
        out.write(workflowInputs.getBytes(StandardCharsets.UTF_8));
      }
    }

    File tempOptionsFile = createSafeTempFile("workflow-options-", "-terra");
    // Adjoin preset options for the options file.
    // Place the project ID + compute SA into the options.
    String projectId = wsmService.getGcpContext(workspaceId, token.getToken()).getProjectId();
    workflowOptions.put("google_project", projectId);
    workflowOptions.put(
        "google_compute_service_account", samService.getPetServiceAccount(projectId, token));
    workflowOptions.put(
        "default_runtime_attributes", new AbstractMap.SimpleEntry("docker", "debian:stable-slim"));

    ObjectMapper mapper = new ObjectMapper();
    try (OutputStream out = new FileOutputStream(tempOptionsFile)) {
      out.write(mapper.writeValueAsString(workflowOptions).getBytes(StandardCharsets.UTF_8));
    }

    File tempLabelsFile = createSafeTempFile("workflow-labels-", "-terra");
    // Adjoin the workspace-id label to the workflow.
    JSONObject jsonLabels = labels == null ? new JSONObject() : new JSONObject(labels);
    jsonLabels.put(WORKSPACE_ID_LABEL_KEY, workspaceId);
    labels = jsonLabels.toString();
    try (OutputStream out = new FileOutputStream(tempLabelsFile)) {
      out.write(labels.getBytes(StandardCharsets.UTF_8));
    }

    File tempWorkflowSourceFile = null;
    if (workflowGcsUri != null) {
      InputStream inputStream =
          fileService.getFile(token, workspaceId, workflowGcsUri, /*convertTo=*/ null);
      tempWorkflowSourceFile = createTempFileFromInputStream(inputStream, "workflow-source-");
    }

    File tempWorkflowDependenciesFile = null;
    if (workflowDependenciesGcsUri != null) {
      InputStream inputStream =
          fileService.getFile(token, workspaceId, workflowDependenciesGcsUri, /*convertTo=*/ null);
      tempWorkflowDependenciesFile =
          createTempFileFromInputStream(inputStream, "workflow-dependencies-");
    }

    // TODO (PF-2650): Write inputs.json + options.json to jes_gcs_root (to leave artifacts in the
    // bucket). This is not required, but it's useful for logging.

    return new WorkflowsApi(getApiClient())
        .submit(
            CROMWELL_CLIENT_API_VERSION,
            tempWorkflowSourceFile,
            workflowUrl,
            workflowOnHold,
            tempInputsFile,
            /*workflowInputs_2=*/ null,
            /*workflowInputs_3=*/ null,
            /*workflowInputs_4=*/ null,
            /*workflowInputs_5=*/ null,
            tempOptionsFile,
            workflowType,
            /*workflowRoot=*/ null,
            workflowTypeVersion,
            tempLabelsFile,
            tempWorkflowDependenciesFile,
            requestedWorkflowId != null ? requestedWorkflowId.toString() : null);
  }

  private File createSafeTempFile(String filePrefix, String fileSuffix) throws IOException {
    FileAttribute<Set<PosixFilePermission>> attr =
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    File resultFile = Files.createTempFile(filePrefix, fileSuffix, attr).toFile();
    if (!SystemUtils.IS_OS_UNIX) {
      resultFile.setReadable(true, true);
      resultFile.setWritable(true, true);
      resultFile.setExecutable(true, true);
    }
    return resultFile;
  }

  private File createTempFileFromInputStream(InputStream inputStream, String tempFilePrefix)
      throws IOException {
    File tempFile = createSafeTempFile(tempFilePrefix, "-terra");
    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return tempFile;
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
                            .end(r.getEnd()))
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
                                            .failures(toApiFailureMessage(m.getFailures()))
                                            .returnCode(m.getReturnCode())
                                            .stdout(m.getStdout())
                                            .stderr(m.getStderr())
                                            .backendLogs(m.getBackendLogs()))
                                .toList()));

    return new ApiWorkflowMetadataResponse()
        .id(UUID.fromString(metadataResponse.getId()))
        .status(metadataResponse.getStatus())
        .submission(metadataResponse.getSubmission())
        .start(metadataResponse.getStart())
        .end(metadataResponse.getEnd())
        .inputs(metadataResponse.getInputs())
        .outputs(metadataResponse.getOutputs())
        .calls(callMetadataMap)
        .failures(toApiFailureMessage(metadataResponse.getFailures()));
  }

  private static ApiFailureMessage toApiFailureMessage(CromwellApiFailureMessage failureMessage) {
    if (failureMessage == null) {
      return null;
    }
    return new ApiFailureMessage()
        .failure(failureMessage.getFailure())
        .timestamp(failureMessage.getTimestamp());
  }
}
