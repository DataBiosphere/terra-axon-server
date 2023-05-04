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
import cromwell.core.path.Path;
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
import scala.util.Try;
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
      UUID workspaceId, UUID resourceId, String objectPath) {
    // Check if the user has access to the workspace.
    wsmService.checkWorkspaceReadAccess(workspaceId, getToken().getToken());

    InputStream resourceObjectStream =
        fileService.getFile(getToken(), workspaceId, resourceId, objectPath, null, null);
    String tempFilename = UUID.randomUUID().toString() + ".wdl";
    File targetFile = new File(tempFilename);
    try {
      Try<DefaultPath> pathTry = DefaultPathBuilder.build(tempFilename);
      Files.copy(resourceObjectStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      if (pathTry.isSuccess()) {
        Path path = pathTry.get();
        boolean showOptionals = true;
        Termination termination = Inputs.inputsJson(path, showOptionals);
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
          Map<String, String> result = new HashMap<>();
          result.put("fail", "fail_in_termination " + errorMessage);
          ApiWorkflowParsedInputsResponse actualResult =
              new ApiWorkflowParsedInputsResponse().inputs(result);
          return new ResponseEntity<>(actualResult, HttpStatus.OK);
        }

      } else {
        Map<String, String> result = new HashMap<>();
        result.put("fail", "fail");
        ApiWorkflowParsedInputsResponse actualResult =
            new ApiWorkflowParsedInputsResponse().inputs(result);
        return new ResponseEntity<>(actualResult, HttpStatus.OK);
      }
    } catch (IOException e) {
      throw new ApiException("Error parsing inputs. %s".formatted(e.toString()));
    } finally {
      if (targetFile.exists()) {
        targetFile.delete();
      }
    }
  }
}
