package bio.terra.axonserver.utils.dataproc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.withSettings;

import bio.terra.axonserver.service.exception.ComponentNotFoundException;
import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.dataproc.model.ClusterConfig;
import com.google.api.services.dataproc.model.EndpointConfig;
import com.google.api.services.dataproc.model.Operation;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.mockito.MockSettings;
import org.mockito.Mockito;

public class GoogleDataprocClusterTest {
  private final ClusterName clusterName =
      ClusterName.builder()
          .projectId("fakeproject")
          .region("fakeregion")
          .name("fakeclustername")
          .build();

  /**
   * Settings required to mock final classes/methods, required for several GCP SDK objects. Requires
   * Mockito >= 4.8.0.
   */
  private final MockSettings finalMockSettings = withSettings().mockMaker(MockMakers.INLINE);

  private final DataprocCow dataprocCow = mock(DataprocCow.class);

  /**
   * Test cluster is wrapped in a Mockito spy so that we can stub/verify calls to {@link
   * GoogleDataprocCluster#pollForSuccess}.
   */
  private final GoogleDataprocCluster cluster =
      spy(new GoogleDataprocCluster(clusterName, dataprocCow));

  private final DataprocCow.Clusters mockClusters = mock(DataprocCow.Clusters.class);
  private final Operation mockOperation = mock(Operation.class, finalMockSettings);

  @BeforeEach
  public void setUp() {
    Mockito.when(dataprocCow.clusters()).thenReturn(mockClusters);
    doNothing().when(cluster).pollForSuccess(any(), any());
  }

  @AfterEach
  public void tearDown() {
    Mockito.reset(dataprocCow, cluster, mockClusters, mockOperation);
  }

  private void verifyPollForSuccess(String operation) {
    ArgumentCaptor<Operation> operationArgumentCaptor = ArgumentCaptor.forClass(Operation.class);
    ArgumentCaptor<String> errorMessageArgumentCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(cluster)
        .pollForSuccess(operationArgumentCaptor.capture(), errorMessageArgumentCaptor.capture());
    assertEquals(mockOperation, operationArgumentCaptor.getValue());
    assertTrue(errorMessageArgumentCaptor.getValue().contains(operation));
  }

  @Test
  public void start_noWait_doNotPoll() throws IOException {

    // Setup cluster start mock
    var mockStart = mock(DataprocCow.Clusters.Start.class, finalMockSettings);
    Mockito.when(mockStart.execute()).thenReturn(mockOperation);
    Mockito.when(mockClusters.start(clusterName)).thenReturn(mockStart);

    // Call the method
    cluster.start(/* wait=*/ false);

    // Make sure pollForSuccess was not called.
    Mockito.verify(cluster, never()).pollForSuccess(any(), any());
  }

  @Test
  public void start_wait_doNotPoll() throws IOException {

    // Setup cluster start mock
    var mockStart = mock(DataprocCow.Clusters.Start.class, finalMockSettings);
    Mockito.when(mockStart.execute()).thenReturn(mockOperation);
    Mockito.when(mockClusters.start(clusterName)).thenReturn(mockStart);

    // Call the method
    cluster.start(true);

    // Verify that pollForSuccess was called with the right operation error message.
    verifyPollForSuccess("start");
  }

  @Test
  public void start_throw() throws IOException {
    Mockito.when(mockClusters.start(clusterName)).thenThrow(new IOException());
    try {
      cluster.start(true);
      fail("Expected InternalServerErrorException");
    } catch (InternalServerErrorException e) {
      assertTrue(e.getMessage().contains("start"));
    }
  }

  @Test
  public void stop_noWait_noPoll() throws IOException {

    // Setup cluster stop mock
    var mockStop = mock(DataprocCow.Clusters.Stop.class, finalMockSettings);
    Mockito.when(mockStop.execute()).thenReturn(mockOperation);
    Mockito.when(mockClusters.stop(clusterName)).thenReturn(mockStop);

    // Call the method
    cluster.stop(false);

    // Make sure pollForSuccess was not called.
    Mockito.verify(cluster, never()).pollForSuccess(any(), any());
  }

  @Test
  public void stop_wait() throws IOException {

    // Setup cluster stop mock
    var mockStop = mock(DataprocCow.Clusters.Stop.class, finalMockSettings);
    Mockito.when(mockStop.execute()).thenReturn(mockOperation);
    Mockito.when(mockClusters.stop(clusterName)).thenReturn(mockStop);

    // Call the method
    cluster.stop(true);

    // Verify that pollForSuccess was called with the right operation error message.
    verifyPollForSuccess("stop");
  }

