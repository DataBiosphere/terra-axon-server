package bio.terra.axonserver.app.controller;

import static bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService.WORKSPACE_ID_LABEL_KEY;

import bio.terra.axonserver.api.CromwellWorkflowApi;
import bio.terra.axonserver.model.ApiWorkflowIdAndLabel;
import bio.terra.axonserver.model.ApiWorkflowIdAndStatus;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowParsedInputsResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.BearerTokenFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cromwell.core.path.DefaultPath;
import cromwell.core.path.DefaultPathBuilder;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import womtool.WomtoolMain.SuccessfulTermination;
import womtool.WomtoolMain.Termination;
import womtool.WomtoolMain.UnsuccessfulTermination;
import womtool.inputs.Inputs;

@Controller
public class CromwellWorkflowController extends ControllerBase implements CromwellWorkflowApi {
  private final CromwellWorkflowService cromwellWorkflowService;
  private final FileService fileService;
  private final WorkspaceManagerService wsmService;

  @Autowired
  public CromwellWorkflowController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      CromwellWorkflowService cromwellWorkflowService,
      FileService fileService,
      WorkspaceManagerService wsmService) {
    super(bearerTokenFactory, request);
    this.cromwellWorkflowService = cromwellWorkflowService;
    this.fileService = fileService;
    this.wsmService = wsmService;
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

  @Override
  public ResponseEntity<ApiWorkflowParsedInputsResponse> parseInputs(
      UUID workspaceId, String gcsPath) {
    // 1) Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());

    InputStream resourceObjectStream = fileService.getFile(getToken(), workspaceId, gcsPath, null);

    try {
      // 2) Write the WDL file to disk
      File targetFile = createSafeTempFile(UUID.randomUUID().toString(), "wdl");
      DefaultPath cromwellPath = DefaultPathBuilder.build(targetFile.toPath());
      Files.copy(resourceObjectStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      // 3) Call Womtool's input parsing method
      boolean showOptionals = true;
      Termination termination = Inputs.inputsJson(cromwellPath, showOptionals);

      // 4) Return the result as json, or return error
      if (termination instanceof SuccessfulTermination) {
        String jsonString = ((SuccessfulTermination) termination).stdout().get();
        // Use Gson to convert the JSON-like string to a Map<String, String>
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> result = new Gson().fromJson(jsonString, mapType);
        ApiWorkflowParsedInputsResponse actualResult =
            new ApiWorkflowParsedInputsResponse().inputs(result);
        return new ResponseEntity<>(actualResult, HttpStatus.OK);
      } else {
        String errorMessage = ((UnsuccessfulTermination) termination).stderr().get();
        throw new ApiException("Error: %s".formatted(errorMessage));
      }
    } catch (IOException e) {
      throw new ApiException("Error parsing inputs. %s".formatted(e.toString()));
    }
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
}
