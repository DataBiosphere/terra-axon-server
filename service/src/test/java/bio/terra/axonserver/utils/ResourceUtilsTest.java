package bio.terra.axonserver.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ResourceUtilsTest {

  @Test
  public void valid() {
    ResourceDescription resourceDescription = mock(ResourceDescription.class);
    ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
    Mockito.when(resourceDescription.getMetadata()).thenReturn(resourceMetadata);
    Mockito.when(resourceMetadata.getResourceType())
        .thenReturn(ResourceType.AWS_SAGEMAKER_NOTEBOOK);
    ResourceUtils.validateResourceType(ResourceType.AWS_SAGEMAKER_NOTEBOOK, resourceDescription);
  }

  @Test
  public void invalid() {
    ResourceDescription resourceDescription = mock(ResourceDescription.class);
    ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
    Mockito.when(resourceDescription.getMetadata()).thenReturn(resourceMetadata);
    Mockito.when(resourceMetadata.getResourceType())
        .thenReturn(ResourceType.AWS_SAGEMAKER_NOTEBOOK);
    assertThrows(
        InvalidResourceTypeException.class,
        () -> ResourceUtils.validateResourceType(ResourceType.AI_NOTEBOOK, resourceDescription));
  }
}
