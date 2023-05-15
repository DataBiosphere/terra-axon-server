package bio.terra.axonserver.utils.notebook;

import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceType;

public abstract class Notebook {

  /**
   * Creates a concrete implementation of the abstract Notebook class.
   *
   * @param workspaceManagerService workspace manager service
   * @param resource the notebook resource description
   * @param accessToken access token representing user
   * @return An instance of either {@link AwsSagemakerNotebook} or {@link GoogleAIPlatformNotebook},
   *     depending on the type of the passed resource description
   * @throws {@link InvalidResourceTypeException} if the passed resource description does not
   *     describe a notebook type resource
   */
  public static Notebook create(
      WorkspaceManagerService workspaceManagerService,
      ResourceDescription resource,
      String accessToken) {
    ResourceType resourceType = resource.getMetadata().getResourceType();
    return switch (resourceType) {
      case AI_NOTEBOOK -> new GoogleAIPlatformNotebook(resource, accessToken);
      case AWS_SAGEMAKER_NOTEBOOK -> new AwsSagemakerNotebook(
          workspaceManagerService, resource, accessToken);
      default -> throw new InvalidResourceTypeException(
          String.format("Resource type %s not a notebook type.", resourceType.toString()));
    };
  }

  /**
   * Start the notebook instance.
   *
   * <p>If wait is true, the call blocks until the notebook has reached status {@link
   * NotebookStatus#ACTIVE}. Otherwise. the call returns immediately, and caller may subsequently
   * check status by calling {@link Notebook#getStatus()}.
   *
   * @param wait if true, wait for operation to complete, otherwise return immediately.
   */
  abstract void start(boolean wait);

  /**
   * Start the notebook instance.
   *
   * <p>If wait is true, the call blocks until the notebook has reached status {@link
   * NotebookStatus#STOPPED}. Otherwise. the call returns immediately, and caller may subsequently
   * check status by calling {@link Notebook#getStatus()}.
   *
   * @param wait if true, wait for operation to complete, otherwise return immediately.
   */
  abstract void stop(boolean wait);

  /**
   * Gets current status of the Notebook instance.
   *
   * @return current status
   */
  abstract NotebookStatus getStatus();

  /**
   * Gets a link by which an authorized user can access the notebook instance.
   *
   * @return a URL providing access to the Notebook instance UI
   */
  abstract String getNotebookProxyUrl();
}
