package bio.terra.axonserver.utils.notebook;

import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.ResourceUtils;
import bio.terra.cloudres.aws.notebook.SageMakerNotebookCow;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import com.google.common.annotations.VisibleForTesting;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

/** Utility class for running common notebook operations on an AWS SageMaker Notebook instance. */
public class AwsSageMakerNotebook {
  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-axon-server").build();

  private final String instanceName;
  private final SageMakerNotebookCow sageMakerNotebookCow;

  private static AwsCredentialsProvider getCredentialProvider(
      WorkspaceManagerService workspaceManagerService,
      ResourceDescription resource,
      String accessToken) {
    ResourceMetadata resourceMetadata = resource.getMetadata();

    AwsCredentialAccessScope accessScope =
        WorkspaceManagerService.inferAwsCredentialAccessScope(
            workspaceManagerService.getHighestRole(
                resourceMetadata.getWorkspaceId(), IamRole.READER, accessToken));

    AwsCredential awsCredential =
        workspaceManagerService.getAwsSageMakerNotebookCredential(
            resourceMetadata.getWorkspaceId(),
            resourceMetadata.getResourceId(),
            accessScope,
            WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MIN,
            accessToken);

    return StaticCredentialsProvider.create(
        AwsSessionCredentials.create(
            awsCredential.getAccessKeyId(),
            awsCredential.getSecretAccessKey(),
            awsCredential.getSessionToken()));
  }

  /** For testing use only, allows use of mock {@link SageMakerNotebookCow}. */
  @VisibleForTesting
  public AwsSageMakerNotebook(String instanceName, SageMakerNotebookCow sageMakerNotebookCow) {
    this.instanceName = instanceName;
    this.sageMakerNotebookCow = sageMakerNotebookCow;
  }

  private AwsSageMakerNotebook(
      WorkspaceManagerService workspaceManagerService,
      ResourceDescription resource,
      String accessToken) {
    this(
        resource.getResourceAttributes().getAwsSageMakerNotebook().getInstanceName(),
        SageMakerNotebookCow.create(
            clientConfig,
            getCredentialProvider(workspaceManagerService, resource, accessToken),
            resource.getMetadata().getControlledResourceMetadata().getRegion()));
  }

  /** Factory method to create an instance of class {@link AwsSageMakerNotebook}. */
  public static AwsSageMakerNotebook create(
      WorkspaceManagerService workspaceManagerService,
      ResourceDescription resourceDescription,
      String accessToken) {
    ResourceUtils.validateResourceType(ResourceType.AWS_SAGEMAKER_NOTEBOOK, resourceDescription);
    return new AwsSageMakerNotebook(workspaceManagerService, resourceDescription, accessToken);
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
    if (wait) {
      sageMakerNotebookCow.startAndWait(instanceName);
    } else {
      sageMakerNotebookCow.start(instanceName);
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
    if (wait) {
      sageMakerNotebookCow.stopAndWait(instanceName);
    } else {
      sageMakerNotebookCow.stop(instanceName);
    }
  }

  private static NotebookStatus toNotebookStatus(NotebookInstanceStatus status) {
    return switch (status) {
      case DELETING -> NotebookStatus.DELETING;
      case FAILED -> NotebookStatus.FAILED;
      case IN_SERVICE -> NotebookStatus.ACTIVE;
      case PENDING -> NotebookStatus.PENDING;
      case STOPPED -> NotebookStatus.STOPPED;
      case STOPPING -> NotebookStatus.STOPPING;
      case UNKNOWN_TO_SDK_VERSION -> NotebookStatus.STATE_UNSPECIFIED;
      case UPDATING -> NotebookStatus.UPDATING;
    };
  }

  /**
   * Gets current status of the Notebook instance.
   *
   * @return current status
   */
  public NotebookStatus getStatus() {
    return toNotebookStatus(sageMakerNotebookCow.get(instanceName).notebookInstanceStatus());
  }

  /**
   * Gets a link by which an authorized user can access the notebook instance.
   *
   * @return a URL providing access to the Notebook instance UI
   */
  public String getProxyUrl() {
    return sageMakerNotebookCow.createPresignedUrl(instanceName);
  }
}
