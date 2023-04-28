package bio.terra.axonserver.service.wsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import bio.terra.axonserver.app.configuration.WsmConfiguration;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkspaceManagerServiceTest extends BaseUnitTest {

  @Autowired private WsmConfiguration wsmConfiguration;
  @Autowired private WorkspaceManagerService workspaceManagerService;
  private final String fakeAccessToken = "fakeaccesstoken";
  private final AwsCredentialAccessScope accessScope = AwsCredentialAccessScope.WRITE_READ;
  private final Integer duration = WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MIN;
  private final ResourceDescription mockResourceDescription = mock(ResourceDescription.class);
  private final ResourceMetadata mockResourceMetadata = mock(ResourceMetadata.class);

  @Test
  public void getAccessScope() {
    assertThrows(
        ForbiddenException.class,
        () -> WorkspaceManagerService.inferAwsCredentialAccessScope(null));

    assertThrows(
        ForbiddenException.class,
        () -> WorkspaceManagerService.inferAwsCredentialAccessScope(IamRole.DISCOVERER));

    assertThrows(
        ForbiddenException.class,
        () -> WorkspaceManagerService.inferAwsCredentialAccessScope(IamRole.APPLICATION));

    assertEquals(
        AwsCredentialAccessScope.READ_ONLY,
        WorkspaceManagerService.inferAwsCredentialAccessScope(IamRole.READER));

    assertEquals(
        AwsCredentialAccessScope.WRITE_READ,
        WorkspaceManagerService.inferAwsCredentialAccessScope(IamRole.WRITER));

    assertEquals(
        AwsCredentialAccessScope.WRITE_READ,
        WorkspaceManagerService.inferAwsCredentialAccessScope(IamRole.OWNER));
  }

  @Test
  void getAwsResourceCredential_badResourceType() {
    Mockito.when(mockResourceDescription.getMetadata()).thenReturn(mockResourceMetadata);
    Mockito.when(mockResourceMetadata.getResourceType()).thenReturn(ResourceType.AI_NOTEBOOK);

    assertThrows(
        InvalidResourceTypeException.class,
        () ->
            workspaceManagerService.getAwsResourceCredential(
                mockResourceDescription, accessScope, duration, fakeAccessToken));
  }

  @Test
  void getAwsResourceCredential_s3StorageFolder() {
    Mockito.when(mockResourceDescription.getMetadata()).thenReturn(mockResourceMetadata);
    Mockito.when(mockResourceMetadata.getResourceType())
        .thenReturn(ResourceType.AWS_S3_STORAGE_FOLDER);

    UUID fakeWorkspaceId = UUID.randomUUID();
    UUID fakeResourceId = UUID.randomUUID();
    Mockito.when(mockResourceMetadata.getWorkspaceId()).thenReturn(fakeWorkspaceId);
    Mockito.when(mockResourceMetadata.getResourceId()).thenReturn(fakeResourceId);

    WorkspaceManagerService wsmServiceSpy = spy(workspaceManagerService);
    AwsCredential mockAwsCredential = mock(AwsCredential.class);
    doReturn(mockAwsCredential)
        .when(wsmServiceSpy)
        .getAwsS3StorageFolderCredential(any(), any(), any(), any(), any());

    AwsCredential outCredential =
        wsmServiceSpy.getAwsResourceCredential(
            mockResourceDescription, accessScope, duration, fakeAccessToken);
    assertEquals(mockAwsCredential, outCredential);

    ArgumentCaptor<String> tokenArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UUID> workspaceArgumentCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<UUID> resourceArgumentCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<Integer> durationArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<AwsCredentialAccessScope> accessScopeArgumentCaptor =
        ArgumentCaptor.forClass(AwsCredentialAccessScope.class);

    Mockito.verify(wsmServiceSpy)
        .getAwsS3StorageFolderCredential(
            workspaceArgumentCaptor.capture(),
            resourceArgumentCaptor.capture(),
            accessScopeArgumentCaptor.capture(),
            durationArgumentCaptor.capture(),
            tokenArgumentCaptor.capture());

    assertEquals(fakeAccessToken, tokenArgumentCaptor.getValue());
    assertEquals(fakeWorkspaceId, workspaceArgumentCaptor.getValue());
    assertEquals(fakeResourceId, resourceArgumentCaptor.getValue());
    assertEquals(AwsCredentialAccessScope.WRITE_READ, accessScopeArgumentCaptor.getValue());
    assertEquals(duration, durationArgumentCaptor.getValue());
  }
}