  @Test
  public void stop_throw() throws IOException {
    Mockito.when(mockClusters.stop(clusterName)).thenThrow(new IOException());
    assertThrows(
        InternalServerErrorException.class,
        () -> {
          cluster.stop(true);
        },
        "Expected InternalServerErrorException");
  }

  @Test
  public void getStatus() throws IOException {
    // Setup cluster get mock
    var mockGet = mock(DataprocCow.Clusters.Get.class, finalMockSettings);
    Mockito.when(mockClusters.get(clusterName)).thenReturn(mockGet);

    for (ClusterStatus status : ClusterStatus.values()) {
      // Wire the request mock to return the fake state
      Cluster mockCluster = mock(Cluster.class, finalMockSettings);
      com.google.api.services.dataproc.model.ClusterStatus mockClusterStatus =
          mock(com.google.api.services.dataproc.model.ClusterStatus.class, finalMockSettings);

      Mockito.when(mockCluster.getStatus()).thenReturn(mockClusterStatus);
      Mockito.when(mockClusterStatus.getState()).thenReturn(status.toString());

      Mockito.when(mockGet.execute()).thenReturn(mockCluster);
      assertEquals(status, cluster.getStatus());
    }
  }

  @Test
  public void getStatus_unknown() throws IOException {
    // Setup cluster get status mock
    var mockGet = mock(DataprocCow.Clusters.Get.class, finalMockSettings);
    Mockito.when(mockClusters.get(clusterName)).thenReturn(mockGet);
    Cluster mockCluster = mock(Cluster.class, finalMockSettings);
    com.google.api.services.dataproc.model.ClusterStatus mockClusterStatus =
        mock(com.google.api.services.dataproc.model.ClusterStatus.class, finalMockSettings);

    Mockito.when(mockCluster.getStatus()).thenReturn(mockClusterStatus);
    Mockito.when(mockClusterStatus.getState()).thenReturn("JUNK");

    Mockito.when(mockGet.execute()).thenReturn(mockCluster);
    assertEquals(ClusterStatus.STATE_UNSPECIFIED, cluster.getStatus());
  }

  @Test
  public void getStatus_throw() throws IOException {
    Mockito.when(mockClusters.get(clusterName)).thenThrow(new IOException());
    try {
      cluster.getStatus();
      fail("Expected InternalServerErrorException");
    } catch (InternalServerErrorException e) {
      assertTrue(e.getMessage().contains("get status"));
    }
  }

  @Test
  public void getComponentUrl() throws IOException {
    var mockGet = mock(DataprocCow.Clusters.Get.class, finalMockSettings);
    Mockito.when(mockClusters.get(clusterName)).thenReturn(mockGet);

    // Wire the request mock to return a fake URI
    String fakeUrl = "http://example.com";
    Cluster mockCluster = mock(Cluster.class, finalMockSettings);
    ClusterConfig mockClusterConfig = mock(ClusterConfig.class, finalMockSettings);
    EndpointConfig mockEndpointConfig = mock(EndpointConfig.class, finalMockSettings);
    Map<String, String> mockHttpPorts = mock(HashMap.class, finalMockSettings);

    Mockito.when(mockCluster.getConfig()).thenReturn(mockClusterConfig);
    Mockito.when(mockClusterConfig.getEndpointConfig()).thenReturn(mockEndpointConfig);
    Mockito.when(mockEndpointConfig.getHttpPorts()).thenReturn(mockHttpPorts);
    Mockito.when(mockHttpPorts.get("JupyterLab")).thenReturn(fakeUrl);

    Mockito.when(mockGet.execute()).thenReturn(mockCluster);

    // Assert that the fake URI is returned
    assertEquals(fakeUrl, cluster.getComponentUrl("JupyterLab"));
  }

  @Test
  public void getComponentUrl_invalidComponent() throws IOException {
    Mockito.when(mockClusters.get(clusterName))
        .thenThrow(new ComponentNotFoundException("component not found"));
    try {
      cluster.getComponentUrl("badComponent");
      fail("Expected ComponentNotFoundException");
    } catch (ComponentNotFoundException e) {
      assertTrue(e.getMessage().contains("component not found"));
    }
  }
}
