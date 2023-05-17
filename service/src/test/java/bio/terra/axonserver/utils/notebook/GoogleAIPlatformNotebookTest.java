package bio.terra.axonserver.utils.notebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.withSettings;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import java.io.IOException;
import javax.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.mockito.MockSettings;
import org.mockito.Mockito;

public class GoogleAIPlatformNotebookTest {
  private final InstanceName instanceName =
      InstanceName.builder()
          .projectId("fakeproject")
          .location("fakelocation")
          .instanceId("fakeinstanceid")
          .build();

  /**
   * Settings required to mock final classes/methods, required for several GCP SDK objects. Requires
   * Mockito >= 4.8.0.
   */
  private final MockSettings finalMockSettings = withSettings().mockMaker(MockMakers.INLINE);

  private final AIPlatformNotebooksCow aiPlatformNotebooksCow = mock(AIPlatformNotebooksCow.class);

  /**
   * Test notebook instance is wrapped in a Mockito spy so that we can stub/verify calls to {@link
   * GoogleAIPlatformNotebook#pollForSuccess}.
   */
  private final GoogleAIPlatformNotebook notebook =
      spy(new GoogleAIPlatformNotebook(instanceName, aiPlatformNotebooksCow));

  private final AIPlatformNotebooksCow.Instances mockInstances =
      mock(AIPlatformNotebooksCow.Instances.class);
  private final Operation mockOperation = mock(Operation.class, finalMockSettings);

  @BeforeEach
  public void setUp() {
    Mockito.when(aiPlatformNotebooksCow.instances()).thenReturn(mockInstances);
    doNothing().when(notebook).pollForSuccess(any(), any());
  }

  @AfterEach
  public void tearDown() {
    Mockito.reset(aiPlatformNotebooksCow, notebook, mockInstances, mockOperation);
  }

  private void verifyPollForSuccess(String operation) {
    ArgumentCaptor<Operation> operationArgumentCaptor = ArgumentCaptor.forClass(Operation.class);
    ArgumentCaptor<String> errorMessageArgumentCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(notebook)
        .pollForSuccess(operationArgumentCaptor.capture(), errorMessageArgumentCaptor.capture());
    assertEquals(mockOperation, operationArgumentCaptor.getValue());
    assertTrue(errorMessageArgumentCaptor.getValue().contains(operation));
  }

  @Test
  public void start() throws IOException {

    // Create the Start Request Mock
    var mockStart = mock(AIPlatformNotebooksCow.Instances.Start.class, finalMockSettings);

    // Wire the request mock to return the fake operation
    Mockito.when(mockStart.execute()).thenReturn(mockOperation);

    // Wire the mock Instances to return the fake request
    Mockito.when(mockInstances.start(instanceName)).thenReturn(mockStart);

    // Call the method
    notebook.start(false);

    // Make sure pollForSuccess was not called.
    Mockito.verify(notebook, never()).pollForSuccess(any(), any());
  }

  @Test
  public void start_wait() throws IOException {

    // Create the Start Request Mock
    var mockStart = mock(AIPlatformNotebooksCow.Instances.Start.class, finalMockSettings);

    // Wire the request mock to return the fake operation
    Mockito.when(mockStart.execute()).thenReturn(mockOperation);

    // Wire the mock Instances to return the fake request
    Mockito.when(mockInstances.start(instanceName)).thenReturn(mockStart);

    // Call the method
    notebook.start(true);

    // Verify that pollForSuccess was called with the right operation error message.
    verifyPollForSuccess("start");
  }

  @Test
  public void start_throw() throws IOException {
    Mockito.when(mockInstances.start(instanceName)).thenThrow(new IOException());
    try {
      notebook.start(true);
      fail("Expected InternalServerErrorException");
    } catch (InternalServerErrorException e) {
      assertTrue(e.getMessage().contains("start"));
    }
  }

