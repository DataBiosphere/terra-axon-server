package bio.terra.axonserver.app.controller;

import static bio.terra.axonserver.testutils.MockMvcUtils.USER_REQUEST;
import static bio.terra.axonserver.testutils.MockMvcUtils.addAuth;
import static bio.terra.axonserver.testutils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.axonserver.model.ApiClusterMetadata;
import bio.terra.axonserver.model.ApiClusterStatus;
import bio.terra.axonserver.model.ApiNotebookStatus;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.model.ApiUrl;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.axonserver.testutils.MockMvcUtils;
import bio.terra.axonserver.utils.dataproc.ClusterStatus;
import bio.terra.axonserver.utils.dataproc.GoogleDataprocCluster;
import bio.terra.axonserver.utils.notebook.GoogleAIPlatformNotebook;
import bio.terra.axonserver.utils.notebook.NotebookStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.dataproc.model.ClusterConfig;
import com.google.api.services.dataproc.model.GceClusterConfig;
import com.google.api.services.dataproc.model.InstanceGroupConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;

public class GcpResourceControllerTest extends BaseUnitTest {
  /**
   * Settings required to mock final classes/methods, required for several GCP SDK objects. Requires
   * Mockito >= 4.8.0.
   */
  private final MockSettings finalMockSettings = withSettings().mockMaker(MockMakers.INLINE);

  @Autowired private MockMvcUtils mockMvcUtils;
  @SpyBean private GcpResourceController gcpResourceController;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();
  private final String fakeToken = "faketoken";

  private String getNotebookOperationPath(String operation) {
    return String.format(
        "/api/workspaces/v1/%s/resources/%s/gcp/notebook/%s", workspaceId, resourceId, operation);
  }

  private String getClusterOperationPath(String operation) {
    return String.format(
        "/api/workspaces/v1/%s/resources/%s/gcp/dataproccluster/%s",
        workspaceId, resourceId, operation);
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
        .perform(addAuth(put(operationPath), USER_REQUEST).queryParam("wait", "true"))
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
            .perform(addAuth(get(operationPath), USER_REQUEST))
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
        mockMvcUtils.getSerializedResponseForGet(USER_REQUEST, operationPath);

    ApiSignedUrlReport url =
        objectMapper.readValue(serializedGetResponse, ApiSignedUrlReport.class);

    assertEquals(fakeUrl, url.getSignedUrl());
    Mockito.verify(notebook).getProxyUrl();
  }

  @Test
  void clusterStart() throws Exception {
    String operationPath = getClusterOperationPath("start");

    GoogleDataprocCluster cluster = mock(GoogleDataprocCluster.class);
    doReturn(cluster).when(gcpResourceController).getCluster(workspaceId, resourceId);
    doNothing().when(cluster).start(anyBoolean());
    mockMvc.perform(addAuth(put(operationPath), USER_REQUEST)).andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(cluster).start(waitCaptor.capture());
    assertEquals(false, waitCaptor.getValue());
  }

  @Test
  void clusterStart_wait() throws Exception {
    String operationPath = getClusterOperationPath("start");

    GoogleDataprocCluster cluster = mock(GoogleDataprocCluster.class);
    doReturn(cluster).when(gcpResourceController).getCluster(workspaceId, resourceId);
    doNothing().when(cluster).start(anyBoolean());
    mockMvc
        .perform(addAuth(put(operationPath), USER_REQUEST).queryParam("wait", "true"))
        .andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(cluster).start(waitCaptor.capture());
    assertEquals(true, waitCaptor.getValue());
  }

  @Test
  void clusterStop() throws Exception {
    String operationPath = getClusterOperationPath("stop");

    GoogleDataprocCluster cluster = mock(GoogleDataprocCluster.class);
    doReturn(cluster).when(gcpResourceController).getCluster(workspaceId, resourceId);
    doNothing().when(cluster).stop(anyBoolean());
    mockMvc.perform(addAuth(put(operationPath), USER_REQUEST)).andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(cluster).stop(waitCaptor.capture());
    assertEquals(false, waitCaptor.getValue());
  }

