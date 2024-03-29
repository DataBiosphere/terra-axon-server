package bio.terra.axonserver.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.axonserver.model.ApiNotebookStatus;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.cloud.aws.AwsService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.axonserver.utils.notebook.AwsSageMakerNotebookUtil;
import bio.terra.axonserver.utils.notebook.NotebookStatus;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;

public class AwsResourceControllerTest extends BaseUnitTest {
  @MockBean private WorkspaceManagerService wsmService;
  @MockBean private AwsService awsService;

  @SpyBean private AwsResourceController awsResourceController;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();
  private final String fakeToken = "faketoken";
  private final String path =
      String.format("/api/workspaces/v1/%s/resources/%s/aws/consoleUrl", workspaceId, resourceId);

  @Test
  void getSignedConsoleUrl_awsOn() throws Exception {
    ResourceDescription mockResourceDescription = mock(ResourceDescription.class);

    // Expect the provided fake token will be passed to getResource, and return our mocked resource.

    Mockito.when(wsmService.getResource(workspaceId, resourceId, fakeToken))
        .thenReturn(mockResourceDescription);

    // Expect the provided fake token and WS ID will be passed to getHighestRole, return a canned
    // highest role.

    IamRole iamRole = IamRole.WRITER;
    Mockito.when(wsmService.getHighestRole(workspaceId, IamRole.READER, fakeToken))
        .thenReturn(iamRole);

    // Expect the provided fake token, mock resource, cannd IAM role, and any duration will be
    // passed to getAwsResourceCredential and return a mocked credential.  We will verify passed
    // duration was in range after the fact.

    AwsCredential mockCredential = mock(AwsCredential.class);
    Mockito.when(
            wsmService.getAwsResourceCredential(
                eq(mockResourceDescription),
                eq(WorkspaceManagerService.inferAwsCredentialAccessScope(iamRole)),
                any(),
                eq(fakeToken)))
        .thenReturn(mockCredential);

    URL fakeUrl = new URL("https://example.com");

    // Expect the mocked resource, cred, and any duration are passed to createSignedConsoleUrl, and
    // return our fake URL.  We will verify passed duration was in range after the fact.

    Mockito.when(
            awsService.createSignedConsoleUrl(
                eq(mockResourceDescription), eq(mockCredential), any()))
        .thenReturn(fakeUrl);

    // Verify that the response was OK, and we got our fake URL back.

    String serializedGetResponse =
        mockMvc
            .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiSignedUrlReport apiSignedUrlReport =
        objectMapper.readValue(serializedGetResponse, ApiSignedUrlReport.class);

    assertEquals(fakeUrl.toString(), apiSignedUrlReport.getSignedUrl());

    ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);

    // Verify that credential duration requested from WSM was within range.

    verify(wsmService)
        .getAwsResourceCredential(any(), any(), integerArgumentCaptor.capture(), any());

    assertThat(
        integerArgumentCaptor.getValue(),
        greaterThanOrEqualTo(WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MIN));
    assertThat(
        integerArgumentCaptor.getValue(),
        lessThanOrEqualTo(WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MAX));

    // Verify that credential duration requested from the console federation endpoint was within
    // range.

    verify(awsService).createSignedConsoleUrl(any(), any(), integerArgumentCaptor.capture());

    assertThat(
        integerArgumentCaptor.getValue(),
        greaterThanOrEqualTo(AwsService.MIN_CONSOLE_SESSION_DURATION));
    assertThat(
        integerArgumentCaptor.getValue(),
        lessThanOrEqualTo(AwsService.MAX_CONSOLE_SESSION_DURATION));
  }

  @Test
  void getSignedConsoleUrl_resourceNotFound() throws Exception {
    Mockito.when(wsmService.getResource(workspaceId, resourceId, fakeToken))
        .thenThrow(new NotFoundException("not found"));
    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isNotFound());
  }

  @Test
  void getSignedConsoleUrl_internalError() throws Exception {
    Mockito.when(wsmService.getResource(workspaceId, resourceId, fakeToken))
        .thenThrow(new InternalServerErrorException("internal error"));
    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void getSignedConsoleUrl_badResourceType() throws Exception {
    ResourceDescription mockResourceDescription = mock(ResourceDescription.class);
    IamRole highestRole = IamRole.WRITER;
    Mockito.when(wsmService.getResource(workspaceId, resourceId, fakeToken))
        .thenReturn(mockResourceDescription);
    Mockito.when(wsmService.getHighestRole(workspaceId, IamRole.READER, fakeToken))
        .thenReturn(highestRole);
    Mockito.when(
            wsmService.getAwsResourceCredential(
                eq(mockResourceDescription),
                eq(WorkspaceManagerService.inferAwsCredentialAccessScope(highestRole)),
                any(),
                eq(fakeToken)))
        .thenThrow(new InvalidResourceTypeException("invalid resource type"));

    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSignedConsoleUrl_unauthorized() throws Exception {
    ResourceDescription mockResourceDescription = mock(ResourceDescription.class);
    IamRole highestRole = IamRole.DISCOVERER;
    Mockito.when(wsmService.getResource(workspaceId, resourceId, fakeToken))
        .thenReturn(mockResourceDescription);
    Mockito.when(wsmService.getHighestRole(workspaceId, IamRole.READER, fakeToken))
        .thenReturn(highestRole);

    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isForbidden());
  }

  private String getNotebookOperationPath(String operation) {
    return String.format(
        "/api/workspaces/v1/%s/resources/%s/aws/notebook/%s", workspaceId, resourceId, operation);
  }

  @Test
  void notebookStart() throws Exception {
    String operationPath = getNotebookOperationPath("start");
    AwsSageMakerNotebookUtil notebook = mock(AwsSageMakerNotebookUtil.class);
    doReturn(notebook).when(awsResourceController).getNotebook(workspaceId, resourceId);
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
    AwsSageMakerNotebookUtil notebook = mock(AwsSageMakerNotebookUtil.class);
    doReturn(notebook).when(awsResourceController).getNotebook(workspaceId, resourceId);
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
    AwsSageMakerNotebookUtil notebook = mock(AwsSageMakerNotebookUtil.class);
    doReturn(notebook).when(awsResourceController).getNotebook(workspaceId, resourceId);
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
    AwsSageMakerNotebookUtil notebook = mock(AwsSageMakerNotebookUtil.class);
    doReturn(notebook).when(awsResourceController).getNotebook(workspaceId, resourceId);
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
    AwsSageMakerNotebookUtil notebook = mock(AwsSageMakerNotebookUtil.class);
    doReturn(notebook)
        .when(awsResourceController)
        .getNotebook(workspaceId, resourceId, AwsCredentialAccessScope.READ_ONLY);
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
    AwsSageMakerNotebookUtil notebook = mock(AwsSageMakerNotebookUtil.class);
    doReturn(notebook).when(awsResourceController).getNotebook(workspaceId, resourceId);
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
