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
import cromwell.core.path.DefaultPath;
import cromwell.core.path.DefaultPathBuilder;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
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
import womtool.WomtoolMain.UnsuccessfulTermination;
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

  public static final String WORKSPACE_ID_LABEL_KEY = "terra-workspace-id";
  public static final String USER_EMAIL_LABEL_KEY = "terra-user-email";

  public static final String GCS_SOURCE_LABEL_KEY = "terra-gcs-source-uri";
  private static final String CROMWELL_CLIENT_API_VERSION = "v1";
  private GoogleCredentials googleCredentials;

  @Autowired
  public CromwellWorkflowService(
      CromwellConfiguration cromwellConfig,
      FileService fileService,
      WorkspaceManagerService wsmService,
      SamService samService,
      GcpService gcpService) {
    this.cromwellConfig = cromwellConfig;
    this.fileService = fileService;
    this.wsmService = wsmService;
    this.samService = samService;
    this.gcpService = gcpService;
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
      Map<String, String> workflowInputs,
      Map<String, Object> workflowOptions,
      String workflowType,
      String workflowTypeVersion,
      Map<String, String> labels,
      String workflowDependenciesGcsUri,
      UUID requestedWorkflowId,
      BearerToken token)
      throws bio.terra.cromwell.client.ApiException, IOException {

    if (workflowGcsUri == null && workflowUrl == null) {
      throw new BadRequestException("workflowGcsUri or workflowUrl needs to be provided.");
    }
    var rootBucket = workflowOptions.get("jes_gcs_root");
    if (rootBucket == null) {
      throw new BadRequestException("workflowOptions.jes_gcs_root must be provided.");
    }

    try (AutoDeletingTempFile tempInputsFile =
            new AutoDeletingTempFile("workflow-inputs-", "-terra");
        AutoDeletingTempFile tempOptionsFile =
            new AutoDeletingTempFile("workflow-options-", "-terra");
        AutoDeletingTempFile tempLabelsFile =
            new AutoDeletingTempFile("workflow-labels-", "-terra");
        AutoDeletingTempFile tempWorkflowSourceFile =
            new AutoDeletingTempFile("workflow-source-", "-terra");
        AutoDeletingTempDir tempDir = new AutoDeletingTempDir("workflow-deps-");
        AutoDeletingTempFile tempWorkflowDependenciesFile =
            new AutoDeletingTempFile("workflow-deps-zip-", "-terra")) {

      // Create inputs file
      ObjectMapper mapper = new ObjectMapper();
      if (workflowInputs != null) {
        try (OutputStream out = new FileOutputStream(tempInputsFile.getFile())) {
          out.write(mapper.writeValueAsString(workflowInputs).getBytes(StandardCharsets.UTF_8));
        }
        logger.info(
            "Wrote inputs {} to tmp file {}", workflowInputs, tempInputsFile.getFile().getPath());
      }

      // Adjoin preset options for the options file.
      // Place the project ID + compute SA + docker image into the options.
      String projectId = wsmService.getGcpContext(workspaceId, token.getToken()).getProjectId();
      String userEmail = samService.getUserStatusInfo(token).getUserEmail();
      String petSaKey = samService.getPetServiceAccountKey(projectId, userEmail);
      workflowOptions.put("user_service_account_json", petSaKey);

      // Limit call caching to root bucket
      String[] cachePrefixes = {rootBucket.toString()};
      workflowOptions.put("call_cache_hit_path_prefixes", cachePrefixes);
      workflowOptions.put("google_project", projectId);
      workflowOptions.put(
          "google_compute_service_account", samService.getPetServiceAccount(projectId, token));
      workflowOptions.put(
          "default_runtime_attributes",
          new AbstractMap.SimpleEntry<>("docker", "debian:stable-slim"));
      try (OutputStream out = new FileOutputStream(tempOptionsFile.getFile())) {
        out.write(mapper.writeValueAsString(workflowOptions).getBytes(StandardCharsets.UTF_8));
        logger.info(
            "Wrote options to tmp file {} (Options omitted due to sensitive data",
            tempOptionsFile.getFile().getPath());
      }

      // Put the user email and workspace ID as labels on the workflow
      // This is used in the UI.
      labels.put(WORKSPACE_ID_LABEL_KEY, workspaceId.toString());
      labels.put(USER_EMAIL_LABEL_KEY, userEmail);

      // Copy the source wdl from GCS
      var sourceWdlPath = tempWorkflowSourceFile.getFile().toPath();
      if (workflowGcsUri != null) {
        labels.put(GCS_SOURCE_LABEL_KEY, workflowGcsUri);
        InputStream inputStream =
            fileService.getFile(token, workspaceId, workflowGcsUri, /*convertTo=*/ null);
        Files.copy(inputStream, sourceWdlPath, StandardCopyOption.REPLACE_EXISTING);
        logger.info(
            "Copied source WDL from {} to tmp file {}", workflowGcsUri, sourceWdlPath.toString());
      }
      try (OutputStream out = new FileOutputStream(tempLabelsFile.getFile())) {
        out.write(mapper.writeValueAsString(labels).getBytes(StandardCharsets.UTF_8));
        logger.info("Wrote labels {} to tmp file {}", labels, tempLabelsFile.getFile().getPath());
      }

      // If the WDL has dependencies, download, zip and submit.
      if (containsImportStatement(sourceWdlPath.toString())) {
        logger.info("Source WDL has dependencies. Downloading all files at root dir.");
        var dirPath = tempDir.getDir().toString();
        downloadWdlDependencies(workspaceId, token, workflowGcsUri, dirPath);
        var zipPath = tempWorkflowDependenciesFile.getFile().toString();
        logger.info("Zipping dependencies to {}", dirPath);
        ZipUtil.pack(new File(dirPath), new File(zipPath));
        logger.info("Zip contains following dependencies:");
        ZipUtil.iterate(new File(zipPath), (in, zipEntry) -> logger.info(zipEntry.getName()));
      }
      logger.info("Submitting all files to Cromwell");
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

  public Map<String, String> parseInputs(UUID workspaceId, String gcsPath, BearerToken token)
      throws IOException, InvalidWdlException {
    // 1) Get the WDL file and write it to disk
    try (AutoDeletingTempDir tempDir = new AutoDeletingTempDir("workflow-deps-"); ) {
      Path mainFilePath = Paths.get(tempDir.getDir().toString(), "main.wdl");
      InputStream resourceObjectStream = fileService.getFile(token, workspaceId, gcsPath, null);
      DefaultPath cromwellPath = DefaultPathBuilder.build(mainFilePath);
      Files.copy(resourceObjectStream, mainFilePath, StandardCopyOption.REPLACE_EXISTING);

      if (containsImportStatement(mainFilePath.toString())) {
        logger.info(
            "Attempting to download wdl dependencies from {} to {}",
            gcsPath,
            tempDir.getDir().toString());
        downloadWdlDependencies(workspaceId, token, gcsPath, tempDir.getDir().toString());
      } else {
        logger.info("Source WDL does not contain dependencies");
      }
      // 2) Call Womtool's input parsing method
      Termination termination = Inputs.inputsJson(cromwellPath, true);

      // 3) Return the result as json, or return error
      if (termination instanceof SuccessfulTermination) {
        String jsonString = ((SuccessfulTermination) termination).stdout().get();
        // Use Gson to convert the JSON-like string to a Map<String, String>
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        return new Gson().fromJson(jsonString, mapType);
      } else {
        String errorMessage = ((UnsuccessfulTermination) termination).stderr().get();
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

  private static boolean containsImportStatement(String filePath) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8));
    String line;
    Pattern importPattern = Pattern.compile("^import\\s\".*\".*");

    while ((line = reader.readLine()) != null) {
      if (importPattern.matcher(line).matches()) {
        reader.close();
        return true;
      }
    }
    reader.close();
    return false;
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
