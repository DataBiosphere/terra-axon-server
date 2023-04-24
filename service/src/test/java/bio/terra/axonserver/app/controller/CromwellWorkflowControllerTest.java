package bio.terra.axonserver.app.controller;

import static bio.terra.axonserver.testutils.MockMvcUtils.USER_REQUEST;
import static bio.terra.axonserver.testutils.MockMvcUtils.addAuth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import bio.terra.axonserver.service.cromwellworkflow.CromwellWorkflowService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.testutils.BaseUnitTest;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class CromwellWorkflowControllerTest extends BaseUnitTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private CromwellWorkflowService cromwellWorkflowService;
  @MockBean private WorkspaceManagerService wsmService;

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();

  private final String CROMWELL_WORKFLOW_STATUS_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/%s/status";
  private final String CROMWELL_WORKFLOW_LABELS_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/%s/labels";
  private final String CROMWELL_WORKFLOW_METADATA_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/%s/metadata";
  private final String CROMWELL_WORKFLOW_QUERY_PATH_FORMAT =
      "/api/workspaces/%s/cromwell/workflows/query";

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
            new CromwellApiWorkflowIdAndStatus().id(workflowId.toString()).status("Fake status"));

    var status =
        mockMvc
            .perform(
                addAuth(
                    get(CROMWELL_WORKFLOW_STATUS_PATH_FORMAT.formatted(workspaceId, workflowId)),
                    USER_REQUEST))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse();
  }

  @Test
  void labels() throws Exception {
    // Stub the workspace access check, and workspace id label matching.
    Mockito.doNothing()
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    // Stub the workflow having the workflow id label, and the label response
    Mockito.when(cromwellWorkflowService.getLabels(workflowId))
        .thenReturn(
            new CromwellApiLabelsResponse()
                .id(workflowId.toString())
                .putLabelsItem(
                    CromwellWorkflowService.WORKSPACE_ID_LABEL_KEY, workspaceId.toString())
                .putLabelsItem("fake-label-key", "fake-label-value"));

    mockMvc
        .perform(
            addAuth(
                get(CROMWELL_WORKFLOW_LABELS_PATH_FORMAT.formatted(workspaceId, workflowId)),
                USER_REQUEST))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }

  @Test
  void metadata() throws Exception {
    // Stub the workspace access check, and workspace id label matching.
    Mockito.doNothing()
        .when(cromwellWorkflowService)
        .validateWorkspaceAccessAndWorkflowLabelMatches(
            workflowId, workspaceId, USER_REQUEST.getToken());

    // Stub the client metadata response.
    Mockito.when(
            cromwellWorkflowService.getMetadata(
                workflowId,
                /*includeKey=*/ null,
                /*excludeKey=*/ null,
                /*expandSubWorkflows=*/ null))
        .thenReturn(new CromwellApiWorkflowMetadataResponse().id(workspaceId.toString()));

    var status =
        mockMvc
            .perform(
                addAuth(
                    get(CROMWELL_WORKFLOW_METADATA_PATH_FORMAT.formatted(workspaceId, workflowId)),
                    USER_REQUEST))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse();
  }

  @Test
  void query() throws Exception {
    // Stub the workspace access check. The query is restricted to only workflows containing the
    // corresponding workspace id label.
    Mockito.doNothing()
        .when(wsmService)
        .checkWorkspaceReadAccess(workspaceId, USER_REQUEST.getToken());

    // Stub the client metadata response.
    Mockito.when(
            cromwellWorkflowService.getQuery(
                null, null, null, null, null, null, null, null, null, null, null, null))
        .thenReturn(
            new CromwellApiWorkflowQueryResponse()
                .addResultsItem(new CromwellApiWorkflowQueryResult().id(workflowId.toString())));

    var status =
        mockMvc
            .perform(
                addAuth(
                    get(CROMWELL_WORKFLOW_QUERY_PATH_FORMAT.formatted(workspaceId)), USER_REQUEST))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse();
  }
}
