package bio.terra.axonserver.app.controller;

import static bio.terra.axonserver.testutils.MockMvcUtils.USER_REQUEST;

import bio.terra.axonserver.model.ApiWorkflowIdAndLabel;
import bio.terra.axonserver.model.ApiWorkflowIdAndStatus;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowParsedInputsResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResult;
import bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.axonserver.testutils.MockMvcUtils;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.iam.BearerToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.swagger.client.model.CromwellApiCallMetadata;
import io.swagger.client.model.CromwellApiFailureMessages;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponseSubmittedFiles;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class CromwellWorkflowControllerTest extends BaseUnitTest {
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private CromwellWorkflowService cromwellWorkflowService;
  @MockBean private WorkspaceManagerService wsmService;

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();
  private final String DEFAULT_WORKFLOW_STATUS = "Submitted";
  private final Date DEFAULT_WORKFLOW_SUBMISSION_DATE = new Date(0);

  private final Map<String, String> DEFAULT_WORKFLOW_LABELS =
      ImmutableMap.of(
          CromwellWorkflowService.WORKSPACE_ID_LABEL_KEY,
          workspaceId.toString(),
          "fake-label-key",
          "fake-label-value");

  private final String CROMWELL_WORKFLOW_STATUS_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/%s/status";
  private final String CROMWELL_WORKFLOW_LABELS_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/%s/labels";
  private final String CROMWELL_WORKFLOW_METADATA_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/%s/metadata";
  private final String CROMWELL_WORKFLOW_QUERY_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/query";
  private final String CROMWELL_WORKFLOW_PARSE_INPUTS_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/parseInputsAndZip/%s";

  @Test
  void status_noWorkspaceAccess_throws403() throws Exception {
    Mockito.doThrow(new ForbiddenException("No workspace access"))
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    mockMvcUtils.getSerializedResponseForGetExpect(
        USER_REQUEST, CROMWELL_WORKFLOW_STATUS_PATH_FORMAT.formatted(workspaceId, workflowId), 403);
  }

  @Test
  void labels_noWorkspaceAccess_throws403() throws Exception {
    Mockito.doThrow(new ForbiddenException("No workspace access"))
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    mockMvcUtils.getSerializedResponseForGetExpect(
        USER_REQUEST, CROMWELL_WORKFLOW_LABELS_PATH_FORMAT.formatted(workspaceId, workflowId), 403);
  }

  @Test
  void metadata_noWorkspaceAccess_throws403() throws Exception {
    Mockito.doThrow(new ForbiddenException("No workspace access"))
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    mockMvcUtils.getSerializedResponseForGetExpect(
        USER_REQUEST,
        CROMWELL_WORKFLOW_METADATA_PATH_FORMAT.formatted(workspaceId, workflowId),
        403);
  }

  @Test
  void query_noWorkspaceAccess_throws403() throws Exception {
    Mockito.doThrow(new ForbiddenException("No workspace access"))
        .when(wsmService)
        .checkWorkspaceReadAccess(workspaceId, USER_REQUEST.getToken());

    mockMvcUtils.getSerializedResponseForGetExpect(
        USER_REQUEST, CROMWELL_WORKFLOW_QUERY_PATH_FORMAT.formatted(workspaceId), 403);
  }

  @Test
  void status() throws Exception {
    // Stub the workspace access check, and workspace id label matching.
    Mockito.doNothing()
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    // Stub the client status response.
    Mockito.when(cromwellWorkflowService.getStatus(workflowId))
        .thenReturn(
            new CromwellApiWorkflowIdAndStatus()
                .id(workflowId.toString())
                .status(DEFAULT_WORKFLOW_STATUS));

    ApiWorkflowIdAndStatus result = getWorkflowStatus(USER_REQUEST, workspaceId, workflowId);
    Assertions.assertEquals(result.getId(), workflowId);
    Assertions.assertEquals(result.getStatus(), DEFAULT_WORKFLOW_STATUS);
  }

  @Test
  void labels() throws Exception {
    // Stub the workspace access check, and workspace id label matching.
    Mockito.doNothing()
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    CromwellApiLabelsResponse fakeLabelResponse =
        new CromwellApiLabelsResponse().id(workflowId.toString()).labels(DEFAULT_WORKFLOW_LABELS);

    // Stub the workflow having the workflow id label, and the label response
    Mockito.when(cromwellWorkflowService.getLabels(workflowId)).thenReturn(fakeLabelResponse);

    ApiWorkflowIdAndLabel result = getWorkflowLabels(USER_REQUEST, workspaceId, workflowId);
    Assertions.assertEquals(result.getId(), workflowId);

    // Check the labels deserialize properly.
    Assertions.assertEquals(result.getLabels().size(), 2);
    Assertions.assertEquals(result.getLabels(), DEFAULT_WORKFLOW_LABELS);
  }

  @Test
  void metadata() throws Exception {
    // Stub the workspace access check, and workspace id label matching.
    Mockito.doNothing()
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    // Verify callRoot is returned in metadata response.
    String callRoot = "gs://path/to/root";

    var callMetadata =
        Arrays.asList(
            new CromwellApiCallMetadata().callRoot(callRoot).returnCode(0),
            new CromwellApiCallMetadata().callRoot(callRoot).returnCode(-1));

    // Verify submittedFiles object is returned in response.
    var submittedFiles =
        new CromwellApiWorkflowMetadataResponseSubmittedFiles()
            .workflow("workflow")
            .options("options")
            .inputs("inputs")
            .workflowType("WDL")
            .root("root")
            .workflowUrl("url")
            .labels("{}");

    // Verify failure message is correctly returned.
    var failureMessage =
        Collections.singletonList(
            new CromwellApiFailureMessages()
                .message("root")
                .causedBy(
                    Collections.singletonList(
                        new CromwellApiFailureMessages()
                            .message("one level down")
                            .causedBy(
                                Collections.singletonList(
                                    new CromwellApiFailureMessages()
                                        .message("two levels down"))))));

    // Stub the client metadata response.
    Mockito.when(
            cromwellWorkflowService.getMetadata(
                workflowId,
                /*includeKey=*/ null,
                /*excludeKey=*/ null,
                /*expandSubWorkflows=*/ null))
        .thenReturn(
            new CromwellApiWorkflowMetadataResponse()
                .id(workflowId.toString())
                .status(DEFAULT_WORKFLOW_STATUS)
                .submission(DEFAULT_WORKFLOW_SUBMISSION_DATE)
                .submittedFiles(submittedFiles)
                .putCallsItem("call-1", callMetadata)
                .failures(failureMessage));

    ApiWorkflowMetadataResponse result = getWorkflowMetadata(USER_REQUEST, workspaceId, workflowId);
    Assertions.assertEquals(result.getId(), workflowId);
    Assertions.assertEquals(result.getStatus(), DEFAULT_WORKFLOW_STATUS);
    Assertions.assertEquals(result.getSubmission(), DEFAULT_WORKFLOW_SUBMISSION_DATE);

    Assertions.assertEquals(result.getCalls().get("call-1").get(0).getCallRoot(), callRoot);

    Assertions.assertEquals(submittedFiles.getInputs(), "inputs");
    Assertions.assertEquals(submittedFiles.getLabels(), "{}");
    Assertions.assertEquals(submittedFiles.getWorkflow(), "workflow");

    Assertions.assertEquals(failureMessage.get(0).getMessage(), "root");
    Assertions.assertEquals(
        failureMessage.get(0).getCausedBy().get(0).getMessage(), "one level down");
    Assertions.assertEquals(
        failureMessage.get(0).getCausedBy().get(0).getCausedBy().get(0).getMessage(),
        "two levels down");
  }

  @Test
  void query() throws Exception {
    // Stub the workspace access check. The query is restricted to only workflows containing the
    // corresponding workspace id label.
    Mockito.doNothing()
        .when(wsmService)
        .checkWorkspaceReadAccess(workspaceId, USER_REQUEST.getToken());

    CromwellApiWorkflowQueryResult fakeWorkflowQueryResult =
        new CromwellApiWorkflowQueryResult()
            .id(workflowId.toString())
            .name("fake-workflow")
            .status(DEFAULT_WORKFLOW_STATUS)
            .labels(DEFAULT_WORKFLOW_LABELS);

    // Stub the client metadata response.
    Mockito.when(
            cromwellWorkflowService.getQuery(
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.anyList(),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null)))
        .thenReturn(
            new CromwellApiWorkflowQueryResponse()
                .totalResultsCount(1)
                .addResultsItem(fakeWorkflowQueryResult));

    ApiWorkflowQueryResponse result = getWorkflowQuery(USER_REQUEST, workspaceId);
    Assertions.assertEquals(result.getTotalResultsCount(), 1);

    ApiWorkflowQueryResult queryResult = result.getResults().get(0);
    Assertions.assertEquals(queryResult.getId(), fakeWorkflowQueryResult.getId());
    Assertions.assertEquals(queryResult.getName(), fakeWorkflowQueryResult.getName());
    Assertions.assertEquals(queryResult.getStatus(), fakeWorkflowQueryResult.getStatus());
  }

  @Test
  void parseInputs() throws Exception {
    // Stub the workspace access check. The query is restricted to only buckets within the
    // corresponding workspace id label.
    Mockito.doNothing()
        .when(wsmService)
        .checkWorkspaceReadAccess(workspaceId, USER_REQUEST.getToken());

    // Stub the client submit response.
    Map<String, String> fake_parse_results = ImmutableMap.of("input-key", "input-value");
    Mockito.when(
            cromwellWorkflowService.parseInputs(
                Mockito.eq(workspaceId),
                /*workflowGcsUri=*/ Mockito.anyString(),
                Mockito.eq(USER_REQUEST)))
        .thenReturn(fake_parse_results);

    ApiWorkflowParsedInputsResponse result =
        parseInputs(USER_REQUEST, workspaceId, "gs://fake-bucket/path/to/object");
    Assertions.assertEquals(result.getInputs(), fake_parse_results);
  }

  private ApiWorkflowIdAndStatus getWorkflowStatus(
      BearerToken token, UUID workspaceId, UUID workflowId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            token, CROMWELL_WORKFLOW_STATUS_PATH_FORMAT.formatted(workspaceId, workflowId));
    return objectMapper.readValue(serializedResponse, ApiWorkflowIdAndStatus.class);
  }

  private ApiWorkflowIdAndLabel getWorkflowLabels(
      BearerToken token, UUID workspaceId, UUID workflowId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            token, CROMWELL_WORKFLOW_LABELS_PATH_FORMAT.formatted(workspaceId, workflowId));
    return objectMapper.readValue(serializedResponse, ApiWorkflowIdAndLabel.class);
  }

  private ApiWorkflowMetadataResponse getWorkflowMetadata(
      BearerToken token, UUID workspaceId, UUID workflowId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            token, CROMWELL_WORKFLOW_METADATA_PATH_FORMAT.formatted(workspaceId, workflowId));
    return objectMapper.readValue(serializedResponse, ApiWorkflowMetadataResponse.class);
  }

  private ApiWorkflowQueryResponse getWorkflowQuery(BearerToken token, UUID workspaceId)
      throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            token, CROMWELL_WORKFLOW_QUERY_PATH_FORMAT.formatted(workspaceId));
    return objectMapper.readValue(serializedResponse, ApiWorkflowQueryResponse.class);
  }

  private ApiWorkflowParsedInputsResponse parseInputs(
      BearerToken token, UUID workspaceId, String gcsPath) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(
            token, CROMWELL_WORKFLOW_PARSE_INPUTS_PATH_FORMAT.formatted(workspaceId, gcsPath));
    return objectMapper.readValue(serializedResponse, ApiWorkflowParsedInputsResponse.class);
  }
}
