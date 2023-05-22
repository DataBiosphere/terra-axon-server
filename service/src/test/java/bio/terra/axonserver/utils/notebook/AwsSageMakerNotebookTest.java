package bio.terra.axonserver.utils.notebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;

import bio.terra.cloudres.aws.notebook.SageMakerNotebookCow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sagemaker.model.DescribeNotebookInstanceResponse;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class AwsSageMakerNotebookTest {
  private final String instanceName = "fakeinstancename";
  private final SageMakerNotebookCow sageMakerNotebookCow = mock(SageMakerNotebookCow.class);

  private final AwsSageMakerNotebook notebook =
      new AwsSageMakerNotebook(instanceName, sageMakerNotebookCow);

  @AfterEach
  public void tearDown() {
    Mockito.reset(sageMakerNotebookCow);
  }

  @Test
  public void start() {
    notebook.start(false);
    ArgumentCaptor<String> instanceNameCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(sageMakerNotebookCow).start(instanceNameCaptor.capture());
    assertEquals(instanceName, instanceNameCaptor.getValue());
  }

  @Test
  public void start_wait() {
    notebook.start(true);
    ArgumentCaptor<String> instanceNameCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(sageMakerNotebookCow).startAndWait(instanceNameCaptor.capture());
    assertEquals(instanceName, instanceNameCaptor.getValue());
  }

  @Test
  public void stop() {
    notebook.stop(false);
    ArgumentCaptor<String> instanceNameCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(sageMakerNotebookCow).stop(instanceNameCaptor.capture());
    assertEquals(instanceName, instanceNameCaptor.getValue());
  }

  @Test
  public void stop_wait() {
    notebook.stop(true);
    ArgumentCaptor<String> instanceNameCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(sageMakerNotebookCow).stopAndWait(instanceNameCaptor.capture());
    assertEquals(instanceName, instanceNameCaptor.getValue());
  }

  private void checkStatus(NotebookInstanceStatus awsStatus, NotebookStatus axonStatus) {
    DescribeNotebookInstanceResponse fakeResponse =
        DescribeNotebookInstanceResponse.builder().notebookInstanceStatus(awsStatus).build();
    Mockito.when(sageMakerNotebookCow.get(instanceName)).thenReturn(fakeResponse);
    assertEquals(axonStatus, notebook.getStatus());
    Mockito.verify(sageMakerNotebookCow).get(any());
    Mockito.reset(sageMakerNotebookCow);
  }

  @Test
  public void getStatus() {
    checkStatus(NotebookInstanceStatus.PENDING, NotebookStatus.PENDING);
    checkStatus(NotebookInstanceStatus.DELETING, NotebookStatus.DELETING);
    checkStatus(NotebookInstanceStatus.FAILED, NotebookStatus.FAILED);
    checkStatus(NotebookInstanceStatus.IN_SERVICE, NotebookStatus.ACTIVE);
    checkStatus(NotebookInstanceStatus.STOPPED, NotebookStatus.STOPPED);
    checkStatus(NotebookInstanceStatus.STOPPING, NotebookStatus.STOPPING);
    checkStatus(NotebookInstanceStatus.UPDATING, NotebookStatus.UPDATING);
    checkStatus(NotebookInstanceStatus.UNKNOWN_TO_SDK_VERSION, NotebookStatus.STATE_UNSPECIFIED);
  }

  @Test
  public void getNotebookProxyUrl() {
    String fakeUrl = "https://example.com";
    Mockito.when(sageMakerNotebookCow.createPresignedUrl(instanceName)).thenReturn(fakeUrl);
    assertEquals(fakeUrl, notebook.getProxyUrl());
    Mockito.verify(sageMakerNotebookCow).createPresignedUrl(instanceName);
  }
}
