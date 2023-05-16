package bio.terra.axonserver.utils.notebook;

import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.workspace.model.ResourceDescription;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.ws.rs.InternalServerErrorException;

public class GoogleAIPlatformNotebook extends Notebook {

  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-axon-server").build();
  private final String instanceName;
  private final AIPlatformNotebooksCow aiPlatformNotebooksCow;

  /** For testing use only, allows use of mock {@link AIPlatformNotebooksCow}. */
  @VisibleForTesting
  public GoogleAIPlatformNotebook(
      String instanceName, AIPlatformNotebooksCow aiPlatformNotebooksCow) {
    this.instanceName = instanceName;
    this.aiPlatformNotebooksCow = aiPlatformNotebooksCow;
  }

  public GoogleAIPlatformNotebook(ResourceDescription resource, String accessToken)
      throws GeneralSecurityException, IOException {
    this(
        resource.getResourceAttributes().getGcpAiNotebookInstance().getInstanceId(),
        AIPlatformNotebooksCow.create(
            clientConfig, CloudStorageUtils.getGoogleCredentialsFromToken(accessToken)));
  }

  /** Do not use, made public for test spies. */
  @VisibleForTesting
  public void pollForSuccess(Operation operation, String errorMessage) {
    OperationCow<Operation> operationCow =
        aiPlatformNotebooksCow.operations().operationCow(operation);

    try {
      operationCow =
          OperationUtils.pollUntilComplete(
              operationCow, Duration.ofSeconds(5), Duration.ofMinutes(3));
    } catch (InterruptedException | IOException e) {
      throw new InternalServerErrorException(errorMessage, e);
    }

    if (operationCow.getOperation().getError() != null) {
      throw new InternalServerErrorException(
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
      throw new InternalServerErrorException(getOperationErrorMessage("start"), e);
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
      throw new InternalServerErrorException(getOperationErrorMessage("stop"), e);
    }
  }

  private Instance get(String operation) {
    try {
      return aiPlatformNotebooksCow.instances().get(instanceName).execute();
    } catch (IOException e) {
      throw new InternalServerErrorException(getOperationErrorMessage(operation), e);
    }
  }

  @Override
  public NotebookStatus getStatus() {
    try {
      return NotebookStatus.valueOf(get("get status").getState());
    } catch (IllegalArgumentException e) {
      return NotebookStatus.STATE_UNSPECIFIED;
    }
  }

  @Override
  public String getProxyUrl() {
    return get("get proxy url").getProxyUri();
  }
}
