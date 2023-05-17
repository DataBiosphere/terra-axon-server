package bio.terra.axonserver.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.axonserver.model.ApiNotebookStatus;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.axonserver.utils.notebook.GoogleAIPlatformNotebook;
import bio.terra.axonserver.utils.notebook.NotebookStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;

public class GcpResourceControllerTest extends BaseUnitTest {

  @SpyBean private GcpResourceController gcpResourceController;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();
  private final String fakeToken = "faketoken";

  private String getNotebookOperationPath(String operation) {
    return String.format(
        "/api/workspaces/v1/%s/resources/%s/gcp/notebook/%s",
        workspaceId.toString(), resourceId.toString(), operation);
  }

  @Test
  void notebookStart() throws Exception {
    String operationPath = getNotebookOperationPath("start");

    GoogleAIPlatformNotebook notebook = mock(GoogleAIPlatformNotebook.class);
    doReturn(notebook).when(gcpResourceController).getNotebook(workspaceId, resourceId);
    doNothing().when(notebook).start(anyBoolean());
    mockMvc
        .perform(put(operationPath).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(notebook).start(waitCaptor.capture());
    assertEquals(false, waitCaptor.getValue());
  }

  @Test
  void notebookStart_wait() throws Exception {
    String operationPath = getNotebookOperationPath("start");

    GoogleAIPlatformNotebook notebook = mock(GoogleAIPlatformNotebook.class);
    doReturn(notebook).when(gcpResourceController).getNotebook(workspaceId, resourceId);
    doNothing().when(notebook).start(anyBoolean());
    mockMvc
        .perform(
            put(operationPath)
                .header("Authorization", String.format("bearer %s", fakeToken))
                .queryParam("wait", "true"))
        .andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(notebook).start(waitCaptor.capture());
    assertEquals(true, waitCaptor.getValue());
  }

  @Test
  void notebookStop() throws Exception {
    String operationPath = getNotebookOperationPath("stop");

    GoogleAIPlatformNotebook notebook = mock(GoogleAIPlatformNotebook.class);
    doReturn(notebook).when(gcpResourceController).getNotebook(workspaceId, resourceId);
    doNothing().when(notebook).stop(anyBoolean());
    mockMvc
        .perform(put(operationPath).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(notebook).stop(waitCaptor.capture());
    assertEquals(false, waitCaptor.getValue());
  }

  @Test
  void notebookStop_wait() throws Exception {
    String operationPath = getNotebookOperationPath("stop");

    GoogleAIPlatformNotebook notebook = mock(GoogleAIPlatformNotebook.class);
    doReturn(notebook).when(gcpResourceController).getNotebook(workspaceId, resourceId);
    doNothing().when(notebook).stop(anyBoolean());
    mockMvc
        .perform(
            put(operationPath)
                .header("Authorization", String.format("bearer %s", fakeToken))
                .queryParam("wait", "true"))
        .andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(notebook).stop(waitCaptor.capture());
    assertEquals(true, waitCaptor.getValue());
  }

  @Test
  void notebookStatus() throws Exception {
    String operationPath = getNotebookOperationPath("status");

    GoogleAIPlatformNotebook notebook = mock(GoogleAIPlatformNotebook.class);
    doReturn(notebook).when(gcpResourceController).getNotebook(workspaceId, resourceId);
    when(notebook.getStatus()).thenReturn(NotebookStatus.ACTIVE);

    String serializedGetResponse =
        mockMvc
            .perform(
                get(operationPath).header("Authorization", String.format("bearer %s", fakeToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiNotebookStatus status =
        objectMapper.readValue(serializedGetResponse, ApiNotebookStatus.class);

    assertEquals(ApiNotebookStatus.NotebookStatusEnum.ACTIVE, status.getNotebookStatus());
    Mockito.verify(notebook).getStatus();
  }

  @Test
  void notebookProxyUrl() throws Exception {
    String operationPath = getNotebookOperationPath("proxyUrl");

    String fakeUrl = "https://example.com";
    GoogleAIPlatformNotebook notebook = mock(GoogleAIPlatformNotebook.class);
    doReturn(notebook).when(gcpResourceController).getNotebook(workspaceId, resourceId);
    when(notebook.getProxyUrl()).thenReturn(fakeUrl);

    String serializedGetResponse =
        mockMvc
            .perform(
                get(operationPath).header("Authorization", String.format("bearer %s", fakeToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiSignedUrlReport url =
        objectMapper.readValue(serializedGetResponse, ApiSignedUrlReport.class);

    assertEquals(fakeUrl, url.getSignedUrl());
    Mockito.verify(notebook).getProxyUrl();
  }
}