  @Test
  void clusterStop_wait() throws Exception {
    String operationPath = getClusterOperationPath("stop");

    GoogleDataprocCluster cluster = mock(GoogleDataprocCluster.class);
    doReturn(cluster).when(gcpResourceController).getCluster(workspaceId, resourceId);
    doNothing().when(cluster).stop(anyBoolean());
    mockMvc
        .perform(addAuth(put(operationPath), USER_REQUEST).queryParam("wait", "true"))
        .andExpect(status().isOk());

    ArgumentCaptor<Boolean> waitCaptor = ArgumentCaptor.forClass(Boolean.class);
    Mockito.verify(cluster).stop(waitCaptor.capture());
    assertEquals(true, waitCaptor.getValue());
  }

  @Test
  void clusterStatus() throws Exception {
    String operationPath = getClusterOperationPath("status");

    GoogleDataprocCluster cluster = mock(GoogleDataprocCluster.class);
    doReturn(cluster).when(gcpResourceController).getCluster(workspaceId, resourceId);
    when(cluster.getStatus()).thenReturn(ClusterStatus.RUNNING);

    String serializedGetResponse =
        mockMvcUtils.getSerializedResponseForGet(USER_REQUEST, operationPath);

    ApiClusterStatus status = objectMapper.readValue(serializedGetResponse, ApiClusterStatus.class);

    assertEquals(ApiClusterStatus.StatusEnum.RUNNING, status.getStatus());
    Mockito.verify(cluster).getStatus();
  }

  @Test
  void clusterComponentUrl() throws Exception {
    String operationPath = getClusterOperationPath("componentUrl");

    String fakeUrl = "https://example.com";
    GoogleDataprocCluster cluster = mock(GoogleDataprocCluster.class);
    doReturn(cluster).when(gcpResourceController).getCluster(workspaceId, resourceId);
    when(cluster.getComponentUrl("fakekey")).thenReturn(fakeUrl);

    String serializedGetResponse =
        mockMvc
            .perform(
                addJsonContentType(addAuth(get(operationPath), USER_REQUEST))
                    .param("componentKey", "fakekey"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiUrl url = objectMapper.readValue(serializedGetResponse, ApiUrl.class);

    assertEquals(fakeUrl, url.getUrl());
    Mockito.verify(cluster).getComponentUrl("fakekey");
  }

  @Test
  void clusterMetadata_get() throws Exception {
    String operationPath = getClusterOperationPath("metadata");

    GoogleDataprocCluster mockGoogleDataprocCluster = mock(GoogleDataprocCluster.class);
    Cluster mockCluster = mock(Cluster.class, finalMockSettings);
    ClusterConfig mockClusterConfig = mock(ClusterConfig.class, finalMockSettings);
    GceClusterConfig mockGceClusterConfig = mock(GceClusterConfig.class, finalMockSettings);
    InstanceGroupConfig mockMasterConfig = mock(InstanceGroupConfig.class, finalMockSettings);
    InstanceGroupConfig mockWorkerConfig = mock(InstanceGroupConfig.class, finalMockSettings);

    doReturn(mockGoogleDataprocCluster)
        .when(gcpResourceController)
        .getCluster(workspaceId, resourceId);
    when(mockGoogleDataprocCluster.getClusterConfig()).thenReturn(mockClusterConfig);
    when(mockCluster.getConfig()).thenReturn(mockClusterConfig);
    when(mockClusterConfig.getGceClusterConfig()).thenReturn(mockGceClusterConfig);
    when(mockClusterConfig.getMasterConfig()).thenReturn(mockMasterConfig);
    when(mockClusterConfig.getWorkerConfig()).thenReturn(mockWorkerConfig);
    when(mockClusterConfig.getSecondaryWorkerConfig()).thenReturn(mockWorkerConfig);
    when(mockMasterConfig.getNumInstances()).thenReturn(1);
    when(mockWorkerConfig.getNumInstances()).thenReturn(2);

    String serializedGetResponse =
        mockMvcUtils.getSerializedResponseForGet(USER_REQUEST, operationPath);

    ApiClusterMetadata metadata =
        objectMapper.readValue(serializedGetResponse, ApiClusterMetadata.class);
    assertEquals(1, metadata.getManagerNodeConfig().getNumInstances());
    assertEquals(2, metadata.getPrimaryWorkerConfig().getNumInstances());
    assertEquals(2, metadata.getSecondaryWorkerConfig().getNumInstances());
    Mockito.verify(mockGoogleDataprocCluster).getClusterConfig();
  }
}
