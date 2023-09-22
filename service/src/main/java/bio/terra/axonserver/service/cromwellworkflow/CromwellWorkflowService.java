package bio.terra.axonserver.service.cromwellworkflow;

import bio.terra.axonserver.app.configuration.CromwellConfiguration;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResult;
import bio.terra.axonserver.service.cloud.gcp.GcpService;
import bio.terra.axonserver.service.exception.InvalidWdlException;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.AutoDeletingTempDir;
import bio.terra.axonserver.utils.AutoDeletingTempFile;
import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.cromwell.api.WorkflowsApi;
import bio.terra.cromwell.client.ApiClient;
import bio.terra.cromwell.client.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cromwell.core.path.DefaultPathBuilder;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zeroturnaround.zip.ZipUtil;
import womtool.WomtoolMain.SuccessfulTermination;
import womtool.WomtoolMain.Termination;
import womtool.inputs.Inputs;

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

  private static final Logger logger = LoggerFactory.getLogger(CromwellWorkflowService.class);
  private final CromwellConfiguration cromwellConfig;
  private final FileService fileService;
  private final WorkspaceManagerService wsmService;
  private final SamService samService;

  private final GcpService gcpService;

  private final ObjectMapper objectMapper;

  private static final String CROMWELL_CLIENT_API_VERSION = "v1";

  // Temp file prefixes
  private static final String INPUTS_PREFIX = "workflow-inputs-";
  private static final String OPTIONS_PREFIX = "workflow-options-";
  private static final String LABELS_PREFIX = "workflow-labels-";
  private static final String SOURCE_PREFIX = "workflow-source-";
  private static final String DEPS_DIR_PREFIX = "workflow-deps-";
  private static final String DEPS_ZIP_PREFIX = "workflow-deps-zip-";
  private static final String SUFFIX = "-terra";

  @Autowired
  public CromwellWorkflowService(
      CromwellConfiguration cromwellConfig,
      FileService fileService,
      WorkspaceManagerService wsmService,
      SamService samService,
      GcpService gcpService,
      ObjectMapper objectMapper) {
    this.cromwellConfig = cromwellConfig;
    this.fileService = fileService;
    this.wsmService = wsmService;
    this.samService = samService;
    this.gcpService = gcpService;
    this.objectMapper = objectMapper;
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
   * @param labels JSON string of labels. is a ZIP file.
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
      Map<String, String> workflowInputs,
      Map<String, Object> workflowOptions,
      String workflowType,
      String workflowTypeVersion,
      Map<String, String> labels,
      UUID requestedWorkflowId,
      BearerToken token)
      throws bio.terra.cromwell.client.ApiException, IOException {

    if (workflowGcsUri == null && workflowUrl == null) {
      throw new BadRequestException("workflowGcsUri or workflowUrl needs to be provided.");
    }
    var rootBucket = workflowOptions.get(WorkflowOptionKeys.JES_GCS_ROOT.getKey());
    if (rootBucket == null) {
      throw new BadRequestException("workflowOptions.jes_gcs_root must be provided.");
    }

    try (AutoDeletingTempFile tempInputsFile = new AutoDeletingTempFile(INPUTS_PREFIX, SUFFIX);
        AutoDeletingTempFile tempOptionsFile = new AutoDeletingTempFile(OPTIONS_PREFIX, SUFFIX);
        AutoDeletingTempFile tempLabelsFile = new AutoDeletingTempFile(LABELS_PREFIX, SUFFIX);
        AutoDeletingTempFile tempWorkflowSourceFile =
            new AutoDeletingTempFile(SOURCE_PREFIX, SUFFIX);
        AutoDeletingTempDir tempDepsDir = new AutoDeletingTempDir(DEPS_DIR_PREFIX);
        AutoDeletingTempFile tempWorkflowDependenciesFile =
            new AutoDeletingTempFile(DEPS_ZIP_PREFIX, SUFFIX)) {

      // Create inputs file
      if (workflowInputs != null) {
        writeToTmpFile(workflowInputs, tempInputsFile);
      }

      // Set preset options
      String projectId = wsmService.getGcpContext(workspaceId, token.getToken()).getProjectId();
      String userEmail = samService.getUserStatusInfo(token).getUserEmail();
      String petSaKey = samService.getPetServiceAccountKey(projectId, userEmail);
      String saEmail = samService.getPetServiceAccount(projectId, token);
      setPresetWorkflowOptions(
          workflowOptions, rootBucket.toString(), projectId, petSaKey, saEmail);
      writeToTmpFile(workflowOptions, tempOptionsFile);
      logger.info(
          "Wrote options to tmp file {} (Options omitted due to sensitive data)",
          tempOptionsFile.getFile().getPath());

      // Copy the source wdl from GCS
      var localMainWdlPath = tempWorkflowSourceFile.getFile().toPath();
      if (workflowGcsUri != null) {
        InputStream inputStream =
            fileService.getFile(token, workspaceId, workflowGcsUri, /*convertTo=*/ null);
        Files.copy(inputStream, localMainWdlPath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Copied source WDL from {} to tmp file {}", workflowGcsUri, localMainWdlPath);
      }

      // Set preset labels
      setPresetLabels(labels, workspaceId, userEmail, workflowGcsUri);
      writeToTmpFile(labels, tempLabelsFile);
      logger.info("Wrote labels {} to tmp file {}", labels, tempLabelsFile.getFile().getPath());

      if (downloadDependenciesIfExist(
          workspaceId, token, localMainWdlPath, workflowGcsUri, tempDepsDir.getDir())) {
        var zipPath = tempWorkflowDependenciesFile.getFile().toString();
        logger.info("Zipping dependencies to {}", zipPath);
        ZipUtil.pack(new File(tempDepsDir.getDir().toString()), new File(zipPath));
      }

      return new WorkflowsApi(getApiClient())
          .submit(
              CROMWELL_CLIENT_API_VERSION,
              tempWorkflowSourceFile.getFile(),
              workflowUrl,
              workflowOnHold,
              tempInputsFile.getFile(),
              /*workflowInputs_2=*/ null,
              /*workflowInputs_3=*/ null,
              /*workflowInputs_4=*/ null,
              /*workflowInputs_5=*/ null,
              tempOptionsFile.getFile(),
              workflowType,
              /*workflowRoot=*/ null,
              workflowTypeVersion,
              tempLabelsFile.getFile(),
              tempWorkflowDependenciesFile.getFile(),
              requestedWorkflowId != null ? requestedWorkflowId.toString() : null);
    }
  }

  /** Retrieve the labels of a workflow. */
  public CromwellApiLabelsResponse getLabels(UUID workflowId)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .labels(CROMWELL_CLIENT_API_VERSION, workflowId.toString());
  }

  /**
   * Use's The Broad's WOMTool to parse inputs from a WDL. Downloads dependent WDLs in order to
   * handle WDLs with sub-wdls.
   *
   * @param workspaceId - Workspace containing WDL
   * @param workflowGcsUri - GCS URI path to wdl
   * @param token - User's OAuth2 token
   * @return inputs - Map of inputs for WDL
   * @throws InvalidWdlException - WomTool will throw this if the WDL is invalid.
   */
  public Map<String, String> parseInputs(UUID workspaceId, String workflowGcsUri, BearerToken token)
      throws IOException, InvalidWdlException {
    try (AutoDeletingTempDir tempDir = new AutoDeletingTempDir(DEPS_DIR_PREFIX)) {
      Path localMainWdlPath = Paths.get(tempDir.getDir().toString(), "main.wdl");
      InputStream resourceObjectStream =
          fileService.getFile(token, workspaceId, workflowGcsUri, null);
      Files.copy(resourceObjectStream, localMainWdlPath, StandardCopyOption.REPLACE_EXISTING);

      downloadDependenciesIfExist(
          workspaceId, token, localMainWdlPath, workflowGcsUri, tempDir.getDir());

      Termination termination = Inputs.inputsJson(DefaultPathBuilder.build(localMainWdlPath), true);
      if (termination instanceof SuccessfulTermination) {
        String jsonString = termination.stdout().get();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        return new Gson().fromJson(jsonString, mapType);
      } else {
        String errorMessage = termination.stderr().get();
        throw new InvalidWdlException(errorMessage);
      }
    }
  }

  /**
   * Checks if the workflow has the required workspace id label (e.g.,
   * "terra-workspace-id:workspaceId").
   */
  private void validateWorkflowLabelMatchesWorkspaceId(UUID workflowId, UUID workspaceId) {
    try {
      Map<String, String> labels = getLabels(workflowId).getLabels();
      var workspaceIdLabel = labels.get(WorkflowLabelKeys.WORKSPACE_ID_LABEL_KEY.getKey());
      if (workspaceIdLabel == null || !workspaceIdLabel.equals(workspaceId.toString())) {
        throw new BadRequestException(
            "Workflow %s is not a member of workspace %s".formatted(workflowId, workspaceId));
      }
    } catch (ApiException e) {
      throw new BadRequestException(
          "Workflow %s is not a member of workspace %s".formatted(workflowId, workspaceId));
    }
  }

  /**
   * Writes Map data to a temp file. Used to write inputs.json, options.json and labels.json
   *
   * @param data - Map data representing JSON to write to file
   * @param tempFile - Temporary file to write to
   */
  private void writeToTmpFile(Map<String, ?> data, AutoDeletingTempFile tempFile)
      throws IOException {
    if (data != null) {
      try (OutputStream out = new FileOutputStream(tempFile.getFile())) {
        out.write(objectMapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  /**
   * Sets required options for Cromwell
   *
   * @param workflowOptions - Existing options set by the user
   * @param rootBucket - Root bucket to run workflow in.
   * @param projectId - Project to run workflow in.
   * @param petSaKey - Pet service account key for the user
   * @param saEmail - Service account email for the user.
   */
  private void setPresetWorkflowOptions(
      Map<String, Object> workflowOptions,
      String rootBucket,
      String projectId,
      String petSaKey,
      String saEmail) {
    // TODO: This will likely change in the near future when we update cromwell to use tokens
    workflowOptions.put(WorkflowOptionKeys.USER_SERVICE_ACCOUNT_JSON.getKey(), petSaKey);
    workflowOptions.put(
        WorkflowOptionKeys.CALL_CACHE_HIT_PATH_PREFIXES.getKey(), new String[] {rootBucket});
    workflowOptions.put(WorkflowOptionKeys.GOOGLE_PROJECT.getKey(), projectId);
    workflowOptions.put(WorkflowOptionKeys.GOOGLE_COMPUTE_SERVICE_ACCOUNT.getKey(), saEmail);
    workflowOptions.put(
        WorkflowOptionKeys.DEFAULT_RUNTIME_ATTRIBUTES.getKey(),
        new AbstractMap.SimpleEntry<>("docker", "debian:stable-slim"));
  }

  /**
   * Set required labels for Cromwell
   *
   * @param workflowLabels - Existing labels set by user.
   * @param workspaceId - Workspace ID the workflow is attached to. This is used to query workflows
   *     in the future.
   * @param userEmail - Email of the user who submitted the job.
   * @param workflowGcsUri - GCS URI of the workflow.
   */
  private void setPresetLabels(
      Map<String, String> workflowLabels,
      UUID workspaceId,
      String userEmail,
      String workflowGcsUri) {
    workflowLabels.put(WorkflowLabelKeys.WORKSPACE_ID_LABEL_KEY.getKey(), workspaceId.toString());
    workflowLabels.put(WorkflowLabelKeys.USER_EMAIL_LABEL_KEY.getKey(), userEmail);
    if (workflowGcsUri != null) {
      // TODO: Deprecate GCS source in favor of general purpose workflow source url key
      // Will update in a future PR once UI is updated.
      workflowLabels.put(WorkflowLabelKeys.GCS_SOURCE_LABEL_KEY.getKey(), workflowGcsUri);
      workflowLabels.put(WorkflowLabelKeys.WORKFLOW_SOURCE_URL_LABEL_KEY.getKey(), workflowGcsUri);
    }
  }

  private boolean containsImportStatement(Path filePath) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new FileInputStream(filePath.toString()), StandardCharsets.UTF_8))) {
      String line;
      Pattern importPattern = Pattern.compile("^import\\s\"[^\"]*\".*");
      while ((line = reader.readLine()) != null) {
        if (importPattern.matcher(line).matches()) {
          return true;
        }
      }
      return false;
    }
  }

  private void downloadWdlDependencies(
      UUID workspaceId, BearerToken token, String workflowGcsUri, String destinationPath) {
    GoogleCredentials googleCredentials = gcpService.getPetSACredentials(workspaceId, token);

    // Parse the bucket and object name
    String[] parts = CloudStorageUtils.extractBucketAndObjectFromUri(workflowGcsUri);
    String sourceBucket = parts[0];
    String sourceObject = parts[1];

    // Parse the directory name if there is one
    int lastIndex = sourceObject.lastIndexOf('/');
    String sourceDir = lastIndex == -1 ? "" : sourceObject.substring(0, lastIndex);

    // Download dependencies
    CloudStorageUtils.downloadGcsDir(
        googleCredentials, sourceBucket, sourceDir, destinationPath, ".wdl");
  }

  public boolean downloadDependenciesIfExist(
      UUID workspaceId,
      BearerToken token,
      Path localWdlPath,
      String workflowGcsUri,
      Path downloadPath)
      throws IOException {

    if (containsImportStatement(localWdlPath)) {
      logger.info("Source WDL has dependencies. Downloading all files to {}.", downloadPath);
      downloadWdlDependencies(workspaceId, token, workflowGcsUri, downloadPath.toString());
      return true;
    }
    logger.info("Source WDL has no dependencies");
    return false;
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
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.convertValue(metadataResponse, ApiWorkflowMetadataResponse.class);
  }
}