  @Test
  public void stop() throws IOException {

    // Create the Start Request Mock
    var mockStop = mock(AIPlatformNotebooksCow.Instances.Stop.class, finalMockSettings);

    // Wire the request mock to return the fake operation
    Mockito.when(mockStop.execute()).thenReturn(mockOperation);

    // Wire the mock Instances to return the fake request
    Mockito.when(mockInstances.stop(instanceName)).thenReturn(mockStop);

    // Call the method
    notebook.stop(false);

    // Make sure pollForSuccess was not called.
    Mockito.verify(notebook, never()).pollForSuccess(any(), any());
  }

  @Test
  public void stop_wait() throws IOException {

    // Create the Start Request Mock
    var mockStop = mock(AIPlatformNotebooksCow.Instances.Stop.class, finalMockSettings);

    // Wire the request mock to return the fake operation
    Mockito.when(mockStop.execute()).thenReturn(mockOperation);

    // Wire the mock Instances to return the fake request
    Mockito.when(mockInstances.stop(instanceName)).thenReturn(mockStop);

    // Call the method
    notebook.stop(true);

    // Verify that pollForSuccess was called with the right operation error message.
    verifyPollForSuccess("stop");
  }

  @Test
  public void stop_throw() throws IOException {
    Mockito.when(mockInstances.stop(instanceName)).thenThrow(new IOException());
    try {
      notebook.stop(true);
      fail("Expected InternalServerErrorException");
    } catch (InternalServerErrorException e) {
      assertTrue(e.getMessage().contains("stop"));
    }
  }

  @Test
  public void getStatus() throws IOException {
    // Create the Start Request Mock
    var mockGet = mock(AIPlatformNotebooksCow.Instances.Get.class, finalMockSettings);
    Mockito.when(mockInstances.get(instanceName)).thenReturn(mockGet);

    for (NotebookStatus status : NotebookStatus.values()) {
      // Wire the request mock to return the fake state
      Instance mockInstance = mock(Instance.class, finalMockSettings);
      Mockito.when(mockInstance.getState()).thenReturn(status.toString());
      Mockito.when(mockGet.execute()).thenReturn(mockInstance);
      assertEquals(status, notebook.getStatus());
    }
  }

  @Test
  public void getStatus_unknown() throws IOException {
    // Create the Start Request Mock
    var mockGet = mock(AIPlatformNotebooksCow.Instances.Get.class, finalMockSettings);
    Mockito.when(mockInstances.get(instanceName)).thenReturn(mockGet);

    // Wire the request mock to return the fake state
    Instance mockInstance = mock(Instance.class, finalMockSettings);
    Mockito.when(mockInstance.getState()).thenReturn("JUNK");
    Mockito.when(mockGet.execute()).thenReturn(mockInstance);
    assertEquals(NotebookStatus.STATE_UNSPECIFIED, notebook.getStatus());
  }

  @Test
  public void getStatus_throw() throws IOException {
    Mockito.when(mockInstances.get(instanceName)).thenThrow(new IOException());
    try {
      notebook.getStatus();
      fail("Expected InternalServerErrorException");
    } catch (InternalServerErrorException e) {
      assertTrue(e.getMessage().contains("get status"));
    }
  }

  @Test
  public void getProxyUrl() throws IOException {
    // Create the Start Request Mock
    var mockGet = mock(AIPlatformNotebooksCow.Instances.Get.class, finalMockSettings);
    Mockito.when(mockInstances.get(instanceName)).thenReturn(mockGet);

    // Wire the request mock to return a fake URI

    String fakeUrl = "http://example.com";
    Instance mockInstance = mock(Instance.class, finalMockSettings);
    Mockito.when(mockInstance.getProxyUri()).thenReturn(fakeUrl);
    Mockito.when(mockGet.execute()).thenReturn(mockInstance);
    assertEquals(fakeUrl, notebook.getProxyUrl());
  }

  @Test
  public void getProxyUrl_throw() throws IOException {
    Mockito.when(mockInstances.get(instanceName)).thenThrow(new IOException());
    try {
      notebook.getProxyUrl();
      fail("Expected InternalServerErrorException");
    } catch (InternalServerErrorException e) {
      assertTrue(e.getMessage().contains("get proxy url"));
    }
  }
}
