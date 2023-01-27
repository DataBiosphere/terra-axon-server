package bio.terra.axonserver.service.file;

import bio.terra.axonserver.service.cloud.gcp.CloudStorageService;
import bio.terra.axonserver.service.convert.ConvertService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.iam.AuthenticatedUserRequest;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.GcpUtils;
import bio.terra.workspace.model.ResourceDescription;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
public class FileService {

  WorkspaceManagerService wsmService;
  ConvertService convertService;

  @Autowired
  public FileService(WorkspaceManagerService wsmService, ConvertService convertService) {
    this.wsmService = wsmService;
    this.convertService = convertService;
  }

  public ByteArrayResource getFile(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String objectPath,
      @Nullable String convertTo) {

    ResourceDescription resource = this.getResource(userRequest, workspaceId, resourceId);
    byte[] file = null;
    String fileName = null;
    switch (resource.getMetadata().getResourceType()) {
      case GCS_BUCKET -> {
        file = this.getGcpBucketFile(userRequest, resource, objectPath);
        fileName = objectPath;
      }
        // case GCS_OBJECT -> System.out.println("GCS_OBJECT");
      default -> throw new InvalidResourceTypeException(
          "Not a file containing resource: " + resource.getMetadata().getResourceType());
    }

    if (file != null && fileName != null && convertTo != null) {
      String fileExtension = FilenameUtils.getExtension(fileName);
      file = this.convertService.convert(file, fileExtension, convertTo, userRequest);
    }
    return new ByteArrayResource(file);
  }

  private byte[] getGcpBucketFile(
      AuthenticatedUserRequest authenticatedUserRequest,
      ResourceDescription resource,
      String objectPath) {
    GoogleCredentials googleCredentials =
        GcpUtils.getGoogleCredentialsFromUserRequest(authenticatedUserRequest);
    String bucketName = resource.getResourceAttributes().getGcpGcsBucket().getBucketName();
    Blob object =
        new CloudStorageService().getBucketObject(googleCredentials, bucketName, objectPath);

    return object.getContent();
  }

  private ResourceDescription getResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) {
    return this.wsmService.getResource(userRequest.getRequiredToken(), workspaceId, resourceId);
  }
}
