package bio.terra.axonserver.utils;

import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceType;

public class ResourceUtils {

  private ResourceUtils() {}

  /**
   * Validate that a resource is of an expected type, throwing {@link InvalidResourceTypeException}
   * if it is not.
   *
   * @param expectedType the expected type of the resource
   * @param resource the resource to check
   * @throws {@link InvalidResourceTypeException} if the resource type is not as expected.
   */
  public static void validateResourceType(ResourceType expectedType, ResourceDescription resource) {
    ResourceType actualType = resource.getMetadata().getResourceType();
    if (actualType != expectedType) {
      throw new InvalidResourceTypeException(
          String.format(
              "Expected resource of type %s, got resource of type %s.", expectedType, actualType));
    }
  }
}
