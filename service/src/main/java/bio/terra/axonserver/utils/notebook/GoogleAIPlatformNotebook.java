package bio.terra.axonserver.utils.notebook;

import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.axonserver.utils.ResourceUtils;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.workspace.model.GcpAiNotebookInstanceAttributes;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceType;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.ws.rs.InternalServerErrorException;

/** Utility class for running common notebook operations on a Google Vertex IA Notebook instance. */
public class GoogleAIPlatformNotebook {

  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-axon-server").build();
  private final InstanceName instanceName;
  private final AIPlatformNotebooksCow aiPlatformNotebooksCow;

  /** For testing use only, allows use of mock {@link AIPlatformNotebooksCow}. */
  @VisibleForTesting
  public GoogleAIPlatformNotebook(
      InstanceName instanceName, AIPlatformNotebooksCow aiPlatformNotebooksCow) {
    this.instanceName = instanceName;
    this.aiPlatformNotebooksCow = aiPlatformNotebooksCow;
  }

  private static InstanceName buildInstanceName(GcpAiNotebookInstanceAttributes attributes) {
    return InstanceName.builder()
        .projectId(attributes.getProjectId())
        .location(attributes.getLocation())
        .instanceId(attributes.getInstanceId())
        .build();
  }

  private GoogleAIPlatformNotebook(ResourceDescription resource, String accessToken)
      throws GeneralSecurityException, IOException {
    this(
        buildInstanceName(resource.getResourceAttributes().getGcpAiNotebookInstance()),
        AIPlatformNotebooksCow.create(
            clientConfig, CloudStorageUtils.getGoogleCredentialsFromToken(accessToken)));
  }

  /** Factory method to create an instance of class {@link GoogleAIPlatformNotebook}. */
  public static GoogleAIPlatformNotebook create(
      ResourceDescription resourceDescription, String accessToken) {
    ResourceUtils.validateResourceType(ResourceType.AI_NOTEBOOK, resourceDescription);

    try {
      return new GoogleAIPlatformNotebook(resourceDescription, accessToken);
    } catch (GeneralSecurityException | IOException e) {
      throw new InternalServerErrorException(e);
    }
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

  /**
   * Start the notebook instance.
   *
   * <p>If wait is true, the call blocks until the notebook has reached status {@link
   * NotebookStatus#ACTIVE}. Otherwise. the call returns immediately, and caller may subsequently
   * check status by calling {@link #getStatus()}.
   *
   * @param wait if true, wait for operation to complete, otherwise return immediately.
   */
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

  /**
   * Stop the notebook instance.
   *
   * <p>If wait is true, the call blocks until the notebook has reached status {@link
   * NotebookStatus#STOPPED}. Otherwise. the call returns immediately, and caller may subsequently
   * check status by calling {@link #getStatus()}.
   *
   * @param wait if true, wait for operation to complete, otherwise return immediately.
   */
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

  /**
   * Gets current status of the Notebook instance.
   *
   * @return current status
   */
  public NotebookStatus getStatus() {
    try {
      return NotebookStatus.valueOf(get("get status").getState());
    } catch (IllegalArgumentException e) {
      return NotebookStatus.STATE_UNSPECIFIED;
    }
  }

  /**
   * Gets a link by which an authorized user can access the notebook instance.
   *
   * @return a URL providing access to the Notebook instance UI
   */
  public String getProxyUrl() {
    return get("get proxy url").getProxyUri();
  }
}
