package bio.terra.axonserver.utils.notebook;

import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.cloudres.aws.notebook.SageMakerNotebookCow;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class AwsSagemakerNotebook extends Notebook {
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

  public AwsSagemakerNotebook(
      WorkspaceManagerService workspaceManagerService,
      ResourceDescription resource,
      String accessToken) {
    this.instanceName =
        resource.getResourceAttributes().getAwsSageMakerNotebook().getInstanceName();

    this.sageMakerNotebookCow =
        SageMakerNotebookCow.create(
            clientConfig,
            getCredentialProvider(workspaceManagerService, resource, accessToken),
            resource.getMetadata().getControlledResourceMetadata().getRegion());
  }

  @Override
  public void start(boolean wait) {
    if (wait) {
      sageMakerNotebookCow.startAndWait(instanceName);
    } else {
      sageMakerNotebookCow.start(instanceName);
    }
  }

  @Override
  public void stop(boolean wait) {
    if (wait) {
      sageMakerNotebookCow.stopAndWait(instanceName);
    } else {
      sageMakerNotebookCow.stop(instanceName);
    }
  }

  private static NotebookStatus toNotebookStatus(NotebookInstanceStatus status) {
    return NotebookStatus.STATE_UNSPECIFIED;
  }

  @Override
  public NotebookStatus getStatus() {
    return toNotebookStatus(sageMakerNotebookCow.get(instanceName).notebookInstanceStatus());
  }

  @Override
  public String getNotebookProxyUrl() {
    return sageMakerNotebookCow.createPresignedUrl(instanceName);
  }
}
