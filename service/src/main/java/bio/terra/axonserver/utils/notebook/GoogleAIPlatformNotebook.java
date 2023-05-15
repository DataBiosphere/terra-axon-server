package bio.terra.axonserver.utils.notebook;

import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.workspace.model.ResourceDescription;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;

public class GoogleAIPlatformNotebook extends Notebook {

  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-axon-server").build();
  private final String instanceName;
  private final AIPlatformNotebooksCow aiPlatformNotebooksCow;

  public GoogleAIPlatformNotebook(ResourceDescription resource, String accessToken) {
    this.instanceName = resource.getResourceAttributes().getGcpAiNotebookInstance().getInstanceId();

    try {
      this.aiPlatformNotebooksCow =
          AIPlatformNotebooksCow.create(
              clientConfig, CloudStorageUtils.getGoogleCredentialsFromToken(accessToken));
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void pollForSuccess(Operation operation, String errorMessage) {
    OperationCow<Operation> operationCow =
        aiPlatformNotebooksCow.operations().operationCow(operation);

    try {
      operationCow =
          OperationUtils.pollUntilComplete(
              operationCow, Duration.ofSeconds(5), Duration.ofMinutes(3));
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }

    if (operationCow.getOperation().getError() != null) {
      throw new RuntimeException(
          errorMessage + operationCow.getOperation().getError().getMessage());
    }
  }

  private static String getOperationErrorMessage(String operation) {
    return String.format("Notebook operation '%s' failed.", operation);
  }

  @Override
  public void start(boolean wait) {
    try {
      Operation startOperation = aiPlatformNotebooksCow.instances().start(instanceName).execute();
      if (wait) {
        pollForSuccess(startOperation, getOperationErrorMessage("start"));
      }
    } catch (IOException e) {
      throw new RuntimeException(getOperationErrorMessage("start"), e);
    }
  }

  @Override
  public void stop(boolean wait) {
    try {
      Operation stopOperation = aiPlatformNotebooksCow.instances().stop(instanceName).execute();
      if (wait) {
        pollForSuccess(stopOperation, getOperationErrorMessage("stop"));
      }
    } catch (IOException e) {
      throw new RuntimeException(getOperationErrorMessage("stop"), e);
    }
  }

  private Instance get(String operation) {
    try {
      return aiPlatformNotebooksCow.instances().get(instanceName).execute();
    } catch (IOException e) {
      throw new RuntimeException(getOperationErrorMessage(operation), e);
    }
  }

  @Override
  public NotebookStatus getStatus() {
    return NotebookStatus.valueOf(get("get status").getState());
  }

  @Override
  public String getNotebookProxyUrl() {
    return get("get proxy url").getProxyUri();
  }
}
